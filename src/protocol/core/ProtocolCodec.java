package protocol.core;

import model.Direction;
import model.FlagStatus;
import model.GameStatus;
import protocol.dto.FlagDto;
import protocol.dto.PlayerDto;
import protocol.enums.ErrorCode;
import protocol.enums.GameOverReason;
import protocol.enums.JoinRejectedReason;
import protocol.messages.ChangeDirectionRequest;
import protocol.messages.ErrorMessage;
import protocol.messages.FlagPickedUpMessage;
import protocol.messages.FlagStolenMessage;
import protocol.messages.GameCountdownMessage;
import protocol.messages.GameOverMessage;
import protocol.messages.GameStartedMessage;
import protocol.messages.GameStateMessage;
import protocol.messages.InteractRequest;
import protocol.messages.JoinAcceptedMessage;
import protocol.messages.JoinRejectedMessage;
import protocol.messages.JoinRequest;
import protocol.messages.LeaveRequest;
import protocol.messages.LobbyStateMessage;
import protocol.messages.PlayerDisconnectedMessage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class ProtocolCodec {
    public byte[] serialize(ProtocolMessage message) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            writeHeader(out, message.type());
            switch (message) {
                case JoinRequest join -> writeString(out, join.name());
                case ChangeDirectionRequest input -> {
                    out.writeShort(input.playerId());
                    out.writeByte(input.direction().code());
                }
                case InteractRequest interact -> out.writeShort(interact.playerId());
                case LeaveRequest leave -> out.writeShort(leave.playerId());
                case JoinAcceptedMessage accepted -> {
                    out.writeShort(accepted.playerId());
                    out.writeShort(accepted.gameId());
                }
                case JoinRejectedMessage rejected -> out.writeByte(rejected.reason().code());
                case LobbyStateMessage lobby -> writeLobbyState(out, lobby);
                case GameCountdownMessage countdown -> out.writeByte(countdown.secondsRemaining());
                case GameStartedMessage started -> writeGameStarted(out, started);
                case GameStateMessage state -> writeGameState(out, state);
                case FlagPickedUpMessage pickedUp -> {
                    out.writeInt((int) pickedUp.tick());
                    out.writeShort(pickedUp.playerId());
                }
                case FlagStolenMessage stolen -> {
                    out.writeInt((int) stolen.tick());
                    out.writeShort(stolen.previousCarrierId());
                    out.writeShort(stolen.newCarrierId());
                }
                case PlayerDisconnectedMessage disconnected -> out.writeShort(disconnected.playerId());
                case GameOverMessage gameOver -> {
                    out.writeShort(gameOver.winnerId());
                    writeString(out, gameOver.winnerName());
                    out.writeByte(gameOver.reason().code());
                }
                case ErrorMessage error -> {
                    out.writeByte(error.code().code());
                    writeString(out, error.description() == null ? "" : error.description());
                }
                default -> throw new ProtocolDecodeException(message.type(), ErrorCode.INVALID_MESSAGE, "Mensaje no soportado.");
            }
            out.flush();
            return bytes.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo serializar el mensaje.", ex);
        }
    }

    public ProtocolMessage deserialize(byte[] payload) {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
            MessageType type = MessageType.fromCode(in.readUnsignedByte());
            int version = in.readUnsignedByte();
            if (version != ProtocolVersion.V3) {
                throw new ProtocolDecodeException(type, ErrorCode.UNSUPPORTED_PROTOCOL_VERSION, "Versión de protocolo no soportada: " + version);
            }
            ProtocolMessage message = switch (type) {
                case JOIN -> new JoinRequest(readString(in));
                case INPUT -> new ChangeDirectionRequest(in.readUnsignedShort(), Direction.fromCode(in.readUnsignedByte()));
                case INTERACT -> new InteractRequest(in.readUnsignedShort());
                case LEAVE -> new LeaveRequest(in.readUnsignedShort());
                case JOIN_ACCEPTED -> new JoinAcceptedMessage(in.readUnsignedShort(), in.readUnsignedShort());
                case JOIN_REJECTED -> new JoinRejectedMessage(JoinRejectedReason.fromCode(in.readUnsignedByte()));
                case LOBBY_STATE -> readLobbyState(in);
                case GAME_COUNTDOWN -> new GameCountdownMessage(in.readUnsignedByte());
                case GAME_STARTED -> readGameStarted(in);
                case GAME_STATE -> readGameState(in);
                case FLAG_PICKED_UP -> new FlagPickedUpMessage(Integer.toUnsignedLong(in.readInt()), in.readUnsignedShort());
                case FLAG_STOLEN -> new FlagStolenMessage(Integer.toUnsignedLong(in.readInt()), in.readUnsignedShort(), in.readUnsignedShort());
                case PLAYER_DISCONNECTED -> new PlayerDisconnectedMessage(in.readUnsignedShort());
                case GAME_OVER -> {
                    int winnerId = in.readUnsignedShort();
                    String winnerName = readString(in);
                    int reason = in.readUnsignedByte();
                    if (reason != GameOverReason.EXITED_CIRCLE_WITH_FLAG.code()) {
                        throw new ProtocolDecodeException(type, ErrorCode.INVALID_ENCODING, "Motivo GAME_OVER desconocido: " + reason);
                    }
                    yield new GameOverMessage(winnerId, winnerName, GameOverReason.EXITED_CIRCLE_WITH_FLAG);
                }
                case ERROR -> new ErrorMessage(ErrorCode.fromCode(in.readUnsignedByte()), readString(in));
                default -> throw new ProtocolDecodeException(type, ErrorCode.INVALID_MESSAGE, "Tipo no válido en TCP.");
            };
            if (in.available() != 0) {
                throw new ProtocolDecodeException(type, ErrorCode.INVALID_ENCODING, "El mensaje contiene bytes extra.");
            }
            return message;
        } catch (ProtocolDecodeException ex) {
            throw ex;
        } catch (IOException | IllegalArgumentException ex) {
            throw new ProtocolDecodeException(null, ErrorCode.INVALID_ENCODING, ex.getMessage());
        }
    }

    public byte[] serializeDiscoverRequest() {
        return new byte[]{(byte) MessageType.DISCOVER_REQUEST.code(), (byte) ProtocolVersion.V3};
    }

    public byte[] serializeDiscoverResponse(int gameId, String serverName, int tcpPort, GameStatus state, int playerCount, int maximumPlayers) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            writeHeader(out, MessageType.DISCOVER_RESPONSE);
            out.writeShort(gameId);
            writeString(out, serverName);
            out.writeShort(tcpPort);
            out.writeByte(state.code());
            out.writeShort(playerCount);
            out.writeShort(maximumPlayers);
            out.flush();
            return bytes.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public DiscoverResponse deserializeDiscoverResponse(byte[] payload, int length) {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload, 0, length));
            MessageType type = MessageType.fromCode(in.readUnsignedByte());
            int version = in.readUnsignedByte();
            if (type != MessageType.DISCOVER_RESPONSE || version != ProtocolVersion.V3) {
                throw new ProtocolDecodeException(type, ErrorCode.INVALID_ENCODING, "Respuesta de descubrimiento inválida.");
            }
            return new DiscoverResponse(
                    in.readUnsignedShort(),
                    readString(in),
                    in.readUnsignedShort(),
                    GameStatus.fromCode(in.readUnsignedByte()),
                    in.readUnsignedShort(),
                    in.readUnsignedShort()
            );
        } catch (IOException | IllegalArgumentException ex) {
            throw new ProtocolDecodeException(null, ErrorCode.INVALID_ENCODING, ex.getMessage());
        }
    }

    public boolean isDiscoverRequest(byte[] payload, int length) {
        return length == 2
                && Byte.toUnsignedInt(payload[0]) == MessageType.DISCOVER_REQUEST.code()
                && Byte.toUnsignedInt(payload[1]) == ProtocolVersion.V3;
    }

    private void writeLobbyState(DataOutputStream out, LobbyStateMessage message) throws IOException {
        out.writeByte(message.state().code());
        out.writeByte(message.players().size());
        for (PlayerDto player : message.players()) {
            out.writeShort(player.playerId());
            writeString(out, player.name());
        }
    }

    private LobbyStateMessage readLobbyState(DataInputStream in) throws IOException {
        GameStatus state = GameStatus.fromCode(in.readUnsignedByte());
        int count = in.readUnsignedByte();
        List<PlayerDto> players = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            players.add(new PlayerDto(in.readUnsignedShort(), readString(in), 0, 0, Direction.NONE, false));
        }
        return new LobbyStateMessage(state, players);
    }

    private void writeGameStarted(DataOutputStream out, GameStartedMessage message) throws IOException {
        out.writeInt(message.mapSize());
        out.writeInt(message.circleRadius());
        out.writeInt(message.playerRadius());
        out.writeInt(message.playerSpeed());
        out.writeInt(message.interactionRadius());
        out.writeShort(message.tickIntervalMs());
        writeFlag(out, message.flag());
        out.writeByte(message.players().size());
        for (PlayerDto player : message.players()) {
            writePlayer(out, player, true);
        }
    }

    private GameStartedMessage readGameStarted(DataInputStream in) throws IOException {
        int mapSize = in.readInt();
        int circleRadius = in.readInt();
        int playerRadius = in.readInt();
        int playerSpeed = in.readInt();
        int interactionRadius = in.readInt();
        int tickIntervalMs = in.readUnsignedShort();
        FlagDto flag = readFlag(in);
        int count = in.readUnsignedByte();
        List<PlayerDto> players = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            players.add(readPlayer(in, true));
        }
        return new GameStartedMessage(mapSize, circleRadius, playerRadius, playerSpeed, interactionRadius, tickIntervalMs, flag, players);
    }

    private void writeGameState(DataOutputStream out, GameStateMessage message) throws IOException {
        out.writeInt((int) message.tick());
        writeFlag(out, message.flag());
        out.writeByte(message.players().size());
        for (PlayerDto player : message.players()) {
            writePlayer(out, player, false);
        }
    }

    private GameStateMessage readGameState(DataInputStream in) throws IOException {
        long tick = Integer.toUnsignedLong(in.readInt());
        FlagDto flag = readFlag(in);
        int count = in.readUnsignedByte();
        List<PlayerDto> players = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            players.add(readPlayer(in, false));
        }
        return new GameStateMessage(tick, flag, players);
    }

    private void writeFlag(DataOutputStream out, FlagDto flag) throws IOException {
        out.writeByte(flag.status().code());
        out.writeShort(flag.carrierId());
        out.writeInt(flag.x());
        out.writeInt(flag.y());
    }

    private FlagDto readFlag(DataInputStream in) throws IOException {
        FlagStatus status = FlagStatus.fromCode(in.readUnsignedByte());
        int carrierId = in.readUnsignedShort();
        int x = in.readInt();
        int y = in.readInt();
        return new FlagDto(x, y, status, carrierId);
    }

    private void writePlayer(DataOutputStream out, PlayerDto player, boolean includeName) throws IOException {
        out.writeShort(player.playerId());
        if (includeName) {
            writeString(out, player.name());
        }
        out.writeInt(player.x());
        out.writeInt(player.y());
        out.writeByte(player.direction().code());
        out.writeByte(player.hasFlag() ? 1 : 0);
    }

    private PlayerDto readPlayer(DataInputStream in, boolean includeName) throws IOException {
        int playerId = in.readUnsignedShort();
        String name = includeName ? readString(in) : "";
        int x = in.readInt();
        int y = in.readInt();
        Direction direction = Direction.fromCode(in.readUnsignedByte());
        boolean hasFlag = in.readUnsignedByte() != 0;
        return new PlayerDto(playerId, name, x, y, direction, hasFlag);
    }

    private void writeHeader(DataOutputStream out, MessageType type) throws IOException {
        out.writeByte(type.code());
        out.writeByte(ProtocolVersion.V3);
    }

    private void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 255) {
            throw new ProtocolDecodeException(null, ErrorCode.INVALID_ENCODING, "String demasiado largo.");
        }
        out.writeByte(bytes.length);
        out.write(bytes);
    }

    private String readString(DataInputStream in) throws IOException {
        int length = in.readUnsignedByte();
        byte[] bytes = in.readNBytes(length);
        if (bytes.length != length) {
            throw new ProtocolDecodeException(null, ErrorCode.INVALID_ENCODING, "String incompleto.");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public record DiscoverResponse(int gameId, String serverName, int tcpPort, GameStatus state, int playerCount, int maximumPlayers) {
    }
}
