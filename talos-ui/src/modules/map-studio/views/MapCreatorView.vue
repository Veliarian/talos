<template>
    <div class="creator-container">
        <!-- Top Bar -->
        <div class="creator-header">
            <button class="btn-back" @click="$emit('cancel')">← НАЗАД ДО КАРТ</button>
            <h3>ІНТЕРАКТИВНИЙ КОНСТРУКТОР ТВД</h3>
            <span class="mode-tag">ВИБІР БОЙОВОГО КВАДРАТА</span>

            <!-- Mode Switcher on Top -->
            <div class="tool-modes">
                <button
                    type="button"
                    class="mode-btn"
                    :class="{ active: interactionMode === 'CLICK_CENTER' }"
                    @click="interactionMode = 'CLICK_CENTER'"
                >
                    📍 Клік для центру
                </button>
                <button
                    type="button"
                    class="mode-btn"
                    :class="{ active: interactionMode === 'DRAG_BOX' }"
                    @click="interactionMode = 'DRAG_BOX'"
                >
                    ⌖ Намалювати рамку (Drag)
                </button>
            </div>
        </div>

        <div class="creator-workspace">
            <!-- Real Interactive Cesium Map Area -->
            <div class="visual-picker-area">
                <div id="creatorMapContainer" ref="creatorMapContainer" class="creator-map-canvas"></div>

                <!-- Floating HUD with active bounds -->
                <div class="picker-map-hud">
                    <span class="hud-label">ОБРАНА ЗОНА ТВД:</span>
                    <strong>{{ form.sizeKm }} × {{ form.sizeKm }} КМ ({{ form.sizeKm * form.sizeKm }} км²)</strong>
                    <span class="hud-hint">
            {{ interactionMode === 'CLICK_CENTER' ? 'Клікніть на карті для переміщення центру' : 'Затисніть ЛКМ та тягніть для малювання рамки' }}
          </span>
                </div>

                <!-- Preview Style Switcher on Map (Hybrid / Topo / Vector) -->
                <div class="map-style-floating-widget">
                    <span class="widget-title">СТИЛЬ ПЕРЕГЛЯДУ:</span>
                    <div class="widget-buttons">
                        <button
                            type="button"
                            class="style-btn"
                            :class="{ active: activeStyle === 'HYBRID' }"
                            @click="switchPreviewStyle('HYBRID')"
                        >
                            🛰️ Супутник + Підписи
                        </button>
                        <button
                            type="button"
                            class="style-btn"
                            :class="{ active: activeStyle === 'TOPO' }"
                            @click="switchPreviewStyle('TOPO')"
                        >
                            🗺️ Топографічна
                        </button>
                        <button
                            type="button"
                            class="style-btn"
                            :class="{ active: activeStyle === 'VECTOR' }"
                            @click="switchPreviewStyle('VECTOR')"
                        >
                            🏙️ Векторна OSM
                        </button>
                    </div>
                </div>
            </div>

            <!-- Right Sidebar: Configuration Dock -->
            <div class="config-sidebar">
                <div class="sidebar-block">
                    <label class="block-title">НАЗВА ПОЛІГОНУ ТА ОПИС</label>
                    <input v-model="form.name" type="text" placeholder="наприклад, Яворів 20x20 ТВД" class="text-input" />
                    <input v-model="form.description" type="text" placeholder="Опис навчань або призначення" class="text-input mt-2" />
                </div>

                <!-- Coordinate System Switcher -->
                <div class="sidebar-block">
                    <label class="block-title">КООРДИНАТИ ТА МАСШТАБ</label>
                    <div class="coord-system-tabs">
                        <button
                            type="button"
                            class="cs-tab"
                            :class="{ active: coordMode === 'DD' }"
                            @click="coordMode = 'DD'"
                        >
                            WGS84 (DD)
                        </button>
                        <button
                            type="button"
                            class="cs-tab"
                            :class="{ active: coordMode === 'DMS' }"
                            @click="coordMode = 'DMS'"
                        >
                            DMS (ГМС)
                        </button>
                        <button
                            type="button"
                            class="cs-tab"
                            :class="{ active: coordMode === 'METRIC' }"
                            @click="coordMode = 'METRIC'"
                        >
                            MGRS Сітка
                        </button>
                    </div>

                    <!-- Decimal Degrees -->
                    <div v-if="coordMode === 'DD'" class="inputs-grid">
                        <div>
                            <label>Широта (Lat °):</label>
                            <input
                                v-model.number="form.centerLat"
                                type="number"
                                step="0.0001"
                                class="text-input"
                                @change="onCoordinateInputChange"
                            />
                        </div>
                        <div>
                            <label>Довгота (Lon °):</label>
                            <input
                                v-model.number="form.centerLon"
                                type="number"
                                step="0.0001"
                                class="text-input"
                                @change="onCoordinateInputChange"
                            />
                        </div>
                    </div>

                    <!-- DMS View -->
                    <div v-else-if="coordMode === 'DMS'" class="dms-view">
                        <div class="dms-line">{{ getDmsDisplay() }}</div>
                        <span class="sub-hint">Конвертовано автоматично з WGS84</span>
                    </div>

                    <!-- MGRS View -->
                    <div v-else-if="coordMode === 'METRIC'" class="dms-view">
                        <div class="dms-line">{{ getMgrsDisplay() }}</div>
                        <span class="sub-hint">Військова координатна сітка НАТО</span>
                    </div>

                    <!-- Metric Size (Km) Slider / Input -->
                    <div class="mt-3">
                        <div class="flex-between">
                            <label>Розмір сторони квадрата:</label>
                            <span class="val-highlight">{{ form.sizeKm }} км</span>
                        </div>
                        <input
                            v-model.number="form.sizeKm"
                            type="range"
                            min="5"
                            max="50"
                            step="1"
                            class="range-slider"
                            @input="onSizeChange"
                        />
                    </div>
                </div>

                <!-- Baselayer Checklist for Offline Storage -->
                <div class="sidebar-block">
                    <label class="block-title">ПІДКЛАДКИ ДЛЯ ЛОКАЛЬНОГО ЗБЕРЕЖЕННЯ</label>
                    <div class="layers-checklist">
                        <label class="check-item">
                            <input type="checkbox" value="SATELLITE" v-model="form.layerTypes" />
                            <span>Супутникова зйомка (Офлайн)</span>
                        </label>
                        <label class="check-item">
                            <input type="checkbox" value="TOPOGRAPHIC" v-model="form.layerTypes" />
                            <span>Топографічна карта (Горизонталі)</span>
                        </label>
                        <label class="check-item">
                            <input type="checkbox" value="TACTICAL" v-model="form.layerTypes" />
                            <span>Тактична штабна контрастна</span>
                        </label>
                    </div>
                </div>

                <!-- Launch Button -->
                <div class="sidebar-actions">
                    <button
                        class="btn-start-ingestion"
                        :disabled="!form.name || loading"
                        @click="startCreation"
                    >
                        {{ loading ? 'ІНІЦІАЛІЗАЦІЯ ТА СТЯГУВАННЯ...' : 'ЗБЕРЕГТИ КАРТУ НА СЕРВЕР' }}
                    </button>
                </div>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted, onUnmounted } from 'vue';
