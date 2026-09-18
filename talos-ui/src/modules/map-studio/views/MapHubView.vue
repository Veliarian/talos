<template>
    <div class="hub-container">
        <!-- Top Tactical Action Bar -->
        <div class="hub-top-bar">
            <div class="title-group">
                <h2>БІБЛІОТЕКА КАРТ ТА ТЕАТРІВ ВОЄННИХ ДІЙ (ТВД)</h2>
                <p>Виберіть існуючу карту для налаштування характеристик або згенеруйте новий квадрат.</p>
            </div>

            <div class="action-group">
                <button class="btn-catalog" @click="$emit('open-catalog')">
                    <span class="btn-icon">⚙</span> ДОВІДНИК ТТХ ОБ'ЄКТІВ
                </button>
                <button class="btn-create" @click="$emit('create-new')">
                    <span class="btn-icon">+</span> СТВОРИТИ НОВУ КАРТУ
                </button>
            </div>
        </div>

        <!-- Search and View Filter -->
        <div class="filter-bar">
            <input
                v-model="searchQuery"
                type="text"
                placeholder="Пошук карти за назвою або локацією..."
                class="search-input"
            />
            <div class="view-stats">
                Усього карт: <strong>{{ maps.length }}</strong>
            </div>
        </div>

        <!-- Maps Grid (Office Style Cards) -->
        <div v-if="filteredMaps.length === 0" class="empty-placeholder">
            <div class="empty-card">
                <div class="radar-icon">⌖</div>
                <h3>Карти не знайдені</h3>
                <p>Створіть свій перший бойовий квадрат розміром 10-50 км для початку роботи.</p>
                <button class="btn-create" @click="$emit('create-new')">+ СТВОРИТИ КАРТУ</button>
            </div>
        </div>

        <div v-else class="cards-grid">
            <div
                v-for="map in filteredMaps"
                :key="map.id"
                class="map-tile-card"
                @click="$emit('open-map', map)"
            >
                <div class="tile-header">
                    <div class="tile-title">{{ map.name }}</div>
                    <span class="badge" :class="map.status.toLowerCase()">{{ map.status }}</span>
                </div>

                <div class="tile-desc">{{ map.description || 'Локальний полігон бойового моделювання' }}</div>

                <div class="tile-specs">
                    <div class="spec-row">
                        <span>Розмір ТВД:</span>
                        <strong>{{ map.sizeKm }} × {{ map.sizeKm }} км</strong>
                    </div>
                    <div class="spec-row">
                        <span>Центр (WGS84):</span>
                        <strong>{{ map.centerLat.toFixed(4) }}°N, {{ map.centerLon.toFixed(4) }}°E</strong>
                    </div>
                    <div class="spec-row">
                        <span>Координатна сітка MGRS:</span>
                        <strong>{{ getMgrs(map.centerLat, map.centerLon) }}</strong>
                    </div>
                </div>

                <div class="tile-footer">
                    <div class="layers-tag-list">
            <span
                v-for="layer in map.layers"
                :key="layer.id"
                class="layer-badge"
                :class="{ active: layer.status === 'READY' }"
            >
              {{ layer.layerType }}
            </span>
                    </div>
                    <button class="btn-open-editor">НАЛАШТУВАТИ ТТХ →</button>
                </div>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import type { MapDetailDto } from '../types';
import { coordConverter } from '../coordConverter';

const props = defineProps<{
    maps: MapDetailDto[];
}>();

defineEmits<{
    (e: 'create-new'): void;
    (e: 'open-map', map: MapDetailDto): void;
    (e: 'open-catalog'): void;
}>();

const searchQuery = ref('');

const filteredMaps = computed(() => {
    if (!searchQuery.value.trim()) return props.maps;
    const q = searchQuery.value.toLowerCase();
    return props.maps.filter(m =>
        m.name.toLowerCase().includes(q) || (m.description && m.description.toLowerCase().includes(q))
    );
});

const getMgrs = (lat: number, lon: number) => {
    return coordConverter.toMgrsEstimate(lat, lon);
};
</script>

