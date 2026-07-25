package protocol;

public record GameOverMessage(int winnerId, String winnerName, GameOverReason reason) implements ProtocolMessage {
    @Override
    public MessageType type() {
        return MessageType.GAME_OVER;
    }
}
