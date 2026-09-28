<template>
    <header class="editor-header">
        <div class="header-left">
            <button type="button" class="btn-back" @click="$emit('back')">← НАЗАД ДО КАРТ</button>
            <div class="theater-badge">
                <span class="th-name">{{ mapName }}</span>
                <span class="th-size">{{ sizeKm }}×{{ sizeKm }} км</span>
            </div>
        </div>

        <!-- MAIN WORKSPACE SELECTOR TABS -->
        <div class="workspace-tabs">
            <button
                type="button"
                class="ws-tab"
                :class="{ active: activeTab === 'ELEVATION' }"
                @click="$emit('select-tab', 'ELEVATION')"
            >
                ⛰️ РЕДАКТОР РЕЛЬЄФУ
            </button>
            <button
                type="button"
                class="ws-tab"
                :class="{ active: activeTab === 'OBJECTS' }"
                @click="$emit('select-tab', 'OBJECTS')"
            >
                🏘️ ТАКТИЧНІ ОБ'ЄКТИ
            </button>
            <button
                type="button"
                class="ws-tab"
                :class="{ active: activeTab === 'TEXTURES' }"
                @click="$emit('select-tab', 'TEXTURES')"
            >
                🗺️ ПІДКЛАДКИ КАРТИ
            </button>
        </div>

        <div class="header-right">
            <span class="spec-badge">100% OFFLINE ТВД</span>
        </div>
    </header>
</template>

<script setup lang="ts">
export type EditorWorkspaceTab = 'ELEVATION' | 'OBJECTS' | 'TEXTURES';

defineProps<{
    mapName: string;
    sizeKm: number;
    activeTab: EditorWorkspaceTab;
}>();

defineEmits<{
    (e: 'back'): void;
    (e: 'select-tab', tab: EditorWorkspaceTab): void;
}>();
</script>

<style scoped>
.editor-header {
    height: 48px;
    background: rgba(15, 23, 42, 0.98);
    border-bottom: 1px solid rgba(0, 168, 255, 0.3);
    padding: 0 16px;
    display: flex;
    justify-content: space-between;
    align-items: center;
    z-index: 100;
}
.header-left { display: flex; align-items: center; gap: 12px; }
.btn-back {
    background: transparent;
    border: 1px solid #475569;
    color: #cbd5e1;
    padding: 5px 10px;
    border-radius: 3px;
    cursor: pointer;
    font-family: monospace;
    font-size: 11px;
}
.btn-back:hover { border-color: #00a8ff; color: #fff; }
.theater-badge { display: flex; align-items: baseline; gap: 6px; font-family: monospace; }
.th-name { font-weight: bold; color: #00a8ff; font-size: 13px; }
.th-size { font-size: 10px; color: #64748b; }

.workspace-tabs { display: flex; gap: 6px; background: #090d16; padding: 4px; border-radius: 4px; border: 1px solid #334155; }
.ws-tab {
    background: transparent;
    border: 1px solid transparent;
    color: #94a3b8;
    padding: 6px 14px;
    font-size: 11px;
    font-family: monospace;
    font-weight: bold;
    cursor: pointer;
    border-radius: 3px;
    transition: all 0.15s;
}
.ws-tab:hover { color: #f8fafc; }
.ws-tab.active { background: #0284c7; border-color: #38bdf8; color: #fff; box-shadow: 0 0 10px rgba(56, 189, 248, 0.35); }

.spec-badge {
    background: rgba(0, 230, 118, 0.15);
    color: #00e676;
    border: 1px solid rgba(0, 230, 118, 0.4);
    padding: 4px 8px;
    border-radius: 3px;
    font-size: 10px;
    font-family: monospace;
}
</style>