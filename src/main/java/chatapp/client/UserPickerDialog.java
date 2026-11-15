package chatapp.client;

import chatapp.common.MessageType;
import chatapp.common.Packet;
import chatapp.common.UserDTO;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * One reusable dialog for three jobs: start a private chat (pick one user), create a group
 * (pick many + name) and add a member (pick one). What happens on OK is a lambda
 * ({@link BiConsumer}) supplied by the caller.
 */
public class UserPickerDialog extends JDialog {
    private static final long serialVersionUID = 1L;

    private final ClientConnection conn;
    private final boolean askName;
    private final BiConsumer<String, List<String>> onPick;

    private final JTextField nameField = Theme.hintField("Group name");
    private final JTextField searchField = Theme.hintField("Search by name or username");
    private final DefaultListModel<UserDTO> model = new DefaultListModel<>();
    private final JList<UserDTO> list = new JList<>(model);
    private final JLabel error = new JLabel(" ");
    private final Consumer<Packet> listener = this::onPacket;

    public UserPickerDialog(Frame owner, ClientConnection conn, String title, boolean multi, boolean askName,
                            BiConsumer<String, List<String>> onPick) {
        super(owner, title, true);
        this.conn = conn;
        this.askName = askName;
        this.onPick = onPick;
        Theme.Palette p = Theme.P;

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(p.surface);

        JPanel top = new JPanel(new java.awt.GridLayout(0, 1, 0, 8));
        top.setOpaque(false);
        top.setBorder(BorderFactory.createEmptyBorder(18, 20, 10, 20));
        JLabel heading = new JLabel(title);
        heading.setFont(Theme.font(java.awt.Font.BOLD, 19f));
        heading.setForeground(p.text);
        top.add(heading);
        if (askName) top.add(nameField);
        top.add(searchField);
        if (multi) {
            JLabel tip = Theme.label("Click people to select them. Ctrl/Cmd+click picks several.");
            top.add(tip);
        }

        list.setBackground(p.surface);
        list.setFixedCellHeight(52);
        list.setSelectionMode(multi ? ListSelectionModel.MULTIPLE_INTERVAL_SELECTION : ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new UserCell());
        JScrollPane scroll = Ui.styleScroll(new JScrollPane(list), p.surface);
        scroll.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, p.border));

        error.setFont(Theme.SMALL);
        error.setForeground(p.danger);
        JButton ok = Theme.button(askName ? "Create group" : "Select");
        JButton cancel = Theme.secondaryButton("Cancel");
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.setBorder(BorderFactory.createEmptyBorder(10, 20, 12, 20));
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btns.setOpaque(false);
        btns.add(cancel);
        btns.add(ok);
        bottom.add(error, BorderLayout.CENTER);
        bottom.add(btns, BorderLayout.EAST);

        root.add(top, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        root.add(bottom, BorderLayout.SOUTH);
        setContentPane(root);
        setSize(440, 560);
        setMinimumSize(new Dimension(380, 440));
        setLocationRelativeTo(owner);

        ok.addActionListener(e -> confirm());
        cancel.addActionListener(e -> dispose());
        getRootPane().setDefaultButton(ok);
        if (!multi) {
            list.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) confirm();
                }
            });
        }
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { search(); }
            @Override public void removeUpdate(DocumentEvent e) { search(); }
            @Override public void changedUpdate(DocumentEvent e) { search(); }
        });

        conn.addListener(listener);
        search();
    }

    private void search() {
        conn.send(new Packet(MessageType.SEARCH_USERS, searchField.getText().trim()));
    }

    private void onPacket(Packet p) {
        if (p.getType() != MessageType.USERS) return;
        List<UserDTO> users = p.getPayload();
        model.clear();
        users.forEach(model::addElement);
    }

    private void confirm() {
        List<UserDTO> selected = list.getSelectedValuesList();
        String name = nameField.getText().trim();
        if (askName && name.isEmpty()) {
            error.setText("Please enter a group name.");
            return;
        }
        if (selected.isEmpty()) {
            error.setText("Please select at least one person.");
            return;
        }
        List<String> usernames = selected.stream().map(UserDTO::getUsername).collect(Collectors.toList());
        dispose();
        onPick.accept(name, usernames);
    }

    @Override
    public void dispose() {
        conn.removeListener(listener);
        super.dispose();
    }

    /** One row: avatar, name, @username and a green dot for people who are online. */
    private static class UserCell extends JPanel implements ListCellRenderer<UserDTO> {
        private static final long serialVersionUID = 1L;
        private final Ui.AvatarView avatar = new Ui.AvatarView(36);
        private final JLabel name = new JLabel();
        private final JLabel handle = new JLabel();
        private final JLabel dot = new JLabel();

        UserCell() {
            setLayout(new BorderLayout(12, 0));
            setBorder(BorderFactory.createEmptyBorder(6, 20, 6, 20));
            JPanel texts = new JPanel();
            texts.setOpaque(false);
            texts.setLayout(new javax.swing.BoxLayout(texts, javax.swing.BoxLayout.Y_AXIS));
            name.setFont(Theme.BOLD);
            handle.setFont(Theme.SMALL);
            texts.add(name);
            texts.add(handle);
            dot.setFont(Theme.SMALL_BOLD);
            add(avatar, BorderLayout.WEST);
            add(texts, BorderLayout.CENTER);
            add(dot, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends UserDTO> l, UserDTO u, int index,
                                                      boolean selected, boolean focus) {
            Theme.Palette p = Theme.P;
            setOpaque(true);
            setBackground(selected ? Ui.mix(p.surface, p.accent, 0.22) : p.surface);
            avatar.set(u.getId(), u.getFullName(), false);
            name.setText(u.getFullName());
            name.setForeground(p.text);
            handle.setText("@" + u.getUsername());
            handle.setForeground(p.muted);
            dot.setText(u.isOnline() ? "online" : "");
            dot.setForeground(new java.awt.Color(0x22C55E));
            return this;
        }
    }
}
