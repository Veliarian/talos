import { type ShallowRef } from 'vue';
import {
    Viewer,
    GeoJsonDataSource,
    Color,
    ColorMaterialProperty,
    ConstantProperty,
    HeightReference,
    DistanceDisplayCondition,
    Entity
} from 'cesium';
import type { FeatureStatus, TacticalModifierData } from '../types';

export function useVectorOverlay(viewer: ShallowRef<Viewer | null>) {
    let vectorDataSource: GeoJsonDataSource | null = null;
    let cachedModifiers: TacticalModifierData[] = [];

    // Shared GPU materials cache to avoid recreation lag
    const colorMaterialCache = new Map<string, ColorMaterialProperty>();
    const getDynamicMaterial = (hexColor: string, alpha: number) => {
        const key = `${hexColor}_${alpha}`;
        if (!colorMaterialCache.has(key)) {
            colorMaterialCache.set(key, new ColorMaterialProperty(Color.fromCssColorString(hexColor).withAlpha(alpha)));
        }
        return colorMaterialCache.get(key)!;
    };

    const CONST_WIDTH_ROAD = new ConstantProperty(4.0);
    const CONST_WIDTH_STREAM = new ConstantProperty(3.0);
    const CONST_WIDTH_CHECKPOINT = new ConstantProperty(6.0);
    const CONST_OUTLINE_FALSE = new ConstantProperty(false);
    const LOD_BUILDINGS = new DistanceDisplayCondition(0.0, 4500.0); // 3D buildings render up to 4.5km

    const applyEntityStyling = (
        entity: Entity,
        modifiersList: TacticalModifierData[],
        is3D: boolean,
        highlightedOsmValue: string | null = null
    ) => {
        const category = entity.properties?.category?.getValue();
        const status = entity.properties?.status?.getValue() as FeatureStatus | undefined;
        const typeValue = entity.properties?.typeValue?.getValue();

        const isHighlighted = (highlightedOsmValue !== null && typeValue === highlightedOsmValue);
        const isDimmed = (highlightedOsmValue !== null && typeValue !== highlightedOsmValue);

        const matchedMod = modifiersList.find(m => m.osmValue === typeValue);
        const customColorHex = matchedMod?.color2d || getDefaultColor(category);

        let finalColorHex = isHighlighted ? '#00ffff' : customColorHex;
        let alpha = isDimmed ? 0.2 : ((category === 'BUILDING') ? 0.9 : 0.65);

        // 1. ROADS (Polyline tightly clamped to 3D terrain)
        if (entity.polyline) {
            entity.polyline.clampToGround = new ConstantProperty(true) as any;
            if (status === 'DESTROYED') entity.polyline.material = getDynamicMaterial('#475569', 0.6);
            else if (status === 'MINED') entity.polyline.material = getDynamicMaterial('#dc2626', 0.95);
            else if (status === 'CHECKPOINT') {
                entity.polyline.material = getDynamicMaterial('#f97316', 0.95);
                entity.polyline.width = CONST_WIDTH_CHECKPOINT;
            } else {
                entity.polyline.material = getDynamicMaterial(finalColorHex, isDimmed ? 0.2 : 0.95);
                entity.polyline.width = (category === 'RIVER') ? CONST_WIDTH_STREAM : CONST_WIDTH_ROAD;
            }
            return;
        }

        // 2. POLYGONS: BUILDINGS, WATER, VEGETATION, SOIL
        if (entity.polygon) {
            entity.polygon.outline = CONST_OUTLINE_FALSE as any;

            if (category === 'BUILDING') {
                const heightMeters = entity.properties?.heightMeters?.getValue() || 6.5;

                // Ground Clamping: Bottom anchors to surface, top rises relative to ground
                entity.polygon.heightReference = new ConstantProperty(HeightReference.CLAMP_TO_GROUND) as any;

                if (is3D) {
                    entity.polygon.extrudedHeightReference = new ConstantProperty(HeightReference.RELATIVE_TO_GROUND) as any;
                    entity.polygon.extrudedHeight = new ConstantProperty(heightMeters) as any;
                    entity.polygon.distanceDisplayCondition = new ConstantProperty(LOD_BUILDINGS) as any;
                } else {
                    entity.polygon.extrudedHeightReference = undefined as any;
                    entity.polygon.extrudedHeight = undefined as any;
                    entity.polygon.distanceDisplayCondition = undefined as any;
                }

                entity.polygon.material = getDynamicMaterial(finalColorHex, alpha);
                return;
            }

            // Flat terrain draped polygons (Water, Forests, Soil)
            entity.polygon.heightReference = new ConstantProperty(HeightReference.CLAMP_TO_GROUND) as any;
            entity.polygon.extrudedHeightReference = undefined as any;
            entity.polygon.extrudedHeight = undefined as any;
            entity.polygon.material = getDynamicMaterial(finalColorHex, alpha);
        }
    };

    const loadVectors = async (
        geoJson: any,
        modifiersList: TacticalModifierData[],
        is3D: boolean,
        isVisible = false
    ) => {
        if (!viewer.value) return;
        cachedModifiers = modifiersList;

        if (vectorDataSource) {
            viewer.value.dataSources.remove(vectorDataSource);
        }

        vectorDataSource = await GeoJsonDataSource.load(geoJson, { clampToGround: true });
        for (const entity of vectorDataSource.entities.values) {
            applyEntityStyling(entity, modifiersList, is3D);
        }

        vectorDataSource.show = isVisible;
        viewer.value.dataSources.add(vectorDataSource);
    };

    const reapplyAllStyling = (modifiersList: TacticalModifierData[], is3D: boolean) => {
        if (!vectorDataSource) return;
        cachedModifiers = modifiersList;
        for (const entity of vectorDataSource.entities.values) {
            applyEntityStyling(entity, modifiersList, is3D);
        }
    };

    const highlightCategoryObjects = (osmValue: string | null, is3D: boolean) => {
        if (!vectorDataSource) return;
        for (const entity of vectorDataSource.entities.values) {
            applyEntityStyling(entity, cachedModifiers, is3D, osmValue);
        }
    };

    const setVectorsVisible = (show: boolean) => {
        if (vectorDataSource) {
            vectorDataSource.show = show;
        }
    };

    const getDefaultColor = (category: string) => {
        switch (category) {
            case 'ROAD': return '#f59e0b';
            case 'BRIDGE': return '#ffffff';
            case 'RIVER':
            case 'OPEN_WATER':
            case 'WATER': return '#0284c7';
            case 'VEGETATION': return '#15803d';
            case 'BUILDING': return '#dc2626';
            default: return '#78350f';
        }
    };

    return {
        loadVectors,
        setVectorsVisible,
        highlightCategoryObjects,
        reapplyAllStyling
    };
}