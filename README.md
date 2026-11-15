# ChatApp v2 - Object Oriented Programming Lab Project

A real-time desktop chat application in Java: accounts with profile pictures, private and group chats,
emojis, photos shown inside the chat, voice notes, document sharing, four colour themes, and a server
that teammates on other computers can join. Built with Swing, TCP sockets, multithreading and JDBC (MySQL via XAMPP).

---

## 1. What is in the zip

| Path | What it is |
|---|---|
| `README.md` | this guide |
| `database/chatapp_db.sql` | full database for a **new** install (6 tables, AUTO_INCREMENT) |
| `database/upgrade_v2.sql` | only for people who already imported the first version |
| `pom.xml`, `src/` | the NetBeans (Maven) project: client, server and shared code |
| `dist/ChatClient.jar` | ready-to-run client for teammates (no database, no NetBeans needed) |
| `dist/run_client.bat`, `run_client.sh`, `README_for_teammates.txt` | one-page instructions to hand to teammates |

### Features
- **Accounts**: sign up with a profile picture (auto-cropped square, optional), password strength bar, sign in/out, change your photo later from the profile screen
- **Chats**: private 1-to-1 chats, named groups with an admin who can add members, chat search, unread badges, last-message preview
- **Rich messages**: emoji picker, photos displayed inline (click to enlarge and save), voice notes with waveform and play button (up to 2 minutes), `.docx` and image files with a download button
- **Themes**: Cyber (dark cyan/violet), Light, Aurora (purple/pink), Matrix (green). Switch from the half-moon button in the sidebar or the dots on the login screen; your choice is remembered
- **Real time**: every message reaches all online members instantly; messages for offline users are waiting in the history when they sign in
- **Encryption**: message text and uploaded files are stored encrypted (AES-256-GCM); the whole connection can be encrypted with TLS (section 5)
- **Security**: PBKDF2 salted password hashing, prepared statements, server-side validation, 5-failed-logins lock, members-only access to chats and files, file extension and content checks, a deserialization allow-list

---

## 2. How the pieces fit together

```
 Teammate PCs (only ChatClient.jar)            Host PC (or cloud machine)
 +-------------------+     TCP port 5555      +--------------------------------+      +----------------+
 | LoginFrame        |  Packet(type,payload)  | ChatServer (ServerSocket)      | JDBC | MySQL (XAMPP)  |
 | RegisterDialog    | <--------------------> |  one ClientHandler thread per  |<---->|  chatapp_db    |
 | MainFrame         |                        |  client + writer thread/queue  |      +----------------+
 | reader/writer     |                        |  DAOs (PreparedStatement)      |
 | threads           |                        |  server_uploads/ (file bytes)  |
 +-------------------+                        +--------------------------------+
```

Only the **host** runs the database and the server. Everybody else just starts the client and types the
host's address. All users share one database, so everyone sees the same accounts and chats.

Database tables: `users` (with `avatar`), `conversations`, `conversation_members`, `messages`
(types TEXT / IMAGE / DOCUMENT / VOICE), `files`, `login_logs`.

---

## 3. Step-by-step: run it on your own PC (host)

### Step 1. Install the tools (once)
JDK 11 or newer (Temurin 17 or 21 is fine), Apache NetBeans, and XAMPP.

