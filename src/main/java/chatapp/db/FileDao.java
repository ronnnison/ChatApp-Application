package chatapp.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

/** Metadata of uploaded files. The bytes themselves are stored on disk by the server. */
public class FileDao {

    public static class FileRecord {
        public final String originalName;
        public final String storedName;

        FileRecord(String originalName, String storedName) {
            this.originalName = originalName;
            this.storedName = storedName;
        }
    }

    public long save(long uploaderId, String originalName, String storedName, String mime, long size)
            throws SQLException {
        String sql = "INSERT INTO files (uploader_id, original_name, stored_name, mime_type, size_bytes) VALUES (?,?,?,?,?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, uploaderId);
            ps.setString(2, originalName);
            ps.setString(3, storedName);
            ps.setString(4, mime);
            ps.setLong(5, size);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    /** Access control: a file can only be downloaded by members of the conversation it was sent in. */
    public Optional<FileRecord> findForUser(long fileId, long userId) throws SQLException {
        String sql = "SELECT f.original_name, f.stored_name FROM files f "
                + "JOIN messages m ON m.file_id = f.id "
                + "JOIN conversation_members cm ON cm.conversation_id = m.conversation_id "
                + "WHERE f.id = ? AND cm.user_id = ? LIMIT 1";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, fileId);
            ps.setLong(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next()
                        ? Optional.of(new FileRecord(rs.getString(1), rs.getString(2)))
                        : Optional.empty();
            }
        }
    }
}
