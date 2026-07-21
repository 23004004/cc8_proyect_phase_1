package protocol;

import model.Direction;
import model.FlagStatus;
import protocol.dto.FlagDto;
import protocol.dto.PlayerDto;
import protocol.dto.PositionDto;
import protocol.json.JsonParser;
import protocol.json.JsonWriter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ProtocolCodec {
    public String serialize(ProtocolMessage message) {
        return JsonWriter.write(toJsonObject(message));
    }

    public ProtocolMessage deserialize(String json) {
        Object parsed = new JsonParser(json).parse();
        Map<String, Object> object = expectObject(parsed);

        String typeValue = requireString(object, "type");
        String version = requireString(object, "protocolVersion");
        MessageType type = MessageType.valueOf(typeValue);
        validateVersion(version);

        return switch (type) {
            case JOIN -> new JoinRequest(version, requireString(object, "name"));
            case CHANGE_DIRECTION -> new ChangeDirectionRequest(
                    version,
                    requireString(object, "gameId"),
                    requireString(object, "playerId"),
                    Direction.valueOf(requireString(object, "direction"))
            );
            case LEAVE -> new LeaveRequest(
                    version,
                    requireString(object, "gameId"),
                    requireString(object, "playerId")
            );
            case JOIN_ACCEPTED -> new JoinAcceptedMessage(
                    version,
                    requireString(object, "playerId"),
                    requireString(object, "gameId")
            );
            case JOIN_REJECTED -> new JoinRejectedMessage(
                    version,
                    JoinRejectedReason.valueOf(requireString(object, "reason"))
            );
            case GAME_STARTED -> new GameStartedMessage(
                    version,
                    requireString(object, "gameId"),
                    requireInt(object, "rows"),
                    requireInt(object, "columns"),
                    requireInt(object, "movementIntervalMs"),
                    requireInt(object, "protectionTimeMs"),
                    readPositions(object, "obstacles"),
                    readFlag(object, "flag"),
                    readPlayers(object, "players")
            );
            case GAME_STATE -> new GameStateMessage(
                    version,
                    requireString(object, "gameId"),
                    requireLong(object, "tick"),
                    readPlayers(object, "players"),
                    readFlag(object, "flag")
            );
            case FLAG_PICKED_UP -> new FlagPickedUpMessage(
                    version,
                    requireString(object, "gameId"),
                    requireLong(object, "tick"),
                    requireString(object, "playerId")
            );
            case FLAG_STOLEN -> new FlagStolenMessage(
                    version,
                    requireString(object, "gameId"),
                    requireLong(object, "tick"),
                    requireString(object, "previousCarrierId"),
                    requireString(object, "newCarrierId"),
                    requireInt(object, "protectionTimeMs")
            );
            case PLAYER_DISCONNECTED -> new PlayerDisconnectedMessage(
                    version,
                    requireString(object, "gameId"),
                    requireString(object, "playerId")
            );
            case GAME_OVER -> new GameOverMessage(
                    version,
                    requireString(object, "gameId"),
                    requireString(object, "winnerId"),
                    requireString(object, "winnerName"),
                    GameOverReason.valueOf(requireString(object, "reason"))
            );
            case ERROR -> new ErrorMessage(
                    version,
                    ErrorCode.valueOf(requireString(object, "code")),
                    requireString(object, "description")
            );
        };
    }

    private Map<String, Object> toJsonObject(ProtocolMessage message) {
        return switch (message.type()) {
            case JOIN -> mapOf(
                    "type", message.type().name(),
                    "protocolVersion", message.protocolVersion(),
                    "name", ((JoinRequest) message).name()
            );
            case CHANGE_DIRECTION -> mapOf(
                    "type", message.type().name(),
                    "protocolVersion", message.protocolVersion(),
                    "gameId", ((ChangeDirectionRequest) message).gameId(),
                    "playerId", ((ChangeDirectionRequest) message).playerId(),
                    "direction", ((ChangeDirectionRequest) message).direction().name()
            );
            case LEAVE -> mapOf(
                    "type", message.type().name(),
                    "protocolVersion", message.protocolVersion(),
                    "gameId", ((LeaveRequest) message).gameId(),
                    "playerId", ((LeaveRequest) message).playerId()
            );
            case JOIN_ACCEPTED -> mapOf(
                    "type", message.type().name(),
                    "protocolVersion", message.protocolVersion(),
                    "playerId", ((JoinAcceptedMessage) message).playerId(),
                    "gameId", ((JoinAcceptedMessage) message).gameId()
            );
            case JOIN_REJECTED -> mapOf(
                    "type", message.type().name(),
                    "protocolVersion", message.protocolVersion(),
                    "reason", ((JoinRejectedMessage) message).reason().name()
            );
            case GAME_STARTED -> mapOf(
                    "type", message.type().name(),
                    "protocolVersion", message.protocolVersion(),
                    "gameId", ((GameStartedMessage) message).gameId(),
                    "rows", ((GameStartedMessage) message).rows(),
                    "columns", ((GameStartedMessage) message).columns(),
                    "movementIntervalMs", ((GameStartedMessage) message).movementIntervalMs(),
                    "protectionTimeMs", ((GameStartedMessage) message).protectionTimeMs(),
                    "obstacles", writePositions(((GameStartedMessage) message).obstacles()),
                    "flag", writeFlag(((GameStartedMessage) message).flag()),
                    "players", writePlayers(((GameStartedMessage) message).players())
            );
            case GAME_STATE -> mapOf(
                    "type", message.type().name(),
                    "protocolVersion", message.protocolVersion(),
                    "gameId", ((GameStateMessage) message).gameId(),
                    "tick", ((GameStateMessage) message).tick(),
                    "players", writePlayers(((GameStateMessage) message).players()),
                    "flag", writeFlag(((GameStateMessage) message).flag())
            );
            case FLAG_PICKED_UP -> mapOf(
                    "type", message.type().name(),
                    "protocolVersion", message.protocolVersion(),
                    "gameId", ((FlagPickedUpMessage) message).gameId(),
                    "tick", ((FlagPickedUpMessage) message).tick(),
                    "playerId", ((FlagPickedUpMessage) message).playerId()
            );
            case FLAG_STOLEN -> mapOf(
                    "type", message.type().name(),
                    "protocolVersion", message.protocolVersion(),
                    "gameId", ((FlagStolenMessage) message).gameId(),
                    "tick", ((FlagStolenMessage) message).tick(),
                    "previousCarrierId", ((FlagStolenMessage) message).previousCarrierId(),
                    "newCarrierId", ((FlagStolenMessage) message).newCarrierId(),
                    "protectionTimeMs", ((FlagStolenMessage) message).protectionTimeMs()
            );
            case PLAYER_DISCONNECTED -> mapOf(
                    "type", message.type().name(),
                    "protocolVersion", message.protocolVersion(),
                    "gameId", ((PlayerDisconnectedMessage) message).gameId(),
                    "playerId", ((PlayerDisconnectedMessage) message).playerId()
            );
            case GAME_OVER -> mapOf(
                    "type", message.type().name(),
                    "protocolVersion", message.protocolVersion(),
                    "gameId", ((GameOverMessage) message).gameId(),
                    "winnerId", ((GameOverMessage) message).winnerId(),
                    "winnerName", ((GameOverMessage) message).winnerName(),
                    "reason", ((GameOverMessage) message).reason().name()
            );
            case ERROR -> mapOf(
                    "type", message.type().name(),
                    "protocolVersion", message.protocolVersion(),
                    "code", ((ErrorMessage) message).code().name(),
                    "description", ((ErrorMessage) message).description()
            );
        };
    }

    private List<Object> writePositions(List<PositionDto> positions) {
        List<Object> result = new ArrayList<>();
        for (PositionDto position : positions) {
            result.add(mapOf(
                    "row", position.row(),
                    "column", position.column()
            ));
        }
        return result;
    }

    private List<Object> writePlayers(List<PlayerDto> players) {
        List<Object> result = new ArrayList<>();
        for (PlayerDto player : players) {
            result.add(mapOf(
                    "playerId", player.playerId(),
                    "name", player.name(),
                    "row", player.row(),
                    "column", player.column(),
                    "direction", player.direction().name(),
                    "insideBoard", player.insideBoard(),
                    "hasFlag", player.hasFlag(),
                    "protected", player.protectedPlayer()
            ));
        }
        return result;
    }

    private Map<String, Object> writeFlag(FlagDto flag) {
        return mapOf(
                "row", flag.row(),
                "column", flag.column(),
                "status", flag.status().name(),
                "carrierId", flag.carrierId()
        );
    }

    private List<PositionDto> readPositions(Map<String, Object> object, String key) {
        List<Object> raw = requireArray(object, key);
        List<PositionDto> result = new ArrayList<>(raw.size());
        for (Object item : raw) {
            Map<String, Object> position = expectObject(item);
            result.add(new PositionDto(
                    requireInt(position, "row"),
                    requireInt(position, "column")
            ));
        }
        return result;
    }

    private List<PlayerDto> readPlayers(Map<String, Object> object, String key) {
        List<Object> raw = requireArray(object, key);
        List<PlayerDto> result = new ArrayList<>(raw.size());
        for (Object item : raw) {
            Map<String, Object> player = expectObject(item);
            result.add(new PlayerDto(
                    requireString(player, "playerId"),
                    requireString(player, "name"),
                    requireInt(player, "row"),
                    requireInt(player, "column"),
                    Direction.valueOf(requireString(player, "direction")),
                    requireBoolean(player, "insideBoard"),
                    requireBoolean(player, "hasFlag"),
                    requireBoolean(player, "protected")
            ));
        }
        return result;
    }

    private FlagDto readFlag(Map<String, Object> object, String key) {
        Map<String, Object> flag = expectObject(requireValue(object, key));
        return new FlagDto(
                requireInt(flag, "row"),
                requireInt(flag, "column"),
                FlagStatus.valueOf(requireString(flag, "status")),
                requireNullableString(flag, "carrierId")
        );
    }

    private void validateVersion(String version) {
        if (!ProtocolVersion.V1_0.equals(version)) {
            throw new IllegalArgumentException("Unsupported protocol version: " + version);
        }
    }

    private Map<String, Object> expectObject(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Expected JSON object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> casted = (Map<String, Object>) map;
        return casted;
    }

    private List<Object> requireArray(Map<String, Object> object, String key) {
        Object value = requireValue(object, key);
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("Field '" + key + "' must be an array");
        }
        @SuppressWarnings("unchecked")
        List<Object> casted = (List<Object>) list;
        return casted;
    }

    private Object requireValue(Map<String, Object> object, String key) {
        if (!object.containsKey(key)) {
            throw new IllegalArgumentException("Missing field: " + key);
        }
        return object.get(key);
    }

    private String requireString(Map<String, Object> object, String key) {
        Object value = requireValue(object, key);
        if (!(value instanceof String string)) {
            throw new IllegalArgumentException("Field '" + key + "' must be a string");
        }
        return string;
    }

    private String requireNullableString(Map<String, Object> object, String key) {
        Object value = requireValue(object, key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String string)) {
            throw new IllegalArgumentException("Field '" + key + "' must be a string or null");
        }
        return string;
    }

    private int requireInt(Map<String, Object> object, String key) {
        long value = requireLong(object, key);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Field '" + key + "' is out of range");
        }
        return (int) value;
    }

    private long requireLong(Map<String, Object> object, String key) {
        Object value = requireValue(object, key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalArgumentException("Field '" + key + "' must be a number");
    }

    private boolean requireBoolean(Map<String, Object> object, String key) {
        Object value = requireValue(object, key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        throw new IllegalArgumentException("Field '" + key + "' must be a boolean");
    }

    private Map<String, Object> mapOf(Object... entries) {
        if (entries.length % 2 != 0) {
            throw new IllegalArgumentException("Entries must be key/value pairs");
        }
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            map.put(String.valueOf(entries[i]), entries[i + 1]);
        }
        return map;
    }
}
