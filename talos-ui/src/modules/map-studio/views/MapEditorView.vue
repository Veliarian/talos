<template>
    <div class="editor-workspace">
        <!-- Top Bar -->
        <div class="editor-header">
            <div class="header-left">
                <button class="btn-back" @click="$emit('back')">← НАЗАД ДО КАРТ</button>
                <h3>РЕДАКТОР ТВД: <span class="map-highlight">{{ map.name }}</span></h3>
            </div>

            <!-- Baselayer Switcher -->
            <div class="header-center" v-if="map.layers && map.layers.length > 0">
                <span class="ctrl-label">ПІДКЛАДКА:</span>
                <div class="layer-buttons">
                    <button
                        v-for="l in map.layers"
                        :key="l.id"
                        class="layer-toggle-btn"
                        :class="{ active: currentLayerType === l.layerType }"
                        @click="switchEditorLayer(l.layerType)"
                    >
                        {{ l.layerType }}
                    </button>
                </div>
            </div>

            <div class="header-right">
                <!-- Toggle Vectors Layer on/off -->
                <button
                    class="btn-toggle-vectors"
                    :class="{ active: showVectors }"
                    @click="toggleVectors"
                >
                    {{ showVectors ? '👁️ ВЕКТОРНИЙ ШАР: УВІМК' : 'ВЕКТОРНИЙ ШАР: ВИМК' }}
                </button>
                <span class="spec-badge green">100% OFFLINE</span>
            </div>
        </div>

        <!-- Main Workspace: Split Screen -->
        <div class="editor-body">
            <!-- 1. Left/Center: Live 3D Cesium Map with Interactive Vectors -->
            <div class="map-viewport">
                <div id="editorMapContainer" ref="editorMapContainer" class="cesium-map-canvas"></div>

                <!-- Floating HUD -->
                <div class="editor-floating-hud">
                    <div>ТВД: <strong>{{ map.name }}</strong> ({{ map.sizeKm * map.sizeKm }} км²)</div>
                    <div class="sub-coords">Підказка: Клікніть на будь-яку дорогу чи будівлю на карті для інспекції</div>
                </div>

                <!-- Inspected Feature Banner (shows when clicking an object on the map) -->
                <div v-if="inspectedFeature" class="feature-inspector-banner">
                    <div class="fib-header">
                        <span class="fib-cat" :class="inspectedFeature.category.toLowerCase()">{{ inspectedFeature.category }}</span>
                        <strong>{{ inspectedFeature.name }}</strong>
                        <button class="fib-close" @click="clearInspection">✕</button>
                    </div>
                    <div class="fib-details">
                        <span>OSM Тег: <code>{{ inspectedFeature.typeKey }}={{ inspectedFeature.typeValue }}</code></span>
                        <span v-if="inspectedModifier">Швидкість колісних: <strong>{{ (inspectedModifier.speedModifierWheeled * 100).toFixed(0) }}%</strong></span>
                        <span v-if="inspectedModifier">Захист укриття: <strong>{{ inspectedModifier.coverDefensePercent }}%</strong></span>
                    </div>
                </div>
            </div>

            <!-- 2. Right: Surface & Objects Characteristics Inspector -->
            <div class="inspector-sidebar">
                <div class="sidebar-header">
                    <div class="sidebar-title-row">
                        <h4>ХАРАКТЕРИСТИКИ ОБ'ЄКТІВ</h4>
                        <button class="btn-add-custom" @click="showAddModal = true">+ ДОДАТИ ТИП</button>
                    </div>
                    <p class="sidebar-desc">
                        Клікніть на тип об'єкта, щоб <strong>підсвітити всі його ділянки на карті</strong>.
                    </p>

                    <!-- Category filter pills -->
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
                </div>

                <!-- Modifiers List -->
                <div class="modifiers-scroll-area">
                    <div v-if="filteredModifiers.length === 0" class="empty-mods">
                        Немає об'єктів у цій категорії.
                    </div>

                    <div
                        v-for="mod in filteredModifiers"
                        :key="mod.id"
                        class="modifier-card"
                        :class="{ highlighted: activeHighlightType === mod.osmValue }"
                        @click="highlightObjectsOnMap(mod)"
                    >
                        <div class="card-head">
                            <span class="cat-badge" :class="mod.category.toLowerCase()">{{ mod.category }}</span>
                            <span class="tag-label">{{ mod.osmKey }} = {{ mod.osmValue }}</span>
                            <button class="btn-locate" title="Підсвітити всі на карті">⌖ ПІДСВІТИТИ</button>
                        </div>

                        <div class="mod-desc">{{ mod.description || 'Без опису' }}</div>

                        <!-- Parameters Grid -->
                        <div class="params-grid" @click.stop>
                            <div class="param-box">
                                <label>Шв. Колісні (x):</label>
                                <input type="number" step="0.05" min="0" max="1.5" v-model.number="mod.speedModifierWheeled" class="num-field" />
                            </div>
                            <div class="param-box">
                                <label>Шв. Гусеничні (x):</label>
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
                            <option value="BUILDING">BUILDING (Будівля / Споруда / Капонір)</option>
                            <option value="ROAD">ROAD (Дорога / Траса / Залізничний насип)</option>
                            <option value="VEGETATION">VEGETATION (Ліс / Чагарник / Лісосмуга)</option>
                            <option value="WATER">WATER (Річка / Болото / Водойма)</option>
                            <option value="SOIL">SOIL (Ґрунт / Рілля / Пісок)</option>
                        </select>
                    </div>

                    <div class="form-grid-2">
                        <div>
                            <label>OSM Ключ (Key):</label>
                            <input v-model="newMod.osmKey" type="text" required placeholder="наприклад, highway або building" />
                        </div>
                        <div>
                            <label>OSM Значення (Value):</label>
                            <input v-model="newMod.osmValue" type="text" required placeholder="наприклад, primary або residential" />
                        </div>
                    </div>

                    <div class="form-row">
                        <label>Опис об'єкта:</label>
                        <input v-model="newMod.description" type="text" placeholder="Призначення або характеристики" />
                    </div>

                    <div class="form-grid-2">
                        <div>
                            <label>Швидкість колісних (коеф. 0.0 - 1.2):</label>
                            <input v-model.number="newMod.speedModifierWheeled" type="number" step="0.05" required />
                        </div>
                        <div>
                            <label>Швидкість гусеничних (коеф. 0.0 - 1.2):</label>
                            <input v-model.number="newMod.speedModifierTracked" type="number" step="0.05" required />
                        </div>
                    </div>

                    <div class="form-grid-2">
                        <div>
                            <label>Дальність видимості всередині (м):</label>
                            <input v-model.number="newMod.visibilityMeters" type="number" step="10" placeholder="Залиште порожнім, якщо відкрите" />
                        </div>
                        <div>
                            <label>Захист від уламків та куль (%):</label>
                            <input v-model.number="newMod.coverDefensePercent" type="number" min="0" max="95" required />
                        </div>
                    </div>

                    <div class="modal-foot">
                        <button type="button" class="btn-cancel" @click="showAddModal = false">СКАСУВАТИ</button>
                        <button type="submit" class="btn-submit">ДОДАТИ ДО КАРТИ</button>
                    </div>
                </form>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue';
