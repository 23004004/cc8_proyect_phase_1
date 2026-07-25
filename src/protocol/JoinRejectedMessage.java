package protocol;

public record JoinRejectedMessage(JoinRejectedReason reason) implements ProtocolMessage {
    @Override
    public MessageType type() {
        return MessageType.JOIN_REJECTED;
    }
}
