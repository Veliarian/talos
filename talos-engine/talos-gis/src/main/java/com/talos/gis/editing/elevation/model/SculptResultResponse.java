package com.talos.gis.editing.elevation.model;

/**
 * Response payload returned after applying radial or linear elevation sculpting operations.
 */
public record SculptResultResponse(
        String status,
        String operation,
        Double newElevationAtCenter
) {
    public static SculptResultResponse success(String operation, Double newElevationAtCenter) {
        return new SculptResultResponse("SUCCESS", operation, newElevationAtCenter);
    }

    public static SculptResultResponse success(String operation) {
        return new SculptResultResponse("SUCCESS", operation, null);
    }

    public static SculptResultResponse failed(String operation) {
        return new SculptResultResponse("FAILED", operation, null);
    }
}