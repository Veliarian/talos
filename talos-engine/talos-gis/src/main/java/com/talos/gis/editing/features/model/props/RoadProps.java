package com.talos.gis.editing.features.model.props;

public record RoadProps(
        String surfaceType,
        float widthMeters,
        int lanesCount,
        boolean isOneWay,
        boolean infantryOnly,
        float coverDefensePercent
) {
    public RoadProps {
        if (surfaceType == null || surfaceType.isBlank()) surfaceType = "Asphalt";
        if (widthMeters <= 0) widthMeters = 8.0f;
        if (lanesCount <= 0) lanesCount = 2;
    }
}