<template>
    <div class="editor-workspace">
        <!-- 1. Top Header with 2 Workspace Tabs -->
        <EditorHeader
            :map-name="map.name"
            :size-km="map.sizeKm"
            :active-tab="activeTab"
            @back="$emit('back')"
            @select-tab="onSelectWorkspaceTab"
        />

        <div class="editor-body">
            <!-- 2. Main Viewport Area -->
            <div class="map-viewport">
                <div ref="canvasContainer" class="cesium-map-canvas"></div>

                <!-- Feature Inspector Slide-up Drawer -->
                <FeatureInspectorDrawer
                    v-if="inspectedFeature && activeTab === 'OBJECTS'"
                    :feature="inspectedFeature"
                    :available-templates="availableTemplates"
                    @close="inspectedFeature = null"
                    @save="saveCurrentFeature"
                    @apply-template="applyTemplateToFeature"
                />
            </div>

            <!-- 3. Focused Right Dock -->
            <aside class="inspector-sidebar">
                <!-- Panel 1: Elevation Studio -->
                <ElevationStudioPanel
                    v-if="activeTab === 'ELEVATION'"
                    v-model:tool-mode="elevationToolMode"
                    v-model:radial-op="radialOp"
                    v-model:linear-op="linearOp"
                    v-model:brush-radius="brushRadius"
                    v-model:line-width="lineWidth"
                    v-model:delta-height="deltaHeight"
                    :history-status="historyStatus"
                    :is-drawing-line="lineStartCartesian !== null"
                    :is-processing="isProcessing"
                    :layers="map.layers"
                    :current-layer-type="currentLayerType"
                    :basemap-opacity="elevationOpacity"
                    @undo="handleUndo"
                    @redo="handleRedo"
                    @cancel-line="lineStartCartesian = null"
                    @select-layer="onSelectBasemapLayer"
                    @update:opacity="onUpdateElevationOpacity"
                />

                <!-- Panel 2: Tactical Objects -->
                <VectorStudioPanel
                    v-else-if="activeTab === 'OBJECTS'"
                    :modifiers="modifiers"
                    :available-templates="availableTemplates"
                    :active-highlight-type="activeHighlightType"
                    :layers="map.layers"
                    :current-layer-type="currentLayerType"
                    :basemap-opacity="objectsOpacity"
                    @highlight="highlightObjectsOnMap"
                    @apply-template="applyTemplateToCategory"
                    @promote-to-template="handlePromoteToTemplate"
                    @sync-all="handleSyncAllTemplates"
                    @select-layer="onSelectBasemapLayer"
                    @update:opacity="onUpdateObjectsOpacity"
                />
            </aside>
        </div>

        <!-- 4. Bottom Tactical Status Bar -->
        <EditorStatusBar
            :cursor-coords="cursorCoordsDisplay"
            :cursor-mgrs="cursorMgrsDisplay"
            :cursor-elevation="cursorElevationDisplay"
            :is-3-d-mode="is3DMode"
            :is-ground-mode="isGroundMode"
            :tool-mode="activeTab === 'ELEVATION' ? elevationToolMode : 'INSPECT'"
            :is-drawing-line="lineStartCartesian !== null"
            @toggle-perspective="fitCamera(!is3DMode)"
            @toggle-ground-mode="toggleGroundMode"
        />
    </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue';
import {
    ScreenSpaceEventHandler,
    ScreenSpaceEventType,
    Cartographic,
    Math as CesiumMath,
    Cartesian2,
    defined
} from 'cesium';
import { mapApi } from '../mapApi';
import { coordConverter } from '@/shared/utils/coordConverter';
import type {
    MapDetailDto,
    SculptOperation,
    LinearSculptOperation,
    TerrainHistoryStatus,
    TacticalModifierData,
    SurfaceTemplateResponse,
    FeatureStatus
} from '../types';
import { useEditorMap } from '../composables/useEditorMap';

