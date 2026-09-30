// useVectorOverlay.ts
// Military-grade 2D/3D vector overlay with Steel Beasts tactical palette and high-speed GPU rendering

import { ref, type ShallowRef } from 'vue';
import {
    Viewer,
    GeoJsonDataSource,
    Color,
    ColorMaterialProperty,
    ConstantProperty,
    HeightReference,
    DistanceDisplayCondition,
    Entity,
    ScreenSpaceEventHandler,
    ScreenSpaceEventType,
    ClassificationType
} from 'cesium';
import type { FeatureStatus, TacticalModifierData, FeatureUpdateRequest } from '../types';

const CONST_CLASSIFICATION_TERRAIN = new ConstantProperty(ClassificationType.TERRAIN);

export function useVectorOverlay(viewer: ShallowRef<Viewer | null>) {
    let vectorDataSource: GeoJsonDataSource | null = null;
    let cachedModifiers: TacticalModifierData[] = [];
    let clickHandler: ScreenSpaceEventHandler | null = null;

    const selectedFeatureId = ref<string | null>(null);

    const colorMaterialCache = new Map<string, ColorMaterialProperty>();
    const getDynamicMaterial = (hexColor: string, alpha: number) => {
        const key = `${hexColor}_${alpha.toFixed(2)}`;
        if (!colorMaterialCache.has(key)) {
            colorMaterialCache.set(key, new ColorMaterialProperty(Color.fromCssColorString(hexColor).withAlpha(alpha)));
        }
        return colorMaterialCache.get(key)!;
    };

    const CONST_OUTLINE_FALSE = new ConstantProperty(false);
    const CONST_HEIGHT_ZERO = new ConstantProperty(0);
    const CONST_CLAMP_TO_GROUND = new ConstantProperty(HeightReference.CLAMP_TO_GROUND);
    const CONST_RELATIVE_TO_GROUND = new ConstantProperty(HeightReference.RELATIVE_TO_GROUND);
    const CONST_TRUE = new ConstantProperty(true);

    // PERFORMANCE: Strict 2200m building LOD eliminates thousands of off-screen GPU draw calls in dense cities
    const LOD_BUILDINGS = new DistanceDisplayCondition(0.0, 2200.0);

    // Tactical Status Colors
    const COLOR_DESTROYED = '#27272a';
    const COLOR_MINED = '#b91c1c';
    const COLOR_CHECKPOINT = '#ea580c';
    const COLOR_SELECTED = '#00ffff';

    /**
     * Resolves realistic military road widths and authentic asphalt/dirt materials
     */
    const resolveRoadSpec = (props: any) => {
        const typeValue = (props?.typeValue?.getValue() || props?.typeValue || '').toLowerCase();
        const customWidth = props?.widthMeters?.getValue?.() ?? props?.widthMeters;
        const lanes = props?.lanesCount?.getValue?.() ?? props?.lanesCount;

        let width = 7.0;
        let surfaceColor = '#27272a'; // Standard worn asphalt

        if (customWidth && Number(customWidth) > 0) {
            width = Number(customWidth);
        } else if (lanes && Number(lanes) > 0) {
            width = Math.max(4.0, Number(lanes) * 3.5);
        } else {
            switch (typeValue) {
                case 'motorway':
                case 'trunk':
                    width = 14.0;
                    surfaceColor = '#18181b'; // Dark tactical highway asphalt
                    break;
                case 'primary':
                    width = 11.0;
                    surfaceColor = '#18181b';
                    break;
                case 'secondary':
                case 'tertiary':
                    width = 8.0;
                    surfaceColor = '#27272a';
                    break;
                case 'residential':
                case 'living_street':
                case 'service':
                    width = 6.0;
                    surfaceColor = '#3f3f46';
                    break;
                case 'track':
                case 'path':
                case 'unpaved':
                case 'dirt':
                    width = 4.0;
                    surfaceColor = '#5c3413'; // Authentic dark earth/mud track
                    break;
                default:
                    width = 6.0;
                    surfaceColor = '#27272a';
            }
        }

        return { width, surfaceColor };
    };

    const applyEntityStyling = (
        entity: Entity,
        modifiersList: TacticalModifierData[],
        highlightedOsmValue: string | null = null
    ) => {
        const props = entity.properties;
        const featureId = props?.id?.getValue() || entity.id;
        const category = props?.category?.getValue();
        const typeKey = props?.typeKey?.getValue();
        const typeValue = props?.typeValue?.getValue();
        const status = props?.status?.getValue() as FeatureStatus | undefined;

        const isSelected = selectedFeatureId.value === featureId;
        const isCategoryHighlighted = (highlightedOsmValue !== null && typeValue === highlightedOsmValue);
        const isDimmed = (highlightedOsmValue !== null && typeValue !== highlightedOsmValue);

        const matchedMod = modifiersList.find(m => m.osmValue === typeValue);
        const baseColorHex = matchedMod?.color2d || getDefaultColor(category);

        let finalColorHex = isSelected
            ? COLOR_SELECTED
            : isCategoryHighlighted
                ? '#38bdf8'
                : baseColorHex;

        // 1. ROADS AND BRIDGES (GroundPolyline batch with authentic asphalt/dirt)
        if (entity.polyline) {
            const isWater = (category === 'RIVER' || category === 'WATER');
            const { width, surfaceColor } = resolveRoadSpec(props);

            let activeColorHex = isWater ? '#1e3a5f' : surfaceColor;
            if (isSelected) activeColorHex = COLOR_SELECTED;
            else if (isCategoryHighlighted) activeColorHex = '#38bdf8';
            else if (status === 'DESTROYED') activeColorHex = COLOR_DESTROYED;
            else if (status === 'MINED') activeColorHex = COLOR_MINED;
            else if (status === 'CHECKPOINT') activeColorHex = COLOR_CHECKPOINT;

            entity.polyline.clampToGround = CONST_TRUE as any;
            entity.polyline.classificationType = CONST_CLASSIFICATION_TERRAIN as any;
            entity.polyline.width = new ConstantProperty(width) as any;
            entity.polyline.material = getDynamicMaterial(activeColorHex, isDimmed ? 0.3 : 0.95);
            return;
        }

        // 2. POLYGONS: 3D BUILDINGS vs DRAPED TERRAIN ZONES
        if (entity.polygon) {
            entity.polygon.outline = CONST_OUTLINE_FALSE as any;

            const isActualBuilding = (category === 'BUILDING' && typeKey === 'building');

            // A. VOLUMETRIC STRUCTURES (Contrasting military architectural tones)
            if (isActualBuilding) {
                const heightMeters = props?.heightMeters?.getValue() || 7.5;

                // Concrete panel / industrial dark steel palette
                let buildingColorHex = '#64748b'; // Reinforced concrete
                if (typeValue === 'industrial' || typeValue === 'warehouse') buildingColorHex = '#334155';
                else if (typeValue === 'apartments') buildingColorHex = '#52525b';
                else if (typeValue === 'house') buildingColorHex = '#7c2d12';

                if (isSelected) buildingColorHex = COLOR_SELECTED;
                else if (isCategoryHighlighted) buildingColorHex = '#38bdf8';
                else if (status === 'DESTROYED') buildingColorHex = COLOR_DESTROYED;
                else if (status === 'MINED') buildingColorHex = COLOR_MINED;

                entity.polygon.height = CONST_HEIGHT_ZERO as any;
                entity.polygon.heightReference = CONST_CLAMP_TO_GROUND as any;
                entity.polygon.extrudedHeightReference = CONST_RELATIVE_TO_GROUND as any;
                entity.polygon.extrudedHeight = new ConstantProperty(heightMeters) as any;
                entity.polygon.distanceDisplayCondition = new ConstantProperty(LOD_BUILDINGS) as any;
                entity.polygon.material = getDynamicMaterial(buildingColorHex, isDimmed ? 0.25 : 1.0);
                return;
            }

            // B. FLAT DRAPED TERRAIN ZONES (Authentic forest floor and deep water)
            entity.polygon.height = undefined as any;
            entity.polygon.heightReference = undefined as any;
            entity.polygon.extrudedHeight = undefined as any;
            entity.polygon.extrudedHeightReference = undefined as any;
            entity.polygon.distanceDisplayCondition = undefined as any;
            entity.polygon.classificationType = CONST_CLASSIFICATION_TERRAIN as any;

            let alpha = 0.65;
            let zoneColor = finalColorHex;

            if (category === 'RIVER' || category === 'OPEN_WATER') {
                zoneColor = '#1e3a5f'; // Deep tactical navy water
                alpha = 0.85;
            } else if (category === 'VEGETATION') {
                zoneColor = '#36432b'; // Deep military olive forest floor
                alpha = 0.75;
            } else if (category === 'BUILDING') {
                zoneColor = '#3f3f46'; // Residential zoning perimeter
                alpha = 0.25;
            }

            entity.polygon.material = getDynamicMaterial(zoneColor, isDimmed ? 0.2 : alpha);
        }
    };

    const loadVectors = async (
        geoJson: any,
        modifiersList: TacticalModifierData[],
        isVisible = false
    ) => {
        if (!viewer.value) return;
        cachedModifiers = modifiersList;

        if (vectorDataSource) {
            viewer.value.dataSources.remove(vectorDataSource);
        }

        vectorDataSource = await GeoJsonDataSource.load(geoJson, { clampToGround: true });
        for (const entity of vectorDataSource.entities.values) {
            applyEntityStyling(entity, modifiersList);
        }

        vectorDataSource.show = isVisible;
        viewer.value.dataSources.add(vectorDataSource);
        viewer.value.scene.requestRender();
    };

    const reapplyAllStyling = (modifiersList: TacticalModifierData[]) => {
        if (!vectorDataSource) return;
        cachedModifiers = modifiersList;
        for (const entity of vectorDataSource.entities.values) {
            applyEntityStyling(entity, modifiersList);
        }
        viewer.value?.scene.requestRender();
    };

    const highlightCategoryObjects = (osmValue: string | null) => {
        if (!vectorDataSource) return;
        for (const entity of vectorDataSource.entities.values) {
            applyEntityStyling(entity, cachedModifiers, osmValue);
        }
        viewer.value?.scene.requestRender();
    };

    const setVectorsVisible = (show: boolean) => {
        if (vectorDataSource) {
            vectorDataSource.show = show;
            viewer.value?.scene.requestRender();
        }
    };

    const updateEntityProperties = (
        featureId: string,
        patch: FeatureUpdateRequest
    ) => {
        if (!vectorDataSource) return;
        const entity = vectorDataSource.entities.values.find(
            e => (e.properties?.id?.getValue() || e.id) === featureId
        );
        if (!entity || !entity.properties) return;

        if (patch.status) entity.properties.addProperty('status', new ConstantProperty(patch.status));
        if (patch.name !== undefined) entity.properties.addProperty('name', new ConstantProperty(patch.name));
        if (patch.heightMeters !== undefined) entity.properties.addProperty('heightMeters', new ConstantProperty(patch.heightMeters));
        if (patch.widthMeters !== undefined) entity.properties.addProperty('widthMeters', new ConstantProperty(patch.widthMeters));

        applyEntityStyling(entity, cachedModifiers);
        viewer.value?.scene.requestRender();
    };

    const enableFeaturePicking = (
        onFeatureSelected: (properties: any | null) => void
    ) => {
        if (!viewer.value) return;
        if (clickHandler) clickHandler.destroy();

        clickHandler = new ScreenSpaceEventHandler(viewer.value.scene.canvas);
        clickHandler.setInputAction((click: { position: { x: number; y: number } }) => {
            if (!viewer.value) return;
            const pickedObject = viewer.value.scene.pick(click.position);

            if (pickedObject && pickedObject.id instanceof Entity) {
                const entity = pickedObject.id;
                const featureId = entity.properties?.id?.getValue() || entity.id;
                selectedFeatureId.value = featureId;

                reapplyAllStyling(cachedModifiers);

                const propsRaw: Record<string, any> = {};
                if (entity.properties) {
                    const propertyNames = entity.properties.propertyNames;
                    for (const prop of propertyNames) {
                        propsRaw[prop] = entity.properties[prop]?.getValue();
                    }
                }
                propsRaw.id = featureId;
                onFeatureSelected(propsRaw);
            } else {
                selectedFeatureId.value = null;
                reapplyAllStyling(cachedModifiers);
                onFeatureSelected(null);
            }
        }, ScreenSpaceEventType.LEFT_CLICK);
    };

    const disableFeaturePicking = () => {
        if (clickHandler) {
            clickHandler.destroy();
            clickHandler = null;
        }
        selectedFeatureId.value = null;
    };

    const getDefaultColor = (category: string) => {
        switch (category) {
            case 'ROAD': return '#27272a';
            case 'BRIDGE': return '#475569';
            case 'RIVER':
            case 'OPEN_WATER':
            case 'WATER': return '#1e3a5f';
            case 'VEGETATION': return '#36432b';
            case 'BUILDING': return '#64748b';
            default: return '#545744';
        }
    };

    const destroyOverlay = () => {
        disableFeaturePicking();
        if (vectorDataSource && viewer.value) {
            viewer.value.dataSources.remove(vectorDataSource);
            vectorDataSource = null;
        }
    };

    return {
        selectedFeatureId,
        loadVectors,
        setVectorsVisible,
        highlightCategoryObjects,
        reapplyAllStyling,
        updateEntityProperties,
        enableFeaturePicking,
        disableFeaturePicking,
        destroyOverlay
    };
}