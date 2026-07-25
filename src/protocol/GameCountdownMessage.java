package protocol;

public record GameCountdownMessage(int secondsRemaining) implements ProtocolMessage {
    @Override
    public MessageType type() {
        return MessageType.GAME_COUNTDOWN;
    }
}
