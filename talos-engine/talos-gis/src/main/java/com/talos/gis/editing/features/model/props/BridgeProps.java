package com.talos.gis.editing.features.model.props;

public record BridgeProps(
        String bridgeType,
        float maxWeightTons,
        float widthMeters,
        float lengthMeters,
        int lanesCount,
        float coverDefensePercent,
        String destructionState
) {
    public BridgeProps {
        if (bridgeType == null || bridgeType.isBlank()) bridgeType = "Reinforced concrete";
        if (maxWeightTons <= 0) maxWeightTons = 60.0f;
        if (destructionState == null) destructionState = "Whole";
    }
}