import EditorHeader, { type EditorWorkspaceTab } from '../components/editor/EditorHeader.vue';
import EditorStatusBar from '../components/editor/EditorStatusBar.vue';
import ElevationStudioPanel from '../components/editor/ElevationStudioPanel.vue';
import VectorStudioPanel from '../components/editor/VectorStudioPanel.vue';
import FeatureInspectorDrawer from '../components/editor/FeatureInspectorDrawer.vue';

const props = defineProps<{ map: MapDetailDto }>();
defineEmits<{ (e: 'back'): void }>();

const canvasContainer = ref<HTMLDivElement | null>(null);
const activeTab = ref<EditorWorkspaceTab>('ELEVATION');

// Elevation Sculpting State
const elevationToolMode = ref<'NAVIGATE' | 'RADIAL' | 'LINEAR'>('NAVIGATE');
const radialOp = ref<SculptOperation>('DIG');
const linearOp = ref<LinearSculptOperation>('TRENCH_DIG');
const brushRadius = ref(80);
const lineWidth = ref(6);
const deltaHeight = ref(3);
const isProcessing = ref(false);

const historyStatus = ref<TerrainHistoryStatus>({ canUndo: false, canRedo: false });
const modifiers = ref<TacticalModifierData[]>([]);
const availableTemplates = ref<SurfaceTemplateResponse[]>([]);
const inspectedFeature = ref<any>(null);
const activeHighlightType = ref<string | null>(null);

// Status bar tracking
const cursorCoordsDisplay = ref('—');
const cursorMgrsDisplay = ref('—');
const cursorElevationDisplay = ref(120);

const {
    viewer,
    is3DMode,
    isGroundMode,
    toggleGroundMode,
    centerAltitudeDisplay,
    currentLayerType,
    elevationOpacity,
    objectsOpacity,
    lineStartCartesian,
    currentMouseCartesian,
    cachedGeoJson,
    initViewer,
    fitCamera,
    mountBaseLayer,
    setElevationOpacity,
    setObjectsOpacity,
    setViewMode,
    loadVectors,
    reapplyAllStyling,
    invalidateTerrainCache,
    highlightCategoryObjects,
    destroyViewer
} = useEditorMap(props.map);

let clickHandler: ScreenSpaceEventHandler | null = null;

const onSelectWorkspaceTab = (tab: EditorWorkspaceTab) => {
    activeTab.value = tab;
    setViewMode(tab, modifiers.value);
};

const onSelectBasemapLayer = (type: string) => {
    currentLayerType.value = type;
    mountBaseLayer(type);
};

const onUpdateElevationOpacity = (val: number) => {
    setElevationOpacity(val);
};

const onUpdateObjectsOpacity = (val: number) => {
    setObjectsOpacity(val);
};

const refreshHistory = async () => {
    historyStatus.value = await mapApi.getTerrainHistoryStatus(props.map.id);
};

const handleUndo = async () => {
    try {
        isProcessing.value = true;
        await mapApi.undoTerrain(props.map.id);
        invalidateTerrainCache();
        await refreshHistory();
    } finally {
        isProcessing.value = false;
    }
};

const handleRedo = async () => {
    try {
        isProcessing.value = true;
        await mapApi.redoTerrain(props.map.id);
        invalidateTerrainCache();
        await refreshHistory();
    } finally {
        isProcessing.value = false;
    }
};

onMounted(async () => {
    if (!canvasContainer.value) return;
    initViewer(canvasContainer.value);

    mountBaseLayer(currentLayerType.value);

    try {
        modifiers.value = await mapApi.getModifiers(props.map.id);
        availableTemplates.value = await mapApi.getTemplates();
        const vectors = await mapApi.getMapVectors(props.map.id);
        await loadVectors(vectors, modifiers.value);
    } catch (err) {
        console.warn('Initial data load error:', err);
    }

    onSelectWorkspaceTab('ELEVATION');
    setupInteractions();
    await refreshHistory();
});

const highlightObjectsOnMap = (mod: TacticalModifierData) => {
    if (activeHighlightType.value === mod.osmValue) {
        activeHighlightType.value = null;
        highlightCategoryObjects(null);
    } else {
        activeHighlightType.value = mod.osmValue;
        highlightCategoryObjects(mod.osmValue);
    }
};

