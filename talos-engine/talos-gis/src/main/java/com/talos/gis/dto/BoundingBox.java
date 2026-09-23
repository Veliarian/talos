package com.talos.gis.dto;

/**
 * Geographic bounding box in WGS84 coordinate system (EPSG:4326).
 */
public record BoundingBox(
        double minLat,
        double maxLat,
        double minLon,
        double maxLon
) {
    public BoundingBox {
        if (minLat > maxLat) {
            throw new IllegalArgumentException("minLat cannot be greater than maxLat");
        }
        if (minLon > maxLon) {
            throw new IllegalArgumentException("minLon cannot be greater than maxLon");
        }
    }

    public double getCenterLat() {
        return (minLat + maxLat) / 2.0;
    }

    public double getCenterLon() {
        return (minLon + maxLon) / 2.0;
    }

    /**
     * Factory method to construct a bounding box from center coordinates and radial delta in kilometers.
     */
    public static BoundingBox fromCenterAndRadiusKm(double centerLat, double centerLon, double radiusKm) {
        double latDelta = radiusKm / 111.132;
        double lonDelta = radiusKm / (111.320 * Math.cos(Math.toRadians(centerLat)));

        return new BoundingBox(
                centerLat - latDelta,
                centerLat + latDelta,
                centerLon - lonDelta,
                centerLon + lonDelta
        );
    }
}