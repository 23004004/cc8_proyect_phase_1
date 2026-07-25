package protocol;

public record FlagStolenMessage(long tick, int previousCarrierId, int newCarrierId) implements ProtocolMessage {
    @Override
    public MessageType type() {
        return MessageType.FLAG_STOLEN;
    }
}
