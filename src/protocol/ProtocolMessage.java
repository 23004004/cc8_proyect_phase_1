package protocol;

public interface ProtocolMessage {
    MessageType type();

    String protocolVersion();
}
