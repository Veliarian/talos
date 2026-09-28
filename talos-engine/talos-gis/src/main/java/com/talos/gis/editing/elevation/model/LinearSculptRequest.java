package com.talos.gis.editing.elevation.model;

/**
 * Request payload for modifying elevation along a linear trajectory (A -> B),
 * enabling trench digging, anti-tank ditches, and earthen berm ramparts.
 */
public record LinearSculptRequest(
        Double startLat,
        Double startLon,
        Double endLat,
        Double endLon,
        Double widthMeters,
        Double deltaMeters,
        String operation // 'TRENCH_DIG', 'AT_DITCH', 'BERM_RAISE'
) {
    public LinearSculptRequest {
        if (operation == null || operation.isBlank()) {
            operation = "TRENCH_DIG";
        } else {
            operation = operation.toUpperCase();
        }
        if (widthMeters == null || widthMeters <= 0.0) {
            widthMeters = 4.0; // Default 4-meter wide trench
        }
        if (deltaMeters == null || deltaMeters <= 0.0) {
            deltaMeters = 2.0; // Default 2-meter depth
        }
    }
}