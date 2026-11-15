package chatapp.common;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;

/**
 * Encryption IN TRANSIT (TLS), switched on simply by the presence of two files:
 *
 *  - on the SERVER PC:  chatapp-server.p12  (the server's private key + certificate)
 *  - on every CLIENT PC: chatapp-server.crt (the matching public certificate)
 *
 * With the files in place, everything between client and server (passwords, messages, photos,
 * voice notes) is encrypted and the client only trusts that exact certificate, so nobody on the
 * Wi-Fi can read it or pose as the server. Without the files the app works exactly as before
 * (plain TCP). See README for the two keytool commands that create the files.
 */
public final class TlsSupport {
    private TlsSupport() { }

    public static final String KEYSTORE_FILE = "chatapp-server.p12";
    public static final String CERT_FILE = "chatapp-server.crt";

    // ------------------------------------------------------------------ server side

    public static boolean serverEnabled() {
        return Files.isRegularFile(keystorePath());
    }

    private static Path keystorePath() {
        return Paths.get(System.getProperty("chatapp.keystore", KEYSTORE_FILE));
    }

    /** A TLS server socket when the keystore file exists, otherwise a normal one. */
    public static ServerSocket createServerSocket(int port) throws IOException {
        if (!serverEnabled()) return new ServerSocket(port);
        String password = System.getProperty("chatapp.keystore.password", System.getenv("CHAT_TLS_PASSWORD"));
        char[] pw = (password == null || password.isEmpty() ? "changeit" : password).toCharArray();
        try {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            try (InputStream in = Files.newInputStream(keystorePath())) {
                ks.load(in, pw);
            }
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, pw);
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(kmf.getKeyManagers(), null, null);
            SSLServerSocket server = (SSLServerSocket) ctx.getServerSocketFactory().createServerSocket(port);
            server.setEnabledProtocols(modernProtocols(server.getSupportedProtocols()));
            return server;
        } catch (GeneralSecurityException | IOException e) {
            throw new IOException("Could not start TLS with " + keystorePath() + " (wrong password? set CHAT_TLS_PASSWORD): "
                    + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------ client side

    /** Looks next to the working directory, next to the jar file, or at -Dchatapp.cert=... */
    public static Path findCertificate() {
        List<Path> candidates = new ArrayList<>();
        String configured = System.getProperty("chatapp.cert");
        if (configured != null && !configured.isEmpty()) candidates.add(Paths.get(configured));
        candidates.add(Paths.get(CERT_FILE));
        try {
            Path jar = Paths.get(TlsSupport.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path dir = Files.isDirectory(jar) ? jar : jar.getParent();
            if (dir != null) candidates.add(dir.resolve(CERT_FILE));
        } catch (Exception ignored) {
            // running from an unusual class loader: the other locations still work
        }
        for (Path p : candidates) if (Files.isRegularFile(p)) return p;
        return null;
    }

    public static boolean clientEnabled() {
        return findCertificate() != null;
    }

    /** Wraps a connected socket in TLS when a certificate file is present, otherwise returns it unchanged. */
    public static Socket wrapClient(Socket plain, String host, int port) throws IOException {
        Path cert = findCertificate();
        if (cert == null) return plain;
        try (InputStream in = Files.newInputStream(cert)) {
            X509Certificate certificate = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
            KeyStore trust = KeyStore.getInstance(KeyStore.getDefaultType());
            trust.load(null, null);
            trust.setCertificateEntry("chatapp-server", certificate); // pinned: ONLY this certificate is trusted
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trust);
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, tmf.getTrustManagers(), null);
            SSLSocket ssl = (SSLSocket) ctx.getSocketFactory().createSocket(plain, host, port, true);
            ssl.setEnabledProtocols(modernProtocols(ssl.getSupportedProtocols()));
            ssl.startHandshake();
            return ssl;
        } catch (GeneralSecurityException e) {
            throw new IOException("Secure connection failed: " + e.getMessage(), e);
        }
    }

    private static String[] modernProtocols(String[] supported) {
        List<String> ok = new ArrayList<>();
        for (String p : new String[]{"TLSv1.3", "TLSv1.2"}) {
            if (Arrays.asList(supported).contains(p)) ok.add(p);
        }
        return ok.toArray(new String[0]);
    }
}
