export type LayerType = 'SATELLITE' | 'TOPOGRAPHIC' | 'TACTICAL';
export type MapStatus = 'CREATED' | 'DOWNLOADING' | 'READY' | 'FAILED';
export type ModifierCategory = 'ROAD' | 'VEGETATION' | 'WATER' | 'SOIL' | 'BUILDING' | 'FORTIFICATION';
export type SculptOperation = 'DIG' | 'RAISE' | 'FLATTEN';
export type FeatureStatus = 'OPERATIONAL' | 'DESTROYED' | 'MINED' | 'CHECKPOINT';

export interface MapLayerDto {
    id: string;
    layerType: LayerType;
    minZoom: number;
    maxZoom: number;
    isDefault: boolean;
    status: MapStatus;
}

export interface MapDetailDto {
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
    layers: MapLayerDto[];
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

export interface SurfaceModifierDto {
    id: string;
    category: ModifierCategory;
    osmKey: string;
    osmValue: string;
    description: string;
    speedModifierWheeled: number;
    speedModifierTracked: number;
    visibilityMeters: number | null;
    coverDefensePercent: number;
}

export interface FeatureUpdateRequestDto {
    name?: string;
    status: FeatureStatus;
    speedModifierOverrideWheeled: number | null;
    speedModifierOverrideTracked: number | null;
    visibilityOverride: number | null;
    coverDefenseOverride: number | null;
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

export interface DefaultModifierDto {
    id: string;
    category: ModifierCategory;
    osmKey: string;
    osmValue: string;
    description: string;
    speedModifierWheeled: number;
    speedModifierTracked: number;
    visibilityMeters: number | null;
    coverDefensePercent: number;
}