import {
    Viewer,
    Cartesian3,
    Color,
    Rectangle,
    ArcGisMapServerImageryProvider,
    UrlTemplateImageryProvider,
    ImageryLayer,
    ScreenSpaceEventHandler,
    ScreenSpaceEventType,
    Cartographic,
    Math as CesiumMath,
    CallbackProperty,
    HeightReference
} from 'cesium';
import { mapApi } from '../mapApi';
import { coordConverter } from '../coordConverter';
import type { MapCreationRequest } from '../types';

const emit = defineEmits<{
    (e: 'cancel'): void;
    (e: 'created'): void;
}>();

const creatorMapContainer = ref<HTMLDivElement | null>(null);
const loading = ref(false);
const coordMode = ref<'DD' | 'DMS' | 'METRIC'>('DD');
const interactionMode = ref<'CLICK_CENTER' | 'DRAG_BOX'>('CLICK_CENTER');
const activeStyle = ref<'HYBRID' | 'TOPO' | 'VECTOR'>('HYBRID');

let viewer: Viewer | null = null;
let handler: ScreenSpaceEventHandler | null = null;
let isDragging = false;
let dragStartCarto: Cartographic | null = null;

const form = reactive<MapCreationRequest>({
    name: '',
    description: '',
    centerLat: 49.9880,
    centerLon: 23.5850,
    sizeKm: 20,
    layerTypes: ['SATELLITE', 'TOPOGRAPHIC'],
    minZoom: 10,
    maxZoom: 15
});

