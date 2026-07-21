package protocol;

public record JoinRequest(
        String protocolVersion,
        String name
) implements ProtocolMessage {
    @Override
    public MessageType type() {
        return MessageType.JOIN;
    }
}
