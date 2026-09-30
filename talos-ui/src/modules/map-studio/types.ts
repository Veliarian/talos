// types.ts
// Domain Types and Contracts for TALOS Tactical GIS Engine

export type LayerType = 'SATELLITE' | 'TOPOGRAPHIC' | 'TACTICAL';
export type MapStatus = 'CREATED' | 'DOWNLOADING' | 'READY' | 'FAILED';
export type SculptOperation = 'DIG' | 'RAISE' | 'FLATTEN';
export type FeatureStatus = 'OPERATIONAL' | 'DESTROYED' | 'MINED' | 'CHECKPOINT';
export type SculptMode = 'RADIAL' | 'LINEAR';
export type LinearSculptOperation = 'TRENCH_DIG' | 'AT_DITCH' | 'BERM_RAISE';

export type ModifierCategory =
    | 'ROAD'
    | 'BRIDGE'
    | 'RIVER'
    | 'OPEN_WATER'
    | 'VEGETATION'
    | 'BUILDING'
    | 'SOIL';

// Category-specific properties
export interface RoadProps {
    surfaceType: string;
    widthMeters: number;
    lanesCount: number;
    isOneWay: boolean;
    infantryOnly: boolean;
    coverDefensePercent: number;
}

export interface BridgeProps {
    bridgeType: string;
    maxWeightTons: number;
    widthMeters: number;
    lengthMeters: number;
    lanesCount: number;
    coverDefensePercent: number;
    destructionState: string;
}

export interface RiverProps {
    waterwayType: string;
    depthMeters: number;
    widthMeters: number;
    flowSpeedMps: number;
    flowDirectionDegrees: number;
    bottomType: string;
    isFordable: boolean;
    maxFordDepthMeters: number;
    speedModifierAmphibious: number;
}

export interface OpenWaterProps {
    waterBodyType: string;
    depthMeters: number;
    bottomType: string;
    reedBeltWidthMeters: number;
    speedModifierAmphibious: number;
    iceCoverState: string;
}

export interface VegetationProps {
    vegetationType: string;
    heightMeters: number;
    stemDiameterCm: number;
    densityPercent: number;
    visibilityMeters: number | null;
    coverDefensePercent: number;
    speedModifierInfantry: number;
}

export interface BuildingProps {
    buildingType: string;
    structureMaterial: string;
    buildingLevels: number;
    heightMeters: number;
    coverDefensePercent: number;
    canEnterUnits: boolean;
    roofType: string;
}

export interface SoilProps {
    soilType: string;
    bearingCapacity: string;
    dustGeneration: string;
    coverDefensePercent: number;
}

export interface SurfaceTemplateResponse {
    id: string;
    category: ModifierCategory;
    osmKey: string;
    osmValue: string;
    description: string;
    movementPriority: number;
    color2d: string;
    texture3d: string;
    speedModifierWheeled: number;
    speedModifierTracked: number;
    visibilityMeters: number | null;
    coverDefensePercent: number;
    propertiesJson?: string | null;
}

export interface SurfaceTemplateRequest {
    category: ModifierCategory;
    osmKey: string;
    osmValue: string;
    description: string;
    movementPriority: number;
    color2d: string;
    texture3d: string;
    speedModifierWheeled: number;
    speedModifierTracked: number;
    visibilityMeters: number | null;
    coverDefensePercent: number;
    propertiesJson: string;
}

export interface TacticalModifierData {
    id: string;
    category: ModifierCategory;
    osmKey: string;
    osmValue: string;
    description: string;
    movementPriority: number;
    color2d: string;
    texture3d: string;
    speedModifierWheeled: number;
    speedModifierTracked: number;
    visibilityMeters: number | null;
    coverDefensePercent: number;
    propertiesJson?: string | null;
}

export interface MapLayerData {
    id: string;
    layerType: LayerType;
    minZoom: number;
    maxZoom: number;
    isDefault: boolean;
    status: MapStatus;
}

export interface MapDetailResponse {
    id: string;
    name: string;
    description: string;
    minLat: number;
    maxLat: number;
    minLon: number;
    maxLon: number;
    centerLat: number;
    centerLon: number;
    sizeKm: number;
    status: MapStatus;
    createdAt: string;
    layers: MapLayerData[];
}

export interface MapCreationRequest {
    name: string;
    description: string;
    centerLat: number;
    centerLon: number;
    sizeKm: number;
    layerTypes: string[];
    minZoom?: number;
    maxZoom?: number;
}

export interface FeatureUpdateRequest {
    name?: string;
    status: FeatureStatus;
    speedModifierOverrideWheeled?: number | null;
    speedModifierOverrideTracked?: number | null;
    visibilityOverride?: number | null;
    coverDefenseOverride?: number | null;
    customNotes?: string;
    heightMeters?: number | null;
    widthMeters?: number | null;
}

export interface TerrainSculptRequest {
    centerLat: number;
    centerLon: number;
    radiusMeters: number;
    operation: SculptOperation;
    deltaMeters: number;
}

export interface TerrainSculptResponse {
    status: string;
    operation: SculptOperation;
    newElevationAtCenter: number;
}

export interface GeoJsonFeatureProperties {
    id: string;
    osmId?: number | null;
    category: string;
    typeKey: string;
    typeValue: string;
    name: string;
    status?: FeatureStatus;
    isCustomModified?: boolean;
    speedOverrideWheeled?: number | null;
    speedOverrideTracked?: number | null;
    visibilityOverride?: number | null;
    coverOverride?: number | null;
    heightMeters?: number | null;
    widthMeters?: number | null;
    customNotes?: string;
}

export interface GeoJsonFeatureCollection {
    type: 'FeatureCollection';
    features: Array<{
        type: 'Feature';
        geometry: {
            type: string;
            coordinates: unknown;
        };
        properties: GeoJsonFeatureProperties;
    }>;
}

export interface LineTerrainSculptRequest {
    startLat: number;
    startLon: number;
    endLat: number;
    endLon: number;
    widthMeters: number;
    deltaMeters: number;
    operation: LinearSculptOperation;
}

export interface TerrainHistoryStatus {
    canUndo: boolean;
    canRedo: boolean;
}

// Backward compatibility aliases during transition
export type MapDetailDto = MapDetailResponse;
export type MapLayerDto = MapLayerData;
export type FeatureUpdateRequestDto = FeatureUpdateRequest;
export type SurfaceModifierDto = TacticalModifierData;
export type DefaultModifierDto = TacticalModifierData;