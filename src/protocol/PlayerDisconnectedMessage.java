package protocol;

public record PlayerDisconnectedMessage(int playerId) implements ProtocolMessage {
    @Override
    public MessageType type() {
        return MessageType.PLAYER_DISCONNECTED;
    }
}
