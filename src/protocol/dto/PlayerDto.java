package protocol.dto;

import model.Direction;

public record PlayerDto(
        String playerId,
        String name,
        int row,
        int column,
        Direction direction,
        boolean insideBoard,
        boolean hasFlag,
        boolean protectedPlayer
) {
}
