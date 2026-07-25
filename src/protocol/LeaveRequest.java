package protocol;

public record LeaveRequest(int playerId) implements ProtocolMessage {
    @Override
    public MessageType type() {
        return MessageType.LEAVE;
    }
}
