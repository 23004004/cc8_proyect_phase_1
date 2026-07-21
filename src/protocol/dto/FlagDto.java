package protocol.dto;

import model.FlagStatus;

public record FlagDto(
        int row,
        int column,
        FlagStatus status,
        String carrierId
) {
}
