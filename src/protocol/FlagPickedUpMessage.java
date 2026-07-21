package protocol;

public record FlagPickedUpMessage(
        String protocolVersion,
        String gameId,
        long tick,
        String playerId
) implements ProtocolMessage {
    @Override
    public MessageType type() {
        return MessageType.FLAG_PICKED_UP;
    }
}
