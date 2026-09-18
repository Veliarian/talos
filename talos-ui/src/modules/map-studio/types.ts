export interface MapLayerDto {
    id: string;
    layerType: 'SATELLITE' | 'TOPOGRAPHIC' | 'TACTICAL';
    minZoom: number;
    maxZoom: number;
    isDefault: boolean;
    status: 'DOWNLOADING' | 'READY' | 'FAILED';
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
    status: 'CREATED' | 'DOWNLOADING' | 'READY' | 'FAILED';
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
    minZoom: number;
    maxZoom: number;
}

export interface SurfaceModifierDto {
    id: string;
    category: 'ROAD' | 'VEGETATION' | 'WATER' | 'SOIL' | 'BUILDING';
    osmKey: string;
    osmValue: string;
    description: string;
    speedModifierWheeled: number;
    speedModifierTracked: number;
    visibilityMeters: number | null;
    coverDefensePercent: number;
}