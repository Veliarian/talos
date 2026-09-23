import type {
    MapDetailDto,
    MapCreationRequest,
    SurfaceModifierDto,
    TerrainSculptRequest,
    TerrainSculptResponse,
    GeoJsonFeatureCollection, FeatureUpdateRequestDto, DefaultModifierDto
} from './types';

// Relative API path handled transparently via Vite reverse proxy in development
const API_BASE = '/api/maps';

/**
 * REST API client for map ingestion, layer inspection, and terrain operations.
 */
export const mapApi = {
    /**
     * Fetch all available theater maps.
     */
    async getAllMaps(): Promise<MapDetailDto[]> {
        const res = await fetch(API_BASE);
        if (!res.ok) throw new Error('Failed to fetch maps');
        return res.json();
    },

    /**
     * Fetch specific map metadata and layers.
     */
    async getMapById(mapId: string): Promise<MapDetailDto> {
        const res = await fetch(`${API_BASE}/${mapId}`);
        if (!res.ok) throw new Error(`Failed to fetch map details for ID: ${mapId}`);
        return res.json();
    },

    /**
     * Trigger server-side map ingestion pipeline.
     */
    async createMap(request: MapCreationRequest): Promise<{ mapId: string; status: string; message: string }> {
        const res = await fetch(API_BASE, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(request)
        });
        if (!res.ok) throw new Error('Failed to initiate map creation');
        return res.json();
    },

    /**
     * Fetch surface modifiers for a specific map.
     */
    async getModifiers(mapId: string): Promise<SurfaceModifierDto[]> {
        const res = await fetch(`${API_BASE}/${mapId}/modifiers`);
        if (!res.ok) throw new Error(`Failed to fetch surface modifiers for map: ${mapId}`);
        return res.json();
    },

    /**
     * Fetch interactive vector GeoJSON features (roads, buildings, vegetation).
     */
    async getMapVectors(mapId: string): Promise<GeoJsonFeatureCollection> {
        const res = await fetch(`${API_BASE}/${mapId}/vectors`);
        if (!res.ok) throw new Error(`Failed to fetch vector features for map: ${mapId}`);
        return res.json();
    },

    /**
     * Update a tactical surface modifier.
     */
    async updateModifier(mapId: string, modifier: SurfaceModifierDto): Promise<void> {
        const res = await fetch(`${API_BASE}/${mapId}/modifiers/${modifier.id}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(modifier)
        });
        if (!res.ok) throw new Error(`Failed to update surface modifier: ${modifier.id}`);
    },

    /**
     * Sculpt elevation terrain raster (dig trenches, raise berms, flatten surfaces).
     */
    async sculptTerrain(mapId: string, request: TerrainSculptRequest): Promise<TerrainSculptResponse> {
        const res = await fetch(`${API_BASE}/${mapId}/terrain/sculpt`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(request)
        });
        if (!res.ok) throw new Error(`Failed to apply terrain sculpt operation: ${request.operation}`);
        return res.json();
    },

    /**
     * Delete map from database and wipe all local tile and DEM files from disk.
     */
    async deleteMap(mapId: string): Promise<void> {
        const res = await fetch(`${API_BASE}/${mapId}`, {
            method: 'DELETE'
        });
        if (!res.ok) throw new Error(`Failed to delete map: ${mapId}`);
    },

    /**
     * Update an individual feature (status, local TTX overrides, notes).
     */
    async updateFeature(mapId: string, featureId: string, payload: FeatureUpdateRequestDto): Promise<void> {
        const res = await fetch(`${API_BASE}/${mapId}/features/${featureId}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        if (!res.ok) throw new Error(`Failed to update feature: ${featureId}`);
    },

    /**
     * Fetch all global doctrine templates from library.
     */
    async getTemplates(): Promise<DefaultModifierDto[]> {
        const res = await fetch(`${API_BASE}/templates`);
        if (!res.ok) throw new Error('Failed to fetch global surface templates');
        return res.json();
    },

    /**
     * Create a new global template in doctrine catalog.
     */
    async createTemplate(dto: Partial<DefaultModifierDto>): Promise<DefaultModifierDto> {
        const res = await fetch(`${API_BASE}/templates`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(dto)
        });
        if (!res.ok) throw new Error('Failed to create template');
        return res.json();
    },

    /**
     * Update an existing global template.
     */
    async updateTemplate(id: string, dto: DefaultModifierDto): Promise<DefaultModifierDto> {
        const res = await fetch(`${API_BASE}/templates/${id}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(dto)
        });
        if (!res.ok) throw new Error(`Failed to update template ${id}`);
        return res.json();
    },

    /**
     * Delete a global template from library.
     */
    async deleteTemplate(id: string): Promise<void> {
        const res = await fetch(`${API_BASE}/templates/${id}`, {
            method: 'DELETE'
        });
        if (!res.ok) throw new Error(`Failed to delete template ${id}`);
    },

    /**
     * Apply a global template to a specific theater map.
     */
    async applyTemplateToMap(mapId: string, templateId: string): Promise<void> {
        const res = await fetch(`${API_BASE}/${mapId}/apply-template/${templateId}`, {
            method: 'POST'
        });
        if (!res.ok) throw new Error(`Failed to apply template ${templateId} to map ${mapId}`);
    }
};