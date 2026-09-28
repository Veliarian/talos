<template>
    <div class="editor-workspace">
        <!-- 1. Top Header with Workspace Tabs -->
        <EditorHeader
            :map-name="map.name"
            :size-km="map.sizeKm"
            :active-tab="activeTab"
            @back="$emit('back')"
            @select-tab="onSelectWorkspaceTab"
        />

        <div class="editor-body">
            <!-- 2. Main Viewport -->
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
                    @undo="handleUndo"
                    @redo="handleRedo"
                    @cancel-line="lineStartCartesian = null"
                />

                <!-- Panel 2: Tactical Objects -->
                <VectorStudioPanel
                    v-else-if="activeTab === 'OBJECTS'"
                    :modifiers="modifiers"
                    :available-templates="availableTemplates"
                    :active-highlight-type="activeHighlightType"
                    @highlight="highlightObjectsOnMap"
                    @apply-template="applyTemplateToCategory"
                    @promote-to-template="handlePromoteToTemplate"
                    @sync-all="handleSyncAllTemplates"
                />

                <!-- Panel 3: Basemap Textures -->
                <TextureStudioPanel
                    v-else-if="activeTab === 'TEXTURES'"
                    :layers="map.layers"
                    :current-layer-type="currentLayerType"
                    @select-layer="onSelectBasemapLayer"
                />
            </aside>
        </div>

        <!-- 4. Bottom Tactical Status Bar -->
        <EditorStatusBar
            :cursor-coords="cursorCoordsDisplay"
            :cursor-mgrs="cursorMgrsDisplay"
            :cursor-elevation="cursorElevationDisplay"
            :is-3-d-mode="is3DMode"
            :tool-mode="activeTab === 'ELEVATION' ? elevationToolMode : (activeTab === 'OBJECTS' ? 'INSPECT' : 'NAVIGATE')"
            :is-drawing-line="lineStartCartesian !== null"
            @toggle-perspective="fitCamera(!is3DMode)"
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
    defined,
    Color,
    ColorMaterialProperty,
    ConstantProperty
} from 'cesium';
import { mapApi } from '../mapApi';
import { coordConverter } from '@/shared/utils/coordConverter';
import type {
    MapDetailDto,
    SculptOperation,
    LinearSculptOperation,
    TerrainHistoryStatus,
    TacticalModifierData,
    DefaultModifierDto,
    FeatureStatus
} from '../types';
import { useEditorMap } from '../composables/useEditorMap';

import EditorHeader, { type EditorWorkspaceTab } from '../components/editor/EditorHeader.vue';
import EditorStatusBar from '../components/editor/EditorStatusBar.vue';
import ElevationStudioPanel from '../components/editor/ElevationStudioPanel.vue';
import TextureStudioPanel from '../components/editor/TextureStudioPanel.vue';
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
const availableTemplates = ref<DefaultModifierDto[]>([]);
const inspectedFeature = ref<any>(null);
const activeHighlightType = ref<string | null>(null);

// Status bar tracking
const cursorCoordsDisplay = ref('—');
const cursorMgrsDisplay = ref('—');
const cursorElevationDisplay = ref(120);

const {
    viewer,
    is3DMode,
    currentLayerType,
    layerStack,
    lineStartCartesian,
    currentMouseCartesian,
    initViewer,
    fitCamera,
    mountBaseLayer,
    applyLayerStack,
    loadVectors,
    reapplyAllStyling,
    invalidateTerrainCache,
    highlightCategoryObjects,
    destroyViewer
} = useEditorMap(props.map);

let clickHandler: ScreenSpaceEventHandler | null = null;

const onSelectWorkspaceTab = (tab: EditorWorkspaceTab) => {
    activeTab.value = tab;
    if (tab === 'ELEVATION') {
        layerStack.elevation = true;
        layerStack.texture = true;
        layerStack.objects = false;
    } else if (tab === 'OBJECTS') {
        layerStack.elevation = false;
        layerStack.texture = true;
        layerStack.objects = true;
    } else if (tab === 'TEXTURES') {
        layerStack.elevation = false;
        layerStack.texture = true;
        layerStack.objects = false;
    }
    applyLayerStack();
};

const onSelectBasemapLayer = (type: string) => {
    currentLayerType.value = type;
    mountBaseLayer(type);
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
    onSelectWorkspaceTab('OBJECTS');

    try {
        modifiers.value = await mapApi.getModifiers(props.map.id);
        availableTemplates.value = await mapApi.getTemplates();
        const vectors = await mapApi.getMapVectors(props.map.id);
        await loadVectors(vectors, modifiers.value);
    } catch (err) {
        console.warn('Initial data load error:', err);
        console.warn('Initial data load error:', err);
    }

    setupInteractions();
    await refreshHistory();
});

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

    // Left Click: Sculpting or Feature Inspecting
    clickHandler.setInputAction(async (event: { position: Cartesian2 }) => {
        const currentViewer = viewer.value;
        if (!currentViewer) return;

        // 1. Objects Workspace: Feature Inspecting
        if (activeTab.value === 'OBJECTS') {
            const picked = currentViewer.scene.pick(event.position);
            if (defined(picked) && picked.id && picked.id.properties) {
                const p = picked.id.properties;
                inspectedFeature.value = {
                    id: p.id ? String(p.id.getValue()) : '',
                    name: p.name ? p.name.getValue() : 'Об’єкт',
                    category: p.category ? p.category.getValue() : 'UNKNOWN',
                    status: (p.status ? p.status.getValue() : 'OPERATIONAL') as FeatureStatus,
                    speedOverrideWheeled: p.speedOverrideWheeled ? Number(p.speedOverrideWheeled.getValue()) : null,
                    speedOverrideTracked: p.speedOverrideTracked ? Number(p.speedOverrideTracked.getValue()) : null,
                    visibilityOverride: p.visibilityOverride ? Number(p.visibilityOverride.getValue()) : null,
                    coverOverride: p.coverOverride ? Number(p.coverOverride.getValue()) : null,
                    cesiumEntity: picked.id
                };
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

const highlightObjectsOnMap = (mod: TacticalModifierData) => {
    // If clicking the same item, toggle off highlight
    if (activeHighlightType.value === mod.osmValue) {
        activeHighlightType.value = null;
        highlightCategoryObjects(null);
    } else {
        activeHighlightType.value = mod.osmValue;
        highlightCategoryObjects(mod.osmValue);
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
    width: 360px; background: rgba(15, 23, 42, 0.98); border-left: 1px solid rgba(0, 168, 255, 0.25);
    display: flex; flex-direction: column; padding: 12px; overflow-y: auto; z-index: 100;
}
</style>