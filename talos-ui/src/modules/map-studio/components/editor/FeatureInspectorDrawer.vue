<!-- FeatureInspectorDrawer.vue -->
<!-- Tactical single-feature inspector drawer with 7-category parameters and status patching -->

<template>
    <div class="feature-inspector-drawer">
        <!-- Header: Title, Category Badge, and Close Button -->
        <div class="drawer-header">
            <div class="header-left">
                <span class="category-badge" :class="feature.category.toLowerCase()">
                    {{ categoryIcon }} {{ feature.category }}
                </span>
                <h3 class="feature-title">{{ feature.name || 'Тактичний об’єкт' }}</h3>
                <span class="feature-osm-type">[{{ feature.typeKey }}={{ feature.typeValue }}]</span>
            </div>
            <div class="header-actions">
                <button type="button" class="btn-close" @click="$emit('close')">✕</button>
            </div>
        </div>

        <div class="drawer-body">
            <!-- 1. Tactical Status Switcher -->
            <div class="param-group">
                <label class="param-label">ТАКТИЧНИЙ СТАТУС ОБ'ЄКТА:</label>
                <div class="status-btn-group">
                    <button
                        type="button"
                        class="status-btn operational"
                        :class="{ active: currentStatus === 'OPERATIONAL' }"
                        @click="currentStatus = 'OPERATIONAL'"
                    >
                        🟢 БОЄГОТОВИЙ
                    </button>
                    <button
                        type="button"
                        class="status-btn destroyed"
                        :class="{ active: currentStatus === 'DESTROYED' }"
                        @click="currentStatus = 'DESTROYED'"
                    >
                        ⚫ ЗРУЙНОВАНИЙ
                    </button>
                    <button
                        type="button"
                        class="status-btn mined"
                        :class="{ active: currentStatus === 'MINED' }"
                        @click="currentStatus = 'MINED'"
                    >
                        🔴 ЗАМІНОВАНО
                    </button>
                    <button
                        type="button"
                        class="status-btn checkpoint"
                        :class="{ active: currentStatus === 'CHECKPOINT' }"
                        @click="currentStatus = 'CHECKPOINT'"
                    >
                        🟠 БЛОКПОСТ
                    </button>
                </div>
            </div>

            <div class="grid-two-cols">
                <!-- 2. Category-Specific Physical Dimensions -->
                <div class="param-card">
                    <h4 class="card-title">ФІЗИЧНІ РОЗМІРИ</h4>

                    <!-- Building Height / Levels -->
                    <div v-if="feature.category === 'BUILDING'" class="field-item">
                        <div class="field-header">
                            <span class="field-name">Висота споруди (м):</span>
                            <span class="field-val">{{ currentHeightMeters }} м</span>
                        </div>
                        <input
                            v-model.number="currentHeightMeters"
                            type="range"
                            min="3"
                            max="70"
                            step="0.5"
                            class="tactical-slider"
                        />
                    </div>

                    <!-- Road Width -->
                    <div v-if="feature.category === 'ROAD' || feature.category === 'BRIDGE'" class="field-item">
                        <div class="field-header">
                            <span class="field-name">Ширина полотна (м):</span>
                            <span class="field-val">{{ currentWidthMeters }} м</span>
                        </div>
                        <input
                            v-model.number="currentWidthMeters"
                            type="range"
                            min="3"
                            max="30"
                            step="0.5"
                            class="tactical-slider"
                        />
                    </div>

                    <!-- Object Display Name -->
                    <div class="field-item">
                        <label class="field-name">Тактична назва / Позивний:</label>
                        <input
                            v-model="customName"
                            type="text"
                            class="tactical-input"
                            placeholder="Наприклад: Опорний пункт 'Омега'..."
                        />
                    </div>
                </div>

                <!-- 3. Tactical Movement & Cover Modifiers -->
                <div class="param-card">
                    <h4 class="card-title">ТАКТИЧНІ МОДИФІКАТОРИ</h4>

                    <div class="field-item">
                        <div class="field-header">
                            <span class="field-name">Колісна техніка (множник):</span>
                            <span class="field-val">x{{ speedWheeled.toFixed(2) }}</span>
                        </div>
                        <input
                            v-model.number="speedWheeled"
                            type="range"
                            min="0"
                            max="1.5"
                            step="0.05"
                            class="tactical-slider"
                        />
                    </div>

                    <div class="field-item">
                        <div class="field-header">
                            <span class="field-name">Гусенична техніка (множник):</span>
                            <span class="field-val">x{{ speedTracked.toFixed(2) }}</span>
                        </div>
                        <input
                            v-model.number="speedTracked"
                            type="range"
                            min="0"
                            max="1.5"
                            step="0.05"
                            class="tactical-slider"
                        />
                    </div>

                    <div class="field-item">
                        <div class="field-header">
                            <span class="field-name">Захист / Укриття (%):</span>
                            <span class="field-val">{{ coverPercent }}%</span>
                        </div>
                        <input
                            v-model.number="coverPercent"
                            type="range"
                            min="0"
                            max="90"
                            step="5"
                            class="tactical-slider"
                        />
                    </div>
                </div>
            </div>

            <!-- 4. Notes / Intelligence -->
            <div class="param-group">
                <label class="param-label">РОЗВІДУВАЛЬНІ ПРИМІТКИ / ОПИС:</label>
                <textarea
                    v-model="customNotes"
                    class="tactical-textarea"
                    rows="2"
                    placeholder="Вкажіть особливості позиції, наявність підвалів, бродів чи перешкод..."
                ></textarea>
            </div>
        </div>

        <!-- Footer Actions -->
        <div class="drawer-footer">
            <button type="button" class="btn-cancel" @click="$emit('close')">СКАСУВАТИ</button>
            <button type="button" class="btn-save" :disabled="isSaving" @click="handleSave">
                {{ isSaving ? 'ЗБЕРЕЖЕННЯ...' : '💾 ЗБЕРЕГТИ ЗМІНИ ОБ’ЄКТА' }}
            </button>
        </div>
    </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import type { FeatureStatus, FeatureUpdateRequest } from '../../types';

