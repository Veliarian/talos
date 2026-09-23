package com.talos.gis.util;

import com.talos.gis.dto.BoundingBox;

/**
 * Utility class for geographic calculations, bounding box generation,
 * and Slippy Map (XYZ tile) coordinate transformations using EPSG:3857 (Web Mercator).
 */
public final class GeoMath {

    private static final double METERS_PER_DEGREE_LAT = 111132.95;
    private static final double MAX_MERCATOR_LAT = 85.05112878;

    private GeoMath() {}

    public record ZoomRange(int minZoom, int maxZoom) {}

    /**
     * Dynamically calculates optimal Slippy Map tile zoom range [minZoom..maxZoom]
     * based on theater size and latitude, preventing exponential tile explosions.
     */
    public static ZoomRange calculateOptimalZoomRange(double centerLat, double sizeKm) {
        double halfSizeMeters = (sizeKm * 1000.0) / 2.0;
        double metersPerDegreeLon = METERS_PER_DEGREE_LAT * Math.cos(Math.toRadians(centerLat));
        double deltaLonBbox = (halfSizeMeters * 2.0) / (metersPerDegreeLon > 100.0 ? metersPerDegreeLon : 100.0);

        // 1. minZoom: Whole theater fits in 1 to 2 overview tiles
        int minZoom = (int) Math.floor(Math.log(360.0 / deltaLonBbox) / Math.log(2));
        minZoom = Math.clamp(minZoom, 3, 13);

        // 2. maxZoom: Target tile budget strictly capped under ~1200 tiles per layer
        int maxZoom = (int) Math.floor(Math.log(12500.0 / deltaLonBbox) / Math.log(2));
        maxZoom = Math.clamp(maxZoom, minZoom + 2, 16);

        return new ZoomRange(minZoom, maxZoom);
    }

    /**
     * Calculates an exact bounding box centered at (centerLat, centerLon) with a square dimension in kilometers.
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

    public static int lonToTileX(double lon, int zoom) {
        return (int) Math.floor((lon + 180.0) / 360.0 * (1 << zoom));
    }

    public static int latToTileY(double lat, int zoom) {
        double clampedLat = Math.clamp(lat, -MAX_MERCATOR_LAT, MAX_MERCATOR_LAT);
        double latRad = Math.toRadians(clampedLat);
        return (int) Math.floor((1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * (1 << zoom));
    }

    public static double lonToTileXDouble(double lon, int zoom) {
        return (lon + 180.0) / 360.0 * (1 << zoom);
    }

    public static double latToTileYDouble(double lat, int zoom) {
        double clampedLat = Math.clamp(lat, -MAX_MERCATOR_LAT, MAX_MERCATOR_LAT);
        double latRad = Math.toRadians(clampedLat);
        return (1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * (1 << zoom);
    }

    public record TileRange(int minX, int maxX, int minY, int maxY) {}

    public static TileRange getTileRange(BoundingBox bbox, int zoom) {
        int minX = lonToTileX(bbox.minLon(), zoom);
        int maxX = lonToTileX(bbox.maxLon(), zoom);
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