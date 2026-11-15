package chatapp.client;

import chatapp.common.ChatMessageDTO;
import chatapp.common.ConversationDTO;
import chatapp.common.MessageType;
import chatapp.common.Packet;
import chatapp.common.Payloads;
import chatapp.common.Protocol;
import chatapp.common.UserDTO;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Main chat window: chat list on the left, conversation + composer on the right. */
public class MainFrame extends JFrame implements MessageBubble.Host {
    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM").withZone(ZoneId.systemDefault());

    private final ClientConnection conn;
    private final UserDTO me;
    private final MediaCache media;

    // ---- sidebar
    private final List<ConversationDTO> all = new ArrayList<>();
    private final DefaultListModel<ConversationDTO> convModel = new DefaultListModel<>();
    private final JList<ConversationDTO> convList = new JList<>(convModel);
    private final Map<Long, Integer> unread = new HashMap<>();
    private final JTextField filterField = Theme.hintField("Search chats");

    // ---- conversation
    private final CardLayout cards = new CardLayout();
    private final JPanel centerCards = new JPanel(cards);
    private final JPanel messagesPanel = new JPanel();
    private final JScrollPane scroll;
    private final Ui.AvatarView headerAvatar = new Ui.AvatarView(42);
    private final JLabel titleLabel = new JLabel(" ");
    private final JLabel subtitleLabel = new JLabel(" ");
    private final Ui.IconButton addMemberBtn = new Ui.IconButton(Ui.Glyph.PLUS, "Add a member", 38, 20);

    // ---- composer
    private final CardLayout composerCards = new CardLayout();
    private final JPanel composer = new JPanel(composerCards);
    private final JTextField input = Theme.hintField("Type a message");
    private final Ui.IconButton emojiBtn = new Ui.IconButton(Ui.Glyph.SMILE, "Emoji", 38, 22);
    private final Ui.IconButton attachBtn = new Ui.IconButton(Ui.Glyph.CLIP, "Send a photo or Word document", 38, 22);
    private final Ui.IconButton micBtn = new Ui.IconButton(Ui.Glyph.MIC, "Record a voice message", 38, 22);
    private final Ui.IconButton sendBtn = new Ui.IconButton(Ui.Glyph.SEND, "Send", 40, 18).accent(true);
    private final JLabel recLabel = new JLabel("0:00");
    private VoiceRecorder recorder;
    private Timer recTimer;
    private long recordingConvId;

    private final Consumer<Packet> listener = this::onPacket;
    private ConversationDTO current;
    private long pendingOpenId;
    private boolean updatingList;

    public MainFrame(ClientConnection conn, UserDTO me, long reopenConversationId) {
        // Title bar of the chat window. Change the format here, the app name lives in Protocol.APP_NAME.
        super(Protocol.APP_NAME + "  •  " + me.getFullName());
        this.conn = conn;
        this.me = me;
        this.media = new MediaCache(conn);
        this.pendingOpenId = reopenConversationId;
        AvatarCache.attach(conn);

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setSize(1120, 720);
        setMinimumSize(new Dimension(860, 540));
        setLocationRelativeTo(null);

        Theme.Palette p = Theme.P;
        getContentPane().setBackground(p.bg);

        messagesPanel.setLayout(new BoxLayout(messagesPanel, BoxLayout.Y_AXIS));
        messagesPanel.setOpaque(false);
        JPanel holder = new ChatBackground();
        holder.setLayout(new BorderLayout());
        holder.add(messagesPanel, BorderLayout.NORTH);
        scroll = Ui.styleScroll(new JScrollPane(holder), p.chatBg);

        centerCards.add(new EmptyState(), "empty");
        centerCards.add(buildChatPanel(), "chat");
        cards.show(centerCards, "empty");

        add(buildSidebar(), BorderLayout.WEST);
        add(centerCards, BorderLayout.CENTER);
        setComposerEnabled(false);

        wireActions();
        conn.addListener(listener);
        conn.send(new Packet(MessageType.GET_CONVERSATIONS));
    }

