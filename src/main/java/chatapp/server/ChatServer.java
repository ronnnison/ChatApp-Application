package chatapp.server;

import chatapp.common.MessageType;
import chatapp.common.Packet;
import chatapp.common.TlsSupport;
import chatapp.common.UserDTO;
import chatapp.db.ConversationDao;
import chatapp.db.DataCipher;
import chatapp.db.FileDao;
import chatapp.db.MessageDao;
import chatapp.db.UserDao;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Accepts TCP connections and starts one {@link ClientHandler} thread per client.
 *
 * CONCURRENCY / DEADLOCK PREVENTION: shared state lives in ConcurrentHashMap (lock-free reads,
 * no global lock). No method here ever holds two locks at once, and sending to a client only
 * puts the packet on that client's own queue, so one slow client can never block the others.
 */
public class ChatServer implements Runnable {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final int port;
    private final Consumer<String> logger;

    private final Map<Long, ClientHandler> online = new ConcurrentHashMap<>();
    private final Set<ClientHandler> handlers = ConcurrentHashMap.newKeySet();

    private final UserDao userDao = new UserDao();
    private final ConversationDao conversationDao = new ConversationDao();
    private final MessageDao messageDao = new MessageDao();
    private final FileDao fileDao = new FileDao();
    private final Path uploadDir = Paths.get("server_uploads");

    private volatile ServerSocket serverSocket;
    private volatile boolean running;
    private Thread acceptThread;

    public ChatServer(int port, Consumer<String> logger) {
        this.port = port;
        this.logger = logger;
    }

    public synchronized void start() throws IOException {
        if (running) return;
        Files.createDirectories(uploadDir);
        DataCipher.get(); // loads or creates chatapp.key now, so a problem shows up at start-up
        serverSocket = TlsSupport.createServerSocket(port); // TLS when chatapp-server.p12 exists
        running = true;
        acceptThread = new Thread(this, "accept-loop");
        acceptThread.start();
        log("Server listening on port " + port);
        log("Encryption in transit: " + (TlsSupport.serverEnabled()
                ? "TLS ON (clients need chatapp-server.crt)"
                : "OFF, plain TCP (add chatapp-server.p12 to turn TLS on, see README)"));
        log("Encryption at rest: AES-256-GCM, key file " + DataCipher.keyFileName() + "  (keep a backup!)");
    }

    @Override
    public void run() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                socket.setKeepAlive(true);
                ClientHandler handler = new ClientHandler(socket, this);
                handlers.add(handler);
                handler.start();
                log("Connection from " + socket.getRemoteSocketAddress());
            } catch (IOException e) {
                if (running) log("Accept error: " + e.getMessage());
            }
        }
    }

    public synchronized void stop() {
        if (!running) return;
        running = false;
        try {
            serverSocket.close(); // unblocks accept()
        } catch (IOException ignored) {
        }
        Iterator<ClientHandler> it = handlers.iterator();
        while (it.hasNext()) {
            it.next().shutdown();
            it.remove();
        }
        online.clear();
        log("Server stopped.");
    }

    public boolean isRunning() { return running; }

    // ---------- online users ----------

    /** @return true if the user was not online yet (one session per account). */
    boolean registerOnline(long userId, ClientHandler handler) {
        return online.putIfAbsent(userId, handler) == null;
    }

    void unregister(long userId, ClientHandler handler) {
        online.remove(userId, handler);
    }

    void removeHandler(ClientHandler handler) {
        handlers.remove(handler);
    }

    boolean isOnline(long userId) {
        return online.containsKey(userId);
    }

    /** STREAMS: names of everybody currently logged in, alphabetically. */
    public List<String> onlineUsernames() {
        return online.values().stream()
                .map(ClientHandler::getUser)
                .filter(Objects::nonNull)
                .map(UserDTO::getUsername)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    // ---------- sending ----------

    /** Delivers a packet to every online member of a conversation (including the sender). */
    void broadcast(long conversationId, Packet packet) {
        try {
            conversationDao.memberIds(conversationId).stream()
                    .map(online::get)
                    .filter(Objects::nonNull)
                    .forEach(h -> h.send(packet));
        } catch (SQLException e) {
            log("DB error while broadcasting: " + e.getMessage());
        }
    }

    /** Delivers a packet to every online user (used when someone changes their profile picture). */
    void broadcastAll(Packet packet) {
        online.values().forEach(h -> h.send(packet));
    }

    /** Pushes a fresh conversation list to a user, if that user is online. */
    void pushConversations(long userId) {
        ClientHandler h = online.get(userId);
        if (h == null) return;
        try {
            h.send(new Packet(MessageType.CONVERSATIONS, new ArrayList<>(conversationDao.listForUser(userId))));
        } catch (SQLException e) {
            log("DB error while pushing conversations: " + e.getMessage());
        }
    }

    // ---------- shared services ----------

    UserDao users() { return userDao; }
    ConversationDao conversations() { return conversationDao; }
    MessageDao messages() { return messageDao; }
    FileDao files() { return fileDao; }
    Path uploadDir() { return uploadDir; }

    public void log(String message) {
        logger.accept(LocalTime.now().format(TIME) + "  " + message);
    }
}
