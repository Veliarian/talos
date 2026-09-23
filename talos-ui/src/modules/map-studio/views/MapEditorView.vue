<template>
    <div class="editor-workspace">
        <!-- Top Bar: Layer Visibility Toggles -->
        <header class="editor-header">
            <div class="header-left">
                <button type="button" class="btn-back" @click="$emit('back')">← НАЗАД ДО КАРТ</button>
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
        </header>

        <!-- Main Workspace -->
        <div class="editor-body">
            <!-- 3D/2D Viewport -->
            <div class="map-viewport">
                <div id="editorMapContainer" ref="editorMapContainer" class="cesium-map-canvas"></div>

                <!-- Floating HUD with 2D/3D Perspective Switcher -->
                <div class="editor-floating-hud">
                    <div class="hud-top-row">
                        <div>ТВД: <strong>{{ map.name }}</strong> ({{ map.sizeKm }}×{{ map.sizeKm }} км)</div>

                        <!-- Perspective Button -->
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

                <!-- Detailed Individual Feature Inspector Modal / Drawer -->
                <div v-if="inspectedFeature && layerStack.objects" class="feature-inspector-banner">
                    <div class="fib-header">
                        <span class="fib-cat" :class="inspectedFeature.category.toLowerCase()">{{ inspectedFeature.category }}</span>
                        <input v-model="inspectedFeature.name" class="fib-name-input" />
                        <button type="button" class="fib-close" @click="clearInspection">✕</button>
                    </div>

                    <div class="fib-status-row">
                        <label>ШАБЛОН ТТХ:</label>
                        <select class="status-select" @change="applyTemplateToSelectedFeature(($event.target as HTMLSelectElement).value)">
                            <option value="">-- Обрати шаблон із довідника --</option>
                            <option v-for="t in availableTemplates" :key="t.id" :value="t.id">
                                {{ t.description }} ({{ t.category }})
                            </option>
                        </select>
                    </div>

                    <div class="fib-meta">
                        OSM: <code>{{ inspectedFeature.typeKey }}={{ inspectedFeature.typeValue }}</code>
                        <span v-if="inspectedFeature.isCustomModified" class="custom-badge">ІНДИВІДУАЛЬНІ ТТХ</span>
                    </div>

                    <!-- Individual Overrides Form -->
                    <div class="fib-overrides-grid">
                        <div class="override-field">
                            <label>Шв. Колісні (оверрайд):</label>
                            <input
                                v-model.number="inspectedFeature.speedOverrideWheeled"
                                type="number"
                                step="0.05"
                                min="0"
                                max="1.5"
                                :placeholder="defaultSpeedWheeled"
                                class="num-field"
                            />
                        </div>
                        <div class="override-field">
                            <label>Шв. Гусеничні (оверрайд):</label>
                            <input
                                v-model.number="inspectedFeature.speedOverrideTracked"
                                type="number"
                                step="0.05"
                                min="0"
                                max="1.5"
                                :placeholder="defaultSpeedTracked"
                                class="num-field"
                            />
                        </div>
                        <div class="override-field">
                            <label>Захист укриття (%):</label>
                            <input
                                v-model.number="inspectedFeature.coverOverride"
                                type="number"
                                step="5"
                                min="0"
                                max="95"
                                :placeholder="defaultCoverDefense"
                                class="num-field"
                            />
                        </div>
                        <div class="override-field">
                            <label>Видимість (м):</label>
                            <input
                                v-model.number="inspectedFeature.visibilityOverride"
                                type="number"
                                step="10"
                                min="0"
                                max="5000"
                                :placeholder="defaultVisibility"
                                class="num-field"
                            />
                        </div>
                    </div>

                    <div class="fib-notes-row">
                        <input v-model="inspectedFeature.customNotes" type="text" placeholder="Тактичні примітки (наприклад, заміновано ТМ-62)" class="text-field" />
                    </div>

                    <div class="fib-actions">
                        <button type="button" class="btn-reset-overrides" @click="resetFeatureOverrides">Скинути до базових</button>
                        <button type="button" class="btn-save-feature" :disabled="savingFeature" @click="saveCurrentFeature">
                            {{ savingFeature ? 'ЗБЕРЕЖЕННЯ...' : 'ЗБЕРЕГТИ ОБ\'ЄКТ' }}
                        </button>
                    </div>
                </div>
            </div>

            <!-- Right Dynamic Accordion Sidebar -->
            <aside class="inspector-sidebar">
                <div class="accordion-container">

                    <!-- ACCORDION BLOCK 1: ELEVATION SCULPTING (Visible only if Elevation layer is ON) -->
                    <div v-if="layerStack.elevation" class="accordion-card">
                        <div class="acc-header" role="button" tabindex="0" @click="accordionOpen.elevation = !accordionOpen.elevation">
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
                                    v-model.number="terrainScale"
                                    type="range"
                                    min="1"
                                    max="5"
                                    step="0.5"
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
                                <input v-model.number="brushRadius" type="range" min="20" max="400" step="10" class="range-slider" />
                            </div>

                            <div class="slider-group">
                                <div class="slider-header">
                                    <span>Глибина / Висота (&Delta;h):</span>
                                    <strong class="val-badge">{{ brushDelta }} м</strong>
                                </div>
                                <input v-model.number="brushDelta" type="range" min="5" max="100" step="5" class="range-slider" />
                            </div>

                            <div v-if="sculptLoading" class="sculpt-status-box">
                                <span class="pulse-dot"></span> Оновлення рельєфу в ході...
                            </div>
                        </div>
                    </div>

                    <!-- ACCORDION BLOCK 2: BASEMAP TEXTURES (Visible only if Textures layer is ON) -->
                    <div v-if="layerStack.texture" class="accordion-card">
                        <div class="acc-header" role="button" tabindex="0" @click="accordionOpen.texture = !accordionOpen.texture">
                            <span class="acc-title">🗺️ ПІДКЛАДКА ТА ТЕКСТУРИ</span>
                            <span class="acc-arrow">{{ accordionOpen.texture ? '▲' : '▼' }}</span>
                        </div>

                        <div v-show="accordionOpen.texture" class="acc-body">
                            <label class="tool-label">ОБЕРІТЬ ТИП ПІДКЛАДКИ:</label>
                            <div class="layer-buttons-stacked">
                                <button
                                    v-for="l in map.layers"
                                    :key="l.id"
                                    type="button"
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
                        <div class="acc-header" role="button" tabindex="0" @click="accordionOpen.objects = !accordionOpen.objects">
                            <div class="title-with-btn">
                                <span class="acc-title">🏘️ КАТЕГОРІЇ ТА ШАБЛОНИ ТТХ</span>
                                <button type="button" class="btn-add-mini" @click.stop="showAddModal = true">+ ДОДАТИ</button>
                            </div>
                            <span class="acc-arrow">{{ accordionOpen.objects ? '▲' : '▼' }}</span>
                        </div>

                        <div v-show="accordionOpen.objects" class="acc-body">
                            <div class="category-filters">
                                <button
                                    v-for="cat in (['ALL', 'ROAD', 'VEGETATION', 'BUILDING', 'WATER', 'SOIL'] as const)"
                                    :key="cat"
                                    type="button"
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
                                    role="button"
                                    tabindex="0"
                                    @click="highlightObjectsOnMap(mod)"
                                >
                                    <div class="card-head">
                                        <span class="cat-badge" :class="mod.category.toLowerCase()">{{ mod.category }}</span>
                                        <span class="tag-label">{{ mod.osmKey }}={{ mod.osmValue }}</span>
                                    </div>
                                    <div class="mod-desc">{{ mod.description || 'Не налаштовано (призначте шаблон)' }}</div>

                                    <!-- Quick Template Selector from Global Library -->
                                    <div class="template-selector-row" @click.stop>
                                        <label>ПРИЗНАЧИТИ ШАБЛОН:</label>
                                        <div class="select-with-btn">
                                            <select v-model="selectedTemplateForMod[mod.id]" class="template-dropdown">
                                                <option value="">-- Оберіть шаблон із довідника --</option>
                                                <option v-for="t in availableTemplates" :key="t.id" :value="t.id">
                                                    {{ t.description }} ({{ t.category }})
                                                </option>
                                            </select>
                                            <button
                                                type="button"
                                                class="btn-apply-tmpl"
                                                :disabled="!selectedTemplateForMod[mod.id]"
                                                @click="applyTemplateToCategory(mod, selectedTemplateForMod[mod.id])"
                                            >
                                                ЗАСТОСУВАТИ
                                            </button>
                                        </div>
                                    </div>

                                    <div class="params-grid" @click.stop>
                                        <div class="param-box">
                                            <label>Шв. Колісні:</label>
                                            <input v-model.number="mod.speedModifierWheeled" type="number" step="0.05" min="0" max="1.5" class="num-field" />
                                        </div>
                                        <div class="param-box">
                                            <label>Шв. Гусеничні:</label>
                                            <input v-model.number="mod.speedModifierTracked" type="number" step="0.05" min="0" max="1.5" class="num-field" />
                                        </div>
                                        <div class="param-box">
                                            <label>Видимість (м):</label>
                                            <input v-model.number="mod.visibilityMeters" type="number" step="10" min="0" max="5000" placeholder="Без меж" class="num-field" />
                                        </div>
                                        <div class="param-box">
                                            <label>Захист (%):</label>
                                            <input v-model.number="mod.coverDefensePercent" type="number" step="5" min="0" max="95" class="num-field" />
                                        </div>
                                    </div>

                                    <div class="card-actions" @click.stop>
                                        <button type="button" class="btn-save-mod" @click="saveModifier(mod)">ЗБЕРЕГТИ КАТЕГОРІЮ</button>
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
            </aside>
        </div>

        <!-- Modal to Add New Custom Object -->
        <div v-if="showAddModal" class="modal-overlay" role="dialog" aria-modal="true" @click.self="showAddModal = false">
            <div class="modal-box">
                <div class="modal-head">
                    <h4>ДОДАТИ НОВИЙ ТИП ОБ'ЄКТА / ПОКРИТТЯ</h4>
                    <button type="button" class="btn-close-modal" aria-label="Close" @click="showAddModal = false">✕</button>
                </div>

                <form class="modal-form" @submit.prevent="createNewModifier">
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
    Cartesian2,
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
    ConstantProperty,
    EllipsoidTerrainProvider,
    HeightReference,
    CustomHeightmapTerrainProvider,
    GeographicTilingScheme,
    defined,
    Material,
    Entity
} from 'cesium';
import { mapApi } from '@/modules/map-studio/mapApi';
import type {
    MapDetailDto,
    SurfaceModifierDto,
    SculptOperation,
    ModifierCategory,
    FeatureStatus, DefaultModifierDto
} from '@/modules/map-studio/types';

