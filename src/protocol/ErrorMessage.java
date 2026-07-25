package protocol;

public record ErrorMessage(ErrorCode code, String description) implements ProtocolMessage {
    @Override
    public MessageType type() {
        return MessageType.ERROR;
    }
}
