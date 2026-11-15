package chatapp.client;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;

/**
 * Records the microphone into a WAV file (16 kHz, 16-bit, mono = 32 KB per second).
 * A background thread reads audio from the microphone while the GUI thread stays responsive.
 */
public class VoiceRecorder {
    public static final AudioFormat FORMAT = new AudioFormat(16000f, 16, 1, true, false);
    public static final int BYTES_PER_SECOND = 32000;
    public static final int MAX_SECONDS = 120;

    private final ByteArrayOutputStream pcm = new ByteArrayOutputStream();
    private TargetDataLine line;
    private Thread thread;
    private volatile boolean recording;
    private volatile int bytes;
    private volatile int peak;     // loudest sample seen so far (0 - 32768)
    private volatile float level;  // loudness of the last 0.1 s (0.0 - 1.0), for the live meter

    public void start() throws LineUnavailableException {
        line = AudioSystem.getTargetDataLine(FORMAT);
        line.open(FORMAT);
        line.start();
        recording = true;
        thread = new Thread(this::capture, "voice-recorder");
        thread.setDaemon(true);
        thread.start();
    }

    private void capture() {
        byte[] chunk = new byte[3200]; // 0.1 second
        while (recording && !Thread.currentThread().isInterrupted()) {
            int n = line.read(chunk, 0, chunk.length); // blocks until the microphone delivers data
            if (n > 0) {
                measure(chunk, n);
                synchronized (pcm) {
                    pcm.write(chunk, 0, n);
                    bytes = pcm.size();
                }
                if (bytes >= MAX_SECONDS * BYTES_PER_SECOND) recording = false;
            }
        }
    }

    /** Updates the peak and the live level from one chunk of 16-bit little-endian samples. */
    private void measure(byte[] chunk, int n) {
        int max = 0;
        double sum = 0;
        int samples = n / 2;
        for (int i = 0; i + 1 < n; i += 2) {
            int v = (short) ((chunk[i + 1] << 8) | (chunk[i] & 0xFF));
            max = Math.max(max, Math.abs(v));
            sum += (double) v * v;
        }
        if (max > peak) peak = max;
        double rms = Math.sqrt(sum / Math.max(1, samples));
        level = (float) Math.min(1.0, rms / 5000.0); // normal speech is roughly 1000 - 5000
    }

    /** 0.0 - 1.0, how loud the microphone is right now. */
    public float level() {
        return level;
    }

    /**
     * True when (almost) nothing but silence was captured. On macOS this is exactly what happens
     * when the app has no microphone permission: Java gets no error, only zeros.
     */
    public boolean isSilent() {
        return peak < 80;
    }

    /** Quiet laptop microphones are boosted (up to 8x) so the voice note is clearly audible. */
    private static void boost(byte[] pcm, int peak) {
        if (peak < 80) return;
        double gain = Math.min(8.0, 23000.0 / peak);
        if (gain < 1.2) return;
        for (int i = 0; i + 1 < pcm.length; i += 2) {
            int v = (short) ((pcm[i + 1] << 8) | (pcm[i] & 0xFF));
            v = (int) Math.max(-32768, Math.min(32767, Math.round(v * gain)));
            pcm[i] = (byte) v;
            pcm[i + 1] = (byte) (v >> 8);
        }
    }

    public long elapsedMs() {
        return bytes * 1000L / BYTES_PER_SECOND;
    }

    public boolean isFull() {
        return bytes >= MAX_SECONDS * BYTES_PER_SECOND;
    }

    /** Stops the microphone and throws the recording away. */
    public void cancel() {
        halt();
    }

    private void halt() {
        recording = false;
        if (line != null) {
            line.stop();
            line.close(); // also wakes up a blocked read()
        }
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(400);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** Stops recording and returns a complete .wav file. */
    public byte[] stop() throws IOException {
        halt();
        byte[] data;
        synchronized (pcm) {
            data = pcm.toByteArray();
        }
        boost(data, peak);
        AudioInputStream in = new AudioInputStream(new ByteArrayInputStream(data), FORMAT,
                data.length / FORMAT.getFrameSize());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        AudioSystem.write(in, AudioFileFormat.Type.WAVE, out);
        return out.toByteArray();
    }
}
