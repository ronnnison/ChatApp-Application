package chatapp.client;

import javax.swing.SwingUtilities;

/** Entry point of the chat client. Run as many copies as you like to simulate several users. */
public class ClientApp {
    public static void main(String[] args) {
        Theme.install();
        SwingUtilities.invokeLater(() -> new LoginFrame(new ClientConnection()).setVisible(true));
    }
}
