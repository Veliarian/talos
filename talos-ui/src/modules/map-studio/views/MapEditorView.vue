<template>
    <div class="editor-workspace">
        <!-- Top Bar: Layer Visibility Toggles -->
        <div class="editor-header">
            <div class="header-left">
                <button class="btn-back" @click="$emit('back')">← НАЗАД ДО КАРТ</button>
                <span class="map-title">{{ map.name }}</span>
            </div>

            <!-- LAYER STACK INDEPENDENT TOGGLES -->
            <div class="layer-stack-bar">
                <span class="stack-label">ШАРИ ТВД:</span>
                <button
                    type="button"
                    class="stack-btn"
                    :class="{ active: layerStack.elevation }"
                    @click="toggleLayer('elevation')"
                >
                    ⛰️ 1. Рельєф (Висоти)
                </button>
                <button
                    type="button"
                    class="stack-btn"
                    :class="{ active: layerStack.texture }"
                    @click="toggleLayer('texture')"
                >
                    🗺️ 2. Текстури (Підкладка)
                </button>
                <button
                    type="button"
                    class="stack-btn"
                    :class="{ active: layerStack.objects }"
                    @click="toggleLayer('objects')"
                >
                    🏘️ 3. Об'єкти (Вектори)
                </button>
            </div>

            <div class="header-right">
                <span class="spec-badge green">100% OFFLINE</span>
            </div>
        </div>

        <!-- Main Workspace -->
        <div class="editor-body">
            <!-- 3D/2D Viewport -->
            <div class="map-viewport">
                <div id="editorMapContainer" ref="editorMapContainer" class="cesium-map-canvas"></div>

                <!-- Floating HUD with 2D/3D Perspective Switcher -->
                <div class="editor-floating-hud">
                    <div class="hud-top-row">
                        <div>ТВД: <strong>{{ map.name }}</strong> ({{ map.sizeKm }}×{{ map.sizeKm }} км)</div>

                        <!-- Perspective Button (placed safely on the map viewport) -->
                        <button
                            type="button"
                            class="hud-perspective-btn"
                            :class="{ is3d: is3DMode }"
                            @click="togglePerspective"
                        >
                            {{ is3DMode ? '🧊 3D РЕЛЬЄФ (ЗБОКУ)' : '🗺️ 2D ВИГЛЯД ЗГОРИ' }}
                        </button>
                    </div>

                    <div class="sub-coords">
                        Перетягування: <strong>ЛКМ</strong> | Наближення: <strong>Колесо миші</strong> |
                        Висота центру: <strong class="alt-text">{{ centerAltitudeDisplay }} м</strong>
                    </div>
                </div>

                <!-- Inspected Feature Banner -->
                <div v-if="inspectedFeature && layerStack.objects" class="feature-inspector-banner">
                    <div class="fib-header">
                        <span class="fib-cat" :class="inspectedFeature.category.toLowerCase()">{{ inspectedFeature.category }}</span>
                        <strong>{{ inspectedFeature.name }}</strong>
                        <button class="fib-close" @click="clearInspection">✕</button>
                    </div>
                    <div class="fib-details">
                        <span>OSM Тег: <code>{{ inspectedFeature.typeKey }}={{ inspectedFeature.typeValue }}</code></span>
                        <span v-if="inspectedModifier">Шв. колісних: <strong>{{ (inspectedModifier.speedModifierWheeled * 100).toFixed(0) }}%</strong></span>
                        <span v-if="inspectedModifier">Захист укриття: <strong>{{ inspectedModifier.coverDefensePercent }}%</strong></span>
                    </div>
                </div>
            </div>

            <!-- Right Dynamic Accordion Sidebar -->
            <div class="inspector-sidebar">
                <div class="accordion-container">

                    <!-- ACCORDION BLOCK 1: ELEVATION SCULPTING (Visible only if Elevation layer is ON) -->
                    <div v-if="layerStack.elevation" class="accordion-card">
                        <div class="acc-header" @click="accordionOpen.elevation = !accordionOpen.elevation">
                            <span class="acc-title">⛰️ СТУДІЯ РЕЛЬЄФУ ТА СКУЛЬПТИНГ</span>
                            <span class="acc-arrow">{{ accordionOpen.elevation ? '▲' : '▼' }}</span>
                        </div>

                        <div v-show="accordionOpen.elevation" class="acc-body">
                            <p class="acc-desc">
                                Рельєф забарвлено за висотами України (0-1500м). Клікніть по карті для скульптингу.
                            </p>

                            <div class="slider-group">
                                <div class="slider-header">
                                    <span>Вертикальний масштаб рельєфу:</span>
                                    <strong class="val-badge">{{ terrainScale }}x</strong>
                                </div>
                                <input
                                    type="range"
                                    min="1"
                                    max="5"
                                    step="0.5"
                                    v-model.number="terrainScale"
                                    class="range-slider"
                                    @input="onTerrainScaleChange"
                                />
                            </div>

                            <label class="tool-label">ОПЕРАЦІЯ ПЕНЗЛЯ:</label>
                            <div class="sculpt-ops-grid">
                                <button
                                    type="button"
                                    class="op-btn"
                                    :class="{ active: sculptOp === 'DIG' }"
                                    @click="sculptOp = 'DIG'"
                                >
                                    ⛏️ Вирити кар'єр / рів
                                </button>
                                <button
                                    type="button"
                                    class="op-btn"
                                    :class="{ active: sculptOp === 'RAISE' }"
                                    @click="sculptOp = 'RAISE'"
                                >
                                    ⛰️ Насипати вал / капонір
                                </button>
                                <button
                                    type="button"
                                    class="op-btn"
                                    :class="{ active: sculptOp === 'FLATTEN' }"
                                    @click="sculptOp = 'FLATTEN'"
                                >
                                    ⎯ Вирівняти майданчик
                                </button>
                            </div>

                            <div class="slider-group">
                                <div class="slider-header">
                                    <span>Радіус дії:</span>
                                    <strong class="val-badge">{{ brushRadius }} м</strong>
                                </div>
                                <input type="range" min="20" max="400" step="10" v-model.number="brushRadius" class="range-slider" />
                            </div>

                            <div class="slider-group">
                                <div class="slider-header">
                                    <span>Глибина / Висота (&Delta;h):</span>
                                    <strong class="val-badge">{{ brushDelta }} м</strong>
                                </div>
                                <input type="range" min="5" max="100" step="5" v-model.number="brushDelta" class="range-slider" />
                            </div>

                            <div v-if="sculptLoading" class="sculpt-status-box">
                                <span class="pulse-dot"></span> Оновлення рельєфу в ході...
                            </div>
                        </div>
                    </div>

                    <!-- ACCORDION BLOCK 2: BASEMAP TEXTURES (Visible only if Textures layer is ON) -->
                    <div v-if="layerStack.texture" class="accordion-card">
                        <div class="acc-header" @click="accordionOpen.texture = !accordionOpen.texture">
                            <span class="acc-title">🗺️ ПІДКЛАДКА ТА ТЕКСТУРИ</span>
                            <span class="acc-arrow">{{ accordionOpen.texture ? '▲' : '▼' }}</span>
                        </div>

                        <div v-show="accordionOpen.texture" class="acc-body">
                            <label class="tool-label">ОБЕРІТЬ ТИП ПІДКЛАДКИ:</label>
                            <div class="layer-buttons-stacked">
                                <button
                                    v-for="l in map.layers"
                                    :key="l.id"
                                    class="composite-layer-btn"
                                    :class="{ active: currentLayerType === l.layerType }"
                                    @click="switchBaseLayerType(l.layerType)"
                                >
                                    {{ l.layerType }} (Zoom {{ l.minZoom }}-{{ l.maxZoom }})
                                </button>
                            </div>
                        </div>
                    </div>

                    <!-- ACCORDION BLOCK 3: OBJECTS & VECTORS (Visible only if Objects layer is ON) -->
                    <div v-if="layerStack.objects" class="accordion-card">
                        <div class="acc-header" @click="accordionOpen.objects = !accordionOpen.objects">
                            <div class="title-with-btn">
                                <span class="acc-title">🏘️ ВЕКТОРНІ ОБ'ЄКТИ ТА ТТХ</span>
                                <button class="btn-add-mini" @click.stop="showAddModal = true">+ ДОДАТИ</button>
                            </div>
                            <span class="acc-arrow">{{ accordionOpen.objects ? '▲' : '▼' }}</span>
                        </div>

                        <div v-show="accordionOpen.objects" class="acc-body">
                            <div class="category-filters">
                                <button
                                    v-for="cat in ['ALL', 'ROAD', 'VEGETATION', 'BUILDING', 'WATER', 'SOIL']"
                                    :key="cat"
                                    class="filter-pill"
                                    :class="{ active: selectedCategory === cat }"
                                    @click="selectedCategory = cat"
                                >
                                    {{ cat === 'ALL' ? 'ВСІ' : cat }}
                                </button>
                            </div>

                            <div class="modifiers-list">
                                <div
                                    v-for="mod in filteredModifiers"
                                    :key="mod.id"
                                    class="modifier-card"
                                    :class="{ highlighted: activeHighlightType === mod.osmValue }"
                                    @click="highlightObjectsOnMap(mod)"
                                >
                                    <div class="card-head">
                                        <span class="cat-badge" :class="mod.category.toLowerCase()">{{ mod.category }}</span>
                                        <span class="tag-label">{{ mod.osmKey }}={{ mod.osmValue }}</span>
                                    </div>
                                    <div class="mod-desc">{{ mod.description || 'Об’єкт місцевості' }}</div>

                                    <div class="params-grid" @click.stop>
                                        <div class="param-box">
                                            <label>Шв. Колісні:</label>
                                            <input type="number" step="0.05" min="0" max="1.5" v-model.number="mod.speedModifierWheeled" class="num-field" />
                                        </div>
                                        <div class="param-box">
                                            <label>Шв. Гусеничні:</label>
                                            <input type="number" step="0.05" min="0" max="1.5" v-model.number="mod.speedModifierTracked" class="num-field" />
                                        </div>
                                        <div class="param-box">
                                            <label>Видимість (м):</label>
                                            <input type="number" step="10" min="0" max="5000" v-model.number="mod.visibilityMeters" placeholder="Без меж" class="num-field" />
                                        </div>
                                        <div class="param-box">
                                            <label>Захист (%):</label>
                                            <input type="number" step="5" min="0" max="95" v-model.number="mod.coverDefensePercent" class="num-field" />
                                        </div>
                                    </div>

                                    <div class="card-actions" @click.stop>
                                        <button class="btn-save-mod" @click="saveModifier(mod)">ЗБЕРЕГТИ ТТХ</button>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>

                    <!-- Empty placeholder if all layers are turned off -->
                    <div v-if="!layerStack.elevation && !layerStack.texture && !layerStack.objects" class="empty-stack-msg">
                        Усі шари вимкнено. Увімкніть хоча б один шар на верхній панелі.
                    </div>

                </div>
            </div>
        </div>

        <!-- Modal to Add New Custom Object -->
        <div v-if="showAddModal" class="modal-overlay">
            <div class="modal-box">
                <div class="modal-head">
                    <h4>ДОДАТИ НОВИЙ ТИП ОБ'ЄКТА / ПОКРИТТЯ</h4>
                    <button class="btn-close-modal" @click="showAddModal = false">✕</button>
                </div>

                <form @submit.prevent="createNewModifier" class="modal-form">
                    <div class="form-row">
                        <label>КАТЕГОРІЯ ОБ'ЄКТА:</label>
                        <select v-model="newMod.category" required>
                            <option value="BUILDING">BUILDING (Будівля / Капонір)</option>
                            <option value="ROAD">ROAD (Дорога / Траса)</option>
                            <option value="VEGETATION">VEGETATION (Ліс / Чагарник)</option>
                            <option value="WATER">WATER (Річка / Болото)</option>
                            <option value="SOIL">SOIL (Ґрунт / Пісок)</option>
                        </select>
                    </div>

                    <div class="form-grid-2">
                        <div>
                            <label>OSM Ключ (Key):</label>
                            <input v-model="newMod.osmKey" type="text" required placeholder="highway або building" />
                        </div>
                        <div>
                            <label>OSM Значення (Value):</label>
                            <input v-model="newMod.osmValue" type="text" required placeholder="primary або residential" />
                        </div>
                    </div>

                    <div class="form-row">
                        <label>Опис об'єкта:</label>
                        <input v-model="newMod.description" type="text" placeholder="Характеристики споруди" />
                    </div>

                    <div class="form-grid-2">
                        <div>
                            <label>Швидкість колісних (0.0 - 1.2):</label>
                            <input v-model.number="newMod.speedModifierWheeled" type="number" step="0.05" required />
                        </div>
                        <div>
                            <label>Швидкість гусеничних (0.0 - 1.2):</label>
                            <input v-model.number="newMod.speedModifierTracked" type="number" step="0.05" required />
                        </div>
                    </div>

                    <div class="form-grid-2">
                        <div>
                            <label>Видимість всередині (м):</label>
                            <input v-model.number="newMod.visibilityMeters" type="number" step="10" placeholder="Без меж" />
                        </div>
                        <div>
                            <label>Захист від куль (%):</label>
                            <input v-model.number="newMod.coverDefensePercent" type="number" min="0" max="95" required />
                        </div>
                    </div>

                    <div class="modal-foot">
                        <button type="button" class="btn-cancel" @click="showAddModal = false">СКАСУВАТИ</button>
                        <button type="submit" class="btn-submit">ДОДАТИ</button>
                    </div>
                </form>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted, onUnmounted } from 'vue';
