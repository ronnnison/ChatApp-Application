package chatapp.client;

import chatapp.common.MessageType;
import chatapp.common.Packet;
import chatapp.common.Payloads;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.swing.SwingWorker;

/**
 * Downloads files (photos, voice notes, documents) on demand and remembers the latest ones.
 * Several widgets may ask for the same file; the request is sent once and all of them are called
 * back. Used on the Swing thread only. Uses a generic LRU map (a LinkedHashMap in access order).
 */
public class MediaCache {
    private static final int THUMB_W = 300;
    private static final int THUMB_H = 260;

    private final ClientConnection conn;

    private final Map<Long, Payloads.FileData> files = lru(12);
    private final Map<Long, BufferedImage> thumbs = lru(80);
    private final Map<Long, List<Consumer<Payloads.FileData>>> waiting = new HashMap<>();

    public MediaCache(ClientConnection conn) {
        this.conn = conn;
    }

    private static <K, V> Map<K, V> lru(int max) {
        return new LinkedHashMap<K, V>(16, 0.75f, true) {
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > max;
            }
        };
    }

    /** Calls {@code callback} (on the Swing thread) as soon as the file bytes are available. */
    public void fetch(long fileId, Consumer<Payloads.FileData> callback) {
        Payloads.FileData cached = files.get(fileId);
        if (cached != null) {
            callback.accept(cached);
            return;
        }
        List<Consumer<Payloads.FileData>> list = waiting.get(fileId);
        if (list == null) {
            list = new ArrayList<>();
            waiting.put(fileId, list);
            conn.send(new Packet(MessageType.DOWNLOAD_FILE, Long.valueOf(fileId)));
        }
        list.add(callback);
    }

    public void onFileData(Payloads.FileData data) {
        files.put(data.fileId, data);
        List<Consumer<Payloads.FileData>> list = waiting.remove(data.fileId);
        if (list != null) list.forEach(c -> c.accept(data));
    }

    /** Called when the server answered a request with an error, so buttons do not wait forever. */
    public void cancelWaiting() {
        waiting.clear();
    }

    /** Small preview image for a photo message (decoded off the Swing thread). null = not an image. */
    public void thumbnail(long fileId, Consumer<BufferedImage> callback) {
        BufferedImage t = thumbs.get(fileId);
        if (t != null) {
            callback.accept(t);
            return;
        }
        fetch(fileId, data -> new SwingWorker<BufferedImage, Void>() {
            @Override
            protected BufferedImage doInBackground() throws Exception {
                BufferedImage src = ImageIO.read(new ByteArrayInputStream(data.data));
                if (src == null) return null;
                double scale = Math.min(1.0, Math.min((double) THUMB_W / src.getWidth(), (double) THUMB_H / src.getHeight()));
                int w = Math.max(1, (int) Math.round(src.getWidth() * scale));
                int h = Math.max(1, (int) Math.round(src.getHeight() * scale));
                Image scaled = src.getScaledInstance(w, h, Image.SCALE_SMOOTH);
                BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = out.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.drawImage(scaled, 0, 0, null);
                g.dispose();
                return out;
            }

            @Override
            protected void done() {
                BufferedImage result = null;
                try {
                    result = get();
                } catch (Exception ignored) {
                }
                if (result != null) thumbs.put(fileId, result);
                callback.accept(result);
            }
        }.execute());
    }
}
