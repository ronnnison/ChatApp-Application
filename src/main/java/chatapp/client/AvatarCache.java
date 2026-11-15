package chatapp.client;

import chatapp.common.MessageType;
import chatapp.common.Packet;
import chatapp.common.Payloads;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;

/**
 * Keeps every profile picture that was already downloaded. A picture is requested from the server
 * the first time somebody needs to draw it; when it arrives all windows repaint.
 * Only used on the Swing thread, so no locking is needed.
 */
public final class AvatarCache {
    private static AvatarCache instance;

    /** Starts a cache for this connection (keeps the old one when it is the same connection). */
    public static synchronized void attach(ClientConnection conn) {
        if (instance == null || instance.conn != conn) instance = new AvatarCache(conn);
    }

    public static synchronized AvatarCache get() {
        return instance;
    }

    private final ClientConnection conn;
    private final Map<Long, BufferedImage> images = new HashMap<>();
    private final Set<Long> noPicture = new HashSet<>();
    private final Set<Long> requested = new HashSet<>();

    private AvatarCache(ClientConnection conn) {
        this.conn = conn;
    }

    /** @return the picture, or null (and a request is sent once) when it is not here yet or does not exist. */
    public BufferedImage image(long userId) {
        BufferedImage img = images.get(userId);
        if (img == null && !noPicture.contains(userId) && requested.add(userId)) {
            conn.send(new Packet(MessageType.GET_AVATAR, Long.valueOf(userId)));
        }
        return img;
    }

    public void onAvatar(Payloads.AvatarData data) {
        requested.remove(data.userId);
        if (data.png == null) {
            images.remove(data.userId);
            noPicture.add(data.userId);
        } else {
            try {
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(data.png));
                if (img != null) {
                    images.put(data.userId, img);
                    noPicture.remove(data.userId);
                }
            } catch (IOException ignored) {
            }
        }
        for (Window w : Window.getWindows()) w.repaint();
    }
}
