package chatapp.common;

import java.io.Serializable;
import java.util.ArrayList;

/** Request/response bodies carried inside a {@link Packet}. All are immutable and Serializable. */
public final class Payloads {
    private Payloads() { }

    public static class Credentials implements Serializable {
        private static final long serialVersionUID = 1L;
        public final String username, password, fullName, email;
        public final byte[] avatar; // optional profile picture (PNG/JPG bytes), may be null

        /** Login form. */
        public Credentials(String username, String password) {
            this(username, password, null, null, null);
        }

        /** Registration form without a picture. */
        public Credentials(String username, String password, String fullName, String email) {
            this(username, password, fullName, email, null);
        }

        /** Registration form. */
        public Credentials(String username, String password, String fullName, String email, byte[] avatar) {
            this.username = username;
            this.password = password;
            this.fullName = fullName;
            this.email = email;
            this.avatar = avatar;
        }
    }

    public static class TextRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        public final long conversationId;
        public final String text;

        public TextRequest(long conversationId, String text) {
            this.conversationId = conversationId;
            this.text = text;
        }
    }

    public static class FileUpload implements Serializable {
        private static final long serialVersionUID = 1L;
        public final long conversationId;
        public final String fileName;
        public final byte[] data;

        public FileUpload(long conversationId, String fileName, byte[] data) {
            this.conversationId = conversationId;
            this.fileName = fileName;
            this.data = data;
        }
    }

    public static class FileData implements Serializable {
        private static final long serialVersionUID = 1L;
        public final long fileId;
        public final String fileName;
        public final byte[] data;

        public FileData(long fileId, String fileName, byte[] data) {
            this.fileId = fileId;
            this.fileName = fileName;
            this.data = data;
        }
    }

    public static class GroupRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        public final String name;
        public final ArrayList<String> members;

        public GroupRequest(String name, ArrayList<String> members) {
            this.name = name;
            this.members = members;
        }
    }

    public static class AddMemberRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        public final long conversationId;
        public final String username;

        public AddMemberRequest(long conversationId, String username) {
            this.conversationId = conversationId;
            this.username = username;
        }
    }

    /** A user's profile picture. {@code png} is null when the user has no picture. */
    public static class AvatarData implements Serializable {
        private static final long serialVersionUID = 1L;
        public final long userId;
        public final byte[] png;

        public AvatarData(long userId, byte[] png) {
            this.userId = userId;
            this.png = png;
        }
    }

    public static class History implements Serializable {
        private static final long serialVersionUID = 1L;
        public final long conversationId;
        public final ArrayList<ChatMessageDTO> messages;

        public History(long conversationId, ArrayList<ChatMessageDTO> messages) {
            this.conversationId = conversationId;
            this.messages = messages;
        }
    }
}
