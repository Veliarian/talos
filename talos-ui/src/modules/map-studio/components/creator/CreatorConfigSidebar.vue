<template>
    <aside class="config-sidebar">
        <!-- Settlement Geocoding Search Bar -->
        <div class="sidebar-block search-block">
            <label class="block-title">🔍 ПОШУК НАСЕЛЕНОГО ПУНКТУ</label>
            <div class="search-input-group">
                <input
                    v-model="searchQuery"
                    type="text"
                    placeholder="Введіть назву (наприклад: Бахмут, Харків, Київ)..."
                    class="text-input search-input"
                    @keydown.enter.prevent="executeSearch"
                />
                <button
                    type="button"
                    class="btn-search"
                    :disabled="isSearching || !searchQuery.trim()"
                    @click="executeSearch"
                >
                    {{ isSearching ? '...' : 'ПОШУК' }}
                </button>
            </div>

            <!-- Search Results Dropdown -->
            <div v-if="searchResults.length > 0" class="search-results-list">
                <div
                    v-for="(item, idx) in searchResults"
                    :key="idx"
                    class="search-result-item"
                    role="button"
                    tabindex="0"
                    @click="selectSearchResult(item)"
                >
                    <span class="res-name">{{ item.display_name.split(',')[0] }}</span>
                    <span class="res-desc">{{ item.display_name }}</span>
                </div>
            </div>
            <div v-else-if="hasSearched && searchResults.length === 0" class="no-results-msg">
                Населений пункт не знайдено.
            </div>
        </div>

        <!-- Name and Purpose -->
        <div class="sidebar-block">
            <label class="block-title">НАЗВА ПОЛІГОНУ ТА ОПИС</label>
            <input v-model="form.name" type="text" placeholder="наприклад, Оперативний напрямок Північ" class="text-input" />
            <input v-model="form.description" type="text" placeholder="Опис навчань або призначення ТВД" class="text-input mt-2" />
        </div>

        <!-- Coordinates Mode Switcher with Full Bi-Directional Editing -->
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
                    @click="syncDmsFromForm(); coordMode = 'DMS'"
                >
                    DMS (ГМС)
                </button>
                <button
                    type="button"
                    class="cs-tab"
                    :class="{ active: coordMode === 'METRIC' }"
                    @click="syncMgrsFromForm(); coordMode = 'METRIC'"
                >
                    MGRS Сітка
                </button>
            </div>

            <!-- 1. Decimal Degrees Inputs -->
            <div v-if="coordMode === 'DD'" class="inputs-grid">
                <div>
                    <label>Широта (Lat °):</label>
                    <input
                        v-model.number="form.centerLat"
                        type="number"
                        step="0.0001"
                        class="text-input"
                        @change="$emit('coord-changed')"
                    />
                </div>
                <div>
                    <label>Довгота (Lon °):</label>
                    <input
                        v-model.number="form.centerLon"
                        type="number"
                        step="0.0001"
                        class="text-input"
                        @change="$emit('coord-changed')"
                    />
                </div>
            </div>

            <!-- 2. Bi-directional DMS Inputs (Degrees, Minutes, Seconds, Hemisphere) -->
            <div v-else-if="coordMode === 'DMS'" class="dms-form">
                <div class="dms-axis-group">
                    <span class="axis-label">Широта (Lat):</span>
                    <div class="dms-row">
                        <input v-model.number="dmsLat.deg" type="number" min="0" max="89" placeholder="°" class="dms-num" @change="applyDmsToForm" />
                        <span>°</span>
                        <input v-model.number="dmsLat.min" type="number" min="0" max="59" placeholder="'" class="dms-num" @change="applyDmsToForm" />
                        <span>'</span>
                        <input v-model.number="dmsLat.sec" type="number" min="0" max="59.99" step="0.1" placeholder="&quot;" class="dms-num" @change="applyDmsToForm" />
                        <span>"</span>
                        <select v-model="dmsLat.hemi" class="hemi-select" @change="applyDmsToForm">
                            <option value="N">N</option>
                            <option value="S">S</option>
                        </select>
                    </div>
                </div>

                <div class="dms-axis-group mt-2">
                    <span class="axis-label">Довгота (Lon):</span>
                    <div class="dms-row">
                        <input v-model.number="dmsLon.deg" type="number" min="0" max="179" placeholder="°" class="dms-num" @change="applyDmsToForm" />
                        <span>°</span>
                        <input v-model.number="dmsLon.min" type="number" min="0" max="59" placeholder="'" class="dms-num" @change="applyDmsToForm" />
                        <span>'</span>
                        <input v-model.number="dmsLon.sec" type="number" min="0" max="59.99" step="0.1" placeholder="&quot;" class="dms-num" @change="applyDmsToForm" />
                        <span>"</span>
                        <select v-model="dmsLon.hemi" class="hemi-select" @change="applyDmsToForm">
                            <option value="E">E</option>
                            <option value="W">W</option>
                        </select>
                    </div>
                </div>
            </div>

            <!-- 3. MGRS Input with Parse Button -->
            <div v-else-if="coordMode === 'METRIC'" class="mgrs-edit-box">
                <input
                    v-model="mgrsInput"
                    type="text"
                    placeholder="наприклад, 36U QA 1234 5678"
                    class="text-input font-mono"
                />
                <button type="button" class="btn-apply-mgrs" @click="applyMgrsToForm">
                    ЗАСТОСУВАТИ MGRS
                </button>
            </div>

            <!-- Size Slider & Direct Number Input (Uncapped, up to 1500+ km) -->
            <div class="mt-3">
                <div class="flex-between">
                    <label>Розмір сторони квадрата:</label>
                    <div class="size-input-wrapper">
                        <input
                            v-model.number="form.sizeKm"
                            type="number"
                            min="5"
                            max="3000"
                            step="5"
                            class="num-size-input"
                            @change="$emit('coord-changed')"
                        />
                        <span class="unit-text">км</span>
                    </div>
                </div>
                <input
                    v-model.number="form.sizeKm"
                    type="range"
                    min="5"
                    max="1500"
                    step="5"
                    class="range-slider"
                    @change="$emit('coord-changed')"
                />
                <div class="size-hint">
                    Площа покриття: <strong>{{ (form.sizeKm * form.sizeKm).toLocaleString() }} км²</strong>
                </div>
            </div>
        </div>

        <!-- Baselayers Selection -->
        <div class="sidebar-block">
            <label class="block-title">ПІДКЛАДКИ ДЛЯ ЛОКАЛЬНОГО ЗБЕРЕЖЕННЯ</label>
            <div class="layers-checklist">
                <label class="check-item">
                    <input v-model="form.layerTypes" type="checkbox" value="SATELLITE" />
                    <span>Супутникова зйомка (Офлайн)</span>
                </label>
                <label class="check-item">
                    <input v-model="form.layerTypes" type="checkbox" value="TOPOGRAPHIC" />
                    <span>Топографічна карта (Горизонталі)</span>
                </label>
                <label class="check-item">
                    <input v-model="form.layerTypes" type="checkbox" value="TACTICAL" />
                    <span>Тактична штабна контрастна</span>
                </label>
            </div>
        </div>

        <!-- Launch Ingestion Button -->
        <div class="sidebar-actions">
            <button
                type="button"
                class="btn-start-ingestion"
                :disabled="!form.name || loading || form.layerTypes.length === 0"
                @click="$emit('submit')"
            >
                {{ loading ? 'ІНІЦІАЛІЗАЦІЯ ТА СТЯГУВАННЯ...' : 'ЗБЕРЕГТИ КАРТУ НА СЕРВЕР' }}
            </button>
        </div>
    </aside>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue';
