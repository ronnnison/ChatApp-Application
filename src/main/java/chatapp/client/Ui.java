package chatapp.client;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicScrollBarUI;

/**
 * Custom-drawn UI parts: vector icons, round avatars, gradient buttons, thin scrollbars, themed
 * dialogs. Everything is painted with Java2D, so it looks the same on every operating system.
 */
public final class Ui {
    private Ui() { }

    // ------------------------------------------------------------------ helpers

    public static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }

    /** Blend of two colours: t=0 gives a, t=1 gives b. */
    public static Color mix(Color a, Color b, double t) {
        return new Color(
                (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    public static void smooth(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    public static Paint accentGradient(double x, double y, double w, double h) {
        return new GradientPaint((float) x, (float) y, Theme.P.accent, (float) (x + w), (float) (y + h), Theme.P.accent2);
    }

    /** Adds "label + field" to a vertical form panel. */
    public static void addField(JPanel p, String label, JComponent field) {
        JLabel l = Theme.label(label);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(l);
        p.add(Box.createVerticalStrut(5));
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, field.getPreferredSize().height + 2));
        p.add(field);
        p.add(Box.createVerticalStrut(12));
    }

    // ------------------------------------------------------------------ icons

    public enum Glyph { SEND, CLIP, MIC, STOP, SMILE, PLAY, DOWNLOAD, PLUS, CLOSE, PALETTE, LOGOUT, DOC, CHAT }

    /** Draws a vector icon inside a square of {@code size} pixels. */
    public static void paintGlyph(Graphics2D g0, Glyph glyph, double x, double y, double size, Color color) {
        Graphics2D g = (Graphics2D) g0.create();
        smooth(g);
        g.translate(x, y);
        double s = size / 24.0;
        g.scale(s, s);
        g.setColor(color);
        g.setStroke(new BasicStroke(1.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (glyph) {
            case SEND: {
                Path2D p = new Path2D.Double();
                p.moveTo(3, 3);
                p.lineTo(21.5, 12);
                p.lineTo(3, 21);
                p.lineTo(6.5, 12);
                p.closePath();
                g.fill(p);
                break;
            }
            case CLIP:
                g.rotate(Math.toRadians(40), 12, 12);
                g.draw(new RoundRectangle2D.Double(7.5, 2.5, 9, 19, 9, 9));
                g.draw(new Line2D.Double(12, 8, 12, 16));
                break;
            case MIC:
                g.draw(new RoundRectangle2D.Double(9, 2.5, 6, 11, 6, 6));
                g.draw(new Arc2D.Double(5.5, 6.5, 13, 11, 180, 180, Arc2D.OPEN));
                g.draw(new Line2D.Double(12, 17.5, 12, 21.5));
                g.draw(new Line2D.Double(8.5, 21.5, 15.5, 21.5));
                break;
            case STOP:
                g.fill(new RoundRectangle2D.Double(6, 6, 12, 12, 3, 3));
                break;
            case SMILE:
                g.draw(new Ellipse2D.Double(3, 3, 18, 18));
                g.fill(new Ellipse2D.Double(8.2, 9, 2.4, 2.8));
                g.fill(new Ellipse2D.Double(13.4, 9, 2.4, 2.8));
                g.draw(new Arc2D.Double(7.3, 8.5, 9.4, 8, 200, 140, Arc2D.OPEN));
                break;
            case PLAY: {
                Path2D p = new Path2D.Double();
                p.moveTo(8, 5);
                p.lineTo(19.5, 12);
                p.lineTo(8, 19);
                p.closePath();
                g.fill(p);
                break;
            }
            case DOWNLOAD:
                g.draw(new Line2D.Double(12, 4, 12, 15));
                g.draw(new Line2D.Double(7, 11, 12, 16));
                g.draw(new Line2D.Double(17, 11, 12, 16));
                g.draw(new Line2D.Double(5, 20, 19, 20));
                break;
            case PLUS:
                g.draw(new Line2D.Double(12, 5, 12, 19));
                g.draw(new Line2D.Double(5, 12, 19, 12));
                break;
            case CLOSE:
                g.draw(new Line2D.Double(7, 7, 17, 17));
                g.draw(new Line2D.Double(17, 7, 7, 17));
                break;
            case PALETTE:
                g.draw(new Ellipse2D.Double(3.5, 3.5, 17, 17));
                g.fill(new Arc2D.Double(3.5, 3.5, 17, 17, 90, 180, Arc2D.PIE));
                break;
            case LOGOUT: {
                Path2D p = new Path2D.Double();
                p.moveTo(10, 4);
                p.lineTo(4, 4);
                p.lineTo(4, 20);
                p.lineTo(10, 20);
                g.draw(p);
                g.draw(new Line2D.Double(9, 12, 20, 12));
                g.draw(new Line2D.Double(16, 8, 20, 12));
                g.draw(new Line2D.Double(16, 16, 20, 12));
                break;
            }
            case DOC: {
                Path2D p = new Path2D.Double();
                p.moveTo(6, 3);
                p.lineTo(14, 3);
                p.lineTo(19, 8);
                p.lineTo(19, 21);
                p.lineTo(6, 21);
                p.closePath();
                g.draw(p);
                g.draw(new Line2D.Double(14, 3, 14, 8));
                g.draw(new Line2D.Double(14, 8, 19, 8));
                g.draw(new Line2D.Double(9, 13, 16, 13));
                g.draw(new Line2D.Double(9, 17, 16, 17));
                break;
            }
            case CHAT: {
                g.draw(new RoundRectangle2D.Double(3, 4, 18, 13, 6, 6));
                Path2D p = new Path2D.Double();
                p.moveTo(8, 16.5);
                p.lineTo(7, 21);
                p.lineTo(12.5, 17);
                p.closePath();
                g.fill(p);
                break;
            }
            default:
                break;
        }
        g.dispose();
    }

    /** Round icon button: transparent until hovered; {@code accent} makes it a filled gradient button. */
    public static class IconButton extends JButton {
        private static final long serialVersionUID = 1L;
        private Glyph glyph;
        private final int iconSize;
        private boolean accent;
        private Color tint;

        public IconButton(Glyph glyph, String tooltip, int box, int iconSize) {
            this.glyph = glyph;
            this.iconSize = iconSize;
            setToolTipText(tooltip);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setMargin(new Insets(0, 0, 0, 0));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(box, box));
        }

        public void setGlyph(Glyph g) {
            this.glyph = g;
            repaint();
        }

        public IconButton accent(boolean on) {
            this.accent = on;
            return this;
        }

        public IconButton tint(Color c) {
            this.tint = c;
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            boolean hot = getModel().isRollover() && isEnabled();
            int d = Math.min(getWidth(), getHeight());
            int ox = (getWidth() - d) / 2;
            int oy = (getHeight() - d) / 2;
            if (accent && isEnabled()) {
                g2.setPaint(accentGradient(ox, oy, d, d));
                g2.fillOval(ox, oy, d, d);
                if (hot) {
                    g2.setColor(new Color(255, 255, 255, 40));
                    g2.fillOval(ox, oy, d, d);
                }
            } else if (hot) {
                g2.setColor(Theme.P.surface2);
                g2.fillOval(ox, oy, d, d);
            }
            Color c = accent ? Theme.P.onAccent : (tint != null ? tint : (hot ? Theme.P.text : Theme.P.muted));
            if (!isEnabled()) c = alpha(Theme.P.muted, 90);
            paintGlyph(g2, glyph, ox + (d - iconSize) / 2.0, oy + (d - iconSize) / 2.0, iconSize, c);
            g2.dispose();
        }
    }

    // ------------------------------------------------------------------ buttons and fields

    public static class GradientButton extends JButton {
        private static final long serialVersionUID = 1L;
        private final boolean primary;

        public GradientButton(String text, boolean primary) {
            super(text);
            this.primary = primary;
            setFont(Theme.BOLD);
            setForeground(primary ? Theme.P.onAccent : Theme.P.text);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(9, 18, 9, 18));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            Shape r = new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1.5, getHeight() - 1.5, 12, 12);
            boolean hot = getModel().isRollover() && isEnabled();
            if (!isEnabled()) {
                g2.setColor(Theme.P.surface2);
                g2.fill(r);
            } else if (primary) {
                g2.setPaint(accentGradient(0, 0, getWidth(), getHeight()));
                g2.fill(r);
                if (hot) {
                    g2.setColor(new Color(255, 255, 255, 38));
                    g2.fill(r);
                }
                if (getModel().isPressed()) {
                    g2.setColor(new Color(0, 0, 0, 40));
                    g2.fill(r);
                }
            } else {
                g2.setColor(hot ? Ui.mix(Theme.P.surface2, Theme.P.accent, 0.12) : Theme.P.surface2);
                g2.fill(r);
                g2.setColor(hot ? Theme.P.accent : Theme.P.border);
                g2.draw(r);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Text field that shows grey hint text while it is empty. */
    public static class HintField extends JTextField {
        private static final long serialVersionUID = 1L;
        private final String hint;

        public HintField(String hint) {
            this.hint = hint;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (getText().isEmpty()) {
                Graphics2D g2 = (Graphics2D) g.create();
                smooth(g2);
                g2.setFont(getFont());
                g2.setColor(alpha(Theme.P.muted, 170));
                Insets in = getInsets();
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(hint, in.left, in.top + (getHeight() - in.top - in.bottom - fm.getHeight()) / 2 + fm.getAscent());
                g2.dispose();
            }
        }
    }

    // ------------------------------------------------------------------ panels

    public static class RoundPanel extends JPanel {
        private static final long serialVersionUID = 1L;
        private Color fill;
        private Color line;
        private final int radius;

        public RoundPanel(Color fill, int radius) {
            this.fill = fill;
            this.radius = radius;
            setOpaque(false);
        }

        public RoundPanel line(Color c) {
            this.line = c;
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            Shape r = new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1.5, getHeight() - 1.5, radius, radius);
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

    // ------------------------------------------------------------------ avatars

    /**
     * Round profile picture. Shows the user's uploaded picture when the {@link AvatarCache} has it,
     * otherwise a coloured circle with initials (or '#' for groups).
     */
    public static class AvatarView extends JComponent {
        private static final long serialVersionUID = 1L;
        private final int size;
        private long userId;
        private String name = "?";
        private boolean group;
        private boolean ring;
        private BufferedImage override;

        public AvatarView(int size) {
            this.size = size;
            Dimension d = new Dimension(size, size);
            setPreferredSize(d);
            setMinimumSize(d);
            setMaximumSize(d);
        }

        public void set(long userId, String name, boolean group) {
            this.userId = userId;
            this.name = name == null || name.trim().isEmpty() ? "?" : name.trim();
            this.group = group;
            repaint();
        }

        /** Used by the sign-up form to preview a picture that is not uploaded yet. */
        public void setImage(BufferedImage image) {
            this.override = image;
            repaint();
        }

        public AvatarView ring(boolean on) {
            this.ring = on;
            return this;
        }

        private String initials() {
            if (group) return "#";
            String[] parts = name.split("\\s+");
            String s = parts[0].substring(0, parts[0].offsetByCodePoints(0, 1));
            if (parts.length > 1) s += parts[parts.length - 1].substring(0, parts[parts.length - 1].offsetByCodePoints(0, 1));
            return s.toUpperCase();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            Ellipse2D circle = new Ellipse2D.Double(1.5, 1.5, size - 3, size - 3);
            BufferedImage img = override;
            AvatarCache cache = AvatarCache.get();
            if (img == null && userId > 0 && !group && cache != null) img = cache.image(userId);

            if (img != null) {
                g2.setClip(circle);
                g2.drawImage(img, 1, 1, size - 2, size - 2, null);
                g2.setClip(null);
            } else {
                float hue = (Math.abs(name.hashCode()) % 360) / 360f;
                Color c1 = Color.getHSBColor(hue, 0.55f, 0.92f);
                Color c2 = Color.getHSBColor((hue + 0.12f) % 1f, 0.70f, 0.70f);
                g2.setPaint(new GradientPaint(0, 0, c1, size, size, c2));
                g2.fill(circle);
                g2.setColor(Color.WHITE);
                g2.setFont(Theme.font(Font.BOLD, size * 0.38f));
                FontMetrics fm = g2.getFontMetrics();
                String t = initials();
                g2.drawString(t, (size - fm.stringWidth(t)) / 2f, (size - fm.getHeight()) / 2f + fm.getAscent());
            }
            if (ring) {
                g2.setPaint(accentGradient(0, 0, size, size));
                g2.setStroke(new BasicStroke(2f));
                g2.draw(new Ellipse2D.Double(1, 1, size - 2, size - 2));
            }
            g2.dispose();
        }
    }

    // ------------------------------------------------------------------ scrolling

    /** Slim rounded scrollbar without arrow buttons. */
    private static class ThinScrollBarUI extends BasicScrollBarUI {
        private final Color track;

        ThinScrollBarUI(Color track) {
            this.track = track;
        }

        @Override
        protected void configureScrollBarColors() {
            thumbColor = Theme.P.border;
            trackColor = track;
        }

        private JButton zero() {
            JButton b = new JButton();
            Dimension d = new Dimension(0, 0);
            b.setPreferredSize(d);
            b.setMinimumSize(d);
            b.setMaximumSize(d);
            return b;
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return zero();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return zero();
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
            g.setColor(track);
            g.fillRect(r.x, r.y, r.width, r.height);
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            if (r.isEmpty()) return;
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            g2.setColor(isThumbRollover() ? Theme.P.accent : Theme.P.border);
            g2.fillRoundRect(r.x + 2, r.y + 2, r.width - 4, r.height - 4, 8, 8);
            g2.dispose();
        }

        @Override
        public Dimension getPreferredSize(JComponent c) {
            return new Dimension(10, 10);
        }
    }

    public static JScrollPane styleScroll(JScrollPane sp, Color trackBg) {
        sp.setBorder(null);
        sp.setViewportBorder(null);
        sp.getViewport().setBackground(trackBg);
        sp.setBackground(trackBg);
        sp.getVerticalScrollBar().setUI(new ThinScrollBarUI(trackBg));
        sp.getVerticalScrollBar().setUnitIncrement(18);
        sp.getVerticalScrollBar().setBackground(trackBg);
        sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        return sp;
    }

    // ------------------------------------------------------------------ theme dots

    /** A row of small coloured circles, one per palette. */
    public static JComponent themeDots(Consumer<Theme.Palette> onPick) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        p.setOpaque(false);
        for (Theme.Palette pal : Theme.ALL) {
            JComponent dot = new JComponent() {
                private static final long serialVersionUID = 1L;

                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    smooth(g2);
                    g2.setPaint(new GradientPaint(0, 0, pal.accent, 22, 22, pal.accent2));
                    g2.fillOval(3, 3, 16, 16);
                    if (pal == Theme.P) {
                        g2.setColor(Theme.P.text);
                        g2.setStroke(new BasicStroke(2f));
                        g2.drawOval(1, 1, 20, 20);
                    }
                    g2.dispose();
                }
            };
            dot.setPreferredSize(new Dimension(22, 22));
            dot.setToolTipText(pal.name);
            dot.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            dot.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    onPick.accept(pal);
                }
            });
            p.add(dot);
        }
        return p;
    }

    // ------------------------------------------------------------------ dialogs

    public static void info(Component parent, String title, String message) {
        show(parent, title, message, false, null, "OK");
    }

    public static void error(Component parent, String title, String message) {
        show(parent, title, message, true, null, "OK");
    }

    /** @return true when the user pressed the "yes" button */
    public static boolean confirm(Component parent, String title, String message, String yes, String no) {
        return show(parent, title, message, false, no, yes);
    }

    private static boolean show(Component parent, String title, String message, boolean isError,
                                String noText, String yesText) {
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        if (owner == null && parent instanceof Window) owner = (Window) parent;
        JDialog d = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
        boolean[] result = {false};

        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBackground(Theme.P.surface);
        root.setBorder(BorderFactory.createEmptyBorder(22, 26, 18, 26));

        JLabel head = new JLabel(title);
        head.setFont(Theme.font(Font.BOLD, 17f));
        head.setForeground(isError ? Theme.P.danger : Theme.P.text);
        head.setAlignmentX(Component.LEFT_ALIGNMENT);
        String html = message.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
        JLabel body = new JLabel("<html><body style='width:300px'>" + html + "</body></html>");
        body.setFont(Theme.BASE);
        body.setForeground(Theme.P.muted);
        body.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (noText != null) {
            JButton no = Theme.secondaryButton(noText);
            no.addActionListener(e -> d.dispose());
            buttons.add(no);
        }
        JButton yes = Theme.button(yesText);
        yes.addActionListener(e -> {
            result[0] = true;
            d.dispose();
        });
        buttons.add(yes);

        root.add(head);
        root.add(Box.createVerticalStrut(8));
        root.add(body);
        root.add(Box.createVerticalStrut(18));
        root.add(buttons);

        d.setContentPane(root);
        d.getRootPane().setDefaultButton(yes);
        d.setResizable(false);
        d.pack();
        d.setLocationRelativeTo(parent);
        d.setVisible(true);
        return result[0];
    }
}
