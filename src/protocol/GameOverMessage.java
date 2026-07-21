package protocol;

public record GameOverMessage(
        String protocolVersion,
        String gameId,
        String winnerId,
        String winnerName,
        GameOverReason reason
) implements ProtocolMessage {
    @Override
    public MessageType type() {
        return MessageType.GAME_OVER;
    }
}