import { coordConverter } from '@/shared/utils/coordConverter';
import type { MapCreationRequest } from '../../types';

interface NominatimResult {
    display_name: string;
    lat: string;
    lon: string;
}

const props = defineProps<{
    form: MapCreationRequest;
    loading: boolean;
}>();

const emit = defineEmits<{
    (e: 'coord-changed'): void;
    (e: 'submit'): void;
}>();

const coordMode = ref<'DD' | 'DMS' | 'METRIC'>('DD');

// Geocoding Search State
const searchQuery = ref('');
const isSearching = ref(false);
const hasSearched = ref(false);
const searchResults = ref<NominatimResult[]>([]);

const executeSearch = async () => {
    const q = searchQuery.value.trim();
    if (!q) return;

    try {
        isSearching.value = true;
        hasSearched.value = true;
        const res = await fetch(`https://nominatim.openstreetmap.org/search?format=json&q=${encodeURIComponent(q)}&limit=5`);
        if (res.ok) {
            searchResults.value = await res.json();
        } else {
            searchResults.value = [];
        }
    } catch {
        searchResults.value = [];
    } finally {
        isSearching.value = false;
    }
};

const selectSearchResult = (item: NominatimResult) => {
    props.form.centerLat = Number(parseFloat(item.lat).toFixed(4));
    props.form.centerLon = Number(parseFloat(item.lon).toFixed(4));
    if (!props.form.name) {
        props.form.name = item.display_name.split(',')[0] + ' ТВД';
    }
    searchResults.value = [];
    hasSearched.value = false;
    emit('coord-changed');
};

