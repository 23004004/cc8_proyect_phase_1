package protocol;

public record LeaveRequest(
        String protocolVersion,
        String gameId,
        String playerId
) implements ProtocolMessage {
    @Override
    public MessageType type() {
        return MessageType.LEAVE;
    }
}