import {
    Viewer,
    Cartesian3,
    Rectangle,
    UrlTemplateImageryProvider,
    ImageryLayer,
    GeoJsonDataSource,
    Color,
    ScreenSpaceEventHandler,
    ScreenSpaceEventType,
    Cartographic,
    Math as CesiumMath,
    createElevationBandMaterial,
    CallbackProperty,
    CallbackPositionProperty,
    ColorMaterialProperty,
    HeightReference,
    CustomHeightmapTerrainProvider,
    GeographicTilingScheme,
    defined
} from 'cesium';
import { mapApi } from '../mapApi';
import type { MapDetailDto, SurfaceModifierDto } from '../types';

const props = defineProps<{
    map: MapDetailDto;
}>();

defineEmits<{
    (e: 'back'): void;
}>();

// Independent Layer Stack toggles
const layerStack = reactive({
    elevation: false, // Elevation Heatmap
    texture: true,    // Baselayer Imagery
    objects: true     // Vectors (Roads, Buildings, Forests)
});

// Accordion collapse state
const accordionOpen = reactive({
    elevation: true,
    texture: true,
    objects: true
});

const is3DMode = ref(false);
const editorMapContainer = ref<HTMLDivElement | null>(null);
const currentLayerType = ref<string>('SATELLITE');
const selectedCategory = ref<string>('ALL');
const modifiers = ref<SurfaceModifierDto[]>([]);
const showAddModal = ref(false);
const activeHighlightType = ref<string | null>(null);
const inspectedFeature = ref<any | null>(null);
const centerAltitudeDisplay = ref<number>(120);

