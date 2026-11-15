package chatapp.common;

import java.io.Serializable;

/** The one object type sent over the socket: a type tag plus an optional payload. */
public class Packet implements Serializable {
    private static final long serialVersionUID = 1L;

    private final MessageType type;
    private final Serializable payload;

    public Packet(MessageType type) {
        this(type, null);
    }

    public Packet(MessageType type, Serializable payload) {
        this.type = type;
        this.payload = payload;
    }

    public MessageType getType() {
        return type;
    }

    /** GENERIC METHOD: the caller decides the type, e.g. {@code UserDTO u = packet.getPayload();} */
    @SuppressWarnings("unchecked")
    public <T> T getPayload() {
        return (T) payload;
    }
}
