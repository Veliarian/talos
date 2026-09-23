package com.talos.gis.dto;

/**
 * DTO payload for modifying an individual geographic feature (road, building, fortification).
 */
public record FeatureUpdateRequestDto(
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
    public FeatureUpdateRequestDto {
        if (status == null || status.isBlank()) {
            status = "OPERATIONAL";
        } else {
            status = status.toUpperCase();
        }
    }
}