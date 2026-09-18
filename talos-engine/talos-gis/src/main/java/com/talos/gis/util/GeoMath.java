package com.talos.gis.util;

import com.talos.model.dto.gis.BoundingBox;

/**
 * Utility class for geographic calculations, bounding box generation,
 * and Slippy Map (XYZ tile) coordinate transformations.
 */
public final class GeoMath {

    private static final double METERS_PER_DEGREE_LAT = 111132.95;

    private GeoMath() {}

    /**
     * Calculates an exact bounding box centered at (lat, lon) with a square dimension in kilometers.
     */
    public static BoundingBox calculateBoundingBox(double centerLat, double centerLon, double sizeKm) {
        double halfSizeMeters = (sizeKm * 1000.0) / 2.0;
        double dLat = halfSizeMeters / METERS_PER_DEGREE_LAT;
        double metersPerDegreeLon = METERS_PER_DEGREE_LAT * Math.cos(Math.toRadians(centerLat));
        double dLon = halfSizeMeters / metersPerDegreeLon;

        return new BoundingBox(
                centerLat - dLat,
                centerLat + dLat,
                centerLon - dLon,
                centerLon + dLon
        );
    }

    /**
     * Converts longitude to tile X coordinate at a given zoom level.
     */
    public static int lonToTileX(double lon, int zoom) {
        return (int) Math.floor((lon + 180.0) / 360.0 * (1 << zoom));
    }

    /**
     * Converts latitude to tile Y coordinate at a given zoom level (Web Mercator projection).
     */
    public static int latToTileY(double lat, int zoom) {
        double latRad = Math.toRadians(lat);
        return (int) Math.floor((1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * (1 << zoom));
    }

    /**
     * Represents an integer range of tile coordinates [min, max] inclusive.
     */
    public record TileRange(int minX, int maxX, int minY, int maxY) {}

    /**
     * Computes the bounding range of tiles covering a given BoundingBox at a specified zoom.
     */
    public static TileRange getTileRange(BoundingBox bbox, int zoom) {
        int minX = lonToTileX(bbox.minLon(), zoom);
        int maxX = lonToTileX(bbox.maxLon(), zoom);
        // In Web Mercator, tile Y=0 is at the North Pole, so maxLat gives minY
        int minY = latToTileY(bbox.maxLat(), zoom);
        int maxY = latToTileY(bbox.minLat(), zoom);

        return new TileRange(
                Math.min(minX, maxX),
                Math.max(minX, maxX),
                Math.min(minY, maxY),
                Math.max(minY, maxY)
        );
    }
}