// Calculate Bounding Box coordinates based on center and square size
const getBbox = (lat: number, lon: number, sizeKm: number) => {
    const halfMeters = (sizeKm * 1000.0) / 2.0;
    const dLat = halfMeters / 111132.95;
    const dLon = halfMeters / (111132.95 * Math.cos(CesiumMath.toRadians(lat)));
    return {
        minLat: lat - dLat,
        maxLat: lat + dLat,
        minLon: lon - dLon,
        maxLon: lon + dLon
    };
};

onMounted(async () => {
    if (!creatorMapContainer.value) return;

    // 1. Initialize viewer without baseLayer initially
    viewer = new Viewer(creatorMapContainer.value, {
        baseLayer: false,
        baseLayerPicker: false,
        geocoder: false,
        homeButton: false,
        sceneModePicker: false,
        navigationHelpButton: false,
        animation: false,
        timeline: false,
        fullscreenButton: false,
        infoBox: false,
        selectionIndicator: false
    });

    viewer.scene.globe.enableLighting = false;

    // 2. Apply initial hybrid satellite with city/country labels
    await switchPreviewStyle('HYBRID');

    // 3. Add real-time bounded tactical rectangle overlay
    viewer.entities.add({
        name: 'Tactical Bounding Box',
        rectangle: {
            coordinates: new CallbackProperty(() => {
                const b = getBbox(form.centerLat, form.centerLon, form.sizeKm);
                return Rectangle.fromDegrees(b.minLon, b.minLat, b.maxLon, b.maxLat);
            }, false),
            material: Color.fromCssColorString('#00a8ff').withAlpha(0.2),
            outline: true,
            outlineColor: Color.fromCssColorString('#00e676'),
            outlineWidth: 3,
            heightReference: HeightReference.CLAMP_TO_GROUND
        }
    });

    // 4. Add center crosshair point
    viewer.entities.add({
        name: 'Center Crosshair',
        position: new CallbackProperty(() => {
            return Cartesian3.fromDegrees(form.centerLon, form.centerLat, 10.0);
        }, false),
        point: {
            pixelSize: 10,
            color: Color.RED,
            outlineColor: Color.WHITE,
            outlineWidth: 2,
            heightReference: HeightReference.CLAMP_TO_GROUND
        }
    });

    // 5. Initial camera flyTo
    viewer.camera.flyTo({
        destination: Cartesian3.fromDegrees(form.centerLon, form.centerLat, 45000),
        duration: 1.5
    });

    setupMousePicking();
});

/**
 * Switch preview styles on the interactive canvas:
 * - HYBRID: Esri Satellite + Transparent Esri Place/City labels
 * - TOPO: OpenTopoMap with elevation contours
 * - VECTOR: CartoDB Voyager street map
 */
