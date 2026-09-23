<template>
    <div class="card-panel">
        <div class="panel-header">
            <h4>ХАРАКТЕРИСТИКИ МІСЦЕВОСТІ ДЛЯ: <span class="highlight">{{ mapName }}</span></h4>
            <span class="info-tag">ПОЛІГОННІ КОЕФІЦІЄНТИ РУХУ ТА УКРИТТІВ</span>
        </div>

        <div v-if="loading" class="empty-state">Завантаження модифікаторів...</div>

        <div v-else class="table-container">
            <table class="tactical-table">
                <thead>
                <tr>
                    <th>КАТЕГОРІЯ</th>
                    <th>ТИП ОБ'ЄКТА (OSM)</th>
                    <th>КОЛІСНІ (КОЕФ.)</th>
                    <th>ГУСЕНИЧНІ (КОЕФ.)</th>
                    <th>ВИДИМІСТЬ (М)</th>
                    <th>ЗАХИСТ УКРИТТЯ (%)</th>
                    <th>ДІЯ</th>
                </tr>
                </thead>
                <tbody>
                <tr v-for="mod in modifiers" :key="mod.id">
                    <td><span class="cat-pill" :class="mod.category.toLowerCase()">{{ mod.category }}</span></td>
                    <td>
                        <strong>{{ mod.osmKey }}={{ mod.osmValue }}</strong>
                        <div class="desc">{{ mod.description }}</div>
                    </td>
                    <td>
                        <input
                            v-model.number="mod.speedModifierWheeled"
                            type="number"
                            step="0.05"
                            min="0"
                            max="1.5"
                            class="num-input"
                        />
                    </td>
                    <td>
                        <input
                            v-model.number="mod.speedModifierTracked"
                            type="number"
                            step="0.05"
                            min="0"
                            max="1.5"
                            class="num-input"
                        />
                    </td>
                    <td>
                        <input
                            v-model.number="mod.visibilityMeters"
                            type="number"
                            step="10"
                            min="10"
                            max="5000"
                            placeholder="Без меж"
                            class="num-input"
                        />
                    </td>
                    <td>
                        <input
                            v-model.number="mod.coverDefensePercent"
                            type="number"
                            step="5"
                            min="0"
                            max="95"
                            class="num-input"
                        />
                    </td>
                    <td>
                        <button
                            type="button"
                            class="btn-save"
                            :disabled="savingId === mod.id"
                            @click="saveModifier(mod)"
                        >
                            {{ savingId === mod.id ? '...' : 'ЗБЕРЕГТИ' }}
                        </button>
                    </td>
                </tr>
                </tbody>
            </table>
        </div>
    </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import { mapApi } from '@/modules/map-studio/mapApi';
import type { SurfaceModifierDto } from '@/modules/map-studio/types';

const props = defineProps<{
    mapId: string;
    mapName: string;
}>();

const modifiers = ref<SurfaceModifierDto[]>([]);
const loading = ref(false);
const savingId = ref<string | null>(null);

const loadModifiers = async () => {
    if (!props.mapId) return;
    try {
        loading.value = true;
        modifiers.value = await mapApi.getModifiers(props.mapId);
    } catch (err: unknown) {
        console.error('[TALOS MODIFIERS] Failed to load surface modifiers:', err);
    } finally {
        loading.value = false;
    }
};

watch(() => props.mapId, loadModifiers, { immediate: true });

const saveModifier = async (mod: SurfaceModifierDto) => {
    try {
        savingId.value = mod.id;
        // Normalize visibility to null if empty or non-positive
        if (mod.visibilityMeters !== null && (isNaN(mod.visibilityMeters) || mod.visibilityMeters <= 0)) {
            mod.visibilityMeters = null;
        }

        await mapApi.updateModifier(props.mapId, mod);
        alert(`Збережено ТТХ для: ${mod.osmValue}`);
    } catch (err: unknown) {
        const message = err instanceof Error ? err.message : String(err);
        alert('Помилка збереження: ' + message);
    } finally {
        savingId.value = null;
    }
};
</script>

<style scoped>
.card-panel {
    background: rgba(15, 23, 42, 0.85);
    border: 1px solid rgba(0, 168, 255, 0.3);
    border-radius: 6px;
    padding: 16px;
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
    color: #cbd5e1;
    font-size: 13px;
}
.highlight {
    color: #f1c40f;
}
.info-tag {
    font-size: 10px;
    color: #94a3b8;
    font-family: monospace;
}
.table-container {
    max-height: 400px;
    overflow-y: auto;
}
.tactical-table {
    width: 100%;
    border-collapse: collapse;
    font-size: 11px;
    font-family: monospace;
}
.tactical-table th {
    text-align: left;
    padding: 8px;
    background: #0b1120;
    color: #00a8ff;
    border-bottom: 1px solid #334155;
}
.tactical-table td {
    padding: 8px;
    border-bottom: 1px solid #1e293b;
    color: #e2e8f0;
}
.desc {
    font-size: 9px;
    color: #64748b;
}
.cat-pill {
    font-size: 8px;
    padding: 2px 4px;
    border-radius: 2px;
    font-weight: bold;
}
.cat-pill.road { background: #1e3a8a; color: #93c5fd; }
.cat-pill.vegetation { background: #064e3b; color: #6ee7b7; }
.cat-pill.water { background: #0c4a6e; color: #7dd3fc; }
.cat-pill.soil { background: #78350f; color: #fde68a; }
.cat-pill.building { background: #4c1d95; color: #c4b5fd; }

.num-input {
    width: 70px;
    background: #0f172a;
    border: 1px solid #334155;
    color: #fff;
    padding: 4px;
    font-size: 11px;
    font-family: monospace;
    border-radius: 3px;
}
.num-input:focus {
    border-color: #00a8ff;
    outline: none;
}
.btn-save {
    background: #047857;
    border: 1px solid #10b981;
    color: #fff;
    padding: 4px 8px;
    font-size: 10px;
    cursor: pointer;
    border-radius: 3px;
    font-family: monospace;
    transition: all 0.15s ease-in-out;
}
.btn-save:hover:not(:disabled) {
    background: #059669;
}
.btn-save:disabled {
    opacity: 0.5;
    cursor: not-allowed;
}
.empty-state {
    padding: 24px;
    text-align: center;
    color: #64748b;
    font-size: 12px;
    font-family: monospace;
}
</style>