<template>
    <footer class="editor-status-bar">
        <div class="status-left">
            <span class="status-item">
                <span class="label">КООРДИНАТИ:</span>
                <strong class="val">{{ cursorCoords }}</strong>
            </span>
            <span class="status-divider">|</span>
            <span class="status-item">
                <span class="label">MGRS:</span>
                <strong class="val text-amber">{{ cursorMgrs }}</strong>
            </span>
            <span class="status-divider">|</span>
            <span class="status-item">
                <span class="label">ВИСОТА РЕЛЬЄФУ:</span>
                <strong class="val text-green">{{ cursorElevation }} м</strong>
            </span>
        </div>

        <div class="status-center">
            <span class="active-tool-pill" :class="activeToolClass">
                {{ activeToolLabel }}
            </span>
        </div>

        <div class="status-right">
            <!-- WASD First Person Roaming Toggle -->
            <button
                v-if="is3DMode"
                type="button"
                class="btn-ground-mode"
                :class="{ active: isGroundMode }"
                @click="$emit('toggle-ground-mode')"
            >
                {{ isGroundMode ? '🪖 НАЗЕМНИЙ РЕЖИМ (WASD: УВІМК)' : '🚶 ОГЛЯД З ПОВЕРХНІ' }}
            </button>

            <!-- 2D / 3D Perspective Toggle -->
            <button
                type="button"
                class="btn-perspective"
                :class="{ is3d: is3DMode }"
                @click="$emit('toggle-perspective')"
            >
                {{ is3DMode ? '🧊 3D РЕЛЬЄФ (НАХИЛ)' : '🗺️ 2D ВИГЛЯД ЗГОРИ (ОРТО)' }}
            </button>
        </div>
    </footer>
</template>

<script setup lang="ts">
import { computed } from 'vue';

const props = defineProps<{
    cursorCoords: string;
    cursorMgrs: string;
    cursorElevation: number;
    is3DMode: boolean;
    isGroundMode: boolean;
    toolMode: string;
    isDrawingLine: boolean;
}>();

defineEmits<{
    (e: 'toggle-perspective'): void;
    (e: 'toggle-ground-mode'): void;
}>();

const activeToolLabel = computed(() => {
    if (props.isGroundMode) return '🎮 КЕРУВАННЯ: WASD + SHIFT (БІГ), ПРАВА КНОПКА МИШІ (ОГЛЯД)';
    if (props.isDrawingLine) return '📏 ЛІНІЯ: ОБЕРІТЬ ТОЧКУ (Б)';
    if (props.toolMode === 'LINEAR') return '📏 ІНСТРУМЕНТ: ЛІНІЯ (А → Б)';
    if (props.toolMode === 'RADIAL') return '🖌️ ІНСТРУМЕНТ: РАДІАЛЬНИЙ ПЕНЗЕЛЬ';
    if (props.toolMode === 'INSPECT') return '🔍 ІНСТРУМЕНТ: ІНСПЕКТОР ОБ\'ЄКТІВ';
    return '✋ РЕЖИМ: НАВІГАЦІЯ ТА ОГЛЯД';
});

const activeToolClass = computed(() => {
    if (props.isGroundMode) return 'ground';
    if (props.isDrawingLine) return 'drawing';
    if (props.toolMode === 'LINEAR' || props.toolMode === 'RADIAL') return 'sculpt';
    if (props.toolMode === 'INSPECT') return 'inspect';
    return 'navigate';
});
</script>

<style scoped>
.editor-status-bar {
    height: 32px;
    background: #090d16;
    border-top: 1px solid rgba(0, 168, 255, 0.25);
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 0 16px;
    font-family: monospace;
    font-size: 11px;
    color: #94a3b8;
    z-index: 100;
}

.status-left { display: flex; align-items: center; gap: 8px; }
.status-item .label { color: #64748b; margin-right: 4px; font-size: 10px; }
.status-item .val { color: #f8fafc; }
.text-amber { color: #f1c40f !important; }
.text-green { color: #00e676 !important; }
.status-divider { color: #334155; }

.active-tool-pill {
    font-size: 10px;
    font-weight: bold;
    padding: 2px 10px;
    border-radius: 3px;
    letter-spacing: 0.5px;
}
.active-tool-pill.navigate { background: rgba(148, 163, 184, 0.15); color: #94a3b8; border: 1px solid #475569; }
.active-tool-pill.sculpt { background: rgba(2, 132, 199, 0.25); color: #38bdf8; border: 1px solid #0284c7; }
.active-tool-pill.drawing { background: rgba(239, 68, 68, 0.25); color: #f87171; border: 1px solid #ef4444; animation: blink 1s infinite alternate; }
.active-tool-pill.inspect { background: rgba(245, 158, 11, 0.2); color: #fbbf24; border: 1px solid #f59e0b; }

@keyframes blink { from { opacity: 0.7; } to { opacity: 1; } }

.btn-perspective {
    background: #1e293b;
    border: 1px solid #00a8ff;
    color: #38bdf8;
    padding: 3px 10px;
    border-radius: 3px;
    font-size: 10px;
    font-family: monospace;
    font-weight: bold;
    cursor: pointer;
    transition: all 0.15s;
}
.btn-perspective:hover { background: #0284c7; color: #fff; }
.btn-perspective.is3d { background: #0284c7; color: #fff; border-color: #38bdf8; box-shadow: 0 0 6px rgba(56, 189, 248, 0.4); }

.btn-ground-mode {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 4px 10px;
    margin-right: 8px;
    font-size: 11px;
    font-weight: 700;
    color: #cbd5e1;
    background: #1e293b;
    border: 1px solid #475569;
    border-radius: 4px;
    cursor: pointer;
    transition: all 0.2s ease;
}

.btn-ground-mode:hover {
    background: #334155;
    color: #f8fafc;
}

.btn-ground-mode.active {
    background: #16a34a;
    border-color: #22c55e;
    color: #ffffff;
    box-shadow: 0 0 10px rgba(34, 197, 94, 0.4);
}

.active-tool-pill.ground {
    background: rgba(34, 197, 94, 0.2);
    border: 1px solid #22c55e;
    color: #4ade80;
}
</style>