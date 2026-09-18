package com.talos.model.dto.gis;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Data Transfer Object containing full details of a bounded theater map.
 */
public record MapDetailDto(
        UUID id,
        String name,
        String description,
        double minLat,
        double maxLat,
        double minLon,
        double maxLon,
        double centerLat,
        double centerLon,
        double sizeKm,
        String status,
        OffsetDateTime createdAt,
        List<MapLayerDto> layers
) {
    public record MapLayerDto(
            UUID id,
            String layerType,
            int minZoom,
            int maxZoom,
            boolean isDefault,
            String status
    ) {}
}