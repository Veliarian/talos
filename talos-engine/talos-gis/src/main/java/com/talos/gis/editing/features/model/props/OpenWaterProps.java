package com.talos.gis.editing.features.model.props;

public record OpenWaterProps(
        String waterBodyType,
        float depthMeters,
        String bottomType,
        float reedBeltWidthMeters,
        float speedModifierAmphibious,
        String iceCoverState
) {
    public OpenWaterProps {
        if (waterBodyType == null || waterBodyType.isBlank()) waterBodyType = "Natural lake";
        if (iceCoverState == null) iceCoverState = "No ice";
    }
}