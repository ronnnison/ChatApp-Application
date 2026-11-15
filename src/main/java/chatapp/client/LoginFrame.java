package chatapp.client;

import chatapp.common.MessageType;
import chatapp.common.Packet;
import chatapp.common.Payloads;
import chatapp.common.Protocol;
import chatapp.common.UserDTO;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RadialGradientPaint;
import java.awt.geom.Point2D;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingWorker;

/** Sign-in screen: branded panel on the left, form on the right, theme dots at the bottom. */
public class LoginFrame extends JFrame {
    private static final long serialVersionUID = 1L;

    private final ClientConnection conn;
    private final JTextField hostField = Theme.hintField("e.g. 192.168.1.5   or   host:port");
    private final JTextField userField = Theme.field();
    private final JPasswordField passField = Theme.passwordField();
    private final JButton loginBtn = Theme.button("Sign in");
    private final JButton registerBtn = Theme.secondaryButton("Create account");
    private final JLabel status = new JLabel(" ");
    private final Consumer<Packet> listener = this::onPacket;

    public LoginFrame(ClientConnection conn) {
        super(Protocol.APP_NAME + " - Sign in");
        this.conn = conn;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(860, 600);
        setResizable(false);
        setLocationRelativeTo(null);

        Theme.Palette p = Theme.P;
        hostField.setText(Theme.pref("host", "localhost"));
        userField.setText(Theme.pref("username", ""));

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBackground(p.surface);
        form.setBorder(BorderFactory.createEmptyBorder(48, 48, 28, 48));

        JLabel hello = new JLabel("Welcome back");
        hello.setFont(Theme.HEADING);
        hello.setForeground(p.text);
        hello.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel sub = new JLabel("Sign in to continue chatting");
        sub.setFont(Theme.BASE);
        sub.setForeground(p.muted);
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);

        form.add(hello);
        form.add(Box.createVerticalStrut(4));
        form.add(sub);
        form.add(Box.createVerticalStrut(28));
        Ui.addField(form, "SERVER ADDRESS", hostField);
        Ui.addField(form, "USERNAME", userField);
        Ui.addField(form, "PASSWORD", passField);

        status.setFont(Theme.SMALL);
        status.setForeground(p.danger);
        status.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(status);
        form.add(Box.createVerticalStrut(10));
        for (JButton b : new JButton[]{loginBtn, registerBtn}) {
            b.setAlignmentX(Component.LEFT_ALIGNMENT);
            b.setMaximumSize(new Dimension(Integer.MAX_VALUE, b.getPreferredSize().height + 4));
        }
        form.add(loginBtn);
        form.add(Box.createVerticalStrut(10));
        form.add(registerBtn);
        form.add(Box.createVerticalGlue());

        JLabel themeLabel = Theme.label("THEME");
        themeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(themeLabel);
        form.add(Box.createVerticalStrut(6));
        java.awt.Component dots = Ui.themeDots(this::switchTheme);
        ((javax.swing.JComponent) dots).setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(dots);

        setLayout(new BorderLayout());
        add(new BrandPanel(), BorderLayout.WEST);
        add(form, BorderLayout.CENTER);
        getRootPane().setDefaultButton(loginBtn);