// Sculpting state
const sculptOp = ref<'DIG' | 'RAISE' | 'FLATTEN'>('DIG');
const brushRadius = ref(100);
const brushDelta = ref(30);
const sculptLoading = ref(false);

let viewer: Viewer | null = null;
let handler: ScreenSpaceEventHandler | null = null;
let vectorDataSource: GeoJsonDataSource | null = null;
let currentImageryLayer: ImageryLayer | null = null;

let currentMousePosition: Cartesian3 | null = null;

const newMod = ref<Partial<SurfaceModifierDto>>({
    category: 'BUILDING',
    osmKey: 'building',
    osmValue: '',
    description: '',
    speedModifierWheeled: 0.0,
    speedModifierTracked: 0.0,
    visibilityMeters: 0,
    coverDefensePercent: 80
});

const geoTilingScheme = new GeographicTilingScheme();

const terrainScale = ref(2.5);

const onTerrainScaleChange = () => {
    if (!viewer) return;
    (viewer.scene as any).verticalExaggeration = terrainScale.value;
};

/**
 * Builds dynamic 3D geometry directly from local server's terrain.tif
 */
const createLocalTerrainProvider = () => {
    return new CustomHeightmapTerrainProvider({
        width: 32,
        height: 32,
        tilingScheme: geoTilingScheme,
        callback: async (x: number, y: number, level: number) => {
            const rect = geoTilingScheme.tileXYToRectangle(x, y, level);

            const minLat = CesiumMath.toDegrees(rect.south);
            const maxLat = CesiumMath.toDegrees(rect.north);
            const minLon = CesiumMath.toDegrees(rect.west);
            const maxLon = CesiumMath.toDegrees(rect.east);

            const url = `http://localhost:8080/api/maps/${props.map.id}/terrain/grid?minLat=${minLat}&maxLat=${maxLat}&minLon=${minLon}&maxLon=${maxLon}&width=32&height=32`;

            try {
                const response = await fetch(url);
                if (!response.ok) return new Float32Array(32 * 32).fill(120.0);
                const buffer = await response.arrayBuffer();
                const floatArray = new Float32Array(buffer);
                // Sample center altitude for display
                centerAltitudeDisplay.value = Math.round(floatArray[512] || 120.0);
                return floatArray;
            } catch {
                return new Float32Array(32 * 32).fill(120.0);
            }
        }
    });
};

