package chatapp.server;

import chatapp.common.ChatMessageDTO;
import chatapp.common.ConversationDTO;
import chatapp.common.AvatarUtil;
import chatapp.common.MessageType;
import chatapp.common.Packet;
import chatapp.common.Payloads;
import chatapp.common.Protocol;
import chatapp.common.UserDTO;
import chatapp.common.Validators;
import chatapp.db.DataCipher;
import chatapp.db.FileDao;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;


public class ClientHandler extends Thread {
    private static final int HISTORY_LIMIT = 200;

    private final Socket socket;
    private final ChatServer server;
    private final BlockingQueue<Packet> outbox = new LinkedBlockingQueue<>();

    private ObjectOutputStream out;
    private ObjectInputStream in;
    private Thread writer;
    private volatile boolean active = true;
    private volatile UserDTO user;

    ClientHandler(Socket socket, ChatServer server) {
        super("client-" + socket.getRemoteSocketAddress());
        this.socket = socket;
        this.server = server;
    }

    UserDTO getUser() { return user; }

   
    void send(Packet packet) {
        if (active) outbox.offer(packet);
    }

    void shutdown() {
        active = false;
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    // ------------------------------------------------------------------ threads

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = Protocol.secureInput(socket.getInputStream());
            writer = new Thread(this::writeLoop, getName() + "-writer");
            writer.setDaemon(true);
            writer.start();

            while (active) {
                Object o = in.readObject();
                if (!(o instanceof Packet)) continue;
                try {
                    handle((Packet) o);
                } catch (SQLException e) {
                    server.log("Database error: " + e.getMessage());
                    error("Database error. Is MySQL running?");
                } catch (IOException e) {
                    server.log("File error: " + e.getMessage());
                    error("The server could not process the file.");
                } catch (RuntimeException e) {
                    server.log("Bad request: " + e);
                    error("Malformed request.");
                }
            }
        } catch (EOFException | SocketException e) {
            // client closed the connection - normal
        } catch (IOException | ClassNotFoundException e) {
            server.log("Connection error: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    private void writeLoop() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Packet p = outbox.take(); // blocks until something is queued
                out.writeObject(p);
                out.flush();
                out.reset(); // do not keep references to old packets (prevents memory growth)
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // we were told to stop
        } catch (IOException e) {
            shutdown();
        }
    }

