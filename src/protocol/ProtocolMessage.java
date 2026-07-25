package protocol;

public interface ProtocolMessage {
    MessageType type();

    default int protocolVersion() {
        return ProtocolVersion.V3;
    }
}
