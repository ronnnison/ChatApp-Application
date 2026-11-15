package chatapp.db;

import chatapp.common.ChatMessageDTO;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class MessageDao implements Dao<ChatMessageDTO> {

    // UNIX_TIMESTAMP(...) keeps the time exact regardless of JDBC time-zone settings.
    private static final String SELECT =
            "SELECT m.id, m.conversation_id, m.sender_id, u.full_name, m.msg_type, m.content, m.file_id, "
            + "f.original_name, f.size_bytes, UNIX_TIMESTAMP(m.sent_at) * 1000 AS sent_ms "
            + "FROM messages m JOIN users u ON u.id = m.sender_id LEFT JOIN files f ON f.id = m.file_id ";

    public long insert(long conversationId, long senderId, ChatMessageDTO.Kind kind, String content, long fileId)
            throws SQLException {
        String sql = "INSERT INTO messages (conversation_id, sender_id, msg_type, content, file_id) VALUES (?,?,?,?,?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, conversationId);
            ps.setLong(2, senderId);
            ps.setString(3, kind.name());
            // message text is stored encrypted (AES-256-GCM), see DataCipher
            if (content == null) ps.setNull(4, Types.VARCHAR);
            else ps.setString(4, DataCipher.get().encryptText(content, conversationId));
            if (fileId > 0) ps.setLong(5, fileId); else ps.setNull(5, Types.BIGINT);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    @Override
    public Optional<ChatMessageDTO> findById(long id) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(SELECT + "WHERE m.id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    /** The newest {@code limit} messages of a conversation, oldest first. */
    public List<ChatMessageDTO> history(long conversationId, int limit) throws SQLException {
        List<ChatMessageDTO> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(SELECT + "WHERE m.conversation_id = ? ORDER BY m.id DESC LIMIT ?")) {
            ps.setLong(1, conversationId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        }
        Collections.reverse(list);
        return list;
    }

    private ChatMessageDTO map(ResultSet rs) throws SQLException {
        return new ChatMessageDTO(
                rs.getLong("id"), rs.getLong("conversation_id"), rs.getLong("sender_id"),
                rs.getString("full_name"), ChatMessageDTO.Kind.valueOf(rs.getString("msg_type")),
                DataCipher.get().decryptText(rs.getString("content"), rs.getLong("conversation_id")),
                rs.getLong("file_id"), rs.getString("original_name"),
                rs.getLong("size_bytes"), rs.getLong("sent_ms"));
    }
}
