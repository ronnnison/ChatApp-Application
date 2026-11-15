package chatapp.db;

import chatapp.common.Payloads;
import chatapp.common.UserDTO;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Accounts, authentication and login audit. Every query uses PreparedStatement (no SQL injection). */
public class UserDao implements Dao<UserDTO> {

    /** @return null on success, otherwise a message that is safe to show to the user. */
    public String register(Payloads.Credentials c, byte[] avatarPng) throws SQLException {
        String salt = PasswordUtil.newSalt();
        String hash = PasswordUtil.hash(c.password, salt);
        String sql = "INSERT INTO users (username, full_name, email, password_hash, salt, avatar) VALUES (?,?,?,?,?,?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, c.username);
            ps.setString(2, c.fullName.trim());
            ps.setString(3, c.email.trim());
            ps.setString(4, hash);
            ps.setString(5, salt);
            if (avatarPng == null) ps.setNull(6, Types.BLOB); else ps.setBytes(6, avatarPng);
            ps.executeUpdate();
            return null;
        } catch (SQLIntegrityConstraintViolationException e) {
            return e.getMessage().contains("uq_users_email")
                    ? "That email is already registered." : "That username is already taken.";
        }
    }

    /** @return the user when username+password match, otherwise null. */
    public UserDTO authenticate(String username, String password) throws SQLException {
        String sql = "SELECT id, username, full_name, password_hash, salt FROM users WHERE username = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                if (!PasswordUtil.verify(password, rs.getString("salt"), rs.getString("password_hash"))) return null;
                UserDTO user = new UserDTO(rs.getLong("id"), rs.getString("username"), rs.getString("full_name"));
                try (PreparedStatement up = con.prepareStatement("UPDATE users SET last_login = NOW() WHERE id = ?")) {
                    up.setLong(1, user.getId());
                    up.executeUpdate();
                }
                return user;
            }
        }
    }

    @Override
    public Optional<UserDTO> findById(long id) throws SQLException {
        return findOne("SELECT id, username, full_name FROM users WHERE id = ?", String.valueOf(id), true);
    }

    public Optional<UserDTO> findByUsername(String username) throws SQLException {
        return findOne("SELECT id, username, full_name FROM users WHERE username = ?", username, false);
    }

    private Optional<UserDTO> findOne(String sql, String value, boolean numeric) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            if (numeric) ps.setLong(1, Long.parseLong(value)); else ps.setString(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next()
                        ? Optional.of(new UserDTO(rs.getLong(1), rs.getString(2), rs.getString(3)))
                        : Optional.empty();
            }
        }
    }

    /** @return the user's profile picture (PNG bytes) or null when they have none. */
    public byte[] getAvatar(long userId) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT avatar FROM users WHERE id = ?")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getBytes(1) : null;
            }
        }
    }

    public void setAvatar(long userId, byte[] png) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("UPDATE users SET avatar = ? WHERE id = ?")) {
            ps.setBytes(1, png);
            ps.setLong(2, userId);
            ps.executeUpdate();
        }
    }

    /** Search by username or full name, excluding the person searching. */
    public List<UserDTO> search(String query, long excludeUserId) throws SQLException {
        String q = "%" + query.trim().replaceAll("[%_\\\\]", "") + "%";
        String sql = "SELECT id, username, full_name FROM users "
                + "WHERE id <> ? AND (username LIKE ? OR full_name LIKE ?) ORDER BY full_name LIMIT 30";
        List<UserDTO> result = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, excludeUserId);
            ps.setString(2, q);
            ps.setString(3, q);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new UserDTO(rs.getLong(1), rs.getString(2), rs.getString(3)));
                }
            }
        }
        return result;
    }

    public void logAttempt(Long userId, String usernameAttempted, boolean success, String ip) throws SQLException {
        String sql = "INSERT INTO login_logs (user_id, username_attempted, success, ip_address) VALUES (?,?,?,?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            if (userId == null) ps.setNull(1, Types.BIGINT); else ps.setLong(1, userId);
            ps.setString(2, usernameAttempted.length() > 30 ? usernameAttempted.substring(0, 30) : usernameAttempted);
            ps.setBoolean(3, success);
            ps.setString(4, ip);
            ps.executeUpdate();
        }
    }

    /** Failed logins for this username in the last 5 minutes (brute-force protection). */
    public int recentFailures(String username) throws SQLException {
        String sql = "SELECT COUNT(*) FROM login_logs WHERE username_attempted = ? AND success = 0 "
                + "AND attempted_at > (NOW() - INTERVAL 5 MINUTE)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }
}