const switchPreviewStyle = async (style: 'HYBRID' | 'TOPO' | 'VECTOR') => {
    if (!viewer) return;
    activeStyle.value = style;

    viewer.imageryLayers.removeAll();

    if (style === 'HYBRID') {
        // 1. Satellite Base Layer
        const satProvider = await ArcGisMapServerImageryProvider.fromUrl(
            'https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer',
            { enablePickFeatures: false }
        );
        viewer.imageryLayers.add(new ImageryLayer(satProvider));

        // 2. Transparent Place / City / Road Reference Labels Overlay
        const labelsProvider = await ArcGisMapServerImageryProvider.fromUrl(
            'https://services.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer',
            { enablePickFeatures: false }
        );
        viewer.imageryLayers.add(new ImageryLayer(labelsProvider));
    } else if (style === 'TOPO') {
        const topoProvider = new UrlTemplateImageryProvider({
            url: 'https://tile.opentopomap.org/{z}/{x}/{y}.png'
        });
        viewer.imageryLayers.add(new ImageryLayer(topoProvider));
    } else if (style === 'VECTOR') {
        const vectorProvider = new UrlTemplateImageryProvider({
            url: 'https://basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png'
        });
        viewer.imageryLayers.add(new ImageryLayer(vectorProvider));
    }
};

const setupMousePicking = () => {
    if (!viewer) return;

    handler = new ScreenSpaceEventHandler(viewer.scene.canvas);

    // LEFT CLICK: Click to Center Mode
    handler.setInputAction((click: any) => {
        if (interactionMode.value !== 'CLICK_CENTER') return;

        const ray = viewer!.camera.getPickRay(click.position);
        if (!ray) return;
        const cartesian = viewer!.scene.globe.pick(ray, viewer!.scene);
        if (!cartesian) return;

        const carto = Cartographic.fromCartesian(cartesian);
        form.centerLat = Number(CesiumMath.toDegrees(carto.latitude).toFixed(4));
        form.centerLon = Number(CesiumMath.toDegrees(carto.longitude).toFixed(4));
    }, ScreenSpaceEventType.LEFT_CLICK);

    // DRAG BOX: Start Drag
    handler.setInputAction((click: any) => {
        if (interactionMode.value !== 'DRAG_BOX') return;

        const ray = viewer!.camera.getPickRay(click.position);
        if (!ray) return;
        const cartesian = viewer!.scene.globe.pick(ray, viewer!.scene);
        if (!cartesian) return;

        dragStartCarto = Cartographic.fromCartesian(cartesian);
        isDragging = true;
        viewer!.scene.screenSpaceCameraController.enableInputs = false;
    }, ScreenSpaceEventType.LEFT_DOWN);

    // DRAG BOX: Release Drag and compute size
    handler.setInputAction((movement: any) => {
        if (interactionMode.value !== 'DRAG_BOX' || !isDragging || !dragStartCarto) return;

        const ray = viewer!.camera.getPickRay(movement.position);
        if (!ray) return;
        const cartesian = viewer!.scene.globe.pick(ray, viewer!.scene);
        if (!cartesian) return;

        const endCarto = Cartographic.fromCartesian(cartesian);

        const lat1 = CesiumMath.toDegrees(dragStartCarto.latitude);
        const lon1 = CesiumMath.toDegrees(dragStartCarto.longitude);
        const lat2 = CesiumMath.toDegrees(endCarto.latitude);
        const lon2 = CesiumMath.toDegrees(endCarto.longitude);

        form.centerLat = Number(((lat1 + lat2) / 2.0).toFixed(4));
        form.centerLon = Number(((lon1 + lon2) / 2.0).toFixed(4));

        const distLatKm = Math.abs(lat2 - lat1) * 111.13;
        const distLonKm = Math.abs(lon2 - lon1) * 111.13 * Math.cos(CesiumMath.toRadians(form.centerLat));
        const maxSideKm = Math.max(distLatKm, distLonKm);

        form.sizeKm = Math.min(50, Math.max(5, Math.round(maxSideKm)));

        isDragging = false;
        dragStartCarto = null;
        viewer!.scene.screenSpaceCameraController.enableInputs = true;
    }, ScreenSpaceEventType.LEFT_UP);
};

