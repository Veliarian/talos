package com.talos.gis.editing.features.model;

/**
 * DTO payload for modifying an individual geographic feature (road, building, fortification).
 */
public record FeaturePatchRequest(
        String name,
        String status, // 'OPERATIONAL', 'DESTROYED', 'MINED', 'CHECKPOINT'
        Float speedModifierOverrideWheeled,
        Float speedModifierOverrideTracked,
        Float visibilityOverride,
        Float coverDefenseOverride,
        String customNotes,
        Float heightMeters,
        Float widthMeters
) {
    public FeaturePatchRequest {
        if (status == null || status.isBlank()) {
            status = "OPERATIONAL";
        } else {
            status = status.toUpperCase();
        }
    }
}