### Step 2. Create the database
1. XAMPP Control Panel: **Start** the **MySQL** module (Apache only if you want phpMyAdmin).
2. Open `http://localhost/phpmyadmin`.
3. **New install:** Import tab, choose `database/chatapp_db.sql`, Go.
   **Already have version 1?** Click `chatapp_db`, open the SQL tab, paste the content of `database/upgrade_v2.sql`, Go.
   It keeps your users and messages. (It uses `ADD COLUMN IF NOT EXISTS`, which XAMPP's MariaDB supports.)

### Step 3. Open the project in NetBeans
1. File, Open Project, pick the `ChatApp` folder (it shows a Maven icon). Wait for the dependency download (MySQL driver).
2. Right-click the project, **Clean and Build**. Source files must be UTF-8 (the pom already sets this) because the emoji picker contains emoji characters.

### Step 4. Start the server
1. `Source Packages > chatapp.server`, right-click `ServerFrame.java`, **Run File**.
2. Click **Start server**. The log must say `Database connection OK.` and `Server listening on port 5555`.
3. The top of the window shows your network address(es), for example `192.168.1.23`. That is what teammates will type.

### Step 5. Start your own client
Right-click `chatapp.client.ClientApp`, **Run File**. Server address: `localhost`. Press **Create account**, add a photo, sign in.
To test alone, run `ClientApp` a second time and create a second account.

### Step 6. Try every feature (use this as your demo script)
1. Create two accounts with different profile pictures.
2. **New chat**, pick the other user, send a message. Add emoji with the smiley button.
3. **Attach** a `.png`/`.jpg`: it appears as a picture in both windows. Click it to enlarge, or press the download arrow.
4. **Attach** a `.docx`: the other user downloads it and opens it.
5. Press the **microphone**, speak, press **send** (or the X to cancel). The other user presses play.
6. **New group**, pick two or more people, send messages, use **+** in the header to add a member (admin only).
7. Try all four themes. Close and reopen a client: history is still there.

---

## 4. Step-by-step: let your teammates join from their own computers

Pick the option that matches where everybody is. Option A is the easiest for a lab demo.

### Option A: same Wi-Fi or same router (simplest)
1. Host: start MySQL, then the server (steps above). Note the address shown in the server window (an IPv4 such as `192.168.1.23`).
2. Host, Windows firewall: allow inbound TCP port 5555 once. Either accept the "Allow access" prompt for Java, or run in an Administrator command prompt:
   `netsh advfirewall firewall add rule name="ChatApp" dir=in action=allow protocol=TCP localport=5555`
3. Give each teammate the `dist` folder (or just `ChatClient.jar`). They install Java 11+ and double-click `ChatClient.jar`
   (or `run_client.bat` / `run_client.sh`).
4. On their login screen: **Server address** = the host's address. Then **Create account** and sign in.
5. Everyone is now in the same chat system at the same time.

If a teammate gets "Connection refused" or a timeout: wrong address, server not started, firewall, or the network blocks
device-to-device traffic. University and public Wi-Fi often does ("client isolation"). Use your phone hotspot for everyone, or Option B.

### Option B: different networks (home, hostel, campus) with a private network app
Tools such as **Tailscale** or **ZeroTier** put all your devices on one private network with no router settings and with encrypted traffic.
1. Everyone installs the app and joins the same private network (Tailscale: the host invites teammates; check the free-plan user limit on their site).
2. Host: run MySQL and the server as usual.
3. Teammates: Server address = the host's private address shown by the app (Tailscale addresses look like `100.x.y.z`).
Because the traffic runs through the encrypted private network, this is also the safest way to use the app away from home.

### Option C: a public tunnel
A TCP tunnel service (for example `ngrok tcp 5555`) gives you a public address such as `0.tcp.example.com:12345`.
Teammates type that whole thing, including the port, into **Server address**. Free plans change the address each time and may need an
account. The app's traffic is not encrypted (see the note below), so use this only for a short demo with throw-away passwords.

### Option D: a cloud machine that is always on (for a long-running demo)
1. Rent a small Linux machine (any provider with a free or cheap tier). Install Java 17 and MariaDB.
2. Create the database: `mysql -u root -p < database/chatapp_db.sql`, and create a database user with a password.
3. Build the project in NetBeans (Clean and Build) and copy `target/ChatApp-1.0.jar` and the MySQL driver jar
   (`mysql-connector-j-8.4.0.jar`, found in `~/.m2` after the first build) to the machine.
4. Run the headless server (no window needed):
   ```
   export DB_USER=chatuser DB_PASSWORD=your_password
   java -cp "ChatApp-1.0.jar:mysql-connector-j-8.4.0.jar" chatapp.server.ServerMain 5555
   ```
   (Other settings: `DB_HOST`, `DB_PORT`, `DB_NAME`.)
5. Open TCP port 5555 in the provider's firewall. Teammates type the machine's public IP.

**Important:** by default the chat connection is plain TCP. On a home or lab network that is fine for a course project.
For a public server turn TLS on (section 5) or use a private network (Option B), and never reuse real passwords.

### How teammates update the client
If you change the code, rebuild and give them the new `ChatClient.jar` (from NetBeans: Clean and Build, then take `target/ChatApp-1.0.jar`;
it runs the client when double-clicked). All users must use the same version, because the client and server share the same data classes.

---

## 5. Encrypting the messages

There are two layers. Use both for the strongest setup.

### Layer 1: encryption at rest (already on, nothing to set up)
Message text and every uploaded file (photos, voice notes, documents) are stored with **AES-256-GCM**. Open phpMyAdmin and look at
the `messages` table: the `content` column shows `enc1:...` gibberish, and the files in `server_uploads/` cannot be opened.
- The key is in the file **`chatapp.key`**, created next to the server the first time it starts (in the NetBeans project folder when
  you run `ServerFrame` from NetBeans). **Back it up and never share or upload it.** If it is lost, old messages show as
  `[message could not be decrypted]` and old files cannot be opened.
- Every message gets its own random IV, is bound to its chat, and any change to the stored data is detected and rejected.
- Messages and files from before this feature are still readable; only new ones are encrypted.
- Code: `chatapp.db.DataCipher` (used by `MessageDao`, `ConversationDao` and `ClientHandler`).

### Layer 2: encryption in transit with TLS (turn on with two files)
This hides passwords, messages, photos and voice notes from anybody on the same Wi-Fi, and makes sure clients only talk to the real server.
1. **Host.** Open a command prompt in the folder where the server runs (the NetBeans project folder) and run the two JDK commands
   (`keytool` is in the JDK `bin` folder; replace the address with yours, or leave the `-ext` part out):
   ```
   keytool -genkeypair -alias chatapp -keyalg RSA -keysize 2048 -validity 3650 -storetype PKCS12 -keystore chatapp-server.p12 -storepass changeit -dname "CN=ChatApp" -ext "san=dns:localhost,ip:192.168.1.23"
   keytool -exportcert -alias chatapp -keystore chatapp-server.p12 -storepass changeit -rfc -file chatapp-server.crt
   ```
2. Restart the server. The log must say `Encryption in transit: TLS ON`.
3. **Every client PC** needs the public file `chatapp-server.crt`: put it **next to `ChatClient.jar`** (the host's own client started from NetBeans finds it in the project folder).
   Send the file to teammates in any way (chat, USB, email); it is public and safe to share. Never share the `.p12` file.
4. When a client is connected through TLS the sidebar shows `@username  ·  encrypted (TLS)`.
- Once `chatapp-server.p12` exists the server accepts **only** TLS clients. To go back to plain TCP, rename the `.p12` file and restart.
- If you create a new `.p12`, teammates need the new `.crt`.
- A different keystore password: set the environment variable `CHAT_TLS_PASSWORD` (or `-Dchatapp.keystore.password=...`) for the server.
- Code: `chatapp.common.TlsSupport` (the client trusts only the one pinned certificate, so nobody can pose as the server).

### What this does not do
This is not end-to-end encryption. The server has the key, so whoever runs the server could read the messages while handling them.
For a course project that is the usual design (the same as most web chat apps). True end-to-end encryption would need a key pair per user and
a key per chat shared with each member, which is a good extension idea for your report.

---

## 6. Renaming the app and other small changes

| I want to change | Where |
|---|---|
| The **name of the app** (title bar of the login, chat and server windows, and the big name on the login screen) | `src/main/java/chatapp/common/Protocol.java`, the line `APP_NAME = "ChatApp"`. Change the text between the quotes. |
| The **format of the chat window title** (for example drop your name or add a slogan) | `chatapp/client/MainFrame.java`, the line `super(Protocol.APP_NAME + "  •  " + me.getFullName());` near the top of the constructor |
| The **server port** | `Protocol.PORT` (clients) and the Port box in the server window |
| **Colours** of a theme, or a new theme | `chatapp/client/Theme.java`: add or edit a `Palette` and put it in `ALL` |
| **Maximum file size / voice length / message length** | `Protocol.MAX_FILE_BYTES`, `VoiceRecorder.MAX_SECONDS`, `Protocol.MAX_TEXT_LENGTH` |
| **Database user and password** | environment variables `DB_USER`, `DB_PASSWORD` (see `DBConnection`), or edit the defaults there |

After a change: Clean and Build in NetBeans, and give teammates the new `ChatClient.jar`.

---

## 7. Course content mapping (for your report and viva)

| Course topic | Where it is used |
|---|---|
| **Inheritance** | `Entity` (abstract) is extended by `UserDTO`, `ConversationDTO`, `ChatMessageDTO`; `ClientHandler extends Thread`; frames extend `JFrame`/`JDialog`; custom components extend `JPanel`/`JButton`/`JComponent` |
| **Polymorphism** | `getDisplayText()` differs per `Displayable`; overridden `paintComponent` in every custom component; list cell renderers |
| **Interfaces** | `Displayable`, `Dao<T>`, `Runnable`, `MessageBubble.Host` (callback interface), `Consumer`/`BiConsumer` listeners |
| **Collections** | `ConcurrentHashMap` (online users), `Set`, `List`, `Map` (unread counters, caches), `BlockingQueue`, `Iterator`, `LinkedHashMap` as an LRU cache in `MediaCache` |
| **Streams** | `ChatServer.onlineUsernames()` and `broadcast()`, `UserPickerDialog`, conversation filtering |
| **Multithreading** | one `ClientHandler` thread per client, reader and writer threads on both sides, `VoiceRecorder` capture thread, `SwingWorker` for file and image work, `SwingUtilities.invokeLater` for GUI updates |
| **Inter-thread communication** | writer threads consume a `LinkedBlockingQueue<Packet>` (producer/consumer) |
| **Deadlock prevention** | no code path holds two locks; lock-free concurrent maps; sending only enqueues |
| **Thread interruption** | `writer.interrupt()` in `ClientHandler.cleanup()`, `InterruptedException` handled in the write loops, recorder shutdown |
| **Networking** | `ServerSocket`/`Socket`, object streams, timeouts, keep-alive, custom protocol (`Packet`, `MessageType`), `host:port` parsing |
| **Security / cryptography (bonus)** | `DataCipher` (AES-256-GCM with `javax.crypto`), `TlsSupport` (`SSLServerSocket` / `SSLSocket`, certificate pinning), `PasswordUtil` (PBKDF2) |
| **JDBC** | `DBConnection` (configurable by environment), four DAOs, `PreparedStatement`, transactions, generated keys, BLOB avatar storage |
| **GUI (AWT/Swing)** | custom-painted components, layout managers, `JFileChooser`, `javax.sound.sampled` audio, `ImageIO`, themes through a `Palette` class |
| **Generics** | `Dao<T>`, generic `Packet.getPayload()`, `JList<T>`, `DefaultListModel<T>`, `BlockingQueue<Packet>`, `Consumer<T>` |
| **Web programming (Servlets/JSP)** | Not part of a desktop chat. If your lab requires it, add a small Tomcat page that lists users and chats from the same database. |

---

## 8. Test checklist for the demo

| # | Test | Expected |
|---|---|---|
| 1 | Sign up with a profile picture | picture shows in sidebar, chat header, bubbles |
| 2 | Sign up with a text file renamed `.png` as the photo | "could not be read" message |
| 3 | Existing username / weak password | clear error message |
| 4 | Wrong password 5 times | account temporarily locked |
| 5 | Same account in two windows | second one refused |
| 6 | Private chat, two PCs | instant delivery both ways |
| 7 | Group with 3 users, add a 4th | all online members receive messages |
| 8 | Send a photo | thumbnail appears inline, click to enlarge |
| 9 | Record and send a voice note | waveform bubble with correct length, plays on the other PC |
| 10 | Send `.docx`; rename an `.exe` to `.png` and send | docx downloads; the fake image is rejected |
| 11 | Message to an offline user | appears after they sign in |
| 12 | Stop the server | clients return to the login screen |
| 13 | Switch themes | whole window restyles, chat stays open |
| 14 | Look at the `messages` table in phpMyAdmin | `content` shows `enc1:...`, not your text |
| 15 | Turn TLS on, client without the `.crt` file | clear message, no connection |
| 16 | Turn TLS on, client with the `.crt` file | connects, sidebar shows `encrypted (TLS)` |

---

## 9. Troubleshooting

| Problem | Fix |
|---|---|
| Server log: `Communications link failure` / refused | Start MySQL in XAMPP |
| `Unknown database 'chatapp_db'` | Import `chatapp_db.sql` |
| `Unknown column 'avatar'` or `Data truncated for column 'msg_type'` | You have the first database version: run `upgrade_v2.sql` |
| `Access denied for user 'root'` | Set `DB_PASSWORD` (environment variable or `-Ddb.password=...`) |
| `No suitable driver found` | Maven has not downloaded the MySQL driver: Clean and Build with internet on |
| Client: "Cannot reach server: Connection refused" | Start the server first; check the address; firewall; same network |
| Client: "Connection timed out" | Wrong IP, firewall, or the Wi-Fi isolates devices (try a hotspot or Option B) |
| Port already in use | Close the other server window, or pick another port in the server window and type `address:port` on the clients |
| Microphone button says it cannot access the microphone | Plug in a microphone; on macOS allow it under System Settings > Privacy > Microphone for NetBeans or Terminal; on Windows check Privacy > Microphone |
| Voice note records but is silent or says "No sound was recorded" | The app has no microphone permission (Java then records only silence). **macOS:** System Settings > Privacy & Security > Microphone, switch on the app you start the chat from (NetBeans, Terminal or Java), then quit and reopen it. **Windows:** Settings > Privacy > Microphone, allow desktop apps. While recording, the bars next to the timer must move when you speak. Also check the right input device in the system sound settings |
| Voice note plays but is very quiet | Raise the system output volume; quiet microphones are boosted automatically (up to 8x) |
| "Encrypted connection failed" | The server uses TLS but the client has the wrong or no `chatapp-server.crt`, or the server does not use TLS but a `.crt` file is next to the client. Remove or replace the file |
| Old messages show `[message could not be decrypted]` | `chatapp.key` was lost or replaced. Put the original key file back next to the server |
| Emoji look black and white | The operating system's emoji font decides: macOS shows colour, Windows shows outline emoji in Java |
| Strange characters in source | Make sure NetBeans uses UTF-8 (Project Properties > Sources > Encoding) |
| XAMPP's MariaDB handshake error | Use `org.mariadb.jdbc:mariadb-java-client:3.3.3` and `jdbc:mariadb://` instead (edit `pom.xml` and `DBConnection`) |

---

## 10. Limits and ideas for extension
Files up to 10 MB, voice notes up to 2 minutes, messages up to 2000 characters. One login per account at a time.
Ideas: TLS encryption, typing indicator, message delete, read receipts, an administrator web dashboard with Servlets/JSP.

## 11. Suggested report outline
1. Introduction and objectives  2. Requirements  3. Design: architecture, protocol, six-table ER diagram
4. Implementation: one section per course topic (section 7 table)  5. Security (section 5)  6. Testing (section 8 table with screenshots)
7. Limitations and future work  8. Conclusion
