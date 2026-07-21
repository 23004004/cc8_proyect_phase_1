package protocol;

public record JoinAcceptedMessage(
        String protocolVersion,
        String playerId,
        String gameId
) implements ProtocolMessage {
    @Override
    public MessageType type() {
        return MessageType.JOIN_ACCEPTED;
    }
}