onMounted(async () => {
    if (!editorMapContainer.value) return;

    // 1. Initialize Cesium Viewer with local terrain provider
    viewer = new Viewer(editorMapContainer.value, {
        baseLayer: false,
        terrainProvider: createLocalTerrainProvider(),
        baseLayerPicker: false,
        geocoder: false,
        homeButton: false,
        sceneModePicker: false,
        navigationHelpButton: false,
        animation: false,
        timeline: false,
        fullscreenButton: false,
        infoBox: false,
        selectionIndicator: false,
        skyBox: false
    });

    // Set tactical terrain vertical exaggeration (2.5x by default so hills and valleys pop out!)
    (viewer.scene as any).verticalExaggeration = 2.5;

    // Enable lighting and slope shadows for realistic 3D depth
    viewer.scene.globe.enableLighting = true;

    viewer.scene.globe.baseColor = Color.fromCssColorString('#064e3b');
    if (viewer.scene.skyAtmosphere) viewer.scene.skyAtmosphere.show = false;
    viewer.scene.globe.showGroundAtmosphere = false;
    viewer.scene.backgroundColor = Color.fromCssColorString('#020617');

    // 2. Clamp globe to the exact bounding box of this 20x20 km theater
    const theaterRect = Rectangle.fromDegrees(
        props.map.minLon, props.map.minLat, props.map.maxLon, props.map.maxLat
    );
    viewer.scene.globe.cartographicLimitRectangle = theaterRect;

    // Zoom bounds: max zoom out is proportional to map size
    viewer.scene.screenSpaceCameraController.minimumZoomDistance = 80.0;
    viewer.scene.screenSpaceCameraController.maximumZoomDistance = Math.max(props.map.sizeKm * 1600.0, 26000.0);

    // 3. Add clean boundary polyline around the 20x20km theater (replaces rectangle outline to prevent warnings)
    const boundaryCorners = [
        Cartesian3.fromDegrees(props.map.minLon, props.map.minLat),
        Cartesian3.fromDegrees(props.map.maxLon, props.map.minLat),
        Cartesian3.fromDegrees(props.map.maxLon, props.map.maxLat),
        Cartesian3.fromDegrees(props.map.minLon, props.map.maxLat),
        Cartesian3.fromDegrees(props.map.minLon, props.map.minLat)
    ];
    viewer.entities.add({
        name: 'Theater Boundary Outline',
        polyline: {
            positions: boundaryCorners,
            width: 3,
            material: Color.fromCssColorString('#00a8ff'),
            clampToGround: true
        }
    });

    setupBrushCursor();
    setupMouseInteractions();

    // 4. Initial Camera: 2D view
    fitCamera(false);

    // Load vector features and initial imagery
    await loadVectorFeatures();
    if (props.map.layers && props.map.layers.length > 0) {
        currentLayerType.value = props.map.layers[0].layerType;
    }
    applyLayerStack();
    await loadModifiers();
});

const fitCamera = (is3d: boolean) => {
    if (!viewer) return;

    const theaterRect = Rectangle.fromDegrees(
        props.map.minLon, props.map.minLat, props.map.maxLon, props.map.maxLat
    );

    // Unlocked drag navigation
    viewer.scene.screenSpaceCameraController.enableRotate = true;
    viewer.scene.screenSpaceCameraController.enableTranslate = true;
    viewer.scene.screenSpaceCameraController.enableZoom = true;

    if (!is3d) {
        viewer.scene.screenSpaceCameraController.enableTilt = false;
        viewer.camera.flyTo({
            destination: theaterRect,
            orientation: {
                heading: 0,
                pitch: CesiumMath.toRadians(-90),
                roll: 0
            },
            duration: 0.8
        });
    } else {
        viewer.scene.screenSpaceCameraController.enableTilt = true;
        viewer.camera.flyTo({
            destination: Cartesian3.fromDegrees(
                props.map.centerLon,
                props.map.centerLat - (props.map.sizeKm / 111.0) * 0.35,
                props.map.sizeKm * 850
            ),
            orientation: {
                heading: 0,
                pitch: CesiumMath.toRadians(-35),
                roll: 0
            },
            duration: 1.0
        });
    }
};

