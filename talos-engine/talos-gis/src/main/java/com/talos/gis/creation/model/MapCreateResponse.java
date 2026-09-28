package com.talos.gis.creation.model;

import java.util.UUID;

/**
 * Response payload returned upon successfully initiating a background map ingestion pipeline.
 */
public record MapCreateResponse(
        UUID mapId,
        String status,
        String message
) {
    public static MapCreateResponse initiated(UUID mapId) {
        return new MapCreateResponse(mapId, "DOWNLOADING", "Map ingestion pipeline started in background");
    }
}