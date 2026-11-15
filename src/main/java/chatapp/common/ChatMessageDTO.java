package chatapp.common;

/** One message (text or file) inside a conversation. File bytes are NOT included. */
public class ChatMessageDTO extends Entity {
    private static final long serialVersionUID = 1L;

    public enum Kind { TEXT, IMAGE, DOCUMENT, VOICE }

    private final long conversationId;
    private final long senderId;
    private final String senderName;
    private final Kind kind;
    private final String content;
    private final long fileId;
    private final String fileName;
    private final long fileSize;
    private final long sentAt; // epoch milliseconds

    public ChatMessageDTO(long id, long conversationId, long senderId, String senderName, Kind kind,
                          String content, long fileId, String fileName, long fileSize, long sentAt) {
        super(id);
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.senderName = senderName;
        this.kind = kind;
        this.content = content;
        this.fileId = fileId;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.sentAt = sentAt;
    }

    public long getConversationId() { return conversationId; }
    public long getSenderId() { return senderId; }
    public String getSenderName() { return senderName; }
    public Kind getKind() { return kind; }
    public String getContent() { return content; }
    public long getFileId() { return fileId; }
    public String getFileName() { return fileName; }
    public long getFileSize() { return fileSize; }
    public long getSentAt() { return sentAt; }
    public boolean isFile() { return kind != Kind.TEXT; }
}
