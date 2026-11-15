package chatapp.client;

import javax.swing.SwingUtilities;


public class ClientApp {
    public static void main(String[] args) {
        Theme.install();
        SwingUtilities.invokeLater(() -> new LoginFrame(new ClientConnection()).setVisible(true));
    }
}
