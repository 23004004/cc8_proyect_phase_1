package protocol.core;

public interface ProtocolMessage {
    MessageType type();

    default int protocolVersion() {
        return ProtocolVersion.V3;
    }
}
