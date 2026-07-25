package protocol;

public record FlagPickedUpMessage(long tick, int playerId) implements ProtocolMessage {
    @Override
    public MessageType type() {
        return MessageType.FLAG_PICKED_UP;
    }
}