interface InspectedFeatureState {
    id: string;
    osmId?: number | null;
    name: string;
    category: string;
    typeKey: string;
    typeValue: string;
    status: FeatureStatus;
    isCustomModified: boolean;
    speedOverrideWheeled: number | null;
    speedOverrideTracked: number | null;
    visibilityOverride: number | null;
    coverOverride: number | null;
    customNotes: string;
    cesiumEntity?: Entity;
}

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
const inspectedFeature = ref<InspectedFeatureState | null>(null);
const centerAltitudeDisplay = ref<number>(120);
const savingFeature = ref(false);
const selectedTemplateForMod = reactive<Record<string, string>>({});

// Sculpting state
const sculptOp = ref<SculptOperation>('DIG');
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
    (viewer.scene as unknown as { verticalExaggeration: number }).verticalExaggeration = terrainScale.value;
};

// Fast in-memory cache to prevent spamming backend with duplicate grid queries
const terrainGridCache = new Map<string, Float32Array>();

const createLocalTerrainProvider = () => {
    return new CustomHeightmapTerrainProvider({
        width: 32,
        height: 32,
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

            const url = `/api/maps/${props.map.id}/terrain/grid?minLat=${minLat}&maxLat=${maxLat}&minLon=${minLon}&maxLon=${maxLon}&width=32&height=32`;

            try {
                const response = await fetch(url);
                if (!response.ok) return new Float32Array(32 * 32).fill(120.0);
                const buffer = await response.arrayBuffer();
                const floatArray = new Float32Array(buffer);
                terrainGridCache.set(cacheKey, floatArray);
                centerAltitudeDisplay.value = Math.round(floatArray[512] || 120.0);
                return floatArray;
            } catch {
                return new Float32Array(32 * 32).fill(120.0);
            }
        }
    });
};

