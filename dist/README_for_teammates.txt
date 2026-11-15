ChatApp - how to join the chat (for teammates)
==============================================
1. Install Java 11 or newer (Temurin/Adoptium JDK or JRE). Check with:  java -version
2. If the host sent you a file called  chatapp-server.crt , put it in THIS folder, next to
   ChatClient.jar. It switches on the encrypted connection. (No file = normal connection.)
3. Double-click ChatClient.jar  (or run_client.bat on Windows, run_client.sh on Mac/Linux).
4. In "Server address" type what the host tells you:
      the host PC's address, for example  192.168.1.23
      or address:port when the host uses a tunnel, for example  0.tcp.example.com:12345
5. Press "Create account" once (add a profile picture if you like), then sign in.
   When the connection is encrypted the sidebar shows  "encrypted (TLS)"  under your name.

Voice messages: the first time you press the microphone, allow microphone access when asked.
While recording, the bars next to the timer must move when you speak. If they stay flat:
  macOS:   System Settings > Privacy & Security > Microphone > switch on Terminal / Java, then restart the chat.
  Windows: Settings > Privacy > Microphone > allow desktop apps.

You do NOT need XAMPP, NetBeans or any database: only the host PC runs those.