const setupInteractions = () => {
    const v = viewer.value;
    if (!v) return;

    clickHandler = new ScreenSpaceEventHandler(v.scene.canvas);

    // Mouse Move: Track cursor coordinates & status bar
    clickHandler.setInputAction((movement: { endPosition: Cartesian2 }) => {
        const currentViewer = viewer.value;
        if (!currentViewer) return;

        const ray = currentViewer.camera.getPickRay(movement.endPosition);
        if (!ray) return;
        const cartesian = currentViewer.scene.globe.pick(ray, currentViewer.scene);
        if (cartesian) {
            currentMouseCartesian.value = cartesian;
            const carto = Cartographic.fromCartesian(cartesian);
            const lat = Number(CesiumMath.toDegrees(carto.latitude).toFixed(4));
            const lon = Number(CesiumMath.toDegrees(carto.longitude).toFixed(4));

            cursorCoordsDisplay.value = `${Math.abs(lat).toFixed(4)}°${lat >= 0 ? 'N' : 'S'}, ${Math.abs(lon).toFixed(4)}°${lon >= 0 ? 'E' : 'W'}`;
            cursorMgrsDisplay.value = coordConverter.toMgrsEstimate(lat, lon);
            cursorElevationDisplay.value = Math.round(carto.height);
        }
    }, ScreenSpaceEventType.MOUSE_MOVE);

    // Left Click: High-speed Batched Picking or Sculpting Actions
    clickHandler.setInputAction(async (event: { position: Cartesian2 }) => {
        const currentViewer = viewer.value;
        if (!currentViewer) return;

        // 1. Objects Workspace: Fast Raycast Picking on Batched Primitives
        if (activeTab.value === 'OBJECTS') {
            const picked = currentViewer.scene.pick(event.position);
            if (defined(picked) && picked.id) {
                const featureId = typeof picked.id === 'string' ? picked.id : picked.id.id;
                const geoJson = cachedGeoJson();
                const matchedFeature = geoJson?.features?.find((f: any) => f.properties?.id === featureId);

                if (matchedFeature) {
                    const p = matchedFeature.properties;
                    inspectedFeature.value = {
                        id: p.id,
                        name: p.name || 'Об’єкт',
                        category: p.category || 'UNKNOWN',
                        status: (p.status || 'OPERATIONAL') as FeatureStatus,
                        speedOverrideWheeled: p.speedOverrideWheeled ?? null,
                        speedOverrideTracked: p.speedOverrideTracked ?? null,
                        visibilityOverride: p.visibilityOverride ?? null,
                        coverOverride: p.coverOverride ?? null
                    };
                }
            }
            return;
        }

        // 2. Elevation Workspace: Sculpting Actions
        if (activeTab.value === 'ELEVATION' && elevationToolMode.value !== 'NAVIGATE') {
            const ray = currentViewer.camera.getPickRay(event.position);
            if (!ray) return;
            const cartesian = currentViewer.scene.globe.pick(ray, currentViewer.scene);
            if (!cartesian) return;

            const carto = Cartographic.fromCartesian(cartesian);
            const lat = Number(CesiumMath.toDegrees(carto.latitude).toFixed(6));
            const lon = Number(CesiumMath.toDegrees(carto.longitude).toFixed(6));

            if (elevationToolMode.value === 'RADIAL') {
                try {
                    isProcessing.value = true;
                    await mapApi.sculptTerrain(props.map.id, {
                        centerLat: lat,
                        centerLon: lon,
                        radiusMeters: brushRadius.value,
                        operation: radialOp.value,
                        deltaMeters: deltaHeight.value
                    });
                    invalidateTerrainCache();
                    await refreshHistory();
                } finally {
                    isProcessing.value = false;
                }
            } else if (elevationToolMode.value === 'LINEAR') {
                if (!lineStartCartesian.value) {
                    lineStartCartesian.value = cartesian;
                } else {
                    const startCarto = Cartographic.fromCartesian(lineStartCartesian.value);
                    const startLat = Number(CesiumMath.toDegrees(startCarto.latitude).toFixed(6));
                    const startLon = Number(CesiumMath.toDegrees(startCarto.longitude).toFixed(6));
                    lineStartCartesian.value = null;

                    try {
                        isProcessing.value = true;
                        await mapApi.sculptTerrainLine(props.map.id, {
                            startLat,
                            startLon,
                            endLat: lat,
                            endLon: lon,
                            widthMeters: lineWidth.value,
                            deltaMeters: deltaHeight.value,
                            operation: linearOp.value
                        });
                        invalidateTerrainCache();
                        await refreshHistory();
                    } finally {
                        isProcessing.value = false;
                    }
                }
            }
        }
    }, ScreenSpaceEventType.LEFT_CLICK);
};

