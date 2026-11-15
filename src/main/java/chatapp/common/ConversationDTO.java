package chatapp.common;

/** A private chat or a group, as seen by one particular user. */
public class ConversationDTO extends Entity implements Displayable {
    private static final long serialVersionUID = 2L;

    public enum Type { PRIVATE, GROUP }

    private final String name;
    private final Type type;
    private final int memberCount;
    private final long peerUserId;     // the other person in a private chat (0 for groups)
    private final String lastPreview;  // "Hello!", "Photo", "Voice message" ...
    private final long lastTime;       // epoch millis of the last message, 0 if none

    public ConversationDTO(long id, String name, Type type, int memberCount,
                           long peerUserId, String lastPreview, long lastTime) {
        super(id);
        this.name = name;
        this.type = type;
        this.memberCount = memberCount;
        this.peerUserId = peerUserId;
        this.lastPreview = lastPreview == null ? "" : lastPreview;
        this.lastTime = lastTime;
    }

    public String getName() { return name; }
    public Type getType() { return type; }
    public int getMemberCount() { return memberCount; }
    public long getPeerUserId() { return peerUserId; }
    public String getLastPreview() { return lastPreview; }
    public long getLastTime() { return lastTime; }
    public boolean isGroup() { return type == Type.GROUP; }

    @Override
    public String getDisplayText() {
        return name == null ? "Chat" : name;
    }

    @Override
    public String toString() {
        return getDisplayText();
    }
}
