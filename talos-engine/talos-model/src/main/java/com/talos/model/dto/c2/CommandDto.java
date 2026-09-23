package com.talos.model.dto.c2;

import java.util.List;

/**
 * Operational command DTO received from C2 client interface.
 */
public record CommandDto(
        String type,
        List<String> unitIds,
        Double targetLat,
        Double targetLon,
        Boolean queue,
        Double speedKmh
) {
    public boolean isQueued() {
        return Boolean.TRUE.equals(queue);
    }
}