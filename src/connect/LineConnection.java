package connect;

import protocol.ProtocolCodec;
import protocol.ProtocolMessage;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.util.Objects;

public final class LineConnection implements Closeable {
    private final Socket socket;
    private final DataInputStream reader;
    private final DataOutputStream writer;
    private final ProtocolCodec codec;

    public LineConnection(Socket socket) throws IOException {
        this.socket = Objects.requireNonNull(socket, "socket must not be null");
        this.reader = new DataInputStream(socket.getInputStream());
        this.writer = new DataOutputStream(socket.getOutputStream());
        this.codec = new ProtocolCodec();
    }

    public ProtocolMessage readMessage() throws IOException {
        int length;
        try {
            length = reader.readUnsignedShort();
        } catch (EOFException ex) {
            return null;
        }
        byte[] payload = reader.readNBytes(length);
        if (payload.length != length) {
            throw new EOFException("Mensaje TCP incompleto.");
        }
        return codec.deserialize(payload);
    }

    public synchronized void sendMessage(ProtocolMessage message) throws IOException {
        byte[] payload = codec.serialize(message);
        if (payload.length > 65535) {
            throw new IOException("Mensaje demasiado grande.");
        }
        writer.writeShort(payload.length);
        writer.write(payload);
        writer.flush();
    }

    public boolean isOpen() {
        return !socket.isClosed();
    }

    @Override
    public synchronized void close() throws IOException {
        socket.close();
    }
}
