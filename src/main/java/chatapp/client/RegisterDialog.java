package chatapp.client;

import chatapp.common.AvatarUtil;
import chatapp.common.MessageType;
import chatapp.common.Packet;
import chatapp.common.Payloads;
import chatapp.common.Validators;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;

/** "Create account" form with an optional profile picture and a live password-strength bar. */
public class RegisterDialog extends JDialog {
    private static final long serialVersionUID = 1L;

    private final ClientConnection conn;
    private final Supplier<String> hostSupplier;
    private final Consumer<String> onSuccess;

    private final Ui.AvatarView avatarView = new Ui.AvatarView(92);
    private final JTextField nameField = Theme.field();
    private final JTextField userField = Theme.field();
    private final JTextField emailField = Theme.field();
    private final JPasswordField passField = Theme.passwordField();
    private final JPasswordField confirmField = Theme.passwordField();
    private final StrengthBar strength = new StrengthBar();
    private final JButton createBtn = Theme.button("Create account");
    private final JLabel status = new JLabel(" ");
    private final Consumer<Packet> listener = this::onPacket;
    private byte[] avatarPng;

    public RegisterDialog(JFrame owner, ClientConnection conn, Supplier<String> hostSupplier, Consumer<String> onSuccess) {
        super(owner, "Create account", true);
        this.conn = conn;
        this.hostSupplier = hostSupplier;
        this.onSuccess = onSuccess;
        Theme.Palette p = Theme.P;

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(p.surface);
        card.setBorder(BorderFactory.createEmptyBorder(24, 34, 24, 34));

        JLabel title = new JLabel("Create your account");
        title.setFont(Theme.font(Font.BOLD, 22f));
        title.setForeground(p.text);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(title);
        card.add(Box.createVerticalStrut(14));

        // ---- profile picture row
        avatarView.ring(true);
        avatarView.set(0, "?", false);
        JButton choose = Theme.secondaryButton("Choose photo");
        JButton remove = Theme.secondaryButton("Remove");
        choose.addActionListener(e -> choosePhoto());
        remove.addActionListener(e -> setAvatar(null));
        JLabel hint = Theme.label("<html>Optional. JPG or PNG,<br>cropped to a square.</html>");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        buttons.setOpaque(false);
        buttons.add(choose);
        buttons.add(Box.createHorizontalStrut(8));
        buttons.add(remove);
        JPanel side = new JPanel();
        side.setOpaque(false);
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        side.add(Box.createVerticalStrut(10));
        side.add(buttons);
        side.add(Box.createVerticalStrut(8));
        side.add(hint);
        JPanel picRow = new JPanel(new BorderLayout(16, 0));
        picRow.setOpaque(false);
        picRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        picRow.add(avatarView, BorderLayout.WEST);
        picRow.add(side, BorderLayout.CENTER);
        card.add(picRow);
        card.add(Box.createVerticalStrut(16));

        Ui.addField(card, "FULL NAME", nameField);
        Ui.addField(card, "USERNAME (letters, digits, _)", userField);
        Ui.addField(card, "EMAIL", emailField);
        Ui.addField(card, "PASSWORD (8+ characters, letter + digit)", passField);
        strength.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.remove(card.getComponentCount() - 1); // drop the spacer after the password field
        card.add(Box.createVerticalStrut(6));
        card.add(strength);
        card.add(Box.createVerticalStrut(12));
        Ui.addField(card, "CONFIRM PASSWORD", confirmField);

        status.setFont(Theme.SMALL);
        status.setForeground(p.danger);
        status.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(status);
        card.add(Box.createVerticalStrut(8));
        createBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        createBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, createBtn.getPreferredSize().height + 4));
        card.add(createBtn);

        setContentPane(card);
        getRootPane().setDefaultButton(createBtn);
        pack();
        setSize(430, getHeight());
        setResizable(false);
        setLocationRelativeTo(owner);

        // initials in the preview follow the typed name
        nameField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { refreshPreview(); }
            @Override public void removeUpdate(DocumentEvent e) { refreshPreview(); }
            @Override public void changedUpdate(DocumentEvent e) { refreshPreview(); }
        });
        passField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { strength.update(new String(passField.getPassword())); }
            @Override public void removeUpdate(DocumentEvent e) { strength.update(new String(passField.getPassword())); }
            @Override public void changedUpdate(DocumentEvent e) { strength.update(new String(passField.getPassword())); }
        });

        createBtn.addActionListener(e -> submit());
        conn.addListener(listener);
    }

    private void refreshPreview() {
        avatarView.set(0, nameField.getText(), false);
    }

    private void choosePhoto() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Pictures (jpg, png, gif, bmp)", "jpg", "jpeg", "png", "gif", "bmp"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = chooser.getSelectedFile();
        new SwingWorker<byte[], Void>() { // decoding a big photo must not freeze the window
            @Override
            protected byte[] doInBackground() throws IOException {
                return AvatarUtil.normalize(Files.readAllBytes(file.toPath()), 30 * 1024 * 1024);
            }

            @Override
            protected void done() {
                try {
                    byte[] png = get();
                    if (png == null) setStatus("That file is not a picture we can read.");
                    else setAvatar(png);
                } catch (InterruptedException | ExecutionException ex) {
                    setStatus("Could not read that file.");
                }
            }
        }.execute();
    }

    private void setAvatar(byte[] png) {
        avatarPng = png;
        try {
            avatarView.setImage(png == null ? null : ImageIO.read(new ByteArrayInputStream(png)));
        } catch (IOException e) {
            avatarView.setImage(null);
        }
        if (png != null) setStatus(" ");
    }

    private void submit() {
        String pw = new String(passField.getPassword());
        if (!pw.equals(new String(confirmField.getPassword()))) {
            setStatus("Passwords do not match.");
            return;
        }
        Payloads.Credentials c = new Payloads.Credentials(userField.getText().trim(), pw,
                nameField.getText().trim(), emailField.getText().trim(), avatarPng);
        String problem = Validators.validateRegistration(c);
        if (problem != null) {
            setStatus(problem);
            return;
        }
        createBtn.setEnabled(false);
        setStatus(" ");
        String host = hostSupplier.get();
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
                    conn.send(new Packet(MessageType.REGISTER, c));
                } catch (InterruptedException | ExecutionException ex) {
                    createBtn.setEnabled(true);
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    setStatus("Cannot reach server: " + cause.getMessage());
                }
            }
        }.execute();
    }

    private void onPacket(Packet p) {
        if (!isShowing()) return;
        switch (p.getType()) {
            case REGISTER_OK:
                Ui.info(this, "Welcome!", "Your account was created. You can sign in now.");
                onSuccess.accept(userField.getText().trim());
                dispose();
                break;
            case REGISTER_FAIL: {
                String reason = p.getPayload();
                createBtn.setEnabled(true);
                setStatus(reason);
                break;
            }
            case DISCONNECTED:
                createBtn.setEnabled(true);
                setStatus("Connection to the server was lost.");
                break;
            default:
                break;
        }
    }

    private void setStatus(String text) {
        status.setText("<html>" + text.replace("<", "&lt;") + "</html>");
    }

    @Override
    public void dispose() {
        conn.removeListener(listener);
        super.dispose();
    }

    /** Four-segment bar: the more varied and longer the password, the more segments light up. */
    private static class StrengthBar extends JComponent {
        private static final long serialVersionUID = 1L;
        private int score;
        private String label = "";

        StrengthBar() {
            setPreferredSize(new Dimension(100, 16));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));
        }

        void update(String pw) {
            int s = 0;
            if (pw.length() >= 8) s++;
            if (pw.chars().anyMatch(Character::isDigit) && pw.chars().anyMatch(Character::isLetter)) s++;
            if (pw.chars().anyMatch(Character::isUpperCase) && pw.chars().anyMatch(Character::isLowerCase)) s++;
            if (pw.length() >= 12 || pw.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch))) s++;
            score = pw.isEmpty() ? 0 : Math.max(1, s);
            label = pw.isEmpty() ? "" : new String[]{"", "Weak", "Okay", "Good", "Strong"}[score];
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Ui.smooth(g2);
            Theme.Palette p = Theme.P;
            Color[] cols = {p.danger, new Color(0xF59E0B), new Color(0x84CC16), new Color(0x22C55E)};
            int segW = 54;
            for (int i = 0; i < 4; i++) {
                g2.setColor(i < score ? cols[Math.max(0, score - 1)] : p.border);
                g2.fillRoundRect(i * (segW + 4), 4, segW, 5, 5, 5);
            }
            g2.setColor(p.muted);
            g2.setFont(Theme.SMALL);
            g2.drawString(label, 4 * (segW + 4) + 6, 10);
            g2.dispose();
        }
    }
}