        loginBtn.addActionListener(e -> doLogin());
        registerBtn.addActionListener(e ->
                new RegisterDialog(this, conn, () -> hostField.getText().trim(), name -> {
                    userField.setText(name);
                    passField.requestFocusInWindow();
                }).setVisible(true));
        conn.addListener(listener);
    }

    private void switchTheme(Theme.Palette palette) {
        Theme.set(palette);
        Theme.setPref("host", hostField.getText().trim());
        Theme.setPref("username", userField.getText().trim());
        conn.removeListener(listener);
        LoginFrame next = new LoginFrame(conn);
        next.setLocation(getLocation());
        next.setVisible(true);
        dispose();
    }

    private void doLogin() {
        String username = userField.getText().trim();
        String password = new String(passField.getPassword());
        if (username.isEmpty() || password.isEmpty()) {
            setStatus("Enter your username and password.");
            return;
        }
        setBusy(true);
        String host = hostField.getText().trim();
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                conn.connectTo(host);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    conn.send(new Packet(MessageType.LOGIN, new Payloads.Credentials(username, password)));
                } catch (InterruptedException | ExecutionException ex) {
                    setBusy(false);
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    setStatus("Cannot reach server: " + cause.getMessage());
                }
            }
        }.execute();
    }

    private void onPacket(Packet p) {
        switch (p.getType()) {
            case AUTH_OK: {
                UserDTO me = p.getPayload();
                Theme.setPref("host", hostField.getText().trim());
                Theme.setPref("username", me.getUsername());
                conn.removeListener(listener);
                MainFrame main = new MainFrame(conn, me, -1);
                main.setVisible(true);
                dispose();
                break;
            }
            case AUTH_FAIL: {
                String reason = p.getPayload();
                setBusy(false);
                setStatus(reason);
                break;
            }
            case DISCONNECTED:
                setBusy(false);
                setStatus("Connection to the server was lost.");
                break;
            default:
                break;
        }
    }

    private void setBusy(boolean busy) {
        loginBtn.setEnabled(!busy);
        loginBtn.setText(busy ? "Signing in..." : "Sign in");
        if (busy) setStatus(" ");
    }

    private void setStatus(String text) {
        status.setText("<html>" + text.replace("<", "&lt;") + "</html>");
    }

    // ------------------------------------------------------------------ left side

    /** Painted brand panel: gradient, glow, grid lines, logo and a short feature list. */
    private static class BrandPanel extends JPanel {
        private static final long serialVersionUID = 1L;

        BrandPanel() {
            setPreferredSize(new Dimension(390, 0));
        }

        private void glow(Graphics2D g, double cx, double cy, float radius, Color c, int alpha) {
            g.setPaint(new RadialGradientPaint(new Point2D.Double(cx, cy), radius, new float[]{0f, 1f},
                    new Color[]{Ui.alpha(c, alpha), Ui.alpha(c, 0)}));
            g.fillRect(0, 0, getWidth(), getHeight());
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Ui.smooth(g2);
            Theme.Palette p = Theme.P;
            int w = getWidth();
            int h = getHeight();
            if (p.dark) {
                g2.setPaint(new java.awt.GradientPaint(0, 0, p.surface2, w, h, p.bg));
                g2.fillRect(0, 0, w, h);
                glow(g2, w * 0.15, h * 0.12, 300f, p.accent, 85);
                glow(g2, w * 0.95, h * 0.95, 340f, p.accent2, 85);
            } else {
                g2.setPaint(Ui.accentGradient(0, 0, w, h));
                g2.fillRect(0, 0, w, h);
            }
            g2.setColor(Ui.alpha(p.dark ? p.accent : Color.WHITE, 20));
            for (int x = 0; x < w; x += 36) g2.drawLine(x, 0, x, h);
            for (int y = 0; y < h; y += 36) g2.drawLine(0, y, w, y);

            Color fg = p.dark ? p.text : Color.WHITE;
            Color soft = Ui.alpha(fg, 190);

            g2.setPaint(Ui.accentGradient(48, 58, 58, 58));
            g2.fillRoundRect(48, 58, 58, 58, 18, 18);
            Ui.paintGlyph(g2, Ui.Glyph.CHAT, 61, 71, 32, p.dark ? p.onAccent : Color.WHITE);

            g2.setColor(fg);
            g2.setFont(Theme.font(Font.BOLD, 40f));
            g2.drawString(Protocol.APP_NAME, 48, 176);
            g2.setColor(soft);
            g2.setFont(Theme.font(Font.PLAIN, 16f));
            g2.drawString("Real-time messaging for your team.", 48, 206);

            String[] features = {
                "Private chats and group rooms",
                "Photos shown right in the chat",
                "Voice notes and emoji",
                "Profile pictures and four themes",
            };
            int y = 290;
            for (String f : features) {
                g2.setPaint(Ui.accentGradient(48, y - 12, 12, 12));
                g2.fillOval(48, y - 11, 9, 9);
                g2.setColor(fg);
                g2.setFont(Theme.font(Font.PLAIN, 15f));
                g2.drawString(f, 70, y);
                y += 34;
            }

            g2.setColor(Ui.alpha(fg, 130));
            g2.setFont(Theme.SMALL);
            FontMetrics fm = g2.getFontMetrics();
            String foot = "Java  ·  Swing  ·  Sockets  ·  JDBC";
            g2.drawString(foot, 48, h - 40 + fm.getAscent() / 2);
            g2.dispose();
        }
    }
}
