package com.talos.model.dto.gis;

/**
 * Geographic bounding box in WGS84 coordinate system (EPSG:4326).
 */
public record BoundingBox(
        double minLat,
        double maxLat,
        double minLon,
        double maxLon
) {
    public double getCenterLat() {
        return (minLat + maxLat) / 2.0;
    }

    public double getCenterLon() {
        return (minLon + maxLon) / 2.0;
    }
}