    private void cleanup() {
        active = false;
        UserDTO u = user;
        if (u != null) {
            server.unregister(u.getId(), this);
            server.log(u.getUsername() + " disconnected");
        }
        server.removeHandler(this);
        if (writer != null) writer.interrupt();
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    // ------------------------------------------------------------------ requests

    private void handle(Packet p) throws SQLException, IOException {
        MessageType type = p.getType();
        if (type == MessageType.REGISTER) { handleRegister(p.getPayload()); return; }
        if (type == MessageType.LOGIN) { handleLogin(p.getPayload()); return; }
        if (user == null) { error("Please log in first."); return; }

        switch (type) {
            case LOGOUT:
                shutdown();
                break;
            case GET_CONVERSATIONS:
                server.pushConversations(user.getId());
                break;
            case SEARCH_USERS:
                handleSearch(p.getPayload());
                break;
            case OPEN_PRIVATE:
                handleOpenPrivate(p.getPayload());
                break;
            case CREATE_GROUP:
                handleCreateGroup(p.getPayload());
                break;
            case ADD_MEMBER:
                handleAddMember(p.getPayload());
                break;
            case GET_HISTORY:
                handleHistory(p.getPayload());
                break;
            case SEND_TEXT:
                handleSendText(p.getPayload());
                break;
            case SEND_FILE:
                handleSendFile(p.getPayload());
                break;
            case DOWNLOAD_FILE:
                handleDownload(p.getPayload());
                break;
            case GET_AVATAR:
                handleGetAvatar(p.getPayload());
                break;
            case UPDATE_AVATAR:
                handleUpdateAvatar(p.<byte[]>getPayload());
                break;
            default:
                error("Unsupported request.");
        }
    }

    private void handleRegister(Payloads.Credentials c) throws SQLException {
        String problem = Validators.validateRegistration(c);
        byte[] avatar = null;
        if (problem == null && c.avatar != null && c.avatar.length > 0) {
            avatar = AvatarUtil.normalize(c.avatar); // re-checked and re-encoded on the server
            if (avatar == null) problem = "That profile picture could not be read. Try a JPG or PNG.";
        }
        if (problem == null) problem = server.users().register(c, avatar);
        if (problem != null) {
            send(new Packet(MessageType.REGISTER_FAIL, problem));
        } else {
            server.log("New account: " + c.username);
            send(new Packet(MessageType.REGISTER_OK, "Account created. You can sign in now."));
        }
    }

    private void handleLogin(Payloads.Credentials c) throws SQLException {
        if (user != null) { error("Already logged in."); return; }
        String username = c.username == null ? "" : c.username.trim();
        String password = c.password == null ? "" : c.password;
        String ip = ((InetSocketAddress) socket.getRemoteSocketAddress()).getAddress().getHostAddress();

        if (server.users().recentFailures(username) >= 5) {
            send(new Packet(MessageType.AUTH_FAIL, "Too many failed attempts. Try again in 5 minutes."));
            return;
        }
        UserDTO u = server.users().authenticate(username, password);
        server.users().logAttempt(u == null ? null : u.getId(), username, u != null, ip);
        if (u == null) {
            send(new Packet(MessageType.AUTH_FAIL, "Invalid username or password."));
            return;
        }
        user = u;
        if (!server.registerOnline(u.getId(), this)) {
            user = null;
            send(new Packet(MessageType.AUTH_FAIL, "This account is already signed in somewhere else."));
            return;
        }
        u.setOnline(true);
        server.log(u.getUsername() + " signed in from " + ip);
        send(new Packet(MessageType.AUTH_OK, u));
    }

    private void handleSearch(String query) throws SQLException {
        List<UserDTO> found = server.users().search(query == null ? "" : query, user.getId());
        found.forEach(u -> u.setOnline(server.isOnline(u.getId())));
        send(new Packet(MessageType.USERS, new ArrayList<>(found)));
    }

    private void handleOpenPrivate(String username) throws SQLException {
        Optional<UserDTO> other = server.users().findByUsername(username == null ? "" : username);
        if (!other.isPresent()) { error("User not found."); return; }
        if (other.get().getId() == user.getId()) { error("You cannot chat with yourself."); return; }
        long convId = server.conversations().getOrCreatePrivate(user.getId(), other.get().getId());
        server.pushConversations(user.getId());
        server.pushConversations(other.get().getId());
        sendOpened(convId);
    }

    private void handleCreateGroup(Payloads.GroupRequest g) throws SQLException {
        String name = g.name == null ? "" : g.name.trim();
        if (name.isEmpty() || name.length() > 50) { error("Group name must be 1-50 characters."); return; }
        Set<Long> ids = new LinkedHashSet<>();
        if (g.members != null) {
            for (String username : new LinkedHashSet<>(g.members)) {
                Optional<UserDTO> m = server.users().findByUsername(username);
                if (m.isPresent() && m.get().getId() != user.getId()) ids.add(m.get().getId());
            }
        }
        if (ids.isEmpty()) { error("Pick at least one other member."); return; }
        if (ids.size() > 50) { error("A group can start with at most 50 members."); return; }

        long convId = server.conversations().createGroup(name, user.getId(), new ArrayList<>(ids));
        server.pushConversations(user.getId());
        ids.forEach(server::pushConversations);
        sendOpened(convId);
        server.log(user.getUsername() + " created group \"" + name + "\"");
    }

    private void handleAddMember(Payloads.AddMemberRequest r) throws SQLException {
        Optional<ConversationDTO> conv = server.conversations().findById(r.conversationId);
        if (!conv.isPresent() || !conv.get().isGroup()) { error("Group not found."); return; }
        if (!server.conversations().isAdmin(r.conversationId, user.getId())) {
            error("Only the group admin can add members.");
            return;
        }
        Optional<UserDTO> target = server.users().findByUsername(r.username == null ? "" : r.username);
        if (!target.isPresent()) { error("User not found."); return; }
        if (server.conversations().isMember(r.conversationId, target.get().getId())) {
            error(target.get().getFullName() + " is already in this group.");
            return;
        }
        server.conversations().addMember(r.conversationId, target.get().getId());
        for (long memberId : server.conversations().memberIds(r.conversationId)) {
            server.pushConversations(memberId); // everyone sees the new member count
        }
        send(new Packet(MessageType.INFO, target.get().getFullName() + " was added to the group."));
    }

    private void handleHistory(Long conversationId) throws SQLException {
        if (conversationId == null || !server.conversations().isMember(conversationId, user.getId())) {
            error("You are not a member of this conversation.");
            return;
        }
        ArrayList<ChatMessageDTO> messages = new ArrayList<>(server.messages().history(conversationId, HISTORY_LIMIT));
        send(new Packet(MessageType.HISTORY, new Payloads.History(conversationId, messages)));
    }

    private void handleSendText(Payloads.TextRequest t) throws SQLException {
        String text = t.text == null ? "" : t.text.trim();
        if (text.isEmpty()) return;
        if (text.length() > Protocol.MAX_TEXT_LENGTH) { error("Message too long."); return; }
        if (!server.conversations().isMember(t.conversationId, user.getId())) { error("Not a member."); return; }
        long id = server.messages().insert(t.conversationId, user.getId(), ChatMessageDTO.Kind.TEXT, text, 0);
        server.messages().findById(id).ifPresent(m -> server.broadcast(t.conversationId, new Packet(MessageType.NEW_MESSAGE, m)));
    }

    private void handleSendFile(Payloads.FileUpload f) throws SQLException, IOException {
        if (!server.conversations().isMember(f.conversationId, user.getId())) { error("Not a member."); return; }
        if (f.data == null || f.data.length == 0) { error("The file is empty."); return; }
        if (f.data.length > Protocol.MAX_FILE_BYTES) {
            error("File too large (max " + Protocol.humanSize(Protocol.MAX_FILE_BYTES) + ").");
            return;
        }
        String cleanName = FileValidator.sanitizeName(f.fileName);
        ChatMessageDTO.Kind kind = FileValidator.detectKind(cleanName, f.data);
        if (kind == null) { error("Only .docx documents and PNG/JPG/GIF/BMP images are allowed."); return; }

        String ext = FileValidator.extension(cleanName);
        String storedName = UUID.randomUUID() + "." + ext; // never trust the client's name for disk paths
        Files.write(server.uploadDir().resolve(storedName), DataCipher.get().encryptBytes(f.data)); // encrypted on disk

        long fileId = server.files().save(user.getId(), cleanName, storedName, FileValidator.mimeType(ext), f.data.length);
        long msgId = server.messages().insert(f.conversationId, user.getId(), kind, null, fileId);
        server.messages().findById(msgId).ifPresent(m -> server.broadcast(f.conversationId, new Packet(MessageType.NEW_MESSAGE, m)));
        server.log(user.getUsername() + " shared " + cleanName + " (" + Protocol.humanSize(f.data.length) + ")");
    }

    private void handleDownload(Long fileId) throws SQLException, IOException {
        if (fileId == null) return;
        Optional<FileDao.FileRecord> rec = server.files().findForUser(fileId, user.getId());
        if (!rec.isPresent()) { error("File not found or access denied."); return; }
        byte[] data = DataCipher.get().decryptBytes(
                Files.readAllBytes(server.uploadDir().resolve(rec.get().storedName)));
        send(new Packet(MessageType.FILE_DATA, new Payloads.FileData(fileId, rec.get().originalName, data)));
    }

    private void handleGetAvatar(Long userId) throws SQLException {
        if (userId == null) return;
        send(new Packet(MessageType.AVATAR, new Payloads.AvatarData(userId, server.users().getAvatar(userId))));
    }

    private void handleUpdateAvatar(byte[] raw) throws SQLException {
        byte[] png = AvatarUtil.normalize(raw);
        if (png == null) { error("That picture could not be read. Try a JPG or PNG."); return; }
        server.users().setAvatar(user.getId(), png);
        // everybody online refreshes their cached copy immediately
        server.broadcastAll(new Packet(MessageType.AVATAR, new Payloads.AvatarData(user.getId(), png)));
        server.log(user.getUsername() + " changed their profile picture");
    }

    // ------------------------------------------------------------------ helpers

    private void sendOpened(long convId) throws SQLException {
        server.conversations().listForUser(user.getId()).stream()
                .filter(c -> c.getId() == convId)
                .findFirst()
                .ifPresent(c -> send(new Packet(MessageType.CONVERSATION_OPENED, c)));
    }

    private void error(String message) {
        send(new Packet(MessageType.ERROR, message));
    }
}