const onCoordinateInputChange = () => {
    if (!viewer) return;
    viewer.camera.flyTo({
        destination: Cartesian3.fromDegrees(form.centerLon, form.centerLat, form.sizeKm * 2500),
        duration: 1.0
    });
};

const onSizeChange = () => {
    // CallbackProperty automatically resizes the rectangle in real time
};

const getDmsDisplay = () => {
    return coordConverter.formatDms(form.centerLat, form.centerLon);
};

const getMgrsDisplay = () => {
    return coordConverter.toMgrsEstimate(form.centerLat, form.centerLon);
};

const startCreation = async () => {
    try {
        loading.value = true;
        await mapApi.createMap(form);
        emit('created');
    } catch (err: any) {
        alert('Помилка генерації карти: ' + err.message);
    } finally {
        loading.value = false;
    }
};

onUnmounted(() => {
    if (handler) handler.destroy();
    if (viewer) viewer.destroy();
});
</script>

<style scoped>
.creator-container {
    width: 100%;
    height: 100vh;
    background: #020617;
    display: flex;
    flex-direction: column;
    box-sizing: border-box;
    overflow: hidden;
}
.creator-header {
    background: rgba(15, 23, 42, 0.95);
    border-bottom: 1px solid rgba(0, 168, 255, 0.3);
    padding: 10px 20px;
    display: flex;
    align-items: center;
    gap: 16px;
    z-index: 100;
}
.btn-back {
    background: transparent;
    border: 1px solid #475569;
    color: #cbd5e1;
    padding: 6px 12px;
    border-radius: 4px;
    cursor: pointer;
    font-family: monospace;
    font-size: 11px;
}
.btn-back:hover {
    border-color: #00a8ff;
    color: #fff;
}
.creator-header h3 {
    margin: 0;
    font-size: 14px;
    color: #00a8ff;
    letter-spacing: 1px;
}
.mode-tag {
    font-size: 10px;
    background: rgba(0, 230, 118, 0.2);
    color: #00e676;
    padding: 2px 6px;
    border-radius: 3px;
    font-family: monospace;
}
.tool-modes {
    margin-left: auto;
    display: flex;
    gap: 6px;
}
.mode-btn {
    background: #1e293b;
    border: 1px solid #334155;
    color: #94a3b8;
    padding: 6px 12px;
    font-size: 11px;
    cursor: pointer;
    border-radius: 4px;
    font-family: monospace;
}
.mode-btn.active {
    background: #0284c7;
    color: #fff;
    border-color: #38bdf8;
    font-weight: bold;
}
.creator-workspace {
    display: flex;
    flex: 1;
    overflow: hidden;
}
.visual-picker-area {
    flex: 1;
    position: relative;
    height: 100%;
}
.creator-map-canvas {
    width: 100%;
    height: 100%;
}

/* Floating HUD */
.picker-map-hud {
    position: absolute;
    top: 16px;
    left: 16px;
    background: rgba(15, 23, 42, 0.88);
    backdrop-filter: blur(6px);
    border: 1px solid rgba(0, 168, 255, 0.4);
    padding: 8px 16px;
    border-radius: 4px;
    font-size: 12px;
    color: #fff;
    font-family: monospace;
    z-index: 10;
    display: flex;
    flex-direction: column;
    gap: 4px;
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.6);
}
.hud-label {
    color: #00a8ff;
    font-size: 10px;
}
.hud-hint {
    font-size: 10px;
    color: #94a3b8;
}