import {
    Viewer,
    Cartesian3,
    Rectangle,
    ArcGisMapServerImageryProvider,
    UrlTemplateImageryProvider,
    ImageryLayer,
    GeoJsonDataSource,
    Color,
    ScreenSpaceEventHandler,
    ScreenSpaceEventType,
    defined,
    Math as CesiumMath
} from 'cesium';
import { mapApi } from '../mapApi';
import type { MapDetailDto, SurfaceModifierDto } from '../types';

const props = defineProps<{
    map: MapDetailDto;
}>();

defineEmits<{
    (e: 'back'): void;
}>();

const editorMapContainer = ref<HTMLDivElement | null>(null);
const currentLayerType = ref<string>('SATELLITE');
const selectedCategory = ref<string>('ALL');
const modifiers = ref<SurfaceModifierDto[]>([]);
const showAddModal = ref(false);
const showVectors = ref(true);

const inspectedFeature = ref<any | null>(null);
const activeHighlightType = ref<string | null>(null);

let viewer: Viewer | null = null;
let handler: ScreenSpaceEventHandler | null = null;
let vectorDataSource: GeoJsonDataSource | null = null;

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

onMounted(async () => {
    if (!editorMapContainer.value) return;

    // Initialize Cesium Viewer
    viewer = new Viewer(editorMapContainer.value, {
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

    // Clamp globe strictly to this map's theater boundaries
    const theaterRect = Rectangle.fromDegrees(
        props.map.minLon,
        props.map.minLat,
        props.map.maxLon,
        props.map.maxLat
    );
    viewer.scene.globe.cartographicLimitRectangle = theaterRect;

    // Mount active baselayer
    if (props.map.layers && props.map.layers.length > 0) {
        currentLayerType.value = props.map.layers[0].layerType;
        await switchEditorLayer(currentLayerType.value);
    }

    // Camera fly to theater
    viewer.camera.flyTo({
        destination: Cartesian3.fromDegrees(props.map.centerLon, props.map.centerLat - 0.04, 5000),
        orientation: {
            heading: CesiumMath.toRadians(0),
            pitch: CesiumMath.toRadians(-35),
            roll: 0.0
        },
        duration: 1.0
    });

    // Load vector features (roads, buildings, vegetation) and click handlers
    await loadVectorFeatures();
    setupVectorPicking();
    await loadModifiers();
});

/**
 * Load interactive vector GeoJSON from backend
 */
const loadVectorFeatures = async () => {
    if (!viewer) return;

    try {
        const geoJson = await mapApi.getMapVectors(props.map.id);
        vectorDataSource = await GeoJsonDataSource.load(geoJson, {
            clampToGround: true,
            strokeWidth: 3
        });

        // Custom default styling for roads, vegetation, buildings
        const entities = vectorDataSource.entities.values;
        for (const entity of entities) {
            const props = entity.properties;
            const category = props?.category?.getValue();

            if (category === 'ROAD') {
                if (entity.polyline) {
                    entity.polyline.material = Color.fromCssColorString('#f59e0b').withAlpha(0.8) as any;
                    entity.polyline.width = 4 as any;
                }
            } else if (category === 'VEGETATION') {
                if (entity.polygon) {
                    entity.polygon.material = Color.fromCssColorString('#10b981').withAlpha(0.3) as any;
                    entity.polygon.outlineColor = Color.fromCssColorString('#059669') as any;
                }
            } else if (category === 'BUILDING') {
                if (entity.polygon) {
                    entity.polygon.material = Color.fromCssColorString('#ef4444').withAlpha(0.4) as any;
                    entity.polygon.outlineColor = Color.fromCssColorString('#b91c1c') as any;
                }
            }
        }

        viewer.dataSources.add(vectorDataSource);
    } catch (err) {
        console.warn('[MAP EDITOR] Could not load vector overlay:', err);
    }
};

/**
 * Handle clicking on a map object (Road / Building / Forest)
 */
const setupVectorPicking = () => {
    if (!viewer) return;

    handler = new ScreenSpaceEventHandler(viewer.scene.canvas);

    handler.setInputAction((click: any) => {
        const picked = viewer!.scene.pick(click.position);

        if (defined(picked) && picked.id && picked.id.properties) {
            const p = picked.id.properties;
            inspectedFeature.value = {
                name: p.name ? p.name.getValue() : 'Об’єкт без назви',
                category: p.category ? p.category.getValue() : 'UNKNOWN',
                typeKey: p.typeKey ? p.typeKey.getValue() : '',
                typeValue: p.typeValue ? p.typeValue.getValue() : '',
                entity: picked.id
            };
            // Auto highlight this type in sidebar
            activeHighlightType.value = inspectedFeature.value.typeValue;
        } else {
            clearInspection();
        }
    }, ScreenSpaceEventType.LEFT_CLICK);
};

const clearInspection = () => {
    inspectedFeature.value = null;
    activeHighlightType.value = null;
    resetMapHighlights();
};

/**
 * Highlight all features of selected type on the 3D map (Sidebar -> Map)
 */
const highlightObjectsOnMap = (mod: SurfaceModifierDto) => {
    if (!vectorDataSource) return;

    activeHighlightType.value = mod.osmValue;
    const entities = vectorDataSource.entities.values;

    for (const entity of entities) {
        const val = entity.properties?.typeValue?.getValue();
        const isMatch = (val === mod.osmValue);

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
    // Reset back to normal tactical colors
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

const toggleVectors = () => {
    showVectors.value = !showVectors.value;
    if (vectorDataSource) {
        vectorDataSource.show = showVectors.value;
    }
};

const switchEditorLayer = async (layerType: string) => {
    if (!viewer) return;
    currentLayerType.value = layerType;
    viewer.imageryLayers.removeAll();

    const tileUrl = `http://localhost:8080/api/maps/${props.map.id}/tiles/${layerType.toLowerCase()}/{z}/{x}/{y}.png`;
    const provider = new UrlTemplateImageryProvider({
        url: tileUrl,
        rectangle: Rectangle.fromDegrees(
            props.map.minLon, props.map.minLat, props.map.maxLon, props.map.maxLat
        ),
        minimumLevel: 12,
        maximumLevel: 16
    });

    viewer.imageryLayers.add(new ImageryLayer(provider));
};

const loadModifiers = async () => {
    try {
        modifiers.value = await mapApi.getModifiers(props.map.id);
    } catch (err) {
        console.error('Failed to load modifiers:', err);
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
        alert(`Оновлено ТТХ: ${mod.osmKey}=${mod.osmValue}`);
    } catch (err: any) {
        alert('Помилка збереження: ' + err.message);
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
        alert(`Додано новий тип об'єкта: ${created.osmKey}=${created.osmValue}`);
    } catch (err: any) {
        alert('Помилка створення: ' + err.message);
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
.header-left { display: flex; align-items: center; gap: 16px; }
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
.editor-header h3 { margin: 0; font-size: 13px; color: #cbd5e1; font-family: monospace; }
.map-highlight { color: #00a8ff; font-weight: bold; }
.header-center { display: flex; align-items: center; gap: 8px; }
.ctrl-label { font-size: 10px; color: #64748b; font-family: monospace; }
.layer-buttons { display: flex; gap: 4px; }
.layer-toggle-btn {
    background: #1e293b;
    border: 1px solid #334155;
    color: #94a3b8;
    padding: 4px 8px;
    font-size: 10px;
    cursor: pointer;
    border-radius: 3px;
    font-family: monospace;
}
.layer-toggle-btn.active { background: #0284c7; color: #fff; border-color: #38bdf8; font-weight: bold; }
.header-right { display: flex; align-items: center; gap: 10px; }
.btn-toggle-vectors {
    background: #1e293b;
    border: 1px solid #475569;
    color: #94a3b8;
    padding: 5px 10px;
    border-radius: 4px;
    font-size: 10px;
    font-family: monospace;
    cursor: pointer;
}
.btn-toggle-vectors.active { background: rgba(0, 168, 255, 0.2); border-color: #00a8ff; color: #38bdf8; }
.spec-badge {
    background: rgba(0, 230, 118, 0.15);
    color: #00e676;
    border: 1px solid rgba(0, 230, 118, 0.4);
    padding: 4px 8px;
    border-radius: 3px;
    font-size: 10px;
    font-family: monospace;
}

/* Split Screen */
.editor-body { display: flex; flex: 1; overflow: hidden; }
.map-viewport { flex: 1; position: relative; background: #000; }
.cesium-map-canvas { width: 100%; height: 100%; }

.editor-floating-hud {
    position: absolute;
    top: 16px;
    left: 16px;
    background: rgba(15, 23, 42, 0.88);
    border: 1px solid rgba(0, 168, 255, 0.3);
    padding: 8px 14px;
    border-radius: 4px;
    font-size: 11px;
    color: #e2e8f0;
    font-family: monospace;
    z-index: 10;
}
.sub-coords { font-size: 9px; color: #94a3b8; margin-top: 2px; }

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
.sidebar-header { padding: 16px; border-bottom: 1px solid #1e293b; }
.sidebar-title-row { display: flex; justify-content: space-between; align-items: center; }
.sidebar-title-row h4 { margin: 0; font-size: 12px; color: #00a8ff; font-family: monospace; }
.btn-add-custom {
    background: #047857;
    border: 1px solid #10b981;
    color: #fff;
    padding: 4px 10px;
    font-size: 10px;
    cursor: pointer;
    border-radius: 3px;
    font-family: monospace;
}
.sidebar-desc { font-size: 11px; color: #94a3b8; margin: 6px 0 12px 0; }
.category-filters { display: flex; flex-wrap: wrap; gap: 4px; }
.filter-pill {
    background: #1e293b;
    border: 1px solid #334155;
    color: #94a3b8;
    padding: 4px 8px;
    border-radius: 3px;
    font-size: 9px;
    cursor: pointer;
    font-family: monospace;
}
.filter-pill.active { background: #0284c7; color: #fff; border-color: #38bdf8; }

.modifiers-scroll-area {
    flex: 1;
    padding: 16px;
    overflow-y: auto;
    display: flex;
    flex-direction: column;
    gap: 12px;
}
.empty-mods { padding: 40px 10px; text-align: center; color: #64748b; font-size: 12px; }
.modifier-card {
    background: rgba(255, 255, 255, 0.02);
    border: 1px solid #334155;
    border-radius: 4px;
    padding: 12px;
    cursor: pointer;
    transition: all 0.2s;
}
.modifier-card:hover { border-color: #00a8ff; background: rgba(0, 168, 255, 0.05); }
.modifier-card.highlighted { border-color: #f1c40f; background: rgba(241, 196, 15, 0.1); }
.card-head { display: flex; align-items: center; justify-content: space-between; }
.cat-badge { font-size: 8px; padding: 2px 5px; border-radius: 2px; font-weight: bold; }
.cat-badge.road { background: #1e3a8a; color: #93c5fd; }
.cat-badge.vegetation { background: #064e3b; color: #6ee7b7; }
.cat-badge.building { background: #7f1d1d; color: #fca5a5; }
.tag-label { font-weight: bold; color: #fff; font-size: 12px; font-family: monospace; }
.btn-locate {
    background: transparent;
    border: 1px solid #00a8ff;
    color: #00a8ff;
    font-size: 9px;
    padding: 2px 6px;
    border-radius: 3px;
    cursor: pointer;
    font-family: monospace;
}
.mod-desc { font-size: 10px; color: #64748b; margin: 4px 0 10px 0; }
.params-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 8px;
    background: rgba(0, 0, 0, 0.25);
    padding: 8px;
    border-radius: 4px;
}
.param-box label { display: block; font-size: 9px; color: #94a3b8; margin-bottom: 2px; font-family: monospace; }
.num-field {
    width: 100%;
    box-sizing: border-box;
    background: #0f172a;
    border: 1px solid #334155;
    color: #fff;
    padding: 4px 6px;
    font-size: 11px;
    font-family: monospace;
    border-radius: 3px;
}
.card-actions { display: flex; justify-content: flex-end; margin-top: 10px; }
.btn-save-mod {
    background: #047857;
    border: 1px solid #10b981;
    color: #fff;
    padding: 4px 10px;
    font-size: 10px;
    cursor: pointer;
    border-radius: 3px;
    font-family: monospace;
}

/* Modal */
.modal-overlay {
    position: fixed;
    top: 0; left: 0; width: 100vw; height: 100vh;
    background: rgba(0, 0, 0, 0.8);
    display: flex; align-items: center; justify-content: center;
    z-index: 10000;
}
.modal-box {
    background: #0f172a; border: 1px solid #00a8ff; width: 520px;
    padding: 20px; border-radius: 6px;
}
.modal-head { display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid #334155; padding-bottom: 8px; margin-bottom: 14px; }
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
.modal-foot { display: flex; justify-content: flex-end; gap: 10px; margin-top: 10px; }
.btn-cancel { background: transparent; border: 1px solid #475569; color: #94a3b8; padding: 6px 14px; font-size: 11px; cursor: pointer; }
.btn-submit { background: #0284c7; border: 1px solid #38bdf8; color: #fff; padding: 6px 16px; font-size: 11px; cursor: pointer; font-weight: bold; }
</style>