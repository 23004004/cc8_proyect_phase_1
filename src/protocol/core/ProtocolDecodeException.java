package protocol.core;

import protocol.enums.ErrorCode;

public final class ProtocolDecodeException extends RuntimeException {
    private final MessageType messageType;
    private final ErrorCode errorCode;

    public ProtocolDecodeException(MessageType messageType, ErrorCode errorCode, String message) {
        super(message);
        this.messageType = messageType;
        this.errorCode = errorCode;
    }

    public MessageType messageType() {
        return messageType;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
