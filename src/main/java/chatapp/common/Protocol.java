package chatapp.common;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;

/** Constants and helpers shared by client and server. */
public final class Protocol {
    private Protocol() { }

    /**
     * The name of the application. CHANGE IT HERE ONLY: it is used in the title bar of the
     * login window, the chat window and the server window, and on the login screen banner.
     */
    public static final String APP_NAME = "ChatApp";

    public static final int PORT = 5555;
    public static final int MAX_FILE_BYTES = 10 * 1024 * 1024; // 10 MB
    public static final int MAX_TEXT_LENGTH = 2000;

    /**
     * Security: Java deserialization can be abused, so only our own classes and a few
     * java.util / java.lang types are accepted. Anything else closes the connection.
     */
    private static final ObjectInputFilter FILTER = ObjectInputFilter.Config.createFilter(
            "maxdepth=20;maxarray=" + (MAX_FILE_BYTES + 1024)
                    + ";chatapp.common.*;java.util.*;java.lang.*;!*");

    public static ObjectInputStream secureInput(InputStream in) throws IOException {
        ObjectInputStream ois = new ObjectInputStream(in);
        ois.setObjectInputFilter(FILTER);
        return ois;
    }

    public static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