const props = defineProps<{
    feature: {
        id: string;
        name: string;
        category: string;
        typeKey: string;
        typeValue: string;
        status: FeatureStatus;
        speedOverrideWheeled?: number | null;
        speedOverrideTracked?: number | null;
        visibilityOverride?: number | null;
        coverOverride?: number | null;
        widthMeters?: number | null;
        heightMeters?: number | null;
        customNotes?: string;
    };
}>();

const emit = defineEmits<{
    (e: 'close'): void;
    (e: 'save', patch: FeatureUpdateRequest): void;
}>();

const isSaving = ref(false);

const currentStatus = ref<FeatureStatus>(props.feature.status || 'OPERATIONAL');
const customName = ref(props.feature.name || '');
const currentHeightMeters = ref(props.feature.heightMeters ?? 7.5);
const currentWidthMeters = ref(props.feature.widthMeters ?? 8.0);
const speedWheeled = ref(props.feature.speedOverrideWheeled ?? 1.0);
const speedTracked = ref(props.feature.speedOverrideTracked ?? 1.0);
const coverPercent = ref(props.feature.coverOverride ?? 20);
const customNotes = ref(props.feature.customNotes || '');

watch(
    () => props.feature,
    (newVal) => {
        currentStatus.value = newVal.status || 'OPERATIONAL';
        customName.value = newVal.name || '';
        currentHeightMeters.value = newVal.heightMeters ?? 7.5;
        currentWidthMeters.value = newVal.widthMeters ?? 8.0;
        speedWheeled.value = newVal.speedOverrideWheeled ?? 1.0;
        speedTracked.value = newVal.speedOverrideTracked ?? 1.0;
        coverPercent.value = newVal.coverOverride ?? 20;
        customNotes.value = newVal.customNotes || '';
    },
    { deep: true }
);

const categoryIcon = computed(() => {
    switch (props.feature.category) {
        case 'ROAD': return '🛣️';
        case 'BUILDING': return '🏢';
        case 'BRIDGE': return '🌉';
        case 'RIVER':
        case 'OPEN_WATER': return '🌊';
        case 'VEGETATION': return '🌲';
        case 'SOIL': return '🏜️';
        default: return '📍';
    }
});

const handleSave = () => {
    isSaving.value = true;
    const patch: FeatureUpdateRequest = {
        name: customName.value,
        status: currentStatus.value,
        speedModifierOverrideWheeled: Number(speedWheeled.value),
        speedModifierOverrideTracked: Number(speedTracked.value),
        coverDefenseOverride: Number(coverPercent.value),
        widthMeters: props.feature.category === 'ROAD' || props.feature.category === 'BRIDGE'
            ? Number(currentWidthMeters.value)
            : null,
        heightMeters: props.feature.category === 'BUILDING'
            ? Number(currentHeightMeters.value)
            : null,
        customNotes: customNotes.value
    };

    emit('save', patch);
    isSaving.value = false;
};
</script>

