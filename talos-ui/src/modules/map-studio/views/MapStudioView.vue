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

        <!-- 3. Specific Map Editor Screen (Modifiers & layers configuration) -->
        <MapEditorView
            v-else-if="currentScreen === 'editor' && selectedMap"
            :map="selectedMap"
            @back="currentScreen = 'hub'"
        />

        <!-- Global Catalog Modal (Accessible from Hub) -->
        <div v-if="openCatalogModal" class="catalog-modal-overlay">
            <div class="catalog-modal-box">
                <div class="modal-top">
                    <h4>ГЛОБАЛЬНИЙ ДОВІДНИК ТТХ ОБ'ЄКТІВ (ЗА ЗАМОВЧУВАННЯМ)</h4>
                    <button class="btn-close" @click="openCatalogModal = false">✕</button>
                </div>
                <p class="modal-desc">
                    Ці коефіцієнти автоматично копіюються у всі нові карти при їх створенні. Зміни тут не впливають на вже створені полігони.
                </p>
                <div class="modal-table-placeholder">
                    <div class="spec-note">
                        Будівлі (залізобетонні/житлові), ліси, річки та дороги вже мають закладені норми захисту (80% для будівель, 50% для лісу).
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { mapApi } from '../mapApi';
import type { MapDetailDto } from '../types';
import MapHubView from './MapHubView.vue';
import MapCreatorView from './MapCreatorView.vue';
import MapEditorView from './MapEditorView.vue';

// Active screen state: 'hub' | 'creator' | 'editor'
const currentScreen = ref<'hub' | 'creator' | 'editor'>('hub');
const maps = ref<MapDetailDto[]>([]);
const selectedMap = ref<MapDetailDto | null>(null);
const openCatalogModal = ref(false);

const fetchMaps = async () => {
    try {
        maps.value = await mapApi.getAllMaps();
    } catch (err) {
        console.error('Failed to load maps list', err);
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
        await fetchMaps();
    } catch (err: any) {
        alert('Помилка видалення карти: ' + err.message);
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