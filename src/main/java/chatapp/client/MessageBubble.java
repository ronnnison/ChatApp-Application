package chatapp.client;

import chatapp.common.ChatMessageDTO;
import chatapp.common.Protocol;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.Timer;

/**
 * One row of the chat. The row decides by message kind (text / photo / voice / document) which
 * content to build (POLYMORPHISM by kind). It never talks to the network itself: everything it
 * needs from the outside goes through the small {@link Host} interface.
 */
public class MessageBubble extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final int MAX_TEXT_WIDTH = 420;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

    /** What a bubble needs from the window that contains it. */
    public interface Host {
        MediaCache media();

        void download(ChatMessageDTO m);

        void viewImage(ChatMessageDTO m);

        /** Called before a bubble changes size (photo arrived); returns whether the chat was scrolled to the end. */
        boolean beforeResize();

        void afterResize(boolean wasAtBottom);
    }

    public MessageBubble(ChatMessageDTO m, boolean mine, boolean showSender, Host host) {
        Theme.Palette p = Theme.P;
        setOpaque(false);
        setLayout(new BorderLayout(8, 0));
        setBorder(BorderFactory.createEmptyBorder(3, 14, 3, 14));

        Color fg = mine ? p.mineText : p.otherText;
        Color sub = Ui.alpha(fg, 165);

        Bubble bubble = new Bubble(mine ? p.mine : p.other, mine ? null : p.border);
        bubble.setLayout(new BoxLayout(bubble, BoxLayout.Y_AXIS));
        bubble.setBorder(BorderFactory.createEmptyBorder(8, 12, 6, 12));

        if (showSender && !mine) {
            JLabel name = new JLabel(m.getSenderName());
            name.setFont(Theme.SMALL_BOLD);
            name.setForeground(p.accent);
            name.setAlignmentX(Component.LEFT_ALIGNMENT);
            bubble.add(name);
            bubble.add(Box.createVerticalStrut(3));
        }

        JLabel time = new JLabel(TIME.format(Instant.ofEpochMilli(m.getSentAt())));
        time.setFont(Theme.SMALL.deriveFont(11f));
        time.setForeground(sub);

        switch (m.getKind()) {
            case IMAGE:
                buildImage(bubble, m, host, fg, sub, time);
                break;
            case VOICE:
                buildVoice(bubble, m, host, fg, sub, time);
                break;
            case DOCUMENT:
                buildDocument(bubble, m, host, fg, sub, time);
                break;
            default:
                buildText(bubble, m, fg, time);
                break;
        }

        JPanel flow = new JPanel(new FlowLayout(mine ? FlowLayout.RIGHT : FlowLayout.LEFT, 0, 0));
        flow.setOpaque(false);
        flow.add(bubble);

        if (!mine) {
            Ui.AvatarView avatar = new Ui.AvatarView(34);
            avatar.set(m.getSenderId(), m.getSenderName(), false);
            JPanel holder = new JPanel(new BorderLayout());
            holder.setOpaque(false);
            holder.add(avatar, BorderLayout.NORTH);
            add(holder, BorderLayout.WEST);
        }
        add(flow, BorderLayout.CENTER);
    }

    // ------------------------------------------------------------------ content builders

    private void buildText(JPanel bubble, ChatMessageDTO m, Color fg, JLabel time) {
        JTextPane pane = EmojiText.pane(m.getContent(), Theme.BASE, fg, MAX_TEXT_WIDTH);
        pane.setAlignmentX(Component.LEFT_ALIGNMENT);
        bubble.add(pane);
        bubble.add(Box.createVerticalStrut(2));
        bubble.add(footer(null, time));
    }

    private void buildImage(JPanel bubble, ChatMessageDTO m, Host host, Color fg, Color sub, JLabel time) {
        ImageView view = new ImageView(m, host);
        view.setAlignmentX(Component.LEFT_ALIGNMENT);
        bubble.add(view);
        bubble.add(Box.createVerticalStrut(5));

        JLabel info = new JLabel(shorten(m.getFileName(), 26) + "  ·  " + Protocol.humanSize(m.getFileSize()));
        info.setFont(Theme.SMALL.deriveFont(11f));
        info.setForeground(sub);
        Ui.IconButton download = new Ui.IconButton(Ui.Glyph.DOWNLOAD, "Save photo", 26, 15);
        download.addActionListener(e -> host.download(m));
        bubble.add(footer(info, time, download));

        host.media().thumbnail(m.getFileId(), img -> {
            boolean atBottom = host.beforeResize();
            view.setThumb(img);
            view.revalidate();
            host.afterResize(atBottom);
        });
    }

    private void buildVoice(JPanel bubble, ChatMessageDTO m, Host host, Color fg, Color sub, JLabel time) {
        Ui.IconButton play = new Ui.IconButton(Ui.Glyph.PLAY, "Play", 38, 18).accent(true);
        Waveform wave = new Waveform(m.getId(), fg, sub);
        double seconds = Math.max(1, (m.getFileSize() - 44) / 32000.0);
        JLabel length = new JLabel(clock((long) seconds));
        length.setFont(Theme.SMALL);
        length.setForeground(sub);

        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(play, BorderLayout.WEST);
        row.add(wave, BorderLayout.CENTER);
        row.add(length, BorderLayout.EAST);
        bubble.add(row);
        bubble.add(Box.createVerticalStrut(2));
        bubble.add(footer(null, time));

        VoicePlayer.Playback[] current = {null};
        Timer ticker = new Timer(60, null);
        ticker.addActionListener(e -> {
            VoicePlayer.Playback pb = current[0];
            if (pb != null) {
                wave.setProgress(pb.progress());
                length.setText(clock((long) (pb.progress() * seconds)));
            }
        });
        Runnable finished = () -> {
            ticker.stop();
            current[0] = null;
            wave.setProgress(0);
            length.setText(clock((long) seconds));
            play.setGlyph(Ui.Glyph.PLAY);
        };
        play.addActionListener(e -> {
            if (current[0] != null && current[0].isPlaying()) {
                current[0].stop();
                return;
            }
            play.setEnabled(false);
            host.media().fetch(m.getFileId(), data -> {
                play.setEnabled(true);
                try {
                    current[0] = VoicePlayer.start(data.data, finished);
                    play.setGlyph(Ui.Glyph.STOP);
                    ticker.start();
                } catch (Exception ex) {
                    Ui.error(this, "Cannot play audio", "This computer could not play the voice note.\n" + ex.getMessage());
                }
            });
        });
    }

    private void buildDocument(JPanel bubble, ChatMessageDTO m, Host host, Color fg, Color sub, JLabel time) {
        Theme.Palette p = Theme.P;
        JComponent icon = new JComponent() {
            private static final long serialVersionUID = 1L;

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                Ui.smooth(g2);
                g2.setPaint(Ui.accentGradient(0, 0, getWidth(), getHeight()));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                Ui.paintGlyph(g2, Ui.Glyph.DOC, 9, 9, 26, p.onAccent);
                g2.dispose();
            }
        };
        icon.setPreferredSize(new Dimension(44, 44));

        JLabel name = new JLabel(shorten(m.getFileName(), 30));
        name.setFont(Theme.BOLD);
        name.setForeground(fg);
        JLabel size = new JLabel("Word document  ·  " + Protocol.humanSize(m.getFileSize()));
        size.setFont(Theme.SMALL);
        size.setForeground(sub);
        JPanel texts = new JPanel();
        texts.setOpaque(false);
        texts.setLayout(new BoxLayout(texts, BoxLayout.Y_AXIS));
        texts.add(name);
        texts.add(size);

        Ui.IconButton download = new Ui.IconButton(Ui.Glyph.DOWNLOAD, "Download", 36, 18).accent(true);
        download.addActionListener(e -> host.download(m));

        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(icon, BorderLayout.WEST);
        row.add(texts, BorderLayout.CENTER);
        row.add(download, BorderLayout.EAST);
        bubble.add(row);
        bubble.add(Box.createVerticalStrut(2));
        bubble.add(footer(null, time));
    }

    private JPanel footer(JComponent left, JComponent... right) {
        JPanel f = new JPanel(new BorderLayout(8, 0));
        f.setOpaque(false);
        f.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (left != null) f.add(left, BorderLayout.WEST);
        JPanel r = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        r.setOpaque(false);
        for (JComponent c : right) r.add(c);
        f.add(r, BorderLayout.EAST);
        f.setMaximumSize(new Dimension(Integer.MAX_VALUE, f.getPreferredSize().height));
        return f;
    }

    private static String shorten(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }

    private static String clock(long seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    /** Rows stay as tall as their content so BoxLayout does not stretch them. */
    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }

    // ------------------------------------------------------------------ parts

    private static class Bubble extends JPanel {
        private static final long serialVersionUID = 1L;
        private final Color fill;
        private final Color line;

        Bubble(Color fill, Color line) {
            this.fill = fill;
            this.line = line;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Ui.smooth(g2);
            Shape r = new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1.5, getHeight() - 1.5, 18, 18);
            g2.setColor(fill);
            g2.fill(r);
            if (line != null) {
                g2.setColor(line);
                g2.draw(r);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Photo preview: grey box while loading, then the picture with rounded corners. Click to enlarge. */
    private static class ImageView extends JComponent {
        private static final long serialVersionUID = 1L;
        private BufferedImage thumb;
        private boolean failed;

        ImageView(ChatMessageDTO m, Host host) {
            setPreferredSize(new Dimension(260, 170));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (thumb != null) host.viewImage(m);
                }
            });
        }

        void setThumb(BufferedImage img) {
            thumb = img;
            failed = img == null;
            setPreferredSize(img == null ? new Dimension(260, 90) : new Dimension(img.getWidth(), img.getHeight()));
            setCursor(img == null ? Cursor.getDefaultCursor() : Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(img == null ? null : "Click to enlarge");
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Ui.smooth(g2);
            Shape r = new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 12, 12);
            if (thumb != null) {
                g2.setClip(r);
                g2.drawImage(thumb, 0, 0, getWidth(), getHeight(), null);
            } else {
                g2.setColor(Ui.alpha(Theme.P.text, 25));
                g2.fill(r);
                g2.setColor(Theme.P.muted);
                g2.setFont(Theme.SMALL);
                FontMetrics fm = g2.getFontMetrics();
                String t = failed ? "Preview not available" : "Loading photo...";
                g2.drawString(t, (getWidth() - fm.stringWidth(t)) / 2, getHeight() / 2 + fm.getAscent() / 2);
            }
            g2.dispose();
        }
    }

    /** Fake-but-stable audio bars. The part already played is drawn brighter. */
    private static class Waveform extends JComponent {
        private static final long serialVersionUID = 1L;
        private static final int BARS = 30;
        private final int[] heights = new int[BARS];
        private final Color played;
        private final Color rest;
        private double progress;

        Waveform(long seed, Color played, Color rest) {
            this.played = played;
            this.rest = Ui.alpha(rest, 120);
            Random rnd = new Random(seed * 7919L);
            for (int i = 0; i < BARS; i++) heights[i] = 6 + rnd.nextInt(20);
            setPreferredSize(new Dimension(150, 30));
        }

        void setProgress(double p) {
            progress = p;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Ui.smooth(g2);
            double step = getWidth() / (double) BARS;
            for (int i = 0; i < BARS; i++) {
                g2.setColor(i / (double) BARS < progress ? played : rest);
                int h = heights[i];
                g2.fillRoundRect((int) (i * step), (getHeight() - h) / 2, 3, h, 3, 3);
            }
            g2.dispose();
        }
    }
}
