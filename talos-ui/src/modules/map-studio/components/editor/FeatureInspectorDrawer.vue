<template>
    <div class="feature-drawer">
        <div class="drawer-header">
            <span class="cat-badge" :class="feature.category.toLowerCase()">{{ feature.category }}</span>
            <input v-model="feature.name" class="name-field" />
            <button type="button" class="btn-close" @click="$emit('close')">✕</button>
        </div>

        <div class="drawer-row">
            <label>СТАТУС ОБ'ЄКТА:</label>
            <select v-model="feature.status" class="status-select" :class="feature.status.toLowerCase()">
                <option value="OPERATIONAL">🟢 В нормі (Operational)</option>
                <option value="DESTROYED">⚫ Зруйновано (Destroyed)</option>
                <option value="MINED">🔴 Заміновано (Mined)</option>
                <option value="CHECKPOINT">🟠 Блокпост (Checkpoint)</option>
            </select>
        </div>

        <div class="drawer-row">
            <label>ШАБЛОН ТТХ:</label>
            <select class="status-select" @change="$emit('apply-template', ($event.target as HTMLSelectElement).value)">
                <option value="">-- Застосувати шаблон до цього об'єкта --</option>
                <option v-for="t in availableTemplates" :key="t.id" :value="t.id">
                    {{ t.description }} ({{ t.category }})
                </option>
            </select>
        </div>

        <!-- Overrides Grid -->
        <div class="overrides-grid">
            <div class="ov-field">
                <label>Шв. Колісні:</label>
                <input v-model.number="feature.speedOverrideWheeled" type="number" step="0.05" placeholder="1.0" class="num-box" />
            </div>
            <div class="ov-field">
                <label>Шв. Гусеничні:</label>
                <input v-model.number="feature.speedOverrideTracked" type="number" step="0.05" placeholder="1.0" class="num-box" />
            </div>
            <div class="ov-field">
                <label>Захист (%):</label>
                <input v-model.number="feature.coverOverride" type="number" step="5" placeholder="0" class="num-box" />
            </div>
            <div class="ov-field">
                <label>Видимість (м):</label>
                <input v-model.number="feature.visibilityOverride" type="number" step="10" placeholder="∞" class="num-box" />
            </div>
        </div>

        <div class="drawer-actions">
            <button type="button" class="btn-save" @click="$emit('save')">ЗБЕРЕГТИ ОБ'ЄКТ</button>
        </div>
    </div>
</template>

<script setup lang="ts">
import type { DefaultModifierDto } from '../../types';

defineProps<{
    feature: any;
    availableTemplates: DefaultModifierDto[];
}>();

defineEmits<{
    (e: 'close'): void;
    (e: 'save'): void;
    (e: 'apply-template', templateId: string): void;
}>();
</script>

<style scoped>
.feature-drawer {
    position: absolute; bottom: 44px; left: 16px;
    background: rgba(15, 23, 42, 0.96); border: 1px solid #f1c40f;
    padding: 12px; border-radius: 6px; width: 360px; font-family: monospace;
    z-index: 100; box-shadow: 0 8px 30px rgba(0, 0, 0, 0.85); display: flex; flex-direction: column; gap: 8px;
}
.drawer-header { display: flex; align-items: center; gap: 6px; }
.cat-badge { font-size: 8px; padding: 2px 4px; border-radius: 2px; font-weight: bold; background: #0284c7; color: #fff; }
.name-field { flex: 1; background: #0b1120; border: 1px solid #334155; color: #fff; padding: 4px; font-size: 11px; }
.btn-close { background: transparent; border: none; color: #94a3b8; cursor: pointer; }

.drawer-row { display: flex; flex-direction: column; gap: 3px; }
.drawer-row label { font-size: 9px; color: #94a3b8; }
.status-select { width: 100%; background: #0b1120; border: 1px solid #334155; color: #fff; padding: 4px; font-size: 10px; border-radius: 2px; }

.overrides-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 6px; }
.ov-field label { font-size: 8px; color: #94a3b8; display: block; margin-bottom: 2px; }
.num-box { width: 100%; box-sizing: border-box; background: #0b1120; border: 1px solid #334155; color: #fff; padding: 3px; font-size: 10px; border-radius: 2px; }

.drawer-actions { display: flex; justify-content: flex-end; margin-top: 4px; }
.btn-save { background: #0284c7; border: 1px solid #38bdf8; color: #fff; padding: 5px 12px; font-size: 10px; font-weight: bold; cursor: pointer; border-radius: 2px; }
</style>