const togglePerspective = () => {
    is3DMode.value = !is3DMode.value;
    fitCamera(is3DMode.value);
};

const toggleLayer = (layerKey: 'elevation' | 'texture' | 'objects') => {
    layerStack[layerKey] = !layerStack[layerKey];
    applyLayerStack();
};

/**
 * Updates Cesium scene with comprehensive hypsometric color scale starting from 0 meters (Mukachevo lowland support)
 */
const applyLayerStack = async () => {
    if (!viewer) return;

    // 1. Elevation Layer: Complete Ukrainian hypsometric spectrum from 0m to 1500m
    if (layerStack.elevation && !layerStack.texture) {
        const bands = [
            {
                entries: [
                    { height: 0.0, color: Color.fromCssColorString('#022c22') },    // 0m (lowlands)
                    { height: 90.0, color: Color.fromCssColorString('#047857') },   // 90m (valleys)
                    { height: 130.0, color: Color.fromCssColorString('#059669') },  // 130m (Mukachevo plain)
                    { height: 200.0, color: Color.fromCssColorString('#10b981') },  // 200m (meadows)
                    { height: 300.0, color: Color.fromCssColorString('#84cc16') },  // 300m (volcanic hills)
                    { height: 450.0, color: Color.fromCssColorString('#facc15') },  // 450m (pre-Carpathians)
                    { height: 650.0, color: Color.fromCssColorString('#f97316') },  // 650m (mid mountains)
                    { height: 900.0, color: Color.fromCssColorString('#dc2626') },  // 900m (Carpathian ridge)
                    { height: 1200.0, color: Color.fromCssColorString('#78350f') }, // 1200m (peaks)
                    { height: 1600.0, color: Color.fromCssColorString('#f8fafc') }  // 1600m (high peaks)
                ]
            }
        ];
        viewer.scene.globe.material = createElevationBandMaterial({
            scene: viewer.scene,
            layers: bands
        });
    } else {
        viewer.scene.globe.material = undefined as any;
    }

    // 2. Texture Layer
    if (layerStack.texture) {
        mountBaseLayer(currentLayerType.value);
    } else {
        if (currentImageryLayer) {
            viewer.imageryLayers.remove(currentImageryLayer);
            currentImageryLayer = null;
        }
    }

    // 3. Objects Layer
    if (vectorDataSource) {
        vectorDataSource.show = layerStack.objects;
    }
};

const mountBaseLayer = (layerType: string) => {
    if (!viewer) return;
    if (currentImageryLayer) {
        viewer.imageryLayers.remove(currentImageryLayer);
        currentImageryLayer = null;
    }

    const tileUrl = `http://localhost:8080/api/maps/${props.map.id}/tiles/${layerType.toLowerCase()}/{z}/{x}/{y}.png`;
    const layerMeta = props.map.layers?.find(l => l.layerType.toUpperCase() === layerType.toUpperCase());
    const minZ = layerMeta ? layerMeta.minZoom : 10;
    const maxZ = layerMeta ? layerMeta.maxZoom : 16;

    const provider = new UrlTemplateImageryProvider({
        url: tileUrl,
        rectangle: Rectangle.fromDegrees(
            props.map.minLon, props.map.minLat, props.map.maxLon, props.map.maxLat
        ),
        minimumLevel: minZ,
        maximumLevel: maxZ
    });

    currentImageryLayer = viewer.imageryLayers.addImageryProvider(provider);
};

const switchBaseLayerType = (layerType: string) => {
    currentLayerType.value = layerType;
    if (layerStack.texture) {
        mountBaseLayer(layerType);
    }
};

const setupBrushCursor = () => {
    if (!viewer) return;
    const defaultCenter = Cartesian3.fromDegrees(props.map.centerLon, props.map.centerLat, 0);

    viewer.entities.add({
        name: 'Sculpt Brush Cursor',
        position: new CallbackPositionProperty(() => currentMousePosition || defaultCenter, false),
        ellipse: {
            semiMajorAxis: new CallbackProperty(() => brushRadius.value, false),
            semiMinorAxis: new CallbackProperty(() => brushRadius.value, false),
            material: new ColorMaterialProperty(
                new CallbackProperty(() => {
                    const c = sculptOp.value === 'DIG' ? Color.RED : Color.CYAN;
                    return c.withAlpha(0.35);
                }, false)
            ),
            outline: true,
            outlineColor: Color.WHITE,
            heightReference: HeightReference.CLAMP_TO_GROUND,
            show: new CallbackProperty(() => layerStack.elevation && currentMousePosition !== null, false) as any
        }
    });
};

