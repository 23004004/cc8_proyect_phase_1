package protocol;

public record JoinAcceptedMessage(int playerId, int gameId) implements ProtocolMessage {
    @Override
    public MessageType type() {
        return MessageType.JOIN_ACCEPTED;
    }
}