const availableTemplates = ref<DefaultModifierDto[]>([]);

const loadAvailableTemplates = async () => {
    try {
        availableTemplates.value = await mapApi.getTemplates();
    } catch (err) {
        console.warn('Could not load global templates:', err);
    }
};

// Shared static material singletons (Enables Cesium GPU draw-call batching)
const MAT_ROAD_OPERATIONAL = new ColorMaterialProperty(Color.fromCssColorString('#f59e0b').withAlpha(0.9));
const MAT_ROAD_DESTROYED = new ColorMaterialProperty(Color.fromCssColorString('#475569').withAlpha(0.6));
const MAT_ROAD_MINED = new ColorMaterialProperty(Color.fromCssColorString('#dc2626').withAlpha(0.9));
const MAT_ROAD_CHECKPOINT = new ColorMaterialProperty(Color.fromCssColorString('#f97316').withAlpha(0.95));

const MAT_FOREST = new ColorMaterialProperty(Color.fromCssColorString('#15803d').withAlpha(0.55)); // Deep tactical green
const MAT_MEADOW = new ColorMaterialProperty(Color.fromCssColorString('#65a30d').withAlpha(0.25)); // Olive meadow
const MAT_SETTLEMENT = new ColorMaterialProperty(Color.fromCssColorString('#64748b').withAlpha(0.35)); // Gray village footprint
const MAT_BUILDING = new ColorMaterialProperty(Color.fromCssColorString('#ef4444').withAlpha(0.7)); // Building footprint
const MAT_WATER_LINE = new ColorMaterialProperty(Color.fromCssColorString('#0284c7').withAlpha(0.9));
const MAT_WATER_POLYGON = new ColorMaterialProperty(Color.fromCssColorString('#0284c7').withAlpha(0.65));
const MAT_RAILWAY = new ColorMaterialProperty(Color.fromCssColorString('#f8fafc').withAlpha(0.8));
const MAT_SOIL = new ColorMaterialProperty(Color.fromCssColorString('#475569').withAlpha(0.2));

