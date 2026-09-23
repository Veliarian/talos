<template>
    <div class="card-panel">
        <div class="panel-header">
            <h4>ЛОКАЛЬНІ КАРТИ НА СЕРВЕРІ ({{ maps.length }})</h4>
            <button type="button" class="btn-refresh" @click="$emit('refresh')">ОНОВИТИ</button>
        </div>

        <div v-if="maps.length === 0" class="empty-state">
            На сервері ще немає збережених карт. Створіть перший квадрат вище.
        </div>

        <div v-else class="maps-grid">
            <div
                v-for="map in maps"
                :key="map.id"
                class="map-card"
                :class="{ active: selectedMapId === map.id }"
                role="button"
                tabindex="0"
                @click="$emit('select', map)"
                @keydown.enter="$emit('select', map)"
            >
                <div class="card-top">
                    <span class="map-name">{{ map.name }}</span>
                    <span class="status-tag" :class="map.status.toLowerCase()">{{ map.status }}</span>
                </div>

                <div class="map-desc">{{ map.description || 'Без опису' }}</div>

                <div class="card-meta">
                    <div>Розмір: <strong>{{ map.sizeKm }}×{{ map.sizeKm }} км</strong></div>
                    <div>Центр: <strong>{{ map.centerLat.toFixed(4) }}, {{ map.centerLon.toFixed(4) }}</strong></div>
                </div>

                <!-- Baselayers badges -->
                <div class="layers-row">
          <span
              v-for="layer in map.layers"
              :key="layer.id"
              class="layer-pill"
              :class="{ ready: layer.status === 'READY' }"
          >
            {{ layer.layerType }} (z{{ layer.minZoom }}-{{ layer.maxZoom }})
          </span>
                </div>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import type { MapDetailDto } from '@/modules/map-studio/types';

defineProps<{
    maps: MapDetailDto[];
    selectedMapId: string | null;
}>();

defineEmits<{
    (e: 'select', map: MapDetailDto): void;
    (e: 'refresh'): void;
}>();
</script>

<style scoped>
.card-panel {
    background: rgba(15, 23, 42, 0.85);
    border: 1px solid rgba(0, 168, 255, 0.3);
    border-radius: 6px;
    padding: 16px;
    margin-bottom: 20px;
}
.panel-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    border-bottom: 1px solid rgba(0, 168, 255, 0.2);
    padding-bottom: 8px;
    margin-bottom: 12px;
}
.panel-header h4 {
    margin: 0;
    color: #00a8ff;
    font-size: 14px;
}
.btn-refresh {
    background: transparent;
    border: 1px solid #475569;
    color: #94a3b8;
    padding: 4px 8px;
    border-radius: 3px;
    cursor: pointer;
    font-size: 10px;
    font-family: monospace;
    transition: all 0.15s ease-in-out;
}
.btn-refresh:hover {
    color: #fff;
    border-color: #00a8ff;
}
.empty-state {
    padding: 24px;
    text-align: center;
    color: #64748b;
    font-size: 12px;
}
.maps-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
    gap: 12px;
}
.map-card {
    background: rgba(255, 255, 255, 0.03);
    border: 1px solid #334155;
    border-radius: 4px;
    padding: 12px;
    cursor: pointer;
    transition: all 0.2s;
    outline: none;
}
.map-card:hover, .map-card:focus-visible {
    border-color: #00a8ff;
    background: rgba(0, 168, 255, 0.05);
}
.map-card.active {
    border-color: #f1c40f;
    background: rgba(241, 196, 15, 0.08);
}
.card-top {
    display: flex;
    justify-content: space-between;
    align-items: center;
}
.map-name {
    font-weight: bold;
    color: #fff;
    font-size: 13px;
}
.status-tag {
    font-size: 9px;
    padding: 2px 6px;
    border-radius: 3px;
    font-family: monospace;
}
.status-tag.ready {
    background: rgba(0, 230, 118, 0.2);
    color: #00e676;
}
.status-tag.downloading {
    background: rgba(241, 196, 15, 0.2);
    color: #f1c40f;
}
.status-tag.failed {
    background: rgba(239, 68, 68, 0.2);
    color: #f87171;
}
.map-desc {
    font-size: 11px;
    color: #94a3b8;
    margin: 4px 0 8px 0;
}
.card-meta {
    font-size: 10px;
    color: #64748b;
    font-family: monospace;
    line-height: 1.5;
}
.card-meta strong {
    color: #cbd5e1;
}
.layers-row {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin-top: 8px;
}
.layer-pill {
    font-size: 9px;
    background: #1e293b;
    border: 1px solid #475569;
    color: #94a3b8;
    padding: 2px 5px;
    border-radius: 3px;
    font-family: monospace;
}
.layer-pill.ready {
    border-color: #0284c7;
    color: #38bdf8;
}
</style>