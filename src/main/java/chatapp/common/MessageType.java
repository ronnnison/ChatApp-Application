package chatapp.common;

/**
 * Every kind of packet that travels between client and server.
 * (Enum used as the "protocol vocabulary".)
 */
public enum MessageType {
    // authentication
    REGISTER, REGISTER_OK, REGISTER_FAIL,
    LOGIN, AUTH_OK, AUTH_FAIL, LOGOUT,
    // conversations
    GET_CONVERSATIONS, CONVERSATIONS,
    SEARCH_USERS, USERS,
    OPEN_PRIVATE, CREATE_GROUP, ADD_MEMBER, CONVERSATION_OPENED,
    // messaging
    GET_HISTORY, HISTORY,
    SEND_TEXT, SEND_FILE, NEW_MESSAGE,
    DOWNLOAD_FILE, FILE_DATA,
    // profile pictures
    GET_AVATAR, AVATAR, UPDATE_AVATAR,
    // generic
    INFO, ERROR,
    /** Created locally by the client when the socket closes. Never sent over the wire. */
    DISCONNECTED
}
