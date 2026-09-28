package com.talos.gis.editing.features.model.props;

public record SoilProps(
        String soilType,
        String bearingCapacity,
        String dustGeneration,
        float coverDefensePercent
) {
    public SoilProps {
        if (soilType == null || soilType.isBlank()) soilType = "The chernozem is dense";
        if (bearingCapacity == null) bearingCapacity = "Solid";
        if (dustGeneration == null) dustGeneration = "Average";
    }
}