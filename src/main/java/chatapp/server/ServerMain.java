package chatapp.server;

import chatapp.common.NetUtil;
import chatapp.common.Protocol;
import chatapp.db.DBConnection;

/**
 * Headless server for hosting on a cloud machine / always-on PC (no window needed).
 * Usage:  java -cp "ChatApp-1.0.jar:mysql-connector-j-8.4.0.jar" chatapp.server.ServerMain [port]
 * (on Windows use ; instead of : between the jar files)
 */
public final class ServerMain {
    private ServerMain() { }

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : Protocol.PORT;
        ChatServer server = new ChatServer(port, System.out::println);

        String dbError = DBConnection.testConnection();
        System.out.println(dbError == null ? "Database connection OK." : "WARNING: database not reachable -> " + dbError);

        server.start();
        for (String address : NetUtil.lanAddresses()) {
            System.out.println("Teammates on this network can connect to: " + address + ":" + port);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        Thread.currentThread().join(); // keep running until the process is stopped
    }
}
