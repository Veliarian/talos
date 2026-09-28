<template>
    <div class="panel-card">
        <div class="panel-header">
            <span class="panel-title">🏘️ ТАКТИЧНІ ОБ'ЄКТИ ТА ПРИЗНАЧЕННЯ ТТХ</span>
        </div>
        <div class="panel-body">
            <!-- Category Filter Pills -->
            <div class="filter-pills-row">
                <button
                    v-for="cat in (['ALL', 'ROAD', 'VEGETATION', 'BUILDING', 'WATER'] as const)"
                    :key="cat"
                    type="button"
                    class="pill-btn"
                    :class="{ active: selectedCategory === cat }"
                    @click="selectedCategory = cat"
                >
                    {{ cat === 'ALL' ? 'ВСІ' : cat }}
                </button>
            </div>

            <!-- List of discovered object types on this map -->
            <div class="modifiers-list">
                <div
                    v-for="mod in filteredModifiers"
                    :key="mod.id"
                    class="mod-item-card"
                    :class="{ highlighted: activeHighlightType === mod.osmValue }"
                    @click="$emit('highlight', mod)"
                >
                    <div class="card-top">
                        <span class="cat-pill" :class="mod.category.toLowerCase()">{{ mod.category }}</span>
                        <code class="osm-tag">{{ mod.osmKey }}={{ mod.osmValue }}</code>
                    </div>

                    <div class="card-desc">{{ mod.description || 'Не налаштовано' }}</div>

                    <!-- Template Assigner Dropdown -->
                    <div class="assign-row" @click.stop>
                        <select v-model="selectedTmpl[mod.id]" class="tmpl-select">
                            <option value="">-- Призначити шаблон ТТХ --</option>
                            <option v-for="t in availableTemplates" :key="t.id" :value="t.id">
                                {{ t.description }} ({{ t.category }})
                            </option>
                        </select>
                        <button
                            type="button"
                            class="btn-apply"
                            :disabled="!selectedTmpl[mod.id]"
                            @click="$emit('apply-template', mod, selectedTmpl[mod.id])"
                        >
                            ✓
                        </button>
                    </div>

                    <!-- TTX Quick Stats Grid -->
                    <div class="ttx-grid" @click.stop>
                        <div class="ttx-cell">
                            <span>Колісні:</span>
                            <strong>{{ (mod.speedModifierWheeled * 100).toFixed(0) }}%</strong>
                        </div>
                        <div class="ttx-cell">
                            <span>Гусеничні:</span>
                            <strong>{{ (mod.speedModifierTracked * 100).toFixed(0) }}%</strong>
                        </div>
                        <div class="ttx-cell">
                            <span>Видимість:</span>
                            <strong>{{ mod.visibilityMeters ? mod.visibilityMeters + 'м' : '∞' }}</strong>
                        </div>
                        <div class="ttx-cell">
                            <span>Захист:</span>
                            <strong>{{ mod.coverDefensePercent }}%</strong>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue';
import type { TacticalModifierData, DefaultModifierDto } from '../../types';

const props = defineProps<{
    modifiers: TacticalModifierData[];
    availableTemplates: DefaultModifierDto[];
    activeHighlightType: string | null;
}>();

defineEmits<{
    (e: 'highlight', mod: TacticalModifierData): void;
    (e: 'apply-template', mod: TacticalModifierData, templateId: string): void;
}>();

const selectedCategory = ref<string>('ALL');
const selectedTmpl = reactive<Record<string, string>>({});

const filteredModifiers = computed(() => {
    if (selectedCategory.value === 'ALL') return props.modifiers;
    return props.modifiers.filter(m => m.category === selectedCategory.value);
});
</script>

<style scoped>
.panel-card { background: rgba(15, 23, 42, 0.85); border: 1px solid #334155; border-radius: 4px; overflow: hidden; }
.panel-header { background: rgba(255, 255, 255, 0.04); padding: 10px 14px; border-bottom: 1px solid rgba(255, 255, 255, 0.05); }
.panel-title { font-size: 11px; font-weight: bold; color: #00a8ff; font-family: monospace; }
.panel-body { padding: 12px; display: flex; flex-direction: column; gap: 10px; font-family: monospace; }

.filter-pills-row { display: flex; flex-wrap: wrap; gap: 4px; }
.pill-btn {
    background: #1e293b; border: 1px solid #334155; color: #94a3b8;
    padding: 3px 6px; font-size: 9px; font-family: monospace; cursor: pointer; border-radius: 2px;
}
.pill-btn.active { background: #0284c7; border-color: #38bdf8; color: #fff; }

.modifiers-list { display: flex; flex-direction: column; gap: 8px; max-height: calc(100vh - 220px); overflow-y: auto; }
.mod-item-card {
    background: rgba(0, 0, 0, 0.25); border: 1px solid #334155; border-radius: 3px; padding: 8px; cursor: pointer;
}
.mod-item-card.highlighted { border-color: #f1c40f; background: rgba(241, 196, 15, 0.1); }
.card-top { display: flex; align-items: center; gap: 6px; margin-bottom: 2px; }
.cat-pill { font-size: 8px; padding: 1px 4px; border-radius: 2px; font-weight: bold; }
.cat-pill.road { background: #1e3a8a; color: #93c5fd; }
.cat-pill.vegetation { background: #064e3b; color: #6ee7b7; }
.cat-pill.building { background: #7f1d1d; color: #fca5a5; }
.cat-pill.water { background: #0c4a6e; color: #7dd3fc; }
.osm-tag { font-size: 10px; color: #fff; font-weight: bold; }
.card-desc { font-size: 9px; color: #64748b; margin-bottom: 6px; }

.assign-row { display: flex; gap: 4px; margin-bottom: 6px; }
.tmpl-select { flex: 1; background: #0b1120; border: 1px solid #334155; color: #fff; font-size: 9px; padding: 3px; border-radius: 2px; }
.btn-apply { background: #047857; border: 1px solid #10b981; color: #fff; padding: 2px 8px; font-size: 9px; cursor: pointer; border-radius: 2px; }
.btn-apply:disabled { opacity: 0.4; cursor: not-allowed; }

.ttx-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 4px; background: rgba(0, 0, 0, 0.2); padding: 4px 6px; border-radius: 2px; }
.ttx-cell { font-size: 9px; display: flex; justify-content: space-between; }
.ttx-cell span { color: #94a3b8; }
.ttx-cell strong { color: #00e676; }
</style>