<style scoped>
.feature-inspector-drawer {
    position: absolute;
    bottom: 38px;
    left: 16px;
    right: 360px;
    background: #0f172a;
    border: 1px solid #334155;
    border-radius: 8px 8px 0 0;
    box-shadow: 0 -8px 24px rgba(0, 0, 0, 0.6);
    z-index: 100;
    color: #e2e8f0;
    font-family: inherit;
    display: flex;
    flex-direction: column;
    max-height: 480px;
    overflow: hidden;
}

.drawer-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 10px 16px;
    background: #1e293b;
    border-bottom: 1px solid #334155;
}

.header-left {
    display: flex;
    align-items: center;
    gap: 10px;
}

.category-badge {
    padding: 3px 8px;
    border-radius: 4px;
    font-size: 11px;
    font-weight: 800;
    letter-spacing: 0.5px;
    background: #334155;
    color: #f8fafc;
}

.category-badge.building { background: #991b1b; }
.category-badge.road { background: #b45309; }
.category-badge.river, .category-badge.open_water { background: #0369a1; }
.category-badge.vegetation { background: #15803d; }
.category-badge.bridge { background: #475569; }

.feature-title {
    font-size: 14px;
    font-weight: 700;
    margin: 0;
    color: #f8fafc;
}

.feature-osm-type {
    font-size: 11px;
    color: #94a3b8;
    font-family: monospace;
}

.btn-close {
    background: transparent;
    border: none;
    color: #94a3b8;
    font-size: 16px;
    cursor: pointer;
    padding: 4px 8px;
}
.btn-close:hover { color: #f8fafc; }

.drawer-body {
    padding: 12px 16px;
    overflow-y: auto;
    display: flex;
    flex-direction: column;
    gap: 12px;
}

.param-group {
    display: flex;
    flex-direction: column;
    gap: 6px;
}

.param-label {
    font-size: 10px;
    font-weight: 800;
    color: #94a3b8;
    letter-spacing: 0.6px;
}

.status-btn-group {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 8px;
}

.status-btn {
    padding: 7px 10px;
    border: 1px solid #334155;
    background: #1e293b;
    border-radius: 4px;
    font-size: 11px;
    font-weight: 700;
    color: #cbd5e1;
    cursor: pointer;
    transition: all 0.15s ease;
}

.status-btn:hover { background: #334155; }
.status-btn.operational.active { background: #15803d; border-color: #22c55e; color: #fff; }
.status-btn.destroyed.active { background: #334155; border-color: #64748b; color: #94a3b8; text-decoration: line-through; }
.status-btn.mined.active { background: #991b1b; border-color: #ef4444; color: #fff; }
.status-btn.checkpoint.active { background: #c2410c; border-color: #f97316; color: #fff; }

.grid-two-cols {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 12px;
}

.param-card {
    background: #162032;
    border: 1px solid #253349;
    border-radius: 6px;
    padding: 10px 12px;
    display: flex;
    flex-direction: column;
    gap: 8px;
}

.card-title {
    font-size: 11px;
    font-weight: 800;
    color: #38bdf8;
    margin: 0 0 4px 0;
    letter-spacing: 0.5px;
}

.field-item {
    display: flex;
    flex-direction: column;
    gap: 3px;
}

.field-header {
    display: flex;
    justify-content: space-between;
    font-size: 11px;
}

.field-name { color: #94a3b8; }
.field-val { font-weight: 700; color: #f8fafc; font-family: monospace; }

.tactical-slider {
    width: 100%;
    accent-color: #38bdf8;
    height: 4px;
    cursor: pointer;
}

.tactical-input, .tactical-textarea {
    width: 100%;
    background: #0f172a;
    border: 1px solid #334155;
    border-radius: 4px;
    color: #f8fafc;
    padding: 5px 8px;
    font-size: 11px;
    box-sizing: border-box;
}

.tactical-input:focus, .tactical-textarea:focus {
    border-color: #38bdf8;
    outline: none;
}

.drawer-footer {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    padding: 8px 16px;
    background: #1e293b;
    border-top: 1px solid #334155;
}

.btn-cancel {
    padding: 6px 14px;
    background: #334155;
    border: none;
    border-radius: 4px;
    color: #cbd5e1;
    font-size: 11px;
    font-weight: 700;
    cursor: pointer;
}

.btn-save {
    padding: 6px 18px;
    background: #0284c7;
    border: none;
    border-radius: 4px;
    color: #ffffff;
    font-size: 11px;
    font-weight: 800;
    cursor: pointer;
    transition: background 0.15s;
}
.btn-save:hover { background: #0369a1; }
.btn-save:disabled { opacity: 0.5; cursor: not-allowed; }
</style>