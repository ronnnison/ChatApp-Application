package chatapp.common;

/** A user as seen by clients (never contains the password hash). */
public class UserDTO extends Entity implements Displayable {
    private static final long serialVersionUID = 1L;

    private final String username;
    private final String fullName;
    private boolean online;

    public UserDTO(long id, String username, String fullName) {
        super(id);
        this.username = username;
        this.fullName = fullName;
    }

    public String getUsername() { return username; }
    public String getFullName() { return fullName; }
    public boolean isOnline() { return online; }
    public void setOnline(boolean online) { this.online = online; }

    /** POLYMORPHISM: each Displayable decides how it is shown in a list. */
    @Override
    public String getDisplayText() {
        return (online ? "● " : "○ ") + fullName + "   (@" + username + ")";
    }

    @Override
    public String toString() {
        return getDisplayText();
    }
}
