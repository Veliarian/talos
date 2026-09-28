<template>
    <div class="panel-card">
        <div class="panel-header">
            <span class="panel-title">🗺️ РАСТРОВІ ПІДКЛАДКИ ТА ТЕКСТУРИ</span>
        </div>
        <div class="panel-body">
            <label class="control-label">ТИП ПІДКЛАДКИ КАРТИ:</label>
            <div class="layers-stack">
                <button
                    v-for="layer in layers"
                    :key="layer.id"
                    type="button"
                    class="layer-pick-btn"
                    :class="{ active: currentLayerType === layer.layerType }"
                    @click="$emit('select-layer', layer.layerType)"
                >
                    <div class="layer-btn-top">
                        <span class="layer-name">
                            {{ layer.layerType === 'SATELLITE' ? '🛰️ Google Satellite HD' : layer.layerType }}
                        </span>
                        <span class="status-dot ready">ОФЛАЙН</span>
                    </div>
                    <span class="layer-meta">Масштаб: Zoom {{ layer.minZoom }} - {{ layer.maxZoom }}</span>
                </button>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import type { MapLayerDto } from '../../types';

defineProps<{
    layers: MapLayerDto[];
    currentLayerType: string;
}>();

defineEmits<{
    (e: 'select-layer', type: string): void;
}>();
</script>

<style scoped>
.panel-card { background: rgba(15, 23, 42, 0.85); border: 1px solid #334155; border-radius: 4px; overflow: hidden; }
.panel-header { background: rgba(255, 255, 255, 0.04); padding: 10px 14px; border-bottom: 1px solid rgba(255, 255, 255, 0.05); }
.panel-title { font-size: 11px; font-weight: bold; color: #00a8ff; font-family: monospace; }
.panel-body { padding: 12px; display: flex; flex-direction: column; gap: 8px; font-family: monospace; }
.control-label { font-size: 9px; color: #94a3b8; font-weight: bold; }
.layers-stack { display: flex; flex-direction: column; gap: 6px; }
.layer-pick-btn {
    background: #1e293b; border: 1px solid #334155; color: #cbd5e1; padding: 8px 10px;
    border-radius: 3px; cursor: pointer; text-align: left; display: flex; flex-direction: column; gap: 2px;
}
.layer-pick-btn.active { background: #0284c7; border-color: #38bdf8; color: #fff; }
.layer-btn-top { display: flex; justify-content: space-between; align-items: center; }
.layer-name { font-size: 11px; font-weight: bold; }
.status-dot { font-size: 8px; padding: 1px 4px; border-radius: 2px; background: rgba(0, 230, 118, 0.2); color: #00e676; }
.layer-meta { font-size: 9px; color: #94a3b8; }
</style>