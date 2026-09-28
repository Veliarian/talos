package com.talos.gis.editing.features.model.props;

public record BuildingProps(
        String buildingType,
        String structureMaterial,
        int buildingLevels,
        float heightMeters,
        float coverDefensePercent,
        boolean canEnterUnits,
        String roofType
) {
    public BuildingProps {
        if (buildingType == null || buildingType.isBlank()) buildingType = "Private residential";
        if (structureMaterial == null) structureMaterial = "Solid brick";
        if (buildingLevels <= 0) buildingLevels = 1;
        if (heightMeters <= 0) heightMeters = 3.5f;
        if (roofType == null) roofType = "Gable";
    }
}