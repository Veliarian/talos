<template>
    <div class="studio-root">
        <!-- 1. Office-style Hub View (List of ready maps) -->
        <MapHubView
            v-if="currentScreen === 'hub'"
            :maps="maps"
            @create-new="currentScreen = 'creator'"
            @open-map="openEditor"
            @open-catalog="openCatalogModal = true"
            @delete-map="onDeleteMap"
        />

        <!-- 2. Interactive Map Creator Screen (Bounding area visual picker + coordinates) -->
        <MapCreatorView
            v-else-if="currentScreen === 'creator'"
            @cancel="currentScreen = 'hub'"
            @created="onMapCreated"
        />

        <!-- 3. Specific Map Editor Screen (Modifiers, vectors & sculpting configuration) -->
        <MapEditorView
            v-else-if="currentScreen === 'editor' && selectedMap"
            :map="selectedMap"
            @back="currentScreen = 'hub'"
            @launch-sim="$emit('switch-to-sim')"
        />

        <!-- Global Catalog Modal (Accessible from Hub) -->
        <GlobalTemplateCatalogModal
            v-if="openCatalogModal"
            @close="openCatalogModal = false"
        />
    </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { mapApi } from '@/modules/map-studio/mapApi';
import type { MapDetailDto } from '@/modules/map-studio/types';
import MapHubView from '@/modules/map-studio/views/MapHubView.vue';
import MapCreatorView from '@/modules/map-studio/views/MapCreatorView.vue';
import MapEditorView from '@/modules/map-studio/views/MapEditorView.vue';
import GlobalTemplateCatalogModal from '../components/GlobalTemplateCatalogModal.vue';

defineEmits<{
    (e: 'switch-to-sim'): void;
}>();

// Active screen state: 'hub' | 'creator' | 'editor'
const currentScreen = ref<'hub' | 'creator' | 'editor'>('hub');
const maps = ref<MapDetailDto[]>([]);
const selectedMap = ref<MapDetailDto | null>(null);
const openCatalogModal = ref(false);

const fetchMaps = async () => {
    try {
        maps.value = await mapApi.getAllMaps();
    } catch (err: unknown) {
        console.error('[TALOS STUDIO] Failed to load maps list:', err);
    }
};

const openEditor = (map: MapDetailDto) => {
    selectedMap.value = map;
    currentScreen.value = 'editor';
};

const onMapCreated = async () => {
    await fetchMaps();
    currentScreen.value = 'hub';
};

const onDeleteMap = async (mapId: string) => {
    try {
        await mapApi.deleteMap(mapId);
        if (selectedMap.value?.id === mapId) {
            selectedMap.value = null;
        }
        await fetchMaps();
    } catch (err: unknown) {
        const message = err instanceof Error ? err.message : String(err);
        alert('Помилка видалення карти: ' + message);
    }
};

onMounted(fetchMaps);
</script>

<style scoped>
.studio-root {
    width: 100vw;
    height: 100vh;
    background: #020617;
    color: #f8fafc;
    box-sizing: border-box;
    overflow-y: auto;
}
.catalog-modal-overlay {
    position: fixed;
    top: 0;
    left: 0;
    width: 100vw;
    height: 100vh;
    background: rgba(0, 0, 0, 0.8);
    backdrop-filter: blur(4px);
    display: flex;
    align-items: center;
    justify-content: center;
    z-index: 10000;
}
.catalog-modal-box {
    background: #0f172a;
    border: 1px solid #00a8ff;
    width: 650px;
    padding: 24px;
    border-radius: 6px;
    box-shadow: 0 8px 32px rgba(0, 0, 0, 0.8);
}
.modal-top {
    display: flex;
    justify-content: space-between;
    align-items: center;
    border-bottom: 1px solid #334155;
    padding-bottom: 8px;
}
.modal-top h4 {
    margin: 0;
    color: #00a8ff;
    font-size: 13px;
    font-family: monospace;
}
.btn-close {
    background: transparent;
    border: none;
    color: #94a3b8;
    font-size: 16px;
    cursor: pointer;
    transition: color 0.15s ease-in-out;
}
.btn-close:hover {
    color: #ffffff;
}
.modal-desc {
    font-size: 12px;
    color: #94a3b8;
    margin: 12px 0;
}
.spec-note {
    background: rgba(0, 168, 255, 0.08);
    border-left: 3px solid #00a8ff;
    padding: 12px;
    font-size: 11px;
    color: #cbd5e1;
    font-family: monospace;
}
</style>