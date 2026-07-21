package protocol;

public enum MessageType {
    JOIN,
    CHANGE_DIRECTION,
    LEAVE,
    JOIN_ACCEPTED,
    JOIN_REJECTED,
    GAME_STARTED,
    GAME_STATE,
    FLAG_PICKED_UP,
    FLAG_STOLEN,
    PLAYER_DISCONNECTED,
    GAME_OVER,
    ERROR
}
