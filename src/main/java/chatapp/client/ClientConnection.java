package chatapp.client;

import chatapp.common.MessageType;
import chatapp.common.Packet;
import chatapp.common.Protocol;
import chatapp.common.TlsSupport;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;


public class ClientConnection {
    private final BlockingQueue<Packet> outbox = new LinkedBlockingQueue<>();
    private final List<Consumer<Packet>> listeners = new CopyOnWriteArrayList<>();

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private Thread readerThread;
    private Thread writerThread;
    private volatile boolean connected;

    private volatile boolean encrypted;

    public boolean isConnected() { return connected; }

    /** True when this connection runs over TLS. */
    public boolean isEncrypted() { return encrypted; }

    private static void closeQuietly(Socket s) {
        try {
            s.close();
        } catch (IOException ignored) {
        }
    }

    public synchronized void connect(String host, int port) throws IOException {
        if (connected) return;
        Socket raw = new Socket();
        Socket s = raw;
        ObjectOutputStream o;
        ObjectInputStream i;
        try {
            raw.connect(new InetSocketAddress(host, port), 4000);
            raw.setKeepAlive(true);
            raw.setSoTimeout(4000); // only for the handshake below
            s = TlsSupport.wrapClient(raw, host, port); // TLS when chatapp-server.crt is present
            o = new ObjectOutputStream(s.getOutputStream());
            o.flush();
            i = Protocol.secureInput(s.getInputStream());
            s.setSoTimeout(0);
        } catch (java.net.ConnectException | java.net.UnknownHostException
                 | java.net.NoRouteToHostException e) {
            closeQuietly(raw);
            throw e; // plain "cannot reach" problems keep their own message
        } catch (IOException e) {
            closeQuietly(raw);
            throw new IOException(TlsSupport.clientEnabled()
                    ? "Encrypted connection failed. Does the server use encryption, and is chatapp-server.crt the host's certificate?"
                    : "The server gave no valid reply. If the host turned on encryption, copy chatapp-server.crt next to ChatClient.jar.", e);
        }
        encrypted = s instanceof javax.net.ssl.SSLSocket;

        socket = s;
        out = o;
        in = i;
        outbox.clear();
        connected = true;

        readerThread = new Thread(this::readLoop, "client-reader");
        readerThread.setDaemon(true);
        readerThread.start();
        writerThread = new Thread(this::writeLoop, "client-writer");
        writerThread.setDaemon(true);
        writerThread.start();
    }

    
    public void connectTo(String spec) throws IOException {
        String host = spec == null ? "" : spec.trim();
        int port = Protocol.PORT;
        int idx = host.lastIndexOf(':');
        if (idx > 0 && host.indexOf(':') == idx) { // exactly one colon = host:port (not IPv6)
            try {
                port = Integer.parseInt(host.substring(idx + 1).trim());
            } catch (NumberFormatException e) {
                throw new IOException("Invalid port in \"" + spec + "\"");
            }
            host = host.substring(0, idx).trim();
        }
        if (host.isEmpty()) host = "localhost";
        connect(host, port);
    }

    public void send(Packet packet) {
        outbox.offer(packet);
    }

    public void addListener(Consumer<Packet> l) { listeners.add(l); }
    public void removeListener(Consumer<Packet> l) { listeners.remove(l); }

    public synchronized void close() {
        connected = false;
        if (writerThread != null) writerThread.interrupt();
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        }
    }

    private void readLoop() {
        try {
            while (connected) {
                Object o = in.readObject();
                if (o instanceof Packet) dispatch((Packet) o);
            }
        } catch (IOException | ClassNotFoundException e) {
            // connection ended
        } finally {
            if (connected) {
                connected = false;
                dispatch(new Packet(MessageType.DISCONNECTED));
            }
            close();
        }
    }

    private void writeLoop() {
        try {
            while (connected) {
                Packet p = outbox.take();
                out.writeObject(p);
                out.flush();
                out.reset();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            close();
        }
    }

    private void dispatch(Packet p) {
        SwingUtilities.invokeLater(() -> {
            for (Consumer<Packet> l : listeners) l.accept(p);
        });
    }
}
