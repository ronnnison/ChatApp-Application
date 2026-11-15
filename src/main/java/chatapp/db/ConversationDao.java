package chatapp.db;

import chatapp.common.ConversationDTO;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Private chats, groups and membership. */
public class ConversationDao implements Dao<ConversationDTO> {

    @Override
    public Optional<ConversationDTO> findById(long id) throws SQLException {
        String sql = "SELECT id, type, name, (SELECT COUNT(*) FROM conversation_members x "
                + "WHERE x.conversation_id = conversations.id) FROM conversations WHERE id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new ConversationDTO(rs.getLong(1), rs.getString(3),
                        ConversationDTO.Type.valueOf(rs.getString(2)), rs.getInt(4), 0, "", 0));
            }
        }
    }

    /** Returns the existing 1-to-1 chat between two users, or creates it. */
    public long getOrCreatePrivate(long userA, long userB) throws SQLException {
        String find = "SELECT c.id FROM conversations c "
                + "JOIN conversation_members m1 ON m1.conversation_id = c.id AND m1.user_id = ? "
                + "JOIN conversation_members m2 ON m2.conversation_id = c.id AND m2.user_id = ? "
                + "WHERE c.type = 'PRIVATE' LIMIT 1";
        try (Connection con = DBConnection.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement(find)) {
                ps.setLong(1, userA);
                ps.setLong(2, userB);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getLong(1);
                }
            }
            con.setAutoCommit(false);
            try {
                long id = insertConversation(con, "PRIVATE", null, userA);
                addMember(con, id, userA, "MEMBER");
                addMember(con, id, userB, "MEMBER");
                con.commit();
                return id;
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        }
    }

    /** Creates a group in one transaction: the creator becomes ADMIN. */
    public long createGroup(String name, long creatorId, List<Long> memberIds) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                long id = insertConversation(con, "GROUP", name, creatorId);
                addMember(con, id, creatorId, "ADMIN");
                for (long uid : memberIds) addMember(con, id, uid, "MEMBER");
                con.commit();
                return id;
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        }
    }

    private long insertConversation(Connection con, String type, String name, long creator) throws SQLException {
        String sql = "INSERT INTO conversations (type, name, created_by) VALUES (?,?,?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, type);
            ps.setString(2, name);
            ps.setLong(3, creator);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private void addMember(Connection con, long convId, long userId, String role) throws SQLException {
        String sql = "INSERT IGNORE INTO conversation_members (conversation_id, user_id, role) VALUES (?,?,?)";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, convId);
            ps.setLong(2, userId);
            ps.setString(3, role);
            ps.executeUpdate();
        }
    }

    public void addMember(long convId, long userId) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            addMember(con, convId, userId, "MEMBER");
        }
    }

    public boolean isMember(long convId, long userId) throws SQLException {
        return exists("SELECT 1 FROM conversation_members WHERE conversation_id = ? AND user_id = ?", convId, userId);
    }

    public boolean isAdmin(long convId, long userId) throws SQLException {
        return exists("SELECT 1 FROM conversation_members WHERE conversation_id = ? AND user_id = ? AND role = 'ADMIN'",
                convId, userId);
    }

    private boolean exists(String sql, long a, long b) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, a);
            ps.setLong(2, b);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public List<Long> memberIds(long convId) throws SQLException {
        List<Long> ids = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT user_id FROM conversation_members WHERE conversation_id = ?")) {
            ps.setLong(1, convId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getLong(1));
            }
        }
        return ids;
    }

    /** Short text shown under a chat name in the sidebar. */
    private static String preview(String type, String content) {
        if (type == null) return "";
        switch (type) {
            case "IMAGE": return "Photo";
            case "VOICE": return "Voice message";
            case "DOCUMENT": return "Document";
            default:
                String t = content == null ? "" : content.replace('\n', ' ').trim();
                return t.length() > 60 ? t.substring(0, 60) : t;
        }
    }

    /** All conversations of one user, most recently active first. Private chats are named after the other person. */
    public List<ConversationDTO> listForUser(long userId) throws SQLException {
        String sql = "SELECT c.id, c.type, c.name, "
                + "(SELECT COUNT(*) FROM conversation_members x WHERE x.conversation_id = c.id) AS cnt, "
                + "(SELECT u.full_name FROM conversation_members m2 JOIN users u ON u.id = m2.user_id "
                + "  WHERE m2.conversation_id = c.id AND m2.user_id <> ? LIMIT 1) AS other_name, "
                + "(SELECT m3.user_id FROM conversation_members m3 "
                + "  WHERE m3.conversation_id = c.id AND m3.user_id <> ? LIMIT 1) AS other_id, "
                + "(SELECT msg_type FROM messages WHERE conversation_id = c.id ORDER BY id DESC LIMIT 1) AS last_type, "
                + "(SELECT content FROM messages WHERE conversation_id = c.id ORDER BY id DESC LIMIT 1) AS last_content, "
                + "(SELECT UNIX_TIMESTAMP(MAX(sent_at)) * 1000 FROM messages WHERE conversation_id = c.id) AS last_ms, "
                + "COALESCE((SELECT MAX(sent_at) FROM messages WHERE conversation_id = c.id), c.created_at) AS last_activity "
                + "FROM conversations c JOIN conversation_members m ON m.conversation_id = c.id "
                + "WHERE m.user_id = ? ORDER BY last_activity DESC, c.id DESC";
        List<ConversationDTO> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, userId);
            ps.setLong(3, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ConversationDTO.Type type = ConversationDTO.Type.valueOf(rs.getString("type"));
                    boolean priv = type == ConversationDTO.Type.PRIVATE;
                    String name = priv ? rs.getString("other_name") : rs.getString("name");
                    long peer = priv ? rs.getLong("other_id") : 0;
                    list.add(new ConversationDTO(rs.getLong("id"), name, type, rs.getInt("cnt"), peer,
                            preview(rs.getString("last_type"),
                                    DataCipher.get().decryptText(rs.getString("last_content"), rs.getLong("id"))),
                            rs.getLong("last_ms")));
                }
            }
        }
        return list;
    }
}