const setupMouseInteractions = () => {
    if (!viewer) return;
    handler = new ScreenSpaceEventHandler(viewer.scene.canvas);

    handler.setInputAction((movement: any) => {
        if (!layerStack.elevation) return;
        const ray = viewer!.camera.getPickRay(movement.endPosition);
        if (!ray) return;
        const cartesian = viewer!.scene.globe.pick(ray, viewer!.scene);
        if (cartesian) currentMousePosition = cartesian;
    }, ScreenSpaceEventType.MOUSE_MOVE);

    handler.setInputAction(async (click: any) => {
        if (layerStack.elevation) {
            const ray = viewer!.camera.getPickRay(click.position);
            if (!ray) return;
            const cartesian = viewer!.scene.globe.pick(ray, viewer!.scene);
            if (!cartesian) return;

            const carto = Cartographic.fromCartesian(cartesian);
            const lat = Number(CesiumMath.toDegrees(carto.latitude).toFixed(5));
            const lon = Number(CesiumMath.toDegrees(carto.longitude).toFixed(5));

            try {
                sculptLoading.value = true;
                await mapApi.sculptTerrain(props.map.id, {
                    centerLat: lat,
                    centerLon: lon,
                    radiusMeters: brushRadius.value,
                    operation: sculptOp.value,
                    deltaMeters: brushDelta.value
                });
                // Re-mount local terrain provider to trigger dynamic 3D deformation
                viewer!.terrainProvider = createLocalTerrainProvider();
            } catch (err: any) {
                alert('Помилка скульптингу: ' + err.message);
            } finally {
                sculptLoading.value = false;
            }
        } else if (layerStack.objects) {
            const picked = viewer!.scene.pick(click.position);
            if (defined(picked) && picked.id && picked.id.properties) {
                const p = picked.id.properties;
                inspectedFeature.value = {
                    name: p.name ? p.name.getValue() : 'Об’єкт без назви',
                    category: p.category ? p.category.getValue() : 'UNKNOWN',
                    typeKey: p.typeKey ? p.typeKey.getValue() : '',
                    typeValue: p.typeValue ? p.typeValue.getValue() : ''
                };
                activeHighlightType.value = inspectedFeature.value.typeValue;
            } else {
                clearInspection();
            }
        }
    }, ScreenSpaceEventType.LEFT_CLICK);
};

const clearInspection = () => {
    inspectedFeature.value = null;
    activeHighlightType.value = null;
    resetMapHighlights();
};

const loadVectorFeatures = async () => {
    if (!viewer) return;
    try {
        const geoJson = await mapApi.getMapVectors(props.map.id);
        vectorDataSource = await GeoJsonDataSource.load(geoJson, {
            clampToGround: true,
            strokeWidth: 3
        });

        const entities = vectorDataSource.entities.values;
        for (const entity of entities) {
            const category = entity.properties?.category?.getValue();
            if (category === 'ROAD' && entity.polyline) {
                entity.polyline.material = Color.fromCssColorString('#f59e0b').withAlpha(0.8) as any;
                entity.polyline.width = 4 as any;
            } else if (category === 'VEGETATION' && entity.polygon) {
                entity.polygon.material = Color.fromCssColorString('#10b981').withAlpha(0.3) as any;
            } else if (category === 'BUILDING' && entity.polygon) {
                entity.polygon.material = Color.fromCssColorString('#ef4444').withAlpha(0.4) as any;
            }
        }
        viewer.dataSources.add(vectorDataSource);
    } catch (err) {}
};

const highlightObjectsOnMap = (mod: SurfaceModifierDto) => {
    if (!vectorDataSource) return;
    activeHighlightType.value = mod.osmValue;
    const entities = vectorDataSource.entities.values;
    for (const entity of entities) {
        const isMatch = (entity.properties?.typeValue?.getValue() === mod.osmValue);
        if (entity.polyline) {
            entity.polyline.material = (isMatch ? Color.CYAN : Color.fromCssColorString('#f59e0b').withAlpha(0.2)) as any;
            entity.polyline.width = (isMatch ? 8 : 2) as any;
        }
        if (entity.polygon) {
            entity.polygon.material = (isMatch ? Color.CYAN.withAlpha(0.7) : Color.fromCssColorString('#10b981').withAlpha(0.1)) as any;
        }
    }
};

const resetMapHighlights = () => {
    if (!vectorDataSource) return;
    const entities = vectorDataSource.entities.values;
    for (const entity of entities) {
        const cat = entity.properties?.category?.getValue();
        if (cat === 'ROAD' && entity.polyline) {
            entity.polyline.material = Color.fromCssColorString('#f59e0b').withAlpha(0.8) as any;
            entity.polyline.width = 4 as any;
        } else if (entity.polygon) {
            entity.polygon.material = Color.fromCssColorString('#10b981').withAlpha(0.3) as any;
        }
    }
};

const inspectedModifier = computed(() => {
    if (!inspectedFeature.value) return null;
    return modifiers.value.find(m => m.osmValue === inspectedFeature.value.typeValue) || null;
});

const loadModifiers = async () => {
    try {
        modifiers.value = await mapApi.getModifiers(props.map.id);
    } catch (err) {}
};

watch(() => props.map.id, loadModifiers);

const filteredModifiers = computed(() => {
    if (selectedCategory.value === 'ALL') return modifiers.value;
    return modifiers.value.filter(m => m.category === selectedCategory.value);
});

const saveModifier = async (mod: SurfaceModifierDto) => {
    try {
        await mapApi.updateModifier(props.map.id, mod);
        alert(`Оновлено ТТХ: ${mod.osmKey}=${mod.osmValue}`);
    } catch (err: any) {
        alert('Помилка: ' + err.message);
    }
};

const createNewModifier = async () => {
    if (!newMod.value.osmKey || !newMod.value.osmValue) return;
    const created: SurfaceModifierDto = {
        id: crypto.randomUUID(),
        category: newMod.value.category as any,
        osmKey: newMod.value.osmKey,
        osmValue: newMod.value.osmValue,
        description: newMod.value.description || '',
        speedModifierWheeled: newMod.value.speedModifierWheeled ?? 1.0,
        speedModifierTracked: newMod.value.speedModifierTracked ?? 1.0,
        visibilityMeters: newMod.value.visibilityMeters || null,
        coverDefensePercent: newMod.value.coverDefensePercent ?? 0
    };
    try {
        await mapApi.updateModifier(props.map.id, created);
        modifiers.value.push(created);
        showAddModal.value = false;
    } catch (err: any) {
        alert('Помилка: ' + err.message);
    }
};

