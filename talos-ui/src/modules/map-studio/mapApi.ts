import type { MapDetailDto, MapCreationRequest, SurfaceModifierDto } from './types';

const API_BASE = 'http://localhost:8080/api/maps';

/**
 * REST API client for map operations
 */
export const mapApi = {
    // Fetch all available local maps
    async getAllMaps(): Promise<MapDetailDto[]> {
        const res = await fetch(API_BASE);
        if (!res.ok) throw new Error('Failed to fetch maps');
        return res.json();
    },

    // Fetch specific map details
    async getMapById(mapId: string): Promise<MapDetailDto> {
        const res = await fetch(`${API_BASE}/${mapId}`);
        if (!res.ok) throw new Error('Failed to fetch map details');
        return res.json();
    },

    // Trigger server-side map ingestion
    async createMap(request: MapCreationRequest): Promise<{ mapId: string; status: string }> {
        const res = await fetch(API_BASE, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(request)
        });
        if (!res.ok) throw new Error('Failed to create map');
        return res.json();
    },

    // Fetch surface modifiers for a map
    async getModifiers(mapId: string): Promise<SurfaceModifierDto[]> {
        const res = await fetch(`${API_BASE}/${mapId}/modifiers`);
        if (!res.ok) throw new Error('Failed to fetch surface modifiers');
        return res.json();
    },

    // Fetch interactive vector GeoJSON features for this map
    async getMapVectors(mapId: string): Promise<any> {
        const res = await fetch(`${API_BASE}/${mapId}/vectors`);
        if (!res.ok) throw new Error('Failed to fetch vector features');
        return res.json();
    },

    // Update a specific modifier
    async updateModifier(mapId: string, modifier: SurfaceModifierDto): Promise<void> {
        const res = await fetch(`${API_BASE}/${mapId}/modifiers/${modifier.id}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(modifier)
        });
        if (!res.ok) throw new Error('Failed to update modifier');
    },

    // Sculpt elevation terrain raster (dig, raise, flatten)
    async sculptTerrain(mapId: string, req: {
        centerLat: number;
        centerLon: number;
        radiusMeters: number;
        operation: 'DIG' | 'RAISE' | 'FLATTEN';
        deltaMeters: number;
    }): Promise<any> {
        const res = await fetch(`${API_BASE}/${mapId}/terrain/sculpt`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(req)
        });
        if (!res.ok) throw new Error('Failed to sculpt terrain');
        return res.json();
    },

    // Delete map from database and wipe all its local files
    async deleteMap(mapId: string): Promise<void> {
        const res = await fetch(`${API_BASE}/${mapId}`, {
            method: 'DELETE'
        });
        if (!res.ok) throw new Error('Failed to delete map');
    }
};