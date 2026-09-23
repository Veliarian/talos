package com.talos.gis.dto;

/**
 * Request payload for modifying the elevation terrain raster (digging trenches/quarries, raising berms, flattening).
 */
public record TerrainSculptRequest(
        Double centerLat,
        Double centerLon,
        Double radiusMeters,
        String operation, // 'DIG', 'RAISE', 'FLATTEN'
        Double deltaMeters
) {
    public TerrainSculptRequest {
        if (operation == null) {
            operation = "DIG";
        } else {
            operation = operation.toUpperCase();
        }
        if (deltaMeters == null) {
            deltaMeters = 5.0;
        }
        if (radiusMeters == null || radiusMeters <= 0.0) {
            radiusMeters = 25.0;
        }
    }
}