<style scoped>
.hub-container {
    max-width: 1280px;
    margin: 0 auto;
    padding: 24px 20px 60px 20px;
    font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
}
.hub-top-bar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    border-bottom: 1px solid rgba(0, 168, 255, 0.2);
    padding-bottom: 20px;
    margin-bottom: 20px;
}
.title-group h2 {
    margin: 0 0 6px 0;
    font-size: 18px;
    letter-spacing: 1px;
    color: #00a8ff;
    font-family: 'Courier New', Courier, monospace;
}
.title-group p {
    margin: 0;
    font-size: 13px;
    color: #94a3b8;
}
.action-group {
    display: flex;
    gap: 12px;
}
.btn-create {
    background: #0284c7;
    border: 1px solid #38bdf8;
    color: #fff;
    padding: 10px 18px;
    border-radius: 4px;
    font-weight: bold;
    font-size: 12px;
    cursor: pointer;
    font-family: monospace;
    transition: background 0.2s;
}
.btn-create:hover {
    background: #0369a1;
}
.btn-catalog {
    background: #1e293b;
    border: 1px solid #475569;
    color: #cbd5e1;
    padding: 10px 16px;
    border-radius: 4px;
    font-size: 12px;
    cursor: pointer;
    font-family: monospace;
}
.btn-catalog:hover {
    border-color: #00a8ff;
    color: #fff;
}
.filter-bar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 24px;
}
.search-input {
    width: 380px;
    background: #0f172a;
    border: 1px solid #334155;
    color: #fff;
    padding: 8px 14px;
    border-radius: 4px;
    font-size: 12px;
    font-family: monospace;
}
.search-input:focus {
    border-color: #00a8ff;
    outline: none;
}
.view-stats {
    font-size: 12px;
    color: #64748b;
    font-family: monospace;
}
.cards-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
    gap: 16px;
}
.map-tile-card {
    background: rgba(15, 23, 42, 0.7);
    border: 1px solid #334155;
    border-radius: 6px;
    padding: 16px;
    cursor: pointer;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
    transition: all 0.2s;
}
.map-tile-card:hover {
    border-color: #00a8ff;
    background: rgba(0, 168, 255, 0.05);
    transform: translateY(-2px);
    box-shadow: 0 4px 16px rgba(0, 168, 255, 0.15);
}
.tile-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
}
.tile-title {
    font-weight: bold;
    font-size: 15px;
    color: #fff;
}
.badge {
    font-size: 9px;
    padding: 2px 6px;
    border-radius: 3px;
    font-family: monospace;
}
.badge.ready {
    background: rgba(0, 230, 118, 0.2);
    color: #00e676;
}
.badge.downloading {
    background: rgba(241, 196, 15, 0.2);
    color: #f1c40f;
}
.tile-desc {
    font-size: 12px;
    color: #94a3b8;
    margin: 8px 0 14px 0;
}
.tile-specs {
    background: rgba(0, 0, 0, 0.3);
    padding: 10px;
    border-radius: 4px;
    border-left: 2px solid #00a8ff;
    margin-bottom: 14px;
}
.spec-row {
    display: flex;
    justify-content: space-between;
    font-size: 11px;
    color: #64748b;
    font-family: monospace;
    margin-bottom: 4px;
}
.spec-row:last-child {
    margin-bottom: 0;
}
.spec-row strong {
    color: #e2e8f0;
}
.tile-footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
    border-top: 1px solid rgba(255, 255, 255, 0.05);
    padding-top: 10px;
}
.layers-tag-list {
    display: flex;
    gap: 4px;
}
.layer-badge {
    font-size: 8px;
    background: #1e293b;
    padding: 2px 5px;
    border-radius: 2px;
    color: #94a3b8;
    font-family: monospace;
}
.layer-badge.active {
    color: #38bdf8;
    border: 1px solid rgba(56, 189, 248, 0.4);
}
.btn-open-editor {
    background: transparent;
    border: none;
    color: #00a8ff;
    font-weight: bold;
    font-size: 11px;
    cursor: pointer;
    font-family: monospace;
}
.empty-placeholder {
    padding: 60px 0;
    text-align: center;
}
.empty-card {
    display: inline-block;
    background: rgba(15, 23, 42, 0.6);
    border: 1px dashed #334155;
    padding: 40px 60px;
    border-radius: 8px;
}
.radar-icon {
    font-size: 36px;
    color: #00a8ff;
    margin-bottom: 12px;
}
</style>