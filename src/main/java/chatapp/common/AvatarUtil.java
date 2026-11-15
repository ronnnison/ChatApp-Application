package chatapp.common;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * Profile-picture helper used by BOTH sides. The client shrinks the chosen photo before upload
 * (so only ~40 KB travels), and the server runs the very same code again, so a modified client
 * can never store a huge or broken image.
 */
public final class AvatarUtil {
    private AvatarUtil() { }

    public static final int SIZE = 192;
    /** Largest raw input the server accepts (clients send already-shrunk pictures). */
    public static final int MAX_INPUT_BYTES = 4 * 1024 * 1024;
    private static final long MAX_PIXELS = 16_000_000L;

    static {
        ImageIO.setUseCache(false);
    }

    public static byte[] normalize(byte[] input) {
        return normalize(input, MAX_INPUT_BYTES);
    }

    /**
     * Decodes any image, crops the centre square, scales it to 192x192 and returns PNG bytes.
     * @return PNG bytes, or null when the data is not a readable image or is unreasonably large
     */
    public static byte[] normalize(byte[] input, int maxBytes) {
        if (input == null || input.length == 0 || input.length > maxBytes) return null;
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(input))) {
            if (iis == null) return null;
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) return null;
            ImageReader reader = readers.next();
            BufferedImage src;
            try {
                reader.setInput(iis, true, true);
                int w = reader.getWidth(0);
                int h = reader.getHeight(0);
                // check the size BEFORE decoding, so a tiny file cannot expand into gigabytes
                if (w < 16 || h < 16 || (long) w * h > MAX_PIXELS) return null;
                src = reader.read(0);
            } finally {
                reader.dispose();
            }
            if (src == null) return null;

            int side = Math.min(src.getWidth(), src.getHeight());
            BufferedImage square = src.getSubimage((src.getWidth() - side) / 2, (src.getHeight() - side) / 2, side, side);
            Image scaled = square.getScaledInstance(SIZE, SIZE, Image.SCALE_SMOOTH);
            BufferedImage out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(scaled, 0, 0, null);
            g.dispose();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            ImageIO.write(out, "png", bo);
            return bo.toByteArray();
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