onUnmounted(() => {
    if (handler) handler.destroy();
    if (viewer) viewer.destroy();
});
</script>

<style scoped>
.editor-workspace {
    width: 100vw;
    height: 100vh;
    background: #020617;
    display: flex;
    flex-direction: column;
    overflow: hidden;
}
.editor-header {
    height: 52px;
    background: rgba(15, 23, 42, 0.95);
    border-bottom: 1px solid rgba(0, 168, 255, 0.3);
    padding: 0 20px;
    display: flex;
    justify-content: space-between;
    align-items: center;
    z-index: 100;
}
.header-left { display: flex; align-items: center; gap: 14px; }
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
.btn-back:hover { border-color: #00a8ff; color: #fff; }
.map-title { font-weight: bold; color: #00a8ff; font-family: monospace; font-size: 13px; }

/* Layer Stack Independent Toggles */
.layer-stack-bar {
    display: flex;
    align-items: center;
    gap: 6px;
    background: #090d16;
    padding: 4px 10px;
    border-radius: 4px;
    border: 1px solid #334155;
}
.stack-label { font-size: 10px; color: #64748b; font-family: monospace; }
.stack-btn {
    background: #1e293b;
    border: 1px solid #334155;
    color: #94a3b8;
    padding: 5px 12px;
    font-size: 11px;
    font-family: monospace;
    cursor: pointer;
    border-radius: 3px;
    transition: all 0.2s;
}
.stack-btn.active {
    background: #0284c7;
    border-color: #38bdf8;
    color: #fff;
    font-weight: bold;
}

.header-right { display: flex; align-items: center; gap: 12px; }
.spec-badge {
    background: rgba(0, 230, 118, 0.15);
    color: #00e676;
    border: 1px solid rgba(0, 230, 118, 0.4);
    padding: 4px 8px;
    border-radius: 3px;
    font-size: 10px;
    font-family: monospace;
}

.editor-body { display: flex; flex: 1; overflow: hidden; }
.map-viewport { flex: 1; position: relative; background: #020617; }
.cesium-map-canvas { width: 100%; height: 100%; }

/* Floating HUD on Map with 2D/3D Button */
.editor-floating-hud {
    position: absolute;
    top: 16px;
    left: 16px;
    background: rgba(15, 23, 42, 0.92);
    border: 1px solid rgba(0, 168, 255, 0.4);
    padding: 10px 16px;
    border-radius: 6px;
    font-size: 11px;
    color: #e2e8f0;
    font-family: monospace;
    z-index: 10;
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.6);
}
.hud-top-row {
    display: flex;
    align-items: center;
    gap: 16px;
    margin-bottom: 4px;
}
.hud-perspective-btn {
    background: #1e293b;
    border: 1px solid #00a8ff;
    color: #38bdf8;
    padding: 4px 10px;
    border-radius: 4px;
    font-size: 10px;
    font-family: monospace;
    font-weight: bold;
    cursor: pointer;
    transition: all 0.2s;
}
.hud-perspective-btn:hover { background: #0284c7; color: #fff; }
.hud-perspective-btn.is3d {
    background: #0284c7;
    color: #fff;
    border-color: #38bdf8;
    box-shadow: 0 0 8px rgba(56, 189, 248, 0.5);
}
.sub-coords { font-size: 9px; color: #94a3b8; }
.alt-text { color: #00e676; }

/* Clicked Feature Inspection Banner */
.feature-inspector-banner {
    position: absolute;
    bottom: 20px;
    left: 20px;
    background: rgba(15, 23, 42, 0.95);
    border: 1px solid #f1c40f;
    padding: 12px 16px;
    border-radius: 6px;
    z-index: 10;
    box-shadow: 0 4px 20px rgba(0,0,0,0.8);
    width: 380px;
    font-family: monospace;
}
.fib-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 6px; }
.fib-cat { font-size: 9px; padding: 2px 5px; border-radius: 2px; }
.fib-cat.road { background: #1e3a8a; color: #93c5fd; }
.fib-cat.vegetation { background: #064e3b; color: #6ee7b7; }
.fib-cat.building { background: #7f1d1d; color: #fca5a5; }
.fib-close { background: transparent; border: none; color: #94a3b8; cursor: pointer; }
.fib-details { display: flex; flex-direction: column; gap: 3px; font-size: 11px; color: #cbd5e1; }

/* Right Inspector Sidebar */
.inspector-sidebar {
    width: 480px;
    background: rgba(15, 23, 42, 0.96);
    border-left: 1px solid rgba(0, 168, 255, 0.2);
    display: flex;
    flex-direction: column;
    overflow: hidden;
}
.accordion-container {
    flex: 1;
    padding: 12px;
    overflow-y: auto;
    display: flex;
    flex-direction: column;
    gap: 12px;
}
.accordion-card {
    background: rgba(255, 255, 255, 0.02);
    border: 1px solid #334155;
    border-radius: 4px;
    overflow: hidden;
}
.acc-header {
    background: rgba(255, 255, 255, 0.04);
    padding: 10px 14px;
    display: flex;
    justify-content: space-between;
    align-items: center;
    cursor: pointer;
    border-bottom: 1px solid rgba(255, 255, 255, 0.05);
}
.acc-title { font-size: 12px; font-weight: bold; color: #00a8ff; font-family: monospace; }
.title-with-btn { display: flex; align-items: center; gap: 10px; }
.acc-arrow { font-size: 10px; color: #64748b; }
.acc-body { padding: 12px; display: flex; flex-direction: column; gap: 10px; }
.acc-desc { font-size: 11px; color: #94a3b8; margin: 0; }

.btn-add-mini {
    background: #047857;
    border: 1px solid #10b981;
    color: #fff;
    padding: 2px 8px;
    font-size: 9px;
    border-radius: 2px;
    cursor: pointer;
    font-family: monospace;
}

/* Sculpting tools */
.tool-label { font-size: 10px; color: #64748b; font-family: monospace; font-weight: bold; }
.sculpt-ops-grid { display: flex; flex-direction: column; gap: 4px; }
.op-btn {
    background: #1e293b;
    border: 1px solid #334155;
    color: #cbd5e1;
    padding: 8px 12px;
    font-size: 11px;
    font-family: monospace;
    cursor: pointer;
    border-radius: 3px;
    text-align: left;
}
.op-btn.active { background: #0284c7; border-color: #38bdf8; color: #fff; font-weight: bold; }
.slider-group { background: rgba(0, 0, 0, 0.25); padding: 8px; border-radius: 3px; }
.slider-header { display: flex; justify-content: space-between; font-size: 10px; color: #94a3b8; font-family: monospace; }
.val-badge { color: #00e676; }
.range-slider { width: 100%; margin-top: 4px; cursor: pointer; }

/* Textures list */
.layer-buttons-stacked { display: flex; flex-direction: column; gap: 4px; }
.composite-layer-btn {
    background: #1e293b;
    border: 1px solid #334155;
    color: #cbd5e1;
    padding: 8px;
    font-size: 11px;
    font-family: monospace;
    cursor: pointer;
    border-radius: 3px;
    text-align: left;
}
.composite-layer-btn.active { background: #0284c7; color: #fff; font-weight: bold; }

/* Objects list */
.category-filters { display: flex; flex-wrap: wrap; gap: 4px; }
.filter-pill {
    background: #1e293b;
    border: 1px solid #334155;
    color: #94a3b8;
    padding: 3px 6px;
    border-radius: 2px;
    font-size: 9px;
    cursor: pointer;
    font-family: monospace;
}
.filter-pill.active { background: #0284c7; color: #fff; border-color: #38bdf8; }
.modifiers-list { display: flex; flex-direction: column; gap: 8px; max-height: 280px; overflow-y: auto; }
.modifier-card { background: rgba(0, 0, 0, 0.2); border: 1px solid #334155; border-radius: 3px; padding: 8px; cursor: pointer; }
.modifier-card.highlighted { border-color: #f1c40f; background: rgba(241, 196, 15, 0.1); }
.card-head { display: flex; align-items: center; gap: 6px; }
.cat-badge { font-size: 8px; padding: 2px 4px; border-radius: 2px; font-weight: bold; }
.cat-badge.road { background: #1e3a8a; color: #93c5fd; }
.cat-badge.vegetation { background: #064e3b; color: #6ee7b7; }
.cat-badge.building { background: #7f1d1d; color: #fca5a5; }
.tag-label { font-weight: bold; color: #fff; font-size: 11px; font-family: monospace; }
.mod-desc { font-size: 9px; color: #64748b; margin: 2px 0 6px 0; }
.params-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 6px; }
.param-box label { font-size: 8px; color: #94a3b8; font-family: monospace; }
.num-field { width: 100%; box-sizing: border-box; background: #0b1120; border: 1px solid #334155; color: #fff; padding: 3px; font-size: 10px; font-family: monospace; border-radius: 2px; }
.card-actions { display: flex; justify-content: flex-end; margin-top: 6px; }
.btn-save-mod { background: #047857; border: 1px solid #10b981; color: #fff; padding: 3px 8px; font-size: 9px; cursor: pointer; font-family: monospace; }

.empty-stack-msg { text-align: center; color: #64748b; font-size: 11px; font-family: monospace; padding: 40px 10px; }

/* Modal */
.modal-overlay {
    position: fixed;
    top: 0; left: 0; width: 100vw; height: 100vh;
    background: rgba(0, 0, 0, 0.8);
    display: flex; align-items: center; justify-content: center;
    z-index: 10000;
}
.modal-box { background: #0f172a; border: 1px solid #00a8ff; width: 500px; padding: 20px; border-radius: 6px; }
.modal-head { display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid #334155; padding-bottom: 8px; margin-bottom: 12px; }
.modal-head h4 { margin: 0; color: #00a8ff; font-size: 12px; font-family: monospace; }
.btn-close-modal { background: transparent; border: none; color: #94a3b8; font-size: 16px; cursor: pointer; }
.modal-form { display: flex; flex-direction: column; gap: 10px; }
.form-grid-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
label { font-size: 10px; color: #94a3b8; font-family: monospace; }
select, input[type="text"], input[type="number"] {
    width: 100%; box-sizing: border-box; background: #0b1120;
    border: 1px solid #334155; color: #fff; padding: 6px 8px;
    font-size: 11px; font-family: monospace; border-radius: 3px;
}
.modal-foot { display: flex; justify-content: flex-end; gap: 10px; margin-top: 8px; }
.btn-cancel { background: transparent; border: 1px solid #475569; color: #94a3b8; padding: 5px 12px; font-size: 10px; cursor: pointer; }
.btn-submit { background: #0284c7; border: 1px solid #38bdf8; color: #fff; padding: 5px 14px; font-size: 10px; cursor: pointer; font-weight: bold; }
</style>