// Bi-directional DMS Form State
const dmsLat = reactive({ deg: 49, min: 59, sec: 16.8, hemi: 'N' as 'N' | 'S' });
const dmsLon = reactive({ deg: 23, min: 35, sec: 6.0, hemi: 'E' as 'E' | 'W' });

const syncDmsFromForm = () => {
    const latObj = coordConverter.toDms(props.form.centerLat, true);
    dmsLat.deg = latObj.degrees;
    dmsLat.min = latObj.minutes;
    dmsLat.sec = latObj.seconds;
    dmsLat.hemi = latObj.hemisphere as 'N' | 'S';

    const lonObj = coordConverter.toDms(props.form.centerLon, false);
    dmsLon.deg = lonObj.degrees;
    dmsLon.min = lonObj.minutes;
    dmsLon.sec = lonObj.seconds;
    dmsLon.hemi = lonObj.hemisphere as 'E' | 'W';
};

const applyDmsToForm = () => {
    props.form.centerLat = coordConverter.fromDms(dmsLat.deg, dmsLat.min, dmsLat.sec, dmsLat.hemi);
    props.form.centerLon = coordConverter.fromDms(dmsLon.deg, dmsLon.min, dmsLon.sec, dmsLon.hemi);
    emit('coord-changed');
};

// Bi-directional MGRS Form State
const mgrsInput = ref('');
const syncMgrsFromForm = () => {
    mgrsInput.value = coordConverter.toMgrsEstimate(props.form.centerLat, props.form.centerLon);
};

const applyMgrsToForm = () => {
    const parsed = coordConverter.fromMgrsEstimate(mgrsInput.value);
    if (parsed) {
        props.form.centerLat = parsed.lat;
        props.form.centerLon = parsed.lon;
        emit('coord-changed');
    } else {
        alert('Не вдалося розпізнати MGRS код');
    }
};

watch(() => [props.form.centerLat, props.form.centerLon], () => {
    if (coordMode.value === 'DMS') syncDmsFromForm();
    if (coordMode.value === 'METRIC') syncMgrsFromForm();
});
</script>

