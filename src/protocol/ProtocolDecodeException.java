package protocol;

public final class ProtocolDecodeException extends RuntimeException {
    private final MessageType messageType;
    private final ErrorCode errorCode;

    public ProtocolDecodeException(MessageType messageType, ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.messageType = messageType;
        this.errorCode = errorCode;
    }

    public ProtocolDecodeException(MessageType messageType, ErrorCode errorCode, String message) {
        this(messageType, errorCode, message, null);
    }

    public MessageType messageType() {
        return messageType;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
