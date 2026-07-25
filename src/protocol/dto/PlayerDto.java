package protocol.dto;

import model.Direction;

public record PlayerDto(
        int playerId,
        String name,
        int x,
        int y,
        Direction direction,
        boolean hasFlag
) {
}
