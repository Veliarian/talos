package com.talos.gis.editing.features.model.props;

public record RiverProps(
        String waterwayType,
        float depthMeters,
        float widthMeters,
        float flowSpeedMps,
        float flowDirectionDegrees,
        String bottomType,
        boolean isFordable,
        float maxFordDepthMeters,
        float speedModifierAmphibious
) {
    public RiverProps {
        if (waterwayType == null || waterwayType.isBlank()) waterwayType = "A navigable river";
        if (bottomType == null) bottomType = "Hard and rocky";
        if (speedModifierAmphibious <= 0) speedModifierAmphibious = 0.6f;
    }
}