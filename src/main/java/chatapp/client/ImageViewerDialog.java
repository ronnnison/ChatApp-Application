package chatapp.client;

import chatapp.common.ChatMessageDTO;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Image;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;

/** Full-size photo viewer opened by clicking a photo in the chat. */
public class ImageViewerDialog extends JDialog {
    private static final long serialVersionUID = 1L;
    private static final int MAX_W = 900;
    private static final int MAX_H = 620;

    public ImageViewerDialog(Window owner, MediaCache media, ChatMessageDTO message, Runnable onSave) {
        super(owner, message.getFileName());
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.P.bg);

        JLabel picture = new JLabel("Loading...", SwingConstants.CENTER);
        picture.setFont(Theme.BASE);
        picture.setForeground(Theme.P.muted);
        picture.setPreferredSize(new Dimension(420, 300));
        picture.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        JButton save = Theme.button("Save photo");
        JButton close = Theme.secondaryButton("Close");
        save.addActionListener(e -> onSave.run());
        close.addActionListener(e -> dispose());
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 10));
        bottom.setBackground(Theme.P.surface);
        bottom.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.P.border));
        bottom.add(close);
        bottom.add(save);

        root.add(picture, BorderLayout.CENTER);
        root.add(bottom, BorderLayout.SOUTH);
        setContentPane(root);
        pack();
        setLocationRelativeTo(owner);

        media.fetch(message.getFileId(), data -> new SwingWorker<BufferedImage, Void>() {
            @Override
            protected BufferedImage doInBackground() throws Exception {
                return ImageIO.read(new ByteArrayInputStream(data.data));
            }

            @Override
            protected void done() {
                try {
                    BufferedImage img = get();
                    if (img == null) {
                        picture.setText("Cannot show this image.");
                        return;
                    }
                    double scale = Math.min(1.0, Math.min((double) MAX_W / img.getWidth(), (double) MAX_H / img.getHeight()));
                    int w = (int) Math.round(img.getWidth() * scale);
                    int h = (int) Math.round(img.getHeight() * scale);
                    picture.setText(null);
                    picture.setIcon(new javax.swing.ImageIcon(img.getScaledInstance(w, h, Image.SCALE_SMOOTH)));
                    picture.setPreferredSize(null);
                    pack();
                    setLocationRelativeTo(owner);
                } catch (Exception ex) {
                    picture.setText("Cannot show this image.");
                }
            }
        }.execute());
    }
}
