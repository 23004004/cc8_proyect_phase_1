package connect;

import protocol.ProtocolCodec;
import protocol.ProtocolMessage;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class LineConnection implements Closeable {
    private final Socket socket;
    private final BufferedReader reader;
    private final BufferedWriter writer;
    private final ProtocolCodec codec;

    public LineConnection(Socket socket) throws IOException {
        this.socket = Objects.requireNonNull(socket, "socket must not be null");
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        this.codec = new ProtocolCodec();
    }

    public ProtocolMessage readMessage() throws IOException {
        String line = reader.readLine();
        if (line == null) {
            return null;
        }
        return codec.deserialize(line);
    }

    public void sendMessage(ProtocolMessage message) throws IOException {
        writer.write(codec.serialize(message));
        writer.write('\n');
        writer.flush();
    }

    public boolean isOpen() {
        return !socket.isClosed();
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
