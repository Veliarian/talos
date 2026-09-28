package com.talos.gis.editing.features.model.props;

public record VegetationProps(
        String vegetationType,
        float heightMeters,
        float stemDiameterCm,
        float densityPercent,
        Float visibilityMeters,
        float coverDefensePercent,
        float speedModifierInfantry
) {
    public VegetationProps {
        if (vegetationType == null || vegetationType.isBlank()) vegetationType = "Coniferous forest";
        if (heightMeters <= 0) heightMeters = 18.0f;
    }
}