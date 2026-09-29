<template>
    <div class="panel-card">
        <div class="panel-header">
            <span class="panel-title">⛰️ РЕЛЬЄФ ТА ІНЖЕНЕРНИЙ СКУЛЬПТИНГ</span>
        </div>

        <div class="panel-body">
            <!-- Undo / Redo Bar -->
            <div class="undo-redo-bar">
                <button
                    type="button"
                    class="btn-history"
                    :disabled="!historyStatus.canUndo || isProcessing"
                    @click="$emit('undo')"
                >
                    ↩ Скасувати (Undo)
                </button>
                <button
                    type="button"
                    class="btn-history"
                    :disabled="!historyStatus.canRedo || isProcessing"
                    @click="$emit('redo')"
                >
                    ↪ Повторити (Redo)
                </button>
            </div>

            <!-- Interaction Mode Selector -->
            <label class="control-label">РЕЖИМ ВЗАЄМОДІЇ З КАРТОЮ:</label>
            <div class="tool-modes-grid">
                <button
                    type="button"
                    class="tool-btn"
                    :class="{ active: toolMode === 'NAVIGATE' }"
                    @click="$emit('update:toolMode', 'NAVIGATE')"
                >
                    ✋ Огляд
                </button>
                <button
                    type="button"
                    class="tool-btn"
                    :class="{ active: toolMode === 'RADIAL' }"
                    @click="$emit('update:toolMode', 'RADIAL')"
                >
                    🖌️ Пензель
                </button>
                <button
                    type="button"
                    class="tool-btn"
                    :class="{ active: toolMode === 'LINEAR' }"
                    @click="$emit('update:toolMode', 'LINEAR')"
                >
                    📏 Лінія (А → Б)
                </button>
            </div>

            <!-- 1. Radial Operations -->
            <div v-if="toolMode === 'RADIAL'" class="sub-tool-section">
                <label class="control-label">ОПЕРАЦІЯ ПЕНЗЛЯ:</label>
                <div class="ops-column">
                    <button
                        type="button"
                        class="op-choice-btn"
                        :class="{ active: radialOp === 'DIG' }"
                        @click="$emit('update:radialOp', 'DIG')"
                    >
                        ⛏️ Вирити яму / вирву
                    </button>
                    <button
                        type="button"
                        class="op-choice-btn"
                        :class="{ active: radialOp === 'RAISE' }"
                        @click="$emit('update:radialOp', 'RAISE')"
                    >
                        ⛰️ Насипати курган / пагорб
                    </button>
                    <button
                        type="button"
                        class="op-choice-btn"
                        :class="{ active: radialOp === 'FLATTEN' }"
                        @click="$emit('update:radialOp', 'FLATTEN')"
                    >
                        ⎯ Вирівняти площину
                    </button>
                </div>

                <!-- Radius with direct input -->
                <div class="input-slider-box">
                    <div class="box-head">
                        <label>Радіус дії:</label>
                        <div class="num-wrap">
                            <input
                                :value="brushRadius"
                                type="number"
                                min="10"
                                max="400"
                                step="5"
                                class="direct-input"
                                @input="$emit('update:brushRadius', Number(($event.target as HTMLInputElement).value))"
                            />
                            <span class="unit">м</span>
                        </div>
                    </div>
                    <input
                        :value="brushRadius"
                        type="range"
                        min="10"
                        max="400"
                        step="5"
                        class="range-slider"
                        @input="$emit('update:brushRadius', Number(($event.target as HTMLInputElement).value))"
                    />
                </div>
            </div>

            <!-- 2. Linear Operations -->
            <div v-else-if="toolMode === 'LINEAR'" class="sub-tool-section">
                <label class="control-label">ТИП ФОРТИФІКАЦІЇ / ЗРІЗУ:</label>
                <div class="ops-column">
                    <button
                        type="button"
                        class="op-choice-btn"
                        :class="{ active: linearOp === 'TRENCH_DIG' }"
                        @click="$emit('update:linearOp', 'TRENCH_DIG')"
                    >
                        🪖 Викопати траншею / окоп
                    </button>
                    <button
                        type="button"
                        class="op-choice-btn"
                        :class="{ active: linearOp === 'AT_DITCH' }"
                        @click="$emit('update:linearOp', 'AT_DITCH')"
                    >
                        🚜 Протитанковий рів (глибокий)
                    </button>
                    <button
                        type="button"
                        class="op-choice-btn"
                        :class="{ active: linearOp === 'BERM_RAISE' }"
                        @click="$emit('update:linearOp', 'BERM_RAISE')"
                    >
                        🛡️ Насипати захисний вал
                    </button>
                </div>

                <div class="drawing-instruction" :class="{ awaiting: isDrawingLine }">
                    <span v-if="!isDrawingLine">📍 Клікніть точку А на карті</span>
                    <div v-else class="drawing-active-row">
                        <span>📍 Точка А зафіксована. Клікніть точку Б</span>
                        <button type="button" class="btn-cancel-line" @click="$emit('cancel-line')">✕ Скасувати</button>
                    </div>
                </div>

                <!-- Width with direct input -->
                <div class="input-slider-box">
                    <div class="box-head">
                        <label>Ширина лінії:</label>
                        <div class="num-wrap">
                            <input
                                :value="lineWidth"
                                type="number"
                                min="2"
                                max="40"
                                step="1"
                                class="direct-input"
                                @input="$emit('update:lineWidth', Number(($event.target as HTMLInputElement).value))"
                            />
                            <span class="unit">м</span>
                        </div>
                    </div>
                    <input
                        :value="lineWidth"
                        type="range"
                        min="2"
                        max="40"
                        step="1"
                        class="range-slider"
                        @input="$emit('update:lineWidth', Number(($event.target as HTMLInputElement).value))"
                    />
                </div>
            </div>

            <!-- Height Delta with direct input -->
            <div v-if="toolMode !== 'NAVIGATE'" class="input-slider-box">
                <div class="box-head">
                    <label>Глибина / Висота (&Delta;h):</label>
                    <div class="num-wrap">
                        <input
                            :value="deltaHeight"
                            type="number"
                            min="0.5"
                            max="50"
                            step="0.5"
                            class="direct-input"
                            @input="$emit('update:deltaHeight', Number(($event.target as HTMLInputElement).value))"
                        />
                        <span class="unit">м</span>
                    </div>
                </div>
                <input
                    :value="deltaHeight"
                    type="range"
                    min="0.5"
                    max="50"
                    step="0.5"
                    class="range-slider"
                    @input="$emit('update:deltaHeight', Number(($event.target as HTMLInputElement).value))"
                />
            </div>

            <!-- Integrated Basemap Underlay Section for Orienting / Spotting Ponds -->
            <div class="underlay-section">
                <div class="underlay-title-row">
                    <span class="control-label">🗺️ ПІДКЛАДКА ДЛЯ ОРІЄНТИРУ:</span>
                    <select
                        :value="currentLayerType"
                        class="layer-mini-select"
                        @change="$emit('select-layer', ($event.target as HTMLSelectElement).value)"
                    >
                        <option v-for="l in layers" :key="l.id" :value="l.layerType">
                            {{ l.layerType === 'SATELLITE' ? 'Google HD' : l.layerType }}
                        </option>
                    </select>
                </div>

                <div class="input-slider-box">
                    <div class="box-head">
                        <label>Прозорість фото (0% = чистий рельєф):</label>
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
                        Підніміть до 30-50%, щоб побачити реальний ставок чи яр на фото та викопати там яму.
                    </span>
                </div>
            </div>

            <div v-if="isProcessing" class="process-banner">
                <span class="pulse-dot"></span> Перерахунок DEM растра...
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import type { SculptOperation, LinearSculptOperation, TerrainHistoryStatus, MapLayerDto } from '../../types';

