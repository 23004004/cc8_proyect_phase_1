package protocol;

public record ErrorMessage(
        String protocolVersion,
        ErrorCode code,
        String description
) implements ProtocolMessage {
    @Override
    public MessageType type() {
        return MessageType.ERROR;
    }
}
