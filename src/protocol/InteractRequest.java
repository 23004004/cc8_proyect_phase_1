package protocol;

public record InteractRequest(int playerId) implements ProtocolMessage {
    @Override
    public MessageType type() {
        return MessageType.INTERACT;
    }
}
