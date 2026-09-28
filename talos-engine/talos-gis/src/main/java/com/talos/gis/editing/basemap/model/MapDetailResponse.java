package com.talos.gis.editing.basemap.model;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Data Transfer Object containing full details of a bounded theater map.
 */
public record MapDetailResponse(
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
        List<MapLayerData> layers
) {
    public MapDetailResponse {
        if (layers == null) {
            layers = List.of();
        }
    }
}