import { ref } from 'vue';
import {
    Cartesian3,
    Color,
    CallbackProperty
} from 'cesium';
import type { MapDetailDto, TacticalModifierData } from '../types';
import { useCesiumViewer } from './useCesiumViewer';
import { useTerrainElevation } from './useTerrainElevation';
import { useBasemapLayers } from './useBasemapLayers';
import { useVectorOverlay } from './useVectorOverlay';
import { useSurfaceCamera } from './useSurfaceCamera';

export function useEditorMap(map: MapDetailDto) {
    const activeViewMode = ref<'ELEVATION' | 'OBJECTS'>('ELEVATION');

    const lineStartCartesian = ref<Cartesian3 | null>(null);
    const currentMouseCartesian = ref<Cartesian3 | null>(null);

    let cachedGeoJson: any = null;
    let cachedModifiers: TacticalModifierData[] = [];

    // 1. Viewer instance & camera
    const { viewer, is3DMode, initViewerInstance, fitCamera: fitCameraBase, destroyViewer: destroyViewerBase } = useCesiumViewer(map);

    // 2. Terrain & elevation (DEM)
    const {
        centerAltitudeDisplay,
        mountTerrainProvider,
        applyElevationHeatmap,
        clearElevationHeatmap,
        invalidateTerrainCache
    } = useTerrainElevation(viewer, map);

    // 3. Basemaps
    const {
        currentLayerType,
        isBasemapVisible,
        elevationOpacity,
        objectsOpacity,
        mountBaseLayer,
        updateImageryAppearance
    } = useBasemapLayers(viewer, map);

    // 4. Vector Overlay with true Ground Clamping
    const {
        loadVectors: loadVectorsInternal,
        setVectorsVisible,
        highlightCategoryObjects: highlightCategoryInternal,
        reapplyAllStyling: reapplyAllInternal
    } = useVectorOverlay(viewer);

    // 5. Surface Camera (WASD)
    const { isGroundMode, toggleGroundMode, disableGroundMode } = useSurfaceCamera(viewer);

    const initViewer = (container: HTMLDivElement) => {
        const v = initViewerInstance(container);
        mountTerrainProvider();

        v.entities.add({
            name: 'Linear Trench Preview',
            polyline: {
                positions: new CallbackProperty(() => {
                    if (lineStartCartesian.value && currentMouseCartesian.value) {
                        return [lineStartCartesian.value, currentMouseCartesian.value];
                    }
                    return [];
                }, false),
                width: 4,
                material: Color.fromCssColorString('#ef4444'),
                clampToGround: true
            }
        });

        fitCamera(false);
    };

    const fitCamera = (is3d: boolean) => {
        if (!viewer.value) return;
        if (isGroundMode.value) {
            disableGroundMode();
        }

        is3DMode.value = is3d;
        fitCameraBase(is3d);

        // Update building extrusion for 2D flat or 3D volumetric mode
        if (activeViewMode.value === 'OBJECTS') {
            reapplyAllInternal(cachedModifiers, is3d);
        }
    };

    const setViewMode = (mode: 'ELEVATION' | 'OBJECTS', modifiersList: TacticalModifierData[] = []) => {
        if (!viewer.value) return;
        activeViewMode.value = mode;
        cachedModifiers = modifiersList;
        const v = viewer.value;

        if (mode === 'ELEVATION') {
            setVectorsVisible(false);
            applyElevationHeatmap(1.0 - elevationOpacity.value);

            v.scene.globe.show = false;
            v.scene.globe.show = true;

            updateImageryAppearance('ELEVATION');
            v.scene.requestRender();
            return;
        }

        if (mode === 'OBJECTS') {
            clearElevationHeatmap();
            v.scene.globe.baseColor = Color.fromCssColorString('#d4c5a9'); // Pure Tactical Sand

            v.scene.globe.show = false;
            v.scene.globe.show = true;

            setVectorsVisible(true);
            updateImageryAppearance('OBJECTS');
            reapplyAllInternal(modifiersList, is3DMode.value);
            v.scene.requestRender();
        }
    };

    const setElevationOpacity = (val: number) => {
        elevationOpacity.value = val;
        applyElevationHeatmap(1.0 - val);
        updateImageryAppearance('ELEVATION');
        if (viewer.value) viewer.value.scene.requestRender();
    };

    const setObjectsOpacity = (val: number) => {
        objectsOpacity.value = val;
        updateImageryAppearance('OBJECTS');
        if (viewer.value) viewer.value.scene.requestRender();
    };

    const loadVectors = async (geoJson: any, modifiersList: TacticalModifierData[]) => {
        cachedGeoJson = geoJson;
        cachedModifiers = modifiersList;
        await loadVectorsInternal(geoJson, modifiersList, is3DMode.value, activeViewMode.value === 'OBJECTS');
    };

    const reapplyAllStyling = (modifiersList: TacticalModifierData[]) => {
        cachedModifiers = modifiersList;
        reapplyAllInternal(modifiersList, is3DMode.value);
    };

    const highlightCategoryObjects = (osmValue: string | null) => {
        highlightCategoryInternal(osmValue, is3DMode.value);
    };

    const destroyViewer = () => {
        disableGroundMode();
        destroyViewerBase();
    };

    return {
        viewer,
        is3DMode,
        isGroundMode,
        toggleGroundMode,
        centerAltitudeDisplay,
        currentLayerType,
        isBasemapVisible,
        elevationOpacity,
        objectsOpacity,
        lineStartCartesian,
        currentMouseCartesian,
        initViewer,
        fitCamera,
        mountBaseLayer,
        setElevationOpacity,
        setObjectsOpacity,
        setViewMode,
        loadVectors,
        reapplyAllStyling,
        highlightCategoryObjects,
        invalidateTerrainCache,
        destroyViewer
    };
}