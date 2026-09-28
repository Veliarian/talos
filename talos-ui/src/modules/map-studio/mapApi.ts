import type {
    MapDetailDto,
    MapCreationRequest,
    SurfaceModifierDto,
    TerrainSculptRequest,
    TerrainSculptResponse,
    GeoJsonFeatureCollection,
    FeatureUpdateRequestDto,
    TerrainHistoryStatus,
    LineTerrainSculptRequest,
    SurfaceTemplateResponse,
    SurfaceTemplateRequest, TacticalModifierData
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
     * Sculpt terrain along a linear vector trajectory (A -> B).
     */
    async sculptTerrainLine(mapId: string, request: LineTerrainSculptRequest): Promise<{ status: string }> {
        const res = await fetch(`${API_BASE}/${mapId}/terrain/sculpt-line`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(request)
        });
        if (!res.ok) throw new Error('Failed to execute linear terrain sculpt');
        return res.json();
    },

    /**
     * Undo last elevation modification.
     */
    async undoTerrain(mapId: string): Promise<void> {
        const res = await fetch(`${API_BASE}/${mapId}/terrain/undo`, {
            method: 'POST'
        });
        if (!res.ok) throw new Error('Failed to undo elevation sculpt');
    },

    /**
     * Redo last reverted elevation modification.
     */
    async redoTerrain(mapId: string): Promise<void> {
        const res = await fetch(`${API_BASE}/${mapId}/terrain/redo`, {
            method: 'POST'
        });
        if (!res.ok) throw new Error('Failed to redo elevation sculpt');
    },

    /**
     * Fetch active Undo/Redo availability status.
     */
    async getTerrainHistoryStatus(mapId: string): Promise<TerrainHistoryStatus> {
        const res = await fetch(`${API_BASE}/${mapId}/terrain/history-status`);
        if (!res.ok) return { canUndo: false, canRedo: false };
        return res.json();
    },

    /**
     * Reorders template priorities (orderedIds: index 0 is highest priority).
     */
    async reorderTemplates(orderedIds: string[]): Promise<void> {
        const res = await fetch(`${API_BASE}/templates/reorder`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ orderedIds })
        });
        if (!res.ok) throw new Error('Failed to reorder templates');
    },

    /**
     * Promotes an object type discovered on a specific map into the Master Doctrine Catalog.
     */
    async promoteModifierToTemplate(mapId: string, modifierId: string): Promise<SurfaceTemplateResponse> {
        const res = await fetch(`${API_BASE}/${mapId}/modifiers/${modifierId}/promote`, {
            method: 'POST'
        });
        if (!res.ok) throw new Error('Failed to promote modifier to master template');
        return res.json();
    },

    /**
     * Auto-sync all features on a map with matching global templates.
     */
    async syncMapTemplates(mapId: string): Promise<{ syncedCount: number }> {
        const res = await fetch(`${API_BASE}/${mapId}/sync-templates`, { method: 'POST' });
        if (!res.ok) throw new Error('Failed to auto-sync templates with map');
        return res.json();
    },

    async getTemplates(): Promise<SurfaceTemplateResponse[]> {
        const res = await fetch(`${API_BASE}/templates`);
        if (!res.ok) throw new Error('Failed to fetch templates');
        return res.json();
    },

    async createTemplate(payload: SurfaceTemplateRequest): Promise<SurfaceTemplateResponse> {
        const res = await fetch(`${API_BASE}/templates`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        if (!res.ok) throw new Error('Failed to create template');
        return res.json();
    },

    async updateTemplate(id: string, payload: SurfaceTemplateRequest): Promise<SurfaceTemplateResponse> {
        const res = await fetch(`${API_BASE}/templates/${id}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        if (!res.ok) throw new Error(`Failed to update template: ${id}`);
        return res.json();
    },

    async deleteTemplate(id: string): Promise<void> {
        const res = await fetch(`${API_BASE}/templates/${id}`, { method: 'DELETE' });
        if (!res.ok) throw new Error(`Failed to delete template: ${id}`);
    },

    async getModifiers(mapId: string): Promise<TacticalModifierData[]> {
        const res = await fetch(`${API_BASE}/${mapId}/modifiers`);
        if (!res.ok) throw new Error(`Failed to fetch modifiers for map: ${mapId}`);
        return res.json();
    },

    async applyTemplateToMap(mapId: string, modifierId: string, templateId: string): Promise<void> {
        const res = await fetch(`${API_BASE}/${mapId}/modifiers/${modifierId}/apply-template/${templateId}`, {
            method: 'POST'
        });
        if (!res.ok) throw new Error(`Failed to apply template: ${templateId}`);
    },
};