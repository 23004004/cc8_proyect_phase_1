package protocol;

public record PlayerDisconnectedMessage(
        String protocolVersion,
        String gameId,
        String playerId
) implements ProtocolMessage {
    @Override
    public MessageType type() {
        return MessageType.PLAYER_DISCONNECTED;
    }
}