    // ------------------------------------------------------------------ construction

    private JPanel buildSidebar() {
        Theme.Palette p = Theme.P;
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setPreferredSize(new Dimension(330, 0));
        sidebar.setBackground(p.surface);
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, p.border));

        // profile row
        Ui.AvatarView mine = new Ui.AvatarView(46).ring(true);
        mine.set(me.getId(), me.getFullName(), false);
        mine.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        mine.setToolTipText("My profile");
        mine.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                new ProfileDialog(MainFrame.this, conn, me).setVisible(true);
            }
        });
        JLabel name = new JLabel(me.getFullName());
        name.setFont(Theme.font(Font.BOLD, 15f));
        name.setForeground(p.text);
        JLabel handle = new JLabel("@" + me.getUsername() + (conn.isEncrypted() ? "  ·  encrypted (TLS)" : ""));
        handle.setFont(Theme.SMALL);
        handle.setForeground(p.muted);
        JPanel who = new JPanel();
        who.setOpaque(false);
        who.setLayout(new BoxLayout(who, BoxLayout.Y_AXIS));
        who.add(name);
        who.add(handle);

        Ui.IconButton themeBtn = new Ui.IconButton(Ui.Glyph.PALETTE, "Change theme", 36, 20);
        Ui.IconButton logout = new Ui.IconButton(Ui.Glyph.LOGOUT, "Sign out", 36, 20);
        themeBtn.addActionListener(e -> showThemeMenu(themeBtn));
        logout.addActionListener(e -> logout());
        JPanel tools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        tools.setOpaque(false);
        tools.add(themeBtn);
        tools.add(logout);

        JPanel profile = new JPanel(new BorderLayout(12, 0));
        profile.setOpaque(false);
        profile.add(mine, BorderLayout.WEST);
        profile.add(who, BorderLayout.CENTER);
        profile.add(tools, BorderLayout.EAST);

        JButton newChat = Theme.button("New chat");
        JButton newGroup = Theme.secondaryButton("New group");
        newChat.addActionListener(e -> new UserPickerDialog(this, conn, "Start a chat", false, false,
                (n, users) -> conn.send(new Packet(MessageType.OPEN_PRIVATE, users.get(0)))).setVisible(true));
        newGroup.addActionListener(e -> new UserPickerDialog(this, conn, "Create a group", true, true,
                (n, users) -> conn.send(new Packet(MessageType.CREATE_GROUP,
                        new Payloads.GroupRequest(n, new ArrayList<>(users))))).setVisible(true));
        JPanel actions = new JPanel(new GridLayout(1, 2, 8, 0));
        actions.setOpaque(false);
        actions.add(newChat);
        actions.add(newGroup);

        filterField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { applyFilter(); }
            @Override public void removeUpdate(DocumentEvent e) { applyFilter(); }
            @Override public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });

        JPanel north = new JPanel();
        north.setOpaque(false);
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        north.setBorder(BorderFactory.createEmptyBorder(16, 16, 12, 16));
        profile.setAlignmentX(Component.LEFT_ALIGNMENT);
        actions.setAlignmentX(Component.LEFT_ALIGNMENT);
        filterField.setAlignmentX(Component.LEFT_ALIGNMENT);
        filterField.setMaximumSize(new Dimension(Integer.MAX_VALUE, filterField.getPreferredSize().height));
        north.add(profile);
        north.add(Box.createVerticalStrut(14));
        north.add(actions);
        north.add(Box.createVerticalStrut(10));
        north.add(filterField);

        convList.setBackground(p.surface);
        convList.setFixedCellHeight(68);
        convList.setCellRenderer(new ConversationCell());
        convList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        convList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || updatingList) return;
            ConversationDTO c = convList.getSelectedValue();
            if (c != null) openConversation(c);
        });
        JScrollPane listScroll = Ui.styleScroll(new JScrollPane(convList), p.surface);
        listScroll.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, p.border));

        sidebar.add(north, BorderLayout.NORTH);
        sidebar.add(listScroll, BorderLayout.CENTER);
        return sidebar;
    }

    private JPanel buildChatPanel() {
        Theme.Palette p = Theme.P;

        titleLabel.setFont(Theme.font(Font.BOLD, 17f));
        titleLabel.setForeground(p.text);
        subtitleLabel.setFont(Theme.SMALL);
        subtitleLabel.setForeground(p.muted);
        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.add(titleLabel);
        titles.add(subtitleLabel);

        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setBackground(p.surface);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, p.border),
                BorderFactory.createEmptyBorder(11, 18, 11, 18)));
        header.add(headerAvatar, BorderLayout.WEST);
        header.add(titles, BorderLayout.CENTER);
        header.add(addMemberBtn, BorderLayout.EAST);
        addMemberBtn.setVisible(false);

        // ---- text composer
        JPanel inputBox = new Ui.RoundPanel(p.surface2, 22).line(p.border);
        inputBox.setLayout(new BorderLayout());
        inputBox.setBorder(BorderFactory.createEmptyBorder(2, 14, 2, 8));
        input.setOpaque(false);
        input.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));
        input.setBackground(new Color(0, 0, 0, 0));
        input.setCaretColor(p.accent);
        // the field keeps no focus border, the rounded box around it is the visual frame
        for (java.awt.event.FocusListener fl : input.getFocusListeners()) input.removeFocusListener(fl);
        inputBox.add(input, BorderLayout.CENTER);
        JPanel micWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        micWrap.setOpaque(false);
        micWrap.add(micBtn);
        inputBox.add(micWrap, BorderLayout.EAST);

        JPanel textBar = new JPanel(new BorderLayout(6, 0));
        textBar.setBackground(p.surface);
        textBar.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        left.add(emojiBtn);
        left.add(attachBtn);
        textBar.add(left, BorderLayout.WEST);
        textBar.add(inputBox, BorderLayout.CENTER);
        JPanel sendWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        sendWrap.setOpaque(false);
        sendWrap.add(sendBtn);
        textBar.add(sendWrap, BorderLayout.EAST);

        // ---- recording composer
        JPanel recBar = new JPanel(new BorderLayout(10, 0));
        recBar.setBackground(p.surface);
        recBar.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 12));
        Ui.IconButton recCancel = new Ui.IconButton(Ui.Glyph.CLOSE, "Cancel recording", 38, 20).tint(p.danger);
        Ui.IconButton recSend = new Ui.IconButton(Ui.Glyph.SEND, "Send voice message", 40, 18).accent(true);
        recCancel.addActionListener(e -> finishRecording(false));
        recSend.addActionListener(e -> finishRecording(true));
        JComponent recDot = new JComponent() {
            private static final long serialVersionUID = 1L;

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                Ui.smooth(g2);
                boolean on = (System.currentTimeMillis() / 500) % 2 == 0;
                g2.setColor(on ? Theme.P.danger : Ui.alpha(Theme.P.danger, 90));
                g2.fillOval(2, 4, 12, 12);
                g2.dispose();
            }
        };
        recDot.setPreferredSize(new Dimension(18, 20));
        recLabel.setFont(Theme.font(Font.BOLD, 16f));
        recLabel.setForeground(p.text);
        // live microphone level: the bars must move while you speak, otherwise nothing is being captured
        JComponent meter = new JComponent() {
            private static final long serialVersionUID = 1L;
            private float shown;

            @Override
            protected void paintComponent(Graphics g) {
                VoiceRecorder r = recorder;
                float now = r == null ? 0f : r.level();
                shown = Math.max(now, shown * 0.85f); // smooth fall-off
                int bars = 16;
                int lit = Math.round(shown * bars);
                Graphics2D g2 = (Graphics2D) g.create();
                Ui.smooth(g2);
                for (int i = 0; i < bars; i++) {
                    int h = 6 + (i * 10) / bars;
                    g2.setColor(i < lit ? Theme.P.accent : Theme.P.border);
                    g2.fillRoundRect(i * 8, 16 - h, 5, h, 3, 3);
                }
                g2.dispose();
            }
        };
        meter.setPreferredSize(new Dimension(16 * 8, 18));
        JLabel recHint = new JLabel("Speak now, the bars should move");
        recHint.setFont(Theme.SMALL);
        recHint.setForeground(p.muted);
        JPanel recInfo = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        recInfo.setOpaque(false);
        recInfo.add(recDot);
        recInfo.add(recLabel);
        recInfo.add(meter);
        recInfo.add(recHint);
        recBar.add(recCancel, BorderLayout.WEST);
        recBar.add(recInfo, BorderLayout.CENTER);
        recBar.add(recSend, BorderLayout.EAST);
        recDot.putClientProperty("blink", Boolean.TRUE);

        composer.add(textBar, "text");
        composer.add(recBar, "rec");
        composer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, p.border));
        composer.setBackground(p.surface);

        JPanel chat = new JPanel(new BorderLayout());
        chat.add(header, BorderLayout.NORTH);
        chat.add(scroll, BorderLayout.CENTER);
        chat.add(composer, BorderLayout.SOUTH);
        return chat;
    }

    private void wireActions() {
        sendBtn.addActionListener(e -> sendText());
        input.addActionListener(e -> sendText());
        attachBtn.addActionListener(e -> attachFile());
        micBtn.addActionListener(e -> startRecording());
        emojiBtn.addActionListener(e -> EmojiPicker.show(emojiBtn, emoji -> {
            input.replaceSelection(emoji);
            input.requestFocusInWindow();
        }));
        addMemberBtn.addActionListener(e -> {
            if (current == null) return;
            long convId = current.getId();
            new UserPickerDialog(this, conn, "Add a member", false, false,
                    (n, users) -> conn.send(new Packet(MessageType.ADD_MEMBER,
                            new Payloads.AddMemberRequest(convId, users.get(0))))).setVisible(true);
        });
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (recorder != null) recorder.cancel();
                conn.removeListener(listener);
                conn.send(new Packet(MessageType.LOGOUT));
                conn.close();
                dispose();
                System.exit(0);
            }
        });
    }

    // ------------------------------------------------------------------ incoming packets

    private void onPacket(Packet p) {
        switch (p.getType()) {
            case CONVERSATIONS: {
                List<ConversationDTO> list = p.getPayload();
                refreshConversations(list);
                break;
            }
            case CONVERSATION_OPENED: {
                ConversationDTO c = p.getPayload();
                selectConversation(c);
                break;
            }
            case HISTORY:
                showHistory(p.getPayload());
                break;
            case NEW_MESSAGE:
                onNewMessage(p.getPayload());
                break;
            case FILE_DATA:
                media.onFileData(p.getPayload());
                break;
            case AVATAR: {
                Payloads.AvatarData data = p.getPayload();
                AvatarCache.get().onAvatar(data);
                break;
            }
            case INFO: {
                String text = p.getPayload();
                Ui.info(this, "Done", text);
                break;
            }
            case ERROR: {
                String text = p.getPayload();
                media.cancelWaiting();
                Ui.error(this, "Something went wrong", text);
                break;
            }
            case DISCONNECTED:
                Ui.error(this, "Disconnected", "The connection to the server was lost.");
                backToLogin();
                break;
            default:
                break;
        }
    }

    private void refreshConversations(List<ConversationDTO> list) {
        all.clear();
        all.addAll(list);
        if (current != null) {
            for (ConversationDTO c : all) {
                if (c.getId() == current.getId()) {
                    current = c; // fresh member count and so on
                    updateHeader();
                }
            }
        }
        applyFilter();
        if (current == null && pendingOpenId > 0) {
            for (ConversationDTO c : all) {
                if (c.getId() == pendingOpenId) {
                    pendingOpenId = 0;
                    selectConversation(c);
                    break;
                }
            }
        }
    }

    private void applyFilter() {
        String q = filterField.getText().trim().toLowerCase();
        updatingList = true;
        convModel.clear();
        int selected = -1;
        for (ConversationDTO c : all) {
            if (q.isEmpty() || c.getDisplayText().toLowerCase().contains(q)) {
                convModel.addElement(c);
                if (current != null && c.getId() == current.getId()) selected = convModel.size() - 1;
            }
        }
        if (selected >= 0) convList.setSelectedIndex(selected);
        else convList.clearSelection();
        updatingList = false;
    }

    private void selectConversation(ConversationDTO c) {
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < convModel.size(); i++) {
                if (convModel.get(i).getId() == c.getId()) {
                    convList.setSelectedIndex(i);
                    return;
                }
            }
            if (pass == 0 && !filterField.getText().isEmpty()) filterField.setText(""); // chat hidden by the filter
            else break;
        }
        convModel.add(0, c);
        convList.setSelectedIndex(0);
    }

    private void openConversation(ConversationDTO c) {
        if (recorder != null) finishRecording(false);
        current = c;
        unread.remove(c.getId());
        convList.repaint();
        updateHeader();
        messagesPanel.removeAll();
        messagesPanel.revalidate();
        messagesPanel.repaint();
        cards.show(centerCards, "chat");
        setComposerEnabled(true);
        conn.send(new Packet(MessageType.GET_HISTORY, Long.valueOf(c.getId())));
        input.requestFocusInWindow();
    }

    private void updateHeader() {
        titleLabel.setText(current.getDisplayText());
        subtitleLabel.setText(current.isGroup() ? current.getMemberCount() + " members" : "Private chat");
        headerAvatar.set(current.getPeerUserId(), current.getDisplayText(), current.isGroup());
        addMemberBtn.setVisible(current.isGroup());
    }

    private void showHistory(Payloads.History h) {
        if (current == null || h.conversationId != current.getId()) return; // user switched meanwhile
        messagesPanel.removeAll();
        for (ChatMessageDTO m : h.messages) messagesPanel.add(bubbleFor(m));
        messagesPanel.revalidate();
        messagesPanel.repaint();
        scrollToBottom();
    }

    private void onNewMessage(ChatMessageDTO m) {
        if (current != null && m.getConversationId() == current.getId()) {
            messagesPanel.add(bubbleFor(m));
            messagesPanel.revalidate();
            messagesPanel.repaint();
            scrollToBottom();
        } else if (m.getSenderId() != me.getId()) {
            unread.merge(m.getConversationId(), 1, Integer::sum);
            Toolkit.getDefaultToolkit().beep();
        }
        conn.send(new Packet(MessageType.GET_CONVERSATIONS)); // re-sort: newest chat goes to the top
    }

    private Component bubbleFor(ChatMessageDTO m) {
        return new MessageBubble(m, m.getSenderId() == me.getId(), current != null && current.isGroup(), this);
    }

    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar bar = scroll.getVerticalScrollBar();
            bar.setValue(bar.getMaximum());
        });
    }

    // ------------------------------------------------------------------ MessageBubble.Host

    @Override
    public MediaCache media() {
        return media;
    }

    @Override
    public void download(ChatMessageDTO m) {
        media.fetch(m.getFileId(), this::saveFile);
    }

    @Override
    public void viewImage(ChatMessageDTO m) {
        new ImageViewerDialog(this, media, m, () -> download(m)).setVisible(true);
    }

    @Override
    public boolean beforeResize() {
        JScrollBar bar = scroll.getVerticalScrollBar();
        return bar.getValue() + bar.getVisibleAmount() >= bar.getMaximum() - 80;
    }

    @Override
    public void afterResize(boolean wasAtBottom) {
        if (wasAtBottom) scrollToBottom();
    }

    // ------------------------------------------------------------------ outgoing actions

    private void sendText() {
        String text = input.getText().trim();
        if (text.isEmpty() || current == null) return;
        if (text.length() > Protocol.MAX_TEXT_LENGTH) {
            Ui.info(this, "Message too long", "Messages are limited to " + Protocol.MAX_TEXT_LENGTH + " characters.");
            return;
        }
        conn.send(new Packet(MessageType.SEND_TEXT, new Payloads.TextRequest(current.getId(), text)));
        input.setText("");
    }

    private void attachFile() {
        if (current == null) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Photos and Word documents (png, jpg, gif, bmp, docx)",
                "docx", "png", "jpg", "jpeg", "gif", "bmp"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = chooser.getSelectedFile();
        if (file.length() > Protocol.MAX_FILE_BYTES) {
            Ui.info(this, "File too large", "That file is larger than " + Protocol.humanSize(Protocol.MAX_FILE_BYTES) + ".");
            return;
        }
        long convId = current.getId();
        attachBtn.setEnabled(false);
        new SwingWorker<byte[], Void>() { // read the file off the GUI thread
            @Override
            protected byte[] doInBackground() throws IOException {
                return Files.readAllBytes(file.toPath());
            }

            @Override
            protected void done() {
                attachBtn.setEnabled(true);
                try {
                    conn.send(new Packet(MessageType.SEND_FILE, new Payloads.FileUpload(convId, file.getName(), get())));
                } catch (InterruptedException | ExecutionException ex) {
                    Ui.error(MainFrame.this, "Error", "Could not read the file.");
                }
            }
        }.execute();
    }

    private void saveFile(Payloads.FileData fd) {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(fd.fileName));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File target = chooser.getSelectedFile();
        try {
            Files.write(target.toPath(), fd.data);
        } catch (IOException ex) {
            Ui.error(this, "Error", "Could not save the file: " + ex.getMessage());
            return;
        }
        if (Ui.confirm(this, "Saved", "Saved to " + target.getAbsolutePath() + "\nOpen it now?", "Open", "Close")
                && Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().open(target);
            } catch (IOException ex) {
                Ui.info(this, "Cannot open", "No application is set up to open this file.");
            }
        }
    }

    // ------------------------------------------------------------------ voice recording

    private void startRecording() {
        if (current == null || recorder != null) return;
        VoiceRecorder r = new VoiceRecorder();
        try {
            r.start();
        } catch (Exception ex) {
            Ui.error(this, "Microphone not available", "Could not use the microphone.\n"
                    + "Check that one is connected and that Java may use it\n"
                    + "(macOS: System Settings > Privacy & Security > Microphone).");
            return;
        }
        recorder = r;
        recordingConvId = current.getId();
        recLabel.setText("0:00");
        composerCards.show(composer, "rec");
        recTimer = new Timer(90, e -> {
            long s = recorder.elapsedMs() / 1000;
            recLabel.setText(String.format("%d:%02d", s / 60, s % 60));
            composer.repaint();
            if (recorder.isFull()) finishRecording(true);
        });
        recTimer.start();
    }

    private void finishRecording(boolean send) {
        VoiceRecorder r = recorder;
        if (r == null) return;
        recorder = null;
        recTimer.stop();
        composerCards.show(composer, "text");
        if (!send) {
            r.cancel();
            return;
        }
        try {
            if (r.elapsedMs() < 600) {
                r.cancel();
                Ui.info(this, "Too short", "Hold on a little longer: the voice message was under one second.");
                return;
            }
            if (r.isSilent()) {
                r.cancel();
                Ui.error(this, "No sound was recorded",
                        "Only silence was captured, so nothing was sent.\n\n"
                        + "macOS: System Settings > Privacy & Security > Microphone, turn ON the app you\n"
                        + "started the chat from (NetBeans, Terminal or Java), then quit and reopen it.\n"
                        + "Windows: Settings > Privacy > Microphone, allow desktop apps.\n"
                        + "Also check that the right input device is selected in the system sound settings.");
                return;
            }
            byte[] wav = r.stop();
            String name = "voice_" + System.currentTimeMillis() / 1000 + ".wav";
            conn.send(new Packet(MessageType.SEND_FILE, new Payloads.FileUpload(recordingConvId, name, wav)));
        } catch (IOException ex) {
            Ui.error(this, "Error", "Could not finish the recording.");
        }
    }

    // ------------------------------------------------------------------ misc

    private void setComposerEnabled(boolean enabled) {
        input.setEnabled(enabled);
        sendBtn.setEnabled(enabled);
        attachBtn.setEnabled(enabled);
        emojiBtn.setEnabled(enabled);
        micBtn.setEnabled(enabled);
    }

    private void showThemeMenu(JComponent anchor) {
        JPopupMenu menu = new JPopupMenu();
        for (Theme.Palette pal : Theme.ALL) {
            JMenuItem item = new JMenuItem((pal == Theme.P ? "✓  " : "     ") + pal.name);
            item.addActionListener(e -> switchTheme(pal));
            menu.add(item);
        }
        menu.show(anchor, 0, anchor.getHeight());
    }

    /** Re-creates this window with the new colours and opens the same chat again. */
    private void switchTheme(Theme.Palette palette) {
        if (palette == Theme.P) return;
        if (recorder != null) finishRecording(false);
        Theme.set(palette);
        conn.removeListener(listener);
        MainFrame next = new MainFrame(conn, me, current == null ? -1 : current.getId());
        next.setBounds(getBounds());
        next.setVisible(true);
        dispose();
    }

    private void logout() {
        conn.send(new Packet(MessageType.LOGOUT));
        backToLogin();
    }

    private void backToLogin() {
        if (recorder != null) finishRecording(false);
        conn.removeListener(listener);
        conn.close();
        dispose();
        new LoginFrame(new ClientConnection()).setVisible(true);
    }

    private static String shortTime(long millis) {
        if (millis <= 0) return "";
        Instant t = Instant.ofEpochMilli(millis);
        LocalDate day = t.atZone(ZoneId.systemDefault()).toLocalDate();
        return day.equals(LocalDate.now()) ? HOUR.format(t) : DAY.format(t);
    }

    // ------------------------------------------------------------------ custom parts

    /** Faint dot grid behind the messages. */
    private static class ChatBackground extends JPanel {
        private static final long serialVersionUID = 1L;

        @Override
        protected void paintComponent(Graphics g) {
            g.setColor(Theme.P.chatBg);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(Ui.alpha(Theme.P.dark ? Theme.P.accent : Theme.P.muted, Theme.P.dark ? 22 : 40));
            Rectangle clip = g.getClipBounds();
            int x0 = clip.x - clip.x % 28;
            int y0 = clip.y - clip.y % 28;
            for (int x = x0; x < clip.x + clip.width; x += 28) {
                for (int y = y0; y < clip.y + clip.height; y += 28) g.fillRect(x + 14, y + 14, 2, 2);
            }
        }
    }

    /** Shown until a chat is opened. */
    private static class EmptyState extends JPanel {
        private static final long serialVersionUID = 1L;

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Ui.smooth(g2);
            Theme.Palette p = Theme.P;
            g2.setColor(p.chatBg);
            g2.fillRect(0, 0, getWidth(), getHeight());
            int cx = getWidth() / 2;
            int cy = getHeight() / 2 - 30;
            g2.setPaint(Ui.accentGradient(cx - 46, cy - 46, 92, 92));
            g2.fillRoundRect(cx - 46, cy - 46, 92, 92, 30, 30);
            Ui.paintGlyph(g2, Ui.Glyph.CHAT, cx - 26, cy - 26, 52, p.onAccent);
            g2.setColor(p.text);
            g2.setFont(Theme.font(Font.BOLD, 20f));
            FontMetrics fm = g2.getFontMetrics();
            String a = "Pick a chat to start talking";
            g2.drawString(a, cx - fm.stringWidth(a) / 2, cy + 90);
            g2.setColor(p.muted);
            g2.setFont(Theme.BASE);
            fm = g2.getFontMetrics();
            String b = "or press New chat / New group on the left";
            g2.drawString(b, cx - fm.stringWidth(b) / 2, cy + 116);
            g2.dispose();
        }
    }

    /** Small rounded counter for unread messages. */
    private static class Badge extends JComponent {
        private static final long serialVersionUID = 1L;
        private int count;

        void setCount(int n) {
            count = n;
            setPreferredSize(n > 0 ? new Dimension(n > 9 ? 28 : 22, 20) : new Dimension(0, 20));
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (count <= 0) return;
            Graphics2D g2 = (Graphics2D) g.create();
            Ui.smooth(g2);
            g2.setPaint(Ui.accentGradient(0, 0, getWidth(), getHeight()));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
            g2.setColor(Theme.P.onAccent);
            g2.setFont(Theme.font(Font.BOLD, 11f));
            FontMetrics fm = g2.getFontMetrics();
            String t = count > 99 ? "99+" : String.valueOf(count);
            g2.drawString(t, (getWidth() - fm.stringWidth(t)) / 2, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }

    /** One chat in the sidebar: avatar, name, last-message preview, time and unread badge. */
    private class ConversationCell extends JPanel implements ListCellRenderer<ConversationDTO> {
        private static final long serialVersionUID = 1L;
        private final Ui.AvatarView avatar = new Ui.AvatarView(46);
        private final JLabel name = new JLabel();
        private final JLabel preview = new JLabel();
        private final JLabel time = new JLabel();
        private final Badge badge = new Badge();

        ConversationCell() {
            setLayout(new BorderLayout(12, 0));
            setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
            JPanel texts = new JPanel(new GridLayout(2, 1, 0, 2));
            texts.setOpaque(false);
            texts.add(name);
            texts.add(preview);
            JPanel right = new JPanel(new BorderLayout(0, 4));
            right.setOpaque(false);
            time.setPreferredSize(new Dimension(60, 16)); // wide enough for "12 Nov"
            right.add(time, BorderLayout.NORTH);
            JPanel badgeWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            badgeWrap.setOpaque(false);
            badgeWrap.add(badge);
            right.add(badgeWrap, BorderLayout.SOUTH);
            add(avatar, BorderLayout.WEST);
            add(texts, BorderLayout.CENTER);
            add(right, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends ConversationDTO> list, ConversationDTO c,
                                                      int index, boolean selected, boolean focus) {
            Theme.Palette p = Theme.P;
            int n = unread.getOrDefault(c.getId(), 0);
            setOpaque(true);
            setBackground(selected ? Ui.mix(p.surface, p.accent, 0.20) : p.surface);
            avatar.set(c.getPeerUserId(), c.getDisplayText(), c.isGroup());
            name.setText(c.getDisplayText());
            name.setFont(n > 0 ? Theme.BOLD : Theme.font(Font.BOLD, 14f));
            name.setForeground(p.text);
            preview.setText(c.getLastPreview().isEmpty() ? "No messages yet" : c.getLastPreview());
            preview.setFont(n > 0 ? Theme.SMALL_BOLD : Theme.SMALL);
            preview.setForeground(n > 0 ? p.text : p.muted);
            time.setText(shortTime(c.getLastTime()));
            time.setFont(Theme.SMALL.deriveFont(11f));
            time.setForeground(n > 0 ? p.accent : p.muted);
            time.setHorizontalAlignment(JLabel.RIGHT);
            badge.setCount(n);
            return this;
        }
    }
}
