<template>
    <div class="panel-card">
        <div class="panel-header">
            <span class="panel-title">🗺️ КАРТОГРАФІЧНІ ПІДКЛАДКИ</span>
            <div class="toggle-switch">
                <input
                    id="basemap-toggle"
                    type="checkbox"
                    :checked="isBasemapVisible"
                    @change="$emit('update:visible', ($event.target as HTMLInputElement).checked)"
                />
                <label for="basemap-toggle">{{ isBasemapVisible ? 'УВІМКНЕНО' : 'ВИМКНЕНО' }}</label>
            </div>
        </div>

        <div v-show="isBasemapVisible" class="panel-body">
            <!-- Style Selector -->
            <label class="control-label">СТИЛЬ РАСТРОВОЇ ПІДКЛАДКИ:</label>
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
                            {{ layer.layerType === 'SATELLITE' ? '🛰️ Google Satellite HD' : (layer.layerType === 'TOPOGRAPHIC' ? '🗺️ Топографічна карта' : '🏙️ Тактична штабна') }}
                        </span>
                        <span class="status-dot ready">ОФЛАЙН</span>
                    </div>
                    <span class="layer-meta">Масштаб детальності: Zoom {{ layer.minZoom }} - {{ layer.maxZoom }}</span>
                </button>
            </div>

            <!-- Opacity / Transparency Slider (0% - 100%) -->
            <div class="slider-box mt-3">
                <div class="box-head">
                    <label>Прозорість підкладки (для обведення):</label>
                    <div class="num-wrap">
                        <input
                            type="number"
                            min="0"
                            max="100"
                            step="5"
                            :value="Math.round(basemapOpacity * 100)"
                            class="direct-input"
                            @input="$emit('update:opacity', Number(($event.target as HTMLInputElement).value) / 100)"
                        />
                        <span class="unit">%</span>
                    </div>
                </div>
                <input
                    type="range"
                    min="0"
                    max="1"
                    step="0.05"
                    :value="basemapOpacity"
                    class="range-slider"
                    @input="$emit('update:opacity', Number(($event.target as HTMLInputElement).value))"
                />
                <span class="hint-text">
                    Зменшіть прозорість до 30-50%, щоб зручно малювати об'єкти по супутниковому фото.
                </span>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import type { MapLayerDto } from '../../types';

defineProps<{
    layers: MapLayerDto[];
    currentLayerType: string;
    isBasemapVisible: boolean;
    basemapOpacity: number;
}>();

defineEmits<{
    (e: 'select-layer', type: string): void;
    (e: 'update:visible', val: boolean): void;
    (e: 'update:opacity', val: number): void;
}>();
</script>

<style scoped>
.panel-card { background: rgba(15, 23, 42, 0.85); border: 1px solid #334155; border-radius: 4px; overflow: hidden; }
.panel-header {
    background: rgba(255, 255, 255, 0.04); padding: 10px 14px; border-bottom: 1px solid rgba(255, 255, 255, 0.05);
    display: flex; justify-content: space-between; align-items: center;
}
.panel-title { font-size: 11px; font-weight: bold; color: #00a8ff; font-family: monospace; }

.toggle-switch { display: flex; align-items: center; gap: 6px; font-size: 10px; font-family: monospace; }
.toggle-switch label { color: #00e676; cursor: pointer; font-weight: bold; }

.panel-body { padding: 12px; display: flex; flex-direction: column; gap: 10px; font-family: monospace; }
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

.slider-box { background: rgba(0, 0, 0, 0.3); padding: 8px; border-radius: 3px; display: flex; flex-direction: column; gap: 4px; }
.box-head { display: flex; justify-content: space-between; align-items: center; font-size: 10px; color: #94a3b8; }
.num-wrap { display: flex; align-items: center; gap: 4px; }
.direct-input {
    width: 45px; background: #0b1120; border: 1px solid #00a8ff; color: #00e676;
    font-weight: bold; padding: 2px 4px; font-size: 11px; font-family: monospace; border-radius: 2px; text-align: right;
}
.unit { color: #64748b; font-size: 10px; }
.range-slider { width: 100%; cursor: pointer; margin-top: 4px; }
.hint-text { font-size: 8px; color: #64748b; margin-top: 4px; line-height: 1.3; }
.mt-3 { margin-top: 8px; }
</style>