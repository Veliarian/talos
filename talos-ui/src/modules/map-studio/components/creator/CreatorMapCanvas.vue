<template>
    <div class="visual-picker-area">
        <div ref="canvasRef" class="creator-map-canvas"></div>

        <!-- Floating HUD with active bounds -->
        <div class="picker-map-hud">
            <span class="hud-label">ОБРАНА ЗОНА ТВД:</span>
            <strong>{{ sizeKm }} × {{ sizeKm }} КМ ({{ sizeKm * sizeKm }} км²)</strong>
            <span class="hud-hint">
                {{ interactionMode === 'CLICK_CENTER' ? 'Клікніть на карті для переміщення центру' : 'Затисніть ЛКМ та тягніть для малювання рамки' }}
            </span>
        </div>

        <!-- Preview Style Switcher on Map -->
        <div class="map-style-floating-widget">
            <span class="widget-title">СТИЛЬ ПЕРЕГЛЯДУ:</span>
            <div class="widget-buttons">
                <button
                    type="button"
                    class="style-btn"
                    :class="{ active: activeStyle === 'HYBRID' }"
                    @click="$emit('update:style', 'HYBRID')"
                >
                    🛰️ Супутник + Підписи
                </button>
                <button
                    type="button"
                    class="style-btn"
                    :class="{ active: activeStyle === 'TOPO' }"
                    @click="$emit('update:style', 'TOPO')"
                >
                    🗺️ Топографічна
                </button>
                <button
                    type="button"
                    class="style-btn"
                    :class="{ active: activeStyle === 'VECTOR' }"
                    @click="$emit('update:style', 'VECTOR')"
                >
                    🏙️ Векторна OSM
                </button>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import type { PreviewStyle, InteractionMode } from '../../composables/useCreatorMap';

defineProps<{
    sizeKm: number;
    interactionMode: InteractionMode;
    activeStyle: PreviewStyle;
}>();

const emit = defineEmits<{
    (e: 'ready', el: HTMLDivElement): void;
    (e: 'update:style', style: PreviewStyle): void;
}>();

const canvasRef = ref<HTMLDivElement | null>(null);

onMounted(() => {
    if (canvasRef.value) {
        emit('ready', canvasRef.value);
    }
});
</script>

<style scoped>
.visual-picker-area {
    flex: 1;
    position: relative;
    height: 100%;
}
.creator-map-canvas {
    width: 100%;
    height: 100%;
}

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
.hud-label { color: #00a8ff; font-size: 10px; }
.hud-hint { font-size: 10px; color: #94a3b8; }

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
.widget-buttons { display: flex; gap: 6px; }
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
.style-btn:hover { border-color: #00a8ff; color: #fff; }
.style-btn.active {
    background: #0284c7;
    border-color: #38bdf8;
    color: #fff;
    font-weight: bold;
}
</style>