package chatapp.client;

import java.io.ByteArrayInputStream;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.BooleanControl;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import javax.swing.SwingUtilities;

/** Plays one voice note at a time; starting another one stops the first. */
public final class VoicePlayer {
    private VoicePlayer() { }

    public static final class Playback {
        private final Clip clip;
        private final Runnable onFinished;
        private volatile boolean done;

        private Playback(Clip clip, Runnable onFinished) {
            this.clip = clip;
            this.onFinished = onFinished;
        }

        public boolean isPlaying() {
            return !done && clip.isRunning();
        }

        /** 0.0 - 1.0 */
        public double progress() {
            if (done) return 0;
            long len = clip.getMicrosecondLength();
            return len <= 0 ? 0 : Math.min(1.0, clip.getMicrosecondPosition() / (double) len);
        }

        public void stop() {
            clip.stop(); // triggers the STOP event, which calls finish()
        }

        private void finish() {
            if (done) return;
            done = true;
            clip.close();
            SwingUtilities.invokeLater(onFinished);
        }
    }

    private static Playback current;

    public static synchronized Playback start(byte[] wav, Runnable onFinished) throws Exception {
        if (current != null) current.stop();
        AudioInputStream in = AudioSystem.getAudioInputStream(new ByteArrayInputStream(wav));
        Clip clip = AudioSystem.getClip();
        clip.open(in);
        try { // make sure the line is neither muted nor turned down
            if (clip.isControlSupported(BooleanControl.Type.MUTE)) {
                ((BooleanControl) clip.getControl(BooleanControl.Type.MUTE)).setValue(false);
            }
            if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), 0f)));
            }
        } catch (RuntimeException ignored) {
            // a mixer without these controls still plays normally
        }
        Playback pb = new Playback(clip, onFinished);
        clip.addLineListener(ev -> {
            if (ev.getType() == LineEvent.Type.STOP) pb.finish();
        });
        current = pb;
        clip.start();
        return pb;
    }
}
