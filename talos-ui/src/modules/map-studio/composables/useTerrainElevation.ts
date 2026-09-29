import { ref, type ShallowRef } from 'vue';
import {
    Viewer,
    CustomHeightmapTerrainProvider,
    GeographicTilingScheme,
    Math as CesiumMath,
    Color,
    createElevationBandMaterial,
    Material
} from 'cesium';
import type { MapDetailDto } from '../types';

export function useTerrainElevation(viewer: ShallowRef<Viewer | null>, map: MapDetailDto) {
    const centerAltitudeDisplay = ref(120);
    const geoTilingScheme = new GeographicTilingScheme();
    const terrainGridCache = new Map<string, Float32Array>();

    const createLocalTerrainProvider = () => {
        return new CustomHeightmapTerrainProvider({
            width: 64,
            height: 64,
            tilingScheme: geoTilingScheme,
            callback: async (x: number, y: number, level: number) => {
                const cacheKey = `${level}_${x}_${y}`;
                if (terrainGridCache.has(cacheKey)) {
                    return terrainGridCache.get(cacheKey)!;
                }

                const rect = geoTilingScheme.tileXYToRectangle(x, y, level);
                const minLat = CesiumMath.toDegrees(rect.south);
                const maxLat = CesiumMath.toDegrees(rect.north);
                const minLon = CesiumMath.toDegrees(rect.west);
                const maxLon = CesiumMath.toDegrees(rect.east);

                const url = `/api/maps/${map.id}/terrain/grid?minLat=${minLat}&maxLat=${maxLat}&minLon=${minLon}&maxLon=${maxLon}&width=64&height=64`;

                try {
                    const response = await fetch(url);
                    if (!response.ok) return new Float32Array(64 * 64).fill(120.0);
                    const buffer = await response.arrayBuffer();
                    const floatArray = new Float32Array(buffer);
                    terrainGridCache.set(cacheKey, floatArray);
                    centerAltitudeDisplay.value = Math.round(floatArray[2048] || 120.0);
                    return floatArray;
                } catch {
                    return new Float32Array(64 * 64).fill(120.0);
                }
            }
        });
    };

    const mountTerrainProvider = () => {
        if (!viewer.value) return;
        viewer.value.terrainProvider = createLocalTerrainProvider();
    };

    /**
     * Applies hypsometric elevation bands with adjustable alpha (allows satellite underlay to shine through).
     */
    const applyElevationHeatmap = (alpha = 1.0) => {
        if (!viewer.value) return;
        const clampedAlpha = Math.max(0.0, Math.min(1.0, alpha));

        // If heatmap is completely transparent, remove material so only satellite is rendered
        if (clampedAlpha < 0.02) {
            viewer.value.scene.globe.material = undefined as unknown as Material;
            return;
        }

        viewer.value.scene.globe.material = createElevationBandMaterial({
            scene: viewer.value.scene,
            layers: [{
                entries: [
                    { height: 0.0, color: Color.fromCssColorString('#022c22').withAlpha(clampedAlpha) },
                    { height: 80.0, color: Color.fromCssColorString('#047857').withAlpha(clampedAlpha) },
                    { height: 140.0, color: Color.fromCssColorString('#059669').withAlpha(clampedAlpha) },
                    { height: 220.0, color: Color.fromCssColorString('#10b981').withAlpha(clampedAlpha) },
                    { height: 320.0, color: Color.fromCssColorString('#84cc16').withAlpha(clampedAlpha) },
                    { height: 460.0, color: Color.fromCssColorString('#facc15').withAlpha(clampedAlpha) },
                    { height: 650.0, color: Color.fromCssColorString('#f97316').withAlpha(clampedAlpha) },
                    { height: 900.0, color: Color.fromCssColorString('#dc2626').withAlpha(clampedAlpha) },
                    { height: 1200.0, color: Color.fromCssColorString('#78350f').withAlpha(clampedAlpha) },
                    { height: 1600.0, color: Color.fromCssColorString('#f8fafc').withAlpha(clampedAlpha) }
                ]
            }]
        });
    };

    const clearElevationHeatmap = () => {
        if (!viewer.value) return;
        viewer.value.scene.globe.material = undefined as unknown as Material;
    };

    const invalidateTerrainCache = () => {
        terrainGridCache.clear();
        if (viewer.value) {
            viewer.value.terrainProvider = createLocalTerrainProvider();
        }
    };

    return {
        centerAltitudeDisplay,
        createLocalTerrainProvider,
        mountTerrainProvider,
        applyElevationHeatmap,
        clearElevationHeatmap,
        invalidateTerrainCache
    };
}