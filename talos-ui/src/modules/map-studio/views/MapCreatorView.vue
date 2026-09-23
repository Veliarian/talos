<template>
    <div class="creator-container">
        <!-- Top Bar -->
        <header class="creator-header">
            <button type="button" class="btn-back" @click="$emit('cancel')">← НАЗАД ДО КАРТ</button>
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
        </header>

        <div class="creator-workspace">
            <!-- 1. Extracted Map Canvas Component -->
            <CreatorMapCanvas
                :size-km="form.sizeKm"
                :interaction-mode="interactionMode"
                :active-style="activeStyle"
                @ready="onCanvasReady"
                @update:style="switchPreviewStyle"
            />

            <!-- 2. Extracted Configuration Sidebar Component -->
            <CreatorConfigSidebar
                :form="form"
                :loading="loading"
                @coord-changed="onCoordinateChange"
                @submit="startCreation"
            />
        </div>
    </div>
</template>

<script setup lang="ts">
import { reactive, ref, onUnmounted } from 'vue';
import { mapApi } from '@/modules/map-studio/mapApi';
import type { MapCreationRequest } from '@/modules/map-studio/types';
import { useCreatorMap } from '../composables/useCreatorMap';
import CreatorMapCanvas from '../components/creator/CreatorMapCanvas.vue';
import CreatorConfigSidebar from '../components/creator/CreatorConfigSidebar.vue';

const emit = defineEmits<{
    (e: 'cancel'): void;
    (e: 'created'): void;
}>();

const loading = ref(false);

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

const {
    interactionMode,
    activeStyle,
    initMap,
    switchPreviewStyle,
    flyToCoordinates,
    destroyMap
} = useCreatorMap(form);

const onCanvasReady = (el: HTMLDivElement) => {
    initMap(el);
};

const onCoordinateChange = () => {
    flyToCoordinates(form.centerLat, form.centerLon, form.sizeKm);
};

const startCreation = async () => {
    try {
        loading.value = true;
        await mapApi.createMap(form);
        emit('created');
    } catch (err: unknown) {
        const message = err instanceof Error ? err.message : String(err);
        alert('Помилка генерації карти: ' + message);
    } finally {
        loading.value = false;
    }
};

onUnmounted(() => {
    destroyMap();
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
.btn-back:hover { border-color: #00a8ff; color: #fff; }
.creator-header h3 { margin: 0; font-size: 14px; color: #00a8ff; letter-spacing: 1px; }
.mode-tag {
    font-size: 10px;
    background: rgba(0, 230, 118, 0.2);
    color: #00e676;
    padding: 2px 6px;
    border-radius: 3px;
    font-family: monospace;
}
.tool-modes { margin-left: auto; display: flex; gap: 6px; }
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
.creator-workspace { display: flex; flex: 1; overflow: hidden; }
</style>