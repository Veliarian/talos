<template>
    <div class="card-panel">
        <div class="panel-header">
            <h4>ГЕНЕРАТОР БОЙОВОГО КВАДРАТА (ТВД)</h4>
            <span class="badge">100% OFFLINE</span>
        </div>

        <form @submit.prevent="submitCreation" class="form-grid">
            <div class="form-group full-width">
                <label>НАЗВА КАРТИ / ПОЛІГОНУ:</label>
                <input v-model="form.name" type="text" required placeholder="наприклад, Яворівський полігон 20х20" />
            </div>

            <div class="form-group full-width">
                <label>ОПИС / ПРИЗНАЧЕННЯ:</label>
                <input v-model="form.description" type="text" placeholder="Базовий полігон навчань підрозділів" />
            </div>

            <div class="form-group">
                <label>ШИРОТА ЦЕНТРУ (LAT):</label>
                <input v-model.number="form.centerLat" type="number" step="0.0001" required />
            </div>

            <div class="form-group">
                <label>ДОВГОТА ЦЕНТРУ (LON):</label>
                <input v-model.number="form.centerLon" type="number" step="0.0001" required />
            </div>

            <div class="form-group">
                <label>РОЗМІР КВАДРАТА (КМ):</label>
                <input v-model.number="form.sizeKm" type="number" min="5" max="50" step="1" required />
            </div>

            <div class="form-group">
                <label>МАКСИМАЛЬНИЙ ЗУМ (ДЕТАЛЬНІСТЬ):</label>
                <select v-model.number="form.maxZoom">
                    <option :value="14">Zoom 14 (Огляд ~10 м/пікс - швидко)</option>
                    <option :value="15">Zoom 15 (Тактичний ~4.5 м/пікс - оптимум)</option>
                    <option :value="16">Zoom 16 (Високий ~2.3 м/пікс - детально)</option>
                </select>
            </div>

            <div class="form-group full-width">
                <label>ПІДКЛАДКИ ДЛЯ ЗАВАНТАЖЕННЯ НА СЕРВЕР:</label>
                <div class="checkbox-row">
                    <label class="checkbox-label">
                        <input type="checkbox" value="SATELLITE" v-model="form.layerTypes" />
                        <span>Супутник (Esri World Imagery)</span>
                    </label>
                    <label class="checkbox-label">
                        <input type="checkbox" value="TOPOGRAPHIC" v-model="form.layerTypes" />
                        <span>Топографія (OpenTopoMap)</span>
                    </label>
                    <label class="checkbox-label">
                        <input type="checkbox" value="TACTICAL" v-model="form.layerTypes" />
                        <span>Тактична (Контрастна штабна)</span>
                    </label>
                </div>
            </div>

            <div class="form-actions full-width">
                <button type="submit" class="btn primary" :disabled="loading || form.layerTypes.length === 0">
                    {{ loading ? 'ІНІЦІАЛІЗАЦІЯ...' : 'ЗАВАНТАЖИТИ КАРТУ НА СЕРВЕР' }}
                </button>
            </div>
        </form>
    </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue';
import { mapApi } from '../mapApi';
import type { MapCreationRequest } from '../types';

const emit = defineEmits<{ (e: 'created'): void }>();
const loading = ref(false);

// Default form coordinates (Yavoriv training area)
const form = reactive<MapCreationRequest>({
    name: '',
    description: '',
    centerLat: 49.9880,
    centerLon: 23.5850,
    sizeKm: 20,
    layerTypes: ['SATELLITE', 'TOPOGRAPHIC'],
    minZoom: 12,
    maxZoom: 15
});

const submitCreation = async () => {
    try {
        loading.value = true;
        await mapApi.createMap(form);
        form.name = '';
        form.description = '';
        emit('created');
    } catch (err: any) {
        alert('Помилка створення карти: ' + err.message);
    } finally {
        loading.value = false;
    }
};
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
    margin-bottom: 16px;
}
.panel-header h4 {
    margin: 0;
    color: #00a8ff;
    font-size: 14px;
    letter-spacing: 1px;
}
.badge {
    background: rgba(0, 230, 118, 0.2);
    color: #00e676;
    padding: 2px 8px;
    border-radius: 4px;
    font-size: 10px;
    font-family: monospace;
}
.form-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 12px;
}
.full-width {
    grid-column: span 2;
}
.form-group label {
    display: block;
    font-size: 11px;
    color: #94a3b8;
    margin-bottom: 4px;
    font-family: monospace;
}
input[type="text"], input[type="number"], select {
    width: 100%;
    box-sizing: border-box;
    background: #0f172a;
    border: 1px solid #334155;
    color: #fff;
    padding: 8px 10px;
    border-radius: 4px;
    font-family: monospace;
}
input:focus, select:focus {
    border-color: #00a8ff;
    outline: none;
}
.checkbox-row {
    display: flex;
    gap: 16px;
    margin-top: 6px;
}
.checkbox-label {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 12px;
    color: #e2e8f0;
    cursor: pointer;
}
.btn.primary {
    width: 100%;
    padding: 10px;
    background: #0284c7;
    border: 1px solid #38bdf8;
    color: #fff;
    font-weight: bold;
    cursor: pointer;
    font-family: monospace;
    border-radius: 4px;
}
.btn.primary:hover:not(:disabled) {
    background: #0369a1;
}
.btn.primary:disabled {
    opacity: 0.5;
    cursor: not-allowed;
}
</style>