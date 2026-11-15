package chatapp.client;

import chatapp.common.AvatarUtil;
import chatapp.common.MessageType;
import chatapp.common.Packet;
import chatapp.common.UserDTO;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.ExecutionException;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Shows your own profile and lets you change your profile picture after sign-up. */
public class ProfileDialog extends JDialog {
    private static final long serialVersionUID = 1L;

    public ProfileDialog(Frame owner, ClientConnection conn, UserDTO me) {
        super(owner, "My profile", true);
        Theme.Palette p = Theme.P;

        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBackground(p.surface);
        root.setBorder(BorderFactory.createEmptyBorder(26, 40, 22, 40));

        Ui.AvatarView avatar = new Ui.AvatarView(120).ring(true);
        avatar.set(me.getId(), me.getFullName(), false);
        avatar.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel name = new JLabel(me.getFullName());
        name.setFont(Theme.font(Font.BOLD, 20f));
        name.setForeground(p.text);
        name.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel handle = new JLabel("@" + me.getUsername());
        handle.setFont(Theme.BASE);
        handle.setForeground(p.muted);
        handle.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel note = Theme.label("");
        note.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton change = Theme.button("Change photo");
        JButton close = Theme.secondaryButton("Close");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        buttons.setOpaque(false);
        buttons.add(change);
        buttons.add(close);
        buttons.setAlignmentX(Component.CENTER_ALIGNMENT);

        root.add(avatar);
        root.add(Box.createVerticalStrut(14));
        root.add(name);
        root.add(handle);
        root.add(Box.createVerticalStrut(10));
        root.add(note);
        root.add(Box.createVerticalStrut(14));
        root.add(buttons);

        change.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new FileNameExtensionFilter("Pictures (jpg, png, gif, bmp)", "jpg", "jpeg", "png", "gif", "bmp"));
            if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
            File file = chooser.getSelectedFile();
            note.setText("Uploading...");
            new SwingWorker<byte[], Void>() {
                @Override
                protected byte[] doInBackground() throws IOException {
                    return AvatarUtil.normalize(Files.readAllBytes(file.toPath()), 30 * 1024 * 1024);
                }

                @Override
                protected void done() {
                    try {
                        byte[] png = get();
                        if (png == null) {
                            note.setText("That file is not a picture we can read.");
                        } else {
                            conn.send(new Packet(MessageType.UPDATE_AVATAR, png));
                            note.setText("Photo updated.");
                        }
                    } catch (InterruptedException | ExecutionException ex) {
                        note.setText("Could not read that file.");
                    }
                }
            }.execute();
        });
        close.addActionListener(e -> dispose());

        setContentPane(root);
        setResizable(false);
        pack();
        setSize(Math.max(getWidth(), 360), getHeight());
        setLocationRelativeTo(owner);
    }
}
