package protocol;

public record FlagStolenMessage(
        String protocolVersion,
        String gameId,
        long tick,
        String previousCarrierId,
        String newCarrierId,
        int protectionTimeMs
) implements ProtocolMessage {
    @Override
    public MessageType type() {
        return MessageType.FLAG_STOLEN;
    }
}
