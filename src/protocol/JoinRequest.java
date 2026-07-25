package protocol;

public record JoinRequest(String name) implements ProtocolMessage {
    @Override
    public MessageType type() {
        return MessageType.JOIN;
    }
}