defineProps<{
    toolMode: 'NAVIGATE' | 'RADIAL' | 'LINEAR';
    radialOp: SculptOperation;
    linearOp: LinearSculptOperation;
    brushRadius: number;
    lineWidth: number;
    deltaHeight: number;
    historyStatus: TerrainHistoryStatus;
    isDrawingLine: boolean;
    isProcessing: boolean;
    layers: MapLayerDto[];
    currentLayerType: string;
    basemapOpacity: number;
}>();

defineEmits<{
    (e: 'update:toolMode', mode: 'NAVIGATE' | 'RADIAL' | 'LINEAR'): void;
    (e: 'update:radialOp', op: SculptOperation): void;
    (e: 'update:linearOp', op: LinearSculptOperation): void;
    (e: 'update:brushRadius', val: number): void;
    (e: 'update:lineWidth', val: number): void;
    (e: 'update:deltaHeight', val: number): void;
    (e: 'undo'): void;
    (e: 'redo'): void;
    (e: 'cancel-line'): void;
    (e: 'select-layer', type: string): void;
    (e: 'update:opacity', val: number): void;
}>();
</script>

<style scoped>
.panel-card { background: rgba(15, 23, 42, 0.85); border: 1px solid #334155; border-radius: 4px; overflow: hidden; }
.panel-header { background: rgba(255, 255, 255, 0.04); padding: 10px 14px; border-bottom: 1px solid rgba(255, 255, 255, 0.05); }
.panel-title { font-size: 11px; font-weight: bold; color: #00a8ff; font-family: monospace; letter-spacing: 0.5px; }
.panel-body { padding: 12px; display: flex; flex-direction: column; gap: 10px; font-family: monospace; }

.undo-redo-bar { display: flex; gap: 6px; }
.btn-history {
    flex: 1; background: #1e293b; border: 1px solid #475569; color: #cbd5e1;
    padding: 6px; font-size: 10px; font-family: monospace; font-weight: bold; cursor: pointer; border-radius: 3px;
    transition: all 0.15s;
}
.btn-history:hover:not(:disabled) { background: #0284c7; border-color: #38bdf8; color: #fff; }
.btn-history:disabled { opacity: 0.35; cursor: not-allowed; }

.control-label { font-size: 9px; color: #94a3b8; font-weight: bold; }
.tool-modes-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 4px; }
.tool-btn {
    background: #1e293b; border: 1px solid #334155; color: #94a3b8;
    padding: 6px 4px; font-size: 9px; font-family: monospace; cursor: pointer; border-radius: 3px;
}
.tool-btn.active { background: #0284c7; border-color: #38bdf8; color: #fff; font-weight: bold; }

.sub-tool-section { display: flex; flex-direction: column; gap: 8px; }
.ops-column { display: flex; flex-direction: column; gap: 4px; }
.op-choice-btn {
    background: #1e293b; border: 1px solid #334155; color: #cbd5e1;
    padding: 6px 10px; font-size: 10px; font-family: monospace; cursor: pointer; border-radius: 3px; text-align: left;
}
.op-choice-btn.active { background: #047857; border-color: #10b981; color: #fff; font-weight: bold; }

.drawing-instruction {
    background: rgba(0, 168, 255, 0.1); border-left: 2px solid #00a8ff;
    padding: 6px 8px; font-size: 10px; color: #38bdf8;
}
.drawing-instruction.awaiting { background: rgba(239, 68, 68, 0.15); border-left-color: #ef4444; color: #fca5a5; }
.drawing-active-row { display: flex; justify-content: space-between; align-items: center; }
.btn-cancel-line { background: #7f1d1d; border: 1px solid #ef4444; color: #fff; font-size: 9px; padding: 2px 6px; border-radius: 2px; cursor: pointer; }

.input-slider-box { background: rgba(0, 0, 0, 0.3); padding: 8px; border-radius: 3px; display: flex; flex-direction: column; gap: 4px; }
.box-head { display: flex; justify-content: space-between; align-items: center; font-size: 10px; color: #94a3b8; }
.num-wrap { display: flex; align-items: center; gap: 4px; }
.direct-input {
    width: 45px; background: #0b1120; border: 1px solid #00a8ff; color: #00e676;
    font-weight: bold; padding: 2px 4px; font-size: 11px; font-family: monospace; border-radius: 2px; text-align: right;
}
.unit { color: #64748b; font-size: 10px; }
.range-slider { width: 100%; cursor: pointer; }

/* Underlay section */
.underlay-section {
    border-top: 1px solid #334155; padding-top: 8px; margin-top: 4px; display: flex; flex-direction: column; gap: 6px;
}
.underlay-title-row { display: flex; justify-content: space-between; align-items: center; }
.layer-mini-select {
    background: #0b1120; border: 1px solid #334155; color: #38bdf8; font-size: 9px; padding: 3px 6px; border-radius: 3px;
}
.hint-text { font-size: 8px; color: #64748b; line-height: 1.3; }

.process-banner {
    background: rgba(2, 132, 199, 0.2); border: 1px solid #0284c7; padding: 6px;
    font-size: 10px; color: #38bdf8; border-radius: 3px; display: flex; align-items: center; gap: 6px;
}
.pulse-dot { width: 6px; height: 6px; border-radius: 50%; background: #00e676; animation: pulse 1s infinite alternate; }
@keyframes pulse { from { opacity: 0.3; } to { opacity: 1; } }
</style>