package protocol;

public record JoinRejectedMessage(
        String protocolVersion,
        JoinRejectedReason reason
) implements ProtocolMessage {
    @Override
    public MessageType type() {
        return MessageType.JOIN_REJECTED;
    }
}
