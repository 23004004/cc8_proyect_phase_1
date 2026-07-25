package protocol.dto;

import model.FlagStatus;

public record FlagDto(
        int x,
        int y,
        FlagStatus status,
        int carrierId
) {
}