// Shared constant properties for width & outline (Eliminates per-frame CPU callbacks)
const CONST_WIDTH_ROAD = new ConstantProperty(3.5);
const CONST_WIDTH_STREAM = new ConstantProperty(2.5);
const CONST_WIDTH_RAILWAY = new ConstantProperty(2.0);
const CONST_WIDTH_CHECKPOINT = new ConstantProperty(6.0);
const CONST_OUTLINE_FALSE = new ConstantProperty(false);

onMounted(async () => {
    if (!editorMapContainer.value) return;

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

    (viewer.scene as unknown as { verticalExaggeration: number }).verticalExaggeration = 2.5;
    viewer.scene.globe.enableLighting = true;
    viewer.scene.globe.baseColor = Color.fromCssColorString('#1e293b');
    if (viewer.scene.skyAtmosphere) viewer.scene.skyAtmosphere.show = false;
    viewer.scene.globe.showGroundAtmosphere = false;
    viewer.scene.backgroundColor = Color.fromCssColorString('#020617');

    const theaterRect = Rectangle.fromDegrees(
        props.map.minLon, props.map.minLat, props.map.maxLon, props.map.maxLat
    );
    viewer.scene.globe.cartographicLimitRectangle = theaterRect;
    viewer.scene.screenSpaceCameraController.minimumZoomDistance = 80.0;
    viewer.scene.screenSpaceCameraController.maximumZoomDistance = Math.max(props.map.sizeKm * 1600.0, 26000.0);

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

    fitCamera(false);

    await loadVectorFeatures();
    if (props.map.layers && props.map.layers.length > 0) {
        currentLayerType.value = props.map.layers[0].layerType;
    }
    applyLayerStack();
    await loadModifiers();
    await loadAvailableTemplates();
});

const applyTemplateToCategory = async (mod: SurfaceModifierDto, templateId: string) => {
    if (!templateId) return;
    try {
        await mapApi.applyTemplateToMap(props.map.id, templateId);
        await loadModifiers();
        alert(`Шаблон успішно застосовано до типу: ${mod.osmValue}`);
    } catch (err) {
        alert('Помилка застосування шаблону: ' + err);
    }
};

const applyTemplateToSelectedFeature = (templateId: string) => {
    if (!inspectedFeature.value || !templateId) return;
    const tmpl = availableTemplates.value.find(t => t.id === templateId);
    if (tmpl) {
        inspectedFeature.value.speedOverrideWheeled = tmpl.speedModifierWheeled;
        inspectedFeature.value.speedOverrideTracked = tmpl.speedModifierTracked;
        inspectedFeature.value.visibilityOverride = tmpl.visibilityMeters;
        inspectedFeature.value.coverOverride = tmpl.coverDefensePercent;
    }
};