/* Floating Map Style Switcher Widget */
.map-style-floating-widget {
    position: absolute;
    bottom: 20px;
    left: 20px;
    background: rgba(15, 23, 42, 0.9);
    backdrop-filter: blur(6px);
    border: 1px solid rgba(0, 168, 255, 0.3);
    padding: 8px 12px;
    border-radius: 6px;
    z-index: 10;
    display: flex;
    flex-direction: column;
    gap: 6px;
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.7);
}
.widget-title {
    font-size: 9px;
    color: #00a8ff;
    font-family: monospace;
    font-weight: bold;
    letter-spacing: 1px;
}
.widget-buttons {
    display: flex;
    gap: 6px;
}
.style-btn {
    background: #1e293b;
    border: 1px solid #334155;
    color: #cbd5e1;
    padding: 6px 10px;
    border-radius: 4px;
    font-size: 11px;
    cursor: pointer;
    font-family: monospace;
    transition: all 0.2s;
}
.style-btn:hover {
    border-color: #00a8ff;
    color: #fff;
}
.style-btn.active {
    background: #0284c7;
    border-color: #38bdf8;
    color: #fff;
    font-weight: bold;
}

.config-sidebar {
    width: 380px;
    background: rgba(15, 23, 42, 0.95);
    border-left: 1px solid rgba(0, 168, 255, 0.2);
    padding: 20px;
    display: flex;
    flex-direction: column;
    gap: 16px;
    overflow-y: auto;
    z-index: 100;
}
.sidebar-block {
    background: rgba(255, 255, 255, 0.02);
    border: 1px solid #334155;
    border-radius: 4px;
    padding: 12px;
}
.block-title {
    display: block;
    font-size: 11px;
    font-weight: bold;
    color: #00a8ff;
    margin-bottom: 8px;
    font-family: monospace;
}
.text-input {
    width: 100%;
    box-sizing: border-box;
    background: #0b1120;
    border: 1px solid #334155;
    color: #fff;
    padding: 8px;
    border-radius: 3px;
    font-family: monospace;
    font-size: 12px;
}
.text-input:focus {
    border-color: #00a8ff;
    outline: none;
}
.mt-2 { margin-top: 8px; }
.mt-3 { margin-top: 12px; }
.coord-system-tabs {
    display: flex;
    gap: 4px;
    margin-bottom: 10px;
}
.cs-tab {
    flex: 1;
    background: #1e293b;
    border: 1px solid #334155;
    color: #94a3b8;
    padding: 6px;
    font-size: 10px;
    cursor: pointer;
    border-radius: 3px;
    font-family: monospace;
}
.cs-tab.active {
    background: #0284c7;
    color: #fff;
    border-color: #38bdf8;
}
.inputs-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 8px;
    font-size: 11px;
    color: #94a3b8;
    font-family: monospace;
}
.dms-view {
    background: #0b1120;
    padding: 8px;
    border-radius: 3px;
    font-family: monospace;
    font-size: 11px;
    color: #f1c40f;
}
.sub-hint {
    font-size: 9px;
    color: #64748b;
    display: block;
    margin-top: 4px;
}
.flex-between {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: 11px;
    color: #cbd5e1;
    font-family: monospace;
}
.val-highlight {
    color: #00e676;
    font-weight: bold;
}
.range-slider {
    width: 100%;
    margin-top: 6px;
    cursor: pointer;
}
.layers-checklist {
    display: flex;
    flex-direction: column;
    gap: 8px;
}
.check-item {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 12px;
    color: #cbd5e1;
    cursor: pointer;
}
.btn-start-ingestion {
    width: 100%;
    background: #0284c7;
    border: 1px solid #38bdf8;
    color: #fff;
    padding: 12px;
    font-weight: bold;
    font-size: 13px;
    font-family: monospace;
    cursor: pointer;
    border-radius: 4px;
    transition: background 0.2s;
}
.btn-start-ingestion:hover:not(:disabled) {
    background: #0369a1;
}
.btn-start-ingestion:disabled {
    opacity: 0.5;
    cursor: not-allowed;
}
</style>