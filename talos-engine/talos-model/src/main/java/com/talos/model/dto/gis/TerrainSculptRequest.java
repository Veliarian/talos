package com.talos.model.dto.gis;

/**
 * Request payload for modifying the elevation terrain raster (digging quarries, raising berms, flattening).
 */
public record TerrainSculptRequest(
        Double centerLat,
        Double centerLon,
        Double radiusMeters,
        String operation, // 'DIG', 'RAISE', 'FLATTEN'
        Double deltaMeters
) {}