const fitCamera = (is3d: boolean) => {
    if (!viewer) return;

    const theaterRect = Rectangle.fromDegrees(
        props.map.minLon, props.map.minLat, props.map.maxLon, props.map.maxLat
    );

    viewer.scene.screenSpaceCameraController.enableRotate = true;
    viewer.scene.screenSpaceCameraController.enableTranslate = true;
    viewer.scene.screenSpaceCameraController.enableZoom = true;

    if (!is3d) {
        // 2D MODE: Use flat terrain provider (zero HTTP calls, flawless 60 FPS)
        viewer.terrainProvider = new EllipsoidTerrainProvider();
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
        // 3D MODE: Mount DEM heightmap
        viewer.terrainProvider = createLocalTerrainProvider();
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

const applyLayerStack = () => {
    if (!viewer) return;

    if (layerStack.elevation && !layerStack.texture) {
        const bands = [
            {
                entries: [
                    { height: 0.0, color: Color.fromCssColorString('#022c22') },
                    { height: 90.0, color: Color.fromCssColorString('#047857') },
                    { height: 130.0, color: Color.fromCssColorString('#059669') },
                    { height: 200.0, color: Color.fromCssColorString('#10b981') },
                    { height: 300.0, color: Color.fromCssColorString('#84cc16') },
                    { height: 450.0, color: Color.fromCssColorString('#facc15') },
                    { height: 650.0, color: Color.fromCssColorString('#f97316') },
                    { height: 900.0, color: Color.fromCssColorString('#dc2626') },
                    { height: 1200.0, color: Color.fromCssColorString('#78350f') },
                    { height: 1600.0, color: Color.fromCssColorString('#f8fafc') }
                ]
            }
        ];
        viewer.scene.globe.material = createElevationBandMaterial({
            scene: viewer.scene,
            layers: bands
        });
    } else {
        viewer.scene.globe.material = undefined as unknown as Material;
    }

    if (layerStack.texture) {
        mountBaseLayer(currentLayerType.value);
    } else {
        if (currentImageryLayer) {
            viewer.imageryLayers.remove(currentImageryLayer);
            currentImageryLayer = null;
        }
    }

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

    const tileUrl = `/api/maps/${props.map.id}/tiles/${layerType.toLowerCase()}/{z}/{x}/{y}.png`;
    const layerMeta = props.map.layers?.find(l => l.layerType.toUpperCase() === layerType.toUpperCase());
    const minZ = layerMeta ? layerMeta.minZoom : 8;
    // Allow Cesium to request crystal-clear street-level tiles up to Zoom 19 (Google HD)
    const maxZ = 19;

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
            heightReference: HeightReference.CLAMP_TO_GROUND,
            show: new CallbackProperty(() => layerStack.elevation && currentMousePosition !== null, false)
        }
    });
};

