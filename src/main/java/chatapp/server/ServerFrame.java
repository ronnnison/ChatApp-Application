package chatapp.server;

import chatapp.common.Protocol;
import chatapp.db.DBConnection;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.io.IOException;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import java.util.List;

/** Server control window. Run this class (right-click > Run File) to start the chat server. */
public class ServerFrame extends JFrame {
    private final JSpinner portSpinner = new JSpinner(new SpinnerNumberModel(Protocol.PORT, 1024, 65535, 1));
    private final JButton startBtn = new JButton("Start server");
    private final JButton stopBtn = new JButton("Stop server");
    private final JLabel statusLabel = new JLabel("Stopped");
    private final JTextArea logArea = new JTextArea();
    private final DefaultListModel<String> onlineModel = new DefaultListModel<>();
    private final JLabel onlineTitle = new JLabel("Online users (0)");
    private ChatServer server;

    public ServerFrame() {
        super(Protocol.APP_NAME + " Server");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(860, 520);
        setLocationRelativeTo(null);

        JPanel top = new JPanel(new BorderLayout(10, 0));
        top.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        JPanel controls = new JPanel();
        controls.add(new JLabel("Port:"));
        controls.add(portSpinner);
        controls.add(startBtn);
        controls.add(stopBtn);
        top.add(controls, BorderLayout.WEST);
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 14f));
        top.add(statusLabel, BorderLayout.EAST);

        java.util.List<String> lan = chatapp.common.NetUtil.lanAddresses();
        JLabel lanLabel = new JLabel(lan.isEmpty()
                ? "No network address found - connect to Wi-Fi so teammates can reach you."
                : "Teammates on the same network type this in the login screen:   " + String.join("   or   ", lan));
        lanLabel.setFont(lanLabel.getFont().deriveFont(Font.BOLD));
        top.add(lanLabel, BorderLayout.SOUTH);

        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        JPanel logPanel = new JPanel(new BorderLayout());
        logPanel.setBorder(BorderFactory.createTitledBorder("Server log"));
        logPanel.add(new JScrollPane(logArea));

        JList<String> onlineList = new JList<>(onlineModel);
        JPanel onlinePanel = new JPanel(new BorderLayout());
        onlinePanel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        onlinePanel.add(onlineTitle, BorderLayout.NORTH);
        onlinePanel.add(new JScrollPane(onlineList));
        onlinePanel.setPreferredSize(new Dimension(220, 0));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, logPanel, onlinePanel);
        split.setResizeWeight(1.0);

        add(top, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);

        startBtn.addActionListener(e -> startServer());
        stopBtn.addActionListener(e -> stopServer());
        updateButtons(false);

        new Timer(1000, e -> refreshOnline()).start();
    }

    private void startServer() {
        String dbError = DBConnection.testConnection();
        if (dbError != null) {
            appendLog("WARNING: cannot reach MySQL -> " + dbError);
            appendLog("Start Apache + MySQL in XAMPP and import database/chatapp_db.sql.");
        } else {
            appendLog("Database connection OK.");
        }
        server = new ChatServer((Integer) portSpinner.getValue(), this::appendLog);
        try {
            server.start();
            updateButtons(true);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not start: " + ex.getMessage()
                    + "\n(Is the port already in use?)", "Server", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void stopServer() {
        if (server != null) server.stop();
        updateButtons(false);
        refreshOnline();
    }

    private void updateButtons(boolean running) {
        startBtn.setEnabled(!running);
        stopBtn.setEnabled(running);
        portSpinner.setEnabled(!running);
        statusLabel.setText(running ? "RUNNING" : "Stopped");
        statusLabel.setForeground(running ? new Color(0x15803D) : Color.GRAY);
    }

    private void refreshOnline() {
        List<String> names = (server != null && server.isRunning()) ? server.onlineUsernames() : List.of();
        onlineModel.clear();
        names.forEach(onlineModel::addElement);
        onlineTitle.setText("Online users (" + names.size() + ")");
    }

    /** Called from many threads, so the UI update is pushed onto the Swing thread. */
    private void appendLog(String line) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(line + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        SwingUtilities.invokeLater(() -> new ServerFrame().setVisible(true));
    }
}