const handlePromoteToTemplate = async (mod: TacticalModifierData) => {
    try {
        await mapApi.promoteModifierToTemplate(props.map.id, mod.id);
        availableTemplates.value = await mapApi.getTemplates();
        alert(`Тип "${mod.osmKey}=${mod.osmValue}" успішно збережено в Головний Довідник!`);
    } catch (err) {
        alert('Помилка збереження в довідник: ' + err);
    }
};

const handleSyncAllTemplates = async () => {
    try {
        const res = await mapApi.syncMapTemplates(props.map.id);
        modifiers.value = await mapApi.getModifiers(props.map.id);
        reapplyAllStyling(modifiers.value);
        alert(`Синхронізовано ${res.syncedCount} типів об'єктів з Головним Довідником!`);
    } catch (err) {
        alert('Помилка синхронізації: ' + err);
    }
};

const applyTemplateToCategory = async (mod: TacticalModifierData, templateId: string) => {
    try {
        await mapApi.applyTemplateToMap(props.map.id, mod.id, templateId);
        modifiers.value = await mapApi.getModifiers(props.map.id);
        reapplyAllStyling(modifiers.value);
        alert(`Шаблон успішно застосовано до: ${mod.osmValue}`);
    } catch (err) {
        alert('Помилка застосування шаблону: ' + err);
    }
};

const applyTemplateToFeature = (templateId: string) => {
    const tmpl = availableTemplates.value.find(t => t.id === templateId);
    if (tmpl && inspectedFeature.value) {
        inspectedFeature.value.speedOverrideWheeled = tmpl.speedModifierWheeled;
        inspectedFeature.value.speedOverrideTracked = tmpl.speedModifierTracked;
        inspectedFeature.value.coverOverride = tmpl.coverDefensePercent;
        inspectedFeature.value.visibilityOverride = tmpl.visibilityMeters;
    }
};

const saveCurrentFeature = async () => {
    if (!inspectedFeature.value) return;
    await mapApi.updateFeature(props.map.id, inspectedFeature.value.id, {
        name: inspectedFeature.value.name,
        status: inspectedFeature.value.status,
        speedModifierOverrideWheeled: inspectedFeature.value.speedOverrideWheeled,
        speedModifierOverrideTracked: inspectedFeature.value.speedOverrideTracked,
        visibilityOverride: inspectedFeature.value.visibilityOverride,
        coverDefenseOverride: inspectedFeature.value.coverOverride
    });
    alert('Об’єкт збережено!');
};

onUnmounted(() => {
    if (clickHandler) clickHandler.destroy();
    destroyViewer();
});
</script>

<style scoped>
.editor-workspace { width: 100vw; height: 100vh; background: #020617; display: flex; flex-direction: column; overflow: hidden; }
.editor-body { display: flex; flex: 1; overflow: hidden; }
.map-viewport { flex: 1; position: relative; background: #020617; }
.cesium-map-canvas { width: 100%; height: 100%; }

.inspector-sidebar {
    width: 380px; background: rgba(15, 23, 42, 0.98); border-left: 1px solid rgba(0, 168, 255, 0.25);
    display: flex; flex-direction: column; padding: 12px; overflow-y: auto; z-index: 100;
}
</style>