<style scoped>
.config-sidebar {
    width: 400px;
    background: rgba(15, 23, 42, 0.95);
    border-left: 1px solid rgba(0, 168, 255, 0.2);
    padding: 20px;
    display: flex;
    flex-direction: column;
    gap: 14px;
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
.text-input:focus { border-color: #00a8ff; outline: none; }
.mt-2 { margin-top: 8px; }
.mt-3 { margin-top: 12px; }

/* Geocoding Search */
.search-input-group { display: flex; gap: 6px; }
.search-input { flex: 1; font-size: 11px; }
.btn-search {
    background: #0284c7;
    border: 1px solid #38bdf8;
    color: #fff;
    padding: 0 12px;
    font-size: 10px;
    font-weight: bold;
    font-family: monospace;
    cursor: pointer;
    border-radius: 3px;
}
.btn-search:disabled { opacity: 0.5; cursor: not-allowed; }
.search-results-list {
    margin-top: 8px;
    background: #0b1120;
    border: 1px solid #00a8ff;
    border-radius: 3px;
    max-height: 160px;
    overflow-y: auto;
}
.search-result-item {
    padding: 6px 10px;
    cursor: pointer;
    border-bottom: 1px solid #1e293b;
    display: flex;
    flex-direction: column;
}
.search-result-item:hover { background: rgba(0, 168, 255, 0.15); }
.res-name { color: #00e676; font-size: 11px; font-weight: bold; font-family: monospace; }
.res-desc { color: #94a3b8; font-size: 9px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.no-results-msg { font-size: 10px; color: #ef4444; margin-top: 6px; font-family: monospace; }

/* Coordinate Tabs */
.coord-system-tabs { display: flex; gap: 4px; margin-bottom: 10px; }
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
.cs-tab.active { background: #0284c7; color: #fff; border-color: #38bdf8; }

.inputs-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 8px;
    font-size: 11px;
    color: #94a3b8;
    font-family: monospace;
}

/* DMS Inputs */
.dms-form { background: #0b1120; padding: 8px; border-radius: 3px; }
.axis-label { font-size: 10px; color: #00a8ff; font-family: monospace; display: block; margin-bottom: 4px; }
.dms-row { display: flex; align-items: center; gap: 4px; font-family: monospace; font-size: 11px; color: #94a3b8; }
.dms-num {
    width: 45px;
    background: #1e293b;
    border: 1px solid #334155;
    color: #fff;
    padding: 4px;
    font-size: 11px;
    font-family: monospace;
    border-radius: 2px;
    text-align: center;
}
.hemi-select {
    background: #0284c7;
    border: 1px solid #38bdf8;
    color: #fff;
    font-size: 10px;
    font-weight: bold;
    padding: 4px;
    border-radius: 2px;
    cursor: pointer;
}

/* MGRS */
.mgrs-edit-box { display: flex; flex-direction: column; gap: 6px; }
.btn-apply-mgrs {
    background: #1e293b;
    border: 1px solid #475569;
    color: #38bdf8;
    padding: 6px;
    font-size: 10px;
    font-family: monospace;
    font-weight: bold;
    cursor: pointer;
    border-radius: 3px;
}
.btn-apply-mgrs:hover { background: #0284c7; color: #fff; }

/* Size Slider and Direct Input */
.flex-between {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: 11px;
    color: #cbd5e1;
    font-family: monospace;
}
.size-input-wrapper { display: flex; align-items: center; gap: 4px; }
.num-size-input {
    width: 65px;
    background: #0b1120;
    border: 1px solid #00a8ff;
    color: #00e676;
    font-weight: bold;
    padding: 3px 6px;
    font-size: 12px;
    font-family: monospace;
    border-radius: 3px;
    text-align: right;
}
.unit-text { font-size: 11px; color: #00e676; font-family: monospace; }
.range-slider { width: 100%; margin-top: 6px; cursor: pointer; }
.size-hint { font-size: 9px; color: #64748b; font-family: monospace; margin-top: 4px; }
.size-hint strong { color: #cbd5e1; }

.layers-checklist { display: flex; flex-direction: column; gap: 8px; }
.check-item { display: flex; align-items: center; gap: 8px; font-size: 12px; color: #cbd5e1; cursor: pointer; }

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
.btn-start-ingestion:hover:not(:disabled) { background: #0369a1; }
.btn-start-ingestion:disabled { opacity: 0.5; cursor: not-allowed; }
</style>