const setupMouseInteractions = () => {
    if (!viewer) return;
    handler = new ScreenSpaceEventHandler(viewer.scene.canvas);

    handler.setInputAction((movement: { endPosition: Cartesian2 }) => {
        if (!layerStack.elevation) return;
        const ray = viewer!.camera.getPickRay(movement.endPosition);
        if (!ray) return;
        const cartesian = viewer!.scene.globe.pick(ray, viewer!.scene);
        if (cartesian) currentMousePosition = cartesian;
    }, ScreenSpaceEventType.MOUSE_MOVE);

    handler.setInputAction(async (event: { position: Cartesian2 }) => {
        if (layerStack.elevation) {
            const ray = viewer!.camera.getPickRay(event.position);
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
                viewer!.terrainProvider = createLocalTerrainProvider();
            } catch (err: unknown) {
                const message = err instanceof Error ? err.message : String(err);
                alert('Помилка скульптингу: ' + message);
            } finally {
                sculptLoading.value = false;
            }
        } else if (layerStack.objects) {
            const picked = viewer!.scene.pick(event.position);
            if (defined(picked) && picked.id && picked.id.properties) {
                const p = picked.id.properties;
                const featureId = p.id ? String(p.id.getValue()) : '';

                inspectedFeature.value = {
                    id: featureId,
                    osmId: p.osmId ? Number(p.osmId.getValue()) : null,
                    name: p.name ? p.name.getValue() : 'Об’єкт без назви',
                    category: p.category ? p.category.getValue() : 'UNKNOWN',
                    typeKey: p.typeKey ? p.typeKey.getValue() : '',
                    typeValue: p.typeValue ? p.typeValue.getValue() : '',
                    status: (p.status ? p.status.getValue() : 'OPERATIONAL') as FeatureStatus,
                    isCustomModified: p.isCustomModified ? Boolean(p.isCustomModified.getValue()) : false,
                    speedOverrideWheeled: p.speedOverrideWheeled ? Number(p.speedOverrideWheeled.getValue()) : null,
                    speedOverrideTracked: p.speedOverrideTracked ? Number(p.speedOverrideTracked.getValue()) : null,
                    visibilityOverride: p.visibilityOverride ? Number(p.visibilityOverride.getValue()) : null,
                    coverOverride: p.coverOverride ? Number(p.coverOverride.getValue()) : null,
                    customNotes: p.customNotes ? String(p.customNotes.getValue()) : '',
                    cesiumEntity: picked.id
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

const applyEntityStyling = (entity: Entity) => {
    const category = entity.properties?.category?.getValue();
    const status = entity.properties?.status?.getValue() as FeatureStatus | undefined;
    const typeKey = entity.properties?.typeKey?.getValue();
    const typeValue = entity.properties?.typeValue?.getValue();

    // 1. ROADS
    if (category === 'ROAD' && entity.polyline) {
        if (typeKey === 'railway') {
            entity.polyline.material = MAT_RAILWAY;
            entity.polyline.width = CONST_WIDTH_RAILWAY;
            return;
        }
        if (status === 'DESTROYED') entity.polyline.material = MAT_ROAD_DESTROYED;
        else if (status === 'MINED') entity.polyline.material = MAT_ROAD_MINED;
        else if (status === 'CHECKPOINT') {
            entity.polyline.material = MAT_ROAD_CHECKPOINT;
            entity.polyline.width = CONST_WIDTH_CHECKPOINT;
            return;
        } else {
            entity.polyline.material = MAT_ROAD_OPERATIONAL;
        }
        entity.polyline.width = CONST_WIDTH_ROAD;
        return;
    }

    // 2. WATER
    if (category === 'WATER') {
        if (entity.polyline) {
            entity.polyline.material = MAT_WATER_LINE;
            entity.polyline.width = CONST_WIDTH_STREAM;
        } else if (entity.polygon) {
            entity.polygon.material = MAT_WATER_POLYGON;
            entity.polygon.outline = CONST_OUTLINE_FALSE as any;
        }
        return;
    }

    // 3. VEGETATION & LANDUSE
    if (category === 'VEGETATION' && entity.polygon) {
        if (typeValue === 'wood' || typeValue === 'forest') {
            entity.polygon.material = MAT_FOREST;
        } else {
            entity.polygon.material = MAT_MEADOW;
        }
        entity.polygon.outline = CONST_OUTLINE_FALSE as any;
        return;
    }

    // 4. BUILDINGS & VILLAGE ZONES
    if (category === 'BUILDING' && entity.polygon) {
        if (typeValue === 'residential' || typeValue === 'industrial') {
            entity.polygon.material = MAT_SETTLEMENT;
        } else {
            entity.polygon.material = MAT_BUILDING;
        }
        entity.polygon.outline = CONST_OUTLINE_FALSE as any;
        return;
    }

    // 5. DEFAULT OPEN GROUND
    if (entity.polygon) {
        entity.polygon.material = MAT_SOIL;
        entity.polygon.outline = CONST_OUTLINE_FALSE as any;
    }
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
            applyEntityStyling(entity);
        }
        viewer.dataSources.add(vectorDataSource);
    } catch (err: unknown) {
        console.warn('[TALOS STUDIO] Could not load vector features:', err);
    }
};

const highlightObjectsOnMap = (mod: SurfaceModifierDto) => {
    if (!vectorDataSource) return;
    activeHighlightType.value = mod.osmValue;
    const highlightMaterial = new ColorMaterialProperty(Color.CYAN);
    const highlightWidth = new ConstantProperty(6);

    const entities = vectorDataSource.entities.values;
    for (const entity of entities) {
        const isMatch = (entity.properties?.typeValue?.getValue() === mod.osmValue);
        if (isMatch) {
            if (entity.polyline) {
                entity.polyline.material = highlightMaterial;
                entity.polyline.width = highlightWidth;
            }
            if (entity.polygon) {
                entity.polygon.material = highlightMaterial;
            }
        } else {
            applyEntityStyling(entity);
        }
    }
};

const resetMapHighlights = () => {
    if (!vectorDataSource) return;
    const entities = vectorDataSource.entities.values;
    for (const entity of entities) {
        applyEntityStyling(entity);
    }
};

const inspectedModifier = computed(() => {
    if (!inspectedFeature.value) return null;
    return modifiers.value.find(m => m.osmValue === inspectedFeature.value?.typeValue) || null;
});

const defaultSpeedWheeled = computed(() => inspectedModifier.value ? String(inspectedModifier.value.speedModifierWheeled) : '1.0');
const defaultSpeedTracked = computed(() => inspectedModifier.value ? String(inspectedModifier.value.speedModifierTracked) : '1.0');
const defaultCoverDefense = computed(() => inspectedModifier.value ? String(inspectedModifier.value.coverDefensePercent) : '0');
const defaultVisibility = computed(() => (inspectedModifier.value?.visibilityMeters) ? String(inspectedModifier.value.visibilityMeters) : 'Без меж');

const resetFeatureOverrides = () => {
    if (!inspectedFeature.value) return;
    inspectedFeature.value.speedOverrideWheeled = null;
    inspectedFeature.value.speedOverrideTracked = null;
    inspectedFeature.value.coverOverride = null;
    inspectedFeature.value.visibilityOverride = null;
    inspectedFeature.value.status = 'OPERATIONAL';
};

const saveCurrentFeature = async () => {
    if (!inspectedFeature.value || !inspectedFeature.value.id) return;
    try {
        savingFeature.value = true;
        await mapApi.updateFeature(props.map.id, inspectedFeature.value.id, {
            name: inspectedFeature.value.name,
            status: inspectedFeature.value.status,
            speedModifierOverrideWheeled: inspectedFeature.value.speedOverrideWheeled,
            speedModifierOverrideTracked: inspectedFeature.value.speedOverrideTracked,
            visibilityOverride: inspectedFeature.value.visibilityOverride,
            coverDefenseOverride: inspectedFeature.value.coverOverride,
            customNotes: inspectedFeature.value.customNotes
        });

        // Update Cesium entity state dynamically without full reload
        if (inspectedFeature.value.cesiumEntity && inspectedFeature.value.cesiumEntity.properties) {
            inspectedFeature.value.cesiumEntity.properties.status = inspectedFeature.value.status;
            inspectedFeature.value.cesiumEntity.properties.name = inspectedFeature.value.name;
            applyEntityStyling(inspectedFeature.value.cesiumEntity);
        }

        inspectedFeature.value.isCustomModified = true;
        alert(`Збережено індивідуальні параметри для: "${inspectedFeature.value.name}"`);
    } catch (err: unknown) {
        const message = err instanceof Error ? err.message : String(err);
        alert('Помилка збереження об\'єкта: ' + message);
    } finally {
        savingFeature.value = false;
    }
};

const loadModifiers = async () => {
    try {
        modifiers.value = await mapApi.getModifiers(props.map.id);
    } catch (err: unknown) {
        console.warn('[TALOS STUDIO] Could not load modifiers:', err);
    }
};

watch(() => props.map.id, loadModifiers);

const filteredModifiers = computed(() => {
    if (selectedCategory.value === 'ALL') return modifiers.value;
    return modifiers.value.filter(m => m.category === selectedCategory.value);
});

const saveModifier = async (mod: SurfaceModifierDto) => {
    try {
        await mapApi.updateModifier(props.map.id, mod);
        alert(`Оновлено ТТХ категорії: ${mod.osmKey}=${mod.osmValue}`);
    } catch (err: unknown) {
        const message = err instanceof Error ? err.message : String(err);
        alert('Помилка: ' + message);
    }
};

const createNewModifier = async () => {
    if (!newMod.value.osmKey || !newMod.value.osmValue) return;
    const created: SurfaceModifierDto = {
        id: crypto.randomUUID(),
        category: (newMod.value.category || 'BUILDING') as ModifierCategory,
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
    } catch (err: unknown) {
        const message = err instanceof Error ? err.message : String(err);
        alert('Помилка: ' + message);
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

/* Clicked Feature Inspection Drawer */
.feature-inspector-banner {
    position: absolute;
    bottom: 20px;
    left: 20px;
    background: rgba(15, 23, 42, 0.97);
    border: 1px solid #f1c40f;
    padding: 14px 18px;
    border-radius: 6px;
    z-index: 10;
    box-shadow: 0 8px 30px rgba(0, 0, 0, 0.85);
    width: 420px;
    font-family: monospace;
    display: flex;
    flex-direction: column;
    gap: 8px;
}
.fib-header { display: flex; align-items: center; gap: 8px; }
.fib-name-input {
    flex: 1;
    background: #0b1120;
    border: 1px solid #334155;
    color: #fff;
    font-size: 11px;
    font-family: monospace;
    font-weight: bold;
    padding: 4px 6px;
    border-radius: 3px;
}
.fib-cat { font-size: 9px; padding: 2px 5px; border-radius: 2px; font-weight: bold; }
.fib-cat.road { background: #1e3a8a; color: #93c5fd; }
.fib-cat.vegetation { background: #064e3b; color: #6ee7b7; }
.fib-cat.building { background: #7f1d1d; color: #fca5a5; }
.fib-cat.water { background: #0c4a6e; color: #7dd3fc; }
.fib-close { background: transparent; border: none; color: #94a3b8; cursor: pointer; font-size: 14px; }

.fib-status-row { display: flex; align-items: center; gap: 10px; font-size: 11px; }
.status-select {
    flex: 1;
    background: #0b1120;
    border: 1px solid #334155;
    color: #fff;
    padding: 5px;
    font-size: 11px;
    font-family: monospace;
    border-radius: 3px;
}
.status-select.operational { border-color: #10b981; }
.status-select.destroyed { border-color: #64748b; color: #94a3b8; }
.status-select.mined { border-color: #ef4444; color: #f87171; font-weight: bold; }
.status-select.checkpoint { border-color: #f97316; color: #fb923c; font-weight: bold; }

.fib-meta { font-size: 10px; color: #64748b; display: flex; justify-content: space-between; align-items: center; }
.custom-badge { background: #0284c7; color: #fff; font-size: 8px; padding: 2px 4px; border-radius: 2px; }

.fib-overrides-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 6px; margin-top: 2px; }
.override-field label { font-size: 9px; color: #94a3b8; display: block; margin-bottom: 2px; }
.text-field {
    width: 100%;
    box-sizing: border-box;
    background: #0b1120;
    border: 1px solid #334155;
    color: #fff;
    padding: 5px;
    font-size: 10px;
    font-family: monospace;
    border-radius: 2px;
}

.fib-actions { display: flex; justify-content: space-between; align-items: center; margin-top: 6px; }
.btn-reset-overrides {
    background: transparent;
    border: 1px solid #475569;
    color: #94a3b8;
    padding: 4px 8px;
    font-size: 9px;
    cursor: pointer;
    font-family: monospace;
    border-radius: 3px;
}
.btn-save-feature {
    background: #0284c7;
    border: 1px solid #38bdf8;
    color: #fff;
    padding: 5px 14px;
    font-size: 10px;
    font-weight: bold;
    cursor: pointer;
    font-family: monospace;
    border-radius: 3px;
}

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
.template-selector-row {
    margin: 6px 0 8px 0;
    background: rgba(0, 0, 0, 0.3);
    padding: 6px;
    border-radius: 3px;
    border-left: 2px solid #00a8ff;
}
.template-selector-row label {
    font-size: 8px;
    color: #00a8ff;
    font-family: monospace;
    display: block;
    margin-bottom: 3px;
}
.select-with-btn {
    display: flex;
    gap: 4px;
}
.template-dropdown {
    flex: 1;
    background: #0b1120;
    border: 1px solid #334155;
    color: #fff;
    font-size: 10px;
    font-family: monospace;
    padding: 3px;
    border-radius: 2px;
}
.btn-apply-tmpl {
    background: #0284c7;
    border: 1px solid #38bdf8;
    color: #fff;
    font-size: 9px;
    font-weight: bold;
    font-family: monospace;
    padding: 3px 8px;
    cursor: pointer;
    border-radius: 2px;
}
.btn-apply-tmpl:disabled {
    opacity: 0.4;
    cursor: not-allowed;
}
</style>