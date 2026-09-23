<template>
    <div class="catalog-modal-overlay" role="dialog" aria-modal="true" @click.self="$emit('close')">
        <div class="catalog-modal-box">
            <!-- Header -->
            <div class="modal-top">
                <div class="title-wrap">
                    <h4>ГЛОБАЛЬНИЙ ДОВІДНИК ТТХ ОБ'ЄКТІВ ТА ПОВЕРХОНЬ</h4>
                    <span class="sub-title">Еталонні військові шаблони прохідності, видимості та захисту</span>
                </div>
                <div class="head-actions">
                    <button type="button" class="btn-new-template" @click="openCreateForm">+ СТВОРИТИ ШАБЛОН</button>
                    <button type="button" class="btn-close" aria-label="Close" @click="$emit('close')">✕</button>
                </div>
            </div>

            <!-- Create / Edit Drawer Form -->
            <div v-if="isEditing" class="template-form-panel">
                <div class="form-title">
                    {{ editingId ? 'РЕДАГУВАТИ ШАБЛОН' : 'НОВИЙ ШАБЛОН ТТХ' }}
                </div>
                <div class="form-grid">
                    <div>
                        <label>Категорія:</label>
                        <select v-model="form.category" class="input-field">
                            <option value="ROAD">ROAD (Шляхи сполучення)</option>
                            <option value="VEGETATION">VEGETATION (Рослинний покрив)</option>
                            <option value="BUILDING">BUILDING (Споруди / Забудова)</option>
                            <option value="WATER">WATER (Водні перешкоди)</option>
                            <option value="SOIL">SOIL (Ґрунти / Поверхні)</option>
                        </select>
                    </div>
                    <div>
                        <label>Назва / Опис пресету:</label>
                        <input v-model="form.description" type="text" placeholder="наприклад, Ліс сосновий (Полісся)" class="input-field" />
                    </div>
                    <div>
                        <label>OSM Key (Ключ):</label>
                        <input v-model="form.osmKey" type="text" placeholder="наприклад: natural, highway" class="input-field" />
                    </div>
                    <div>
                        <label>OSM Value (Значення):</label>
                        <input v-model="form.osmValue" type="text" placeholder="наприклад: wood, primary, orchard" class="input-field" />
                    </div>
                    <div>
                        <label>Швидкість колісних (0.0 - 1.5):</label>
                        <input v-model.number="form.speedModifierWheeled" type="number" step="0.05" class="input-field" />
                    </div>
                    <div>
                        <label>Швидкість гусеничних (0.0 - 1.5):</label>
                        <input v-model.number="form.speedModifierTracked" type="number" step="0.05" class="input-field" />
                    </div>
                    <div>
                        <label>Видимість усередині (м):</label>
                        <input v-model.number="form.visibilityMeters" type="number" step="10" placeholder="Без меж (порожньо)" class="input-field" />
                    </div>
                    <div>
                        <label>Захист від куль/уламків (%):</label>
                        <input v-model.number="form.coverDefensePercent" type="number" min="0" max="95" class="input-field" />
                    </div>
                </div>
                <div class="form-actions">
                    <button type="button" class="btn-cancel" @click="isEditing = false">Скасувати</button>
                    <button type="button" class="btn-save" :disabled="!form.osmKey || !form.osmValue" @click="saveForm">
                        Зберегти шаблон
                    </button>
                </div>
            </div>

            <!-- Table of Templates -->
            <div class="table-wrap">
                <div v-if="loading" class="empty-msg">Завантаження шаблонів...</div>
                <div v-else-if="templates.length === 0 && !isEditing" class="empty-msg">
                    Довідник порожній. Натисніть «+ Створити шаблон», щоб додати перші військові стандарти місцевості.
                </div>
                <table v-else class="catalog-table">
                    <thead>
                    <tr>
                        <th>КАТЕГОРІЯ</th>
                        <th>OSM ТЕГ</th>
                        <th>ОПИС / ПРИЗНАЧЕННЯ</th>
                        <th>КОЛІСНІ</th>
                        <th>ГУСЕНИЧНІ</th>
                        <th>ВИДИМІСТЬ</th>
                        <th>ЗАХИСТ</th>
                        <th>ДІЇ</th>
                    </tr>
                    </thead>
                    <tbody>
                    <tr v-for="t in templates" :key="t.id">
                        <td><span class="cat-tag" :class="t.category.toLowerCase()">{{ t.category }}</span></td>
                        <td><code>{{ t.osmKey }}={{ t.osmValue }}</code></td>
                        <td><strong>{{ t.description || '—' }}</strong></td>
                        <td class="num-val">{{ (t.speedModifierWheeled * 100).toFixed(0) }}%</td>
                        <td class="num-val">{{ (t.speedModifierTracked * 100).toFixed(0) }}%</td>
                        <td class="num-val">{{ t.visibilityMeters ? t.visibilityMeters + ' м' : '∞' }}</td>
                        <td class="num-val">{{ t.coverDefensePercent }}%</td>
                        <td class="action-cell">
                            <button type="button" class="btn-edit" title="Редагувати" @click="startEdit(t)">✎</button>
                            <button type="button" class="btn-delete" title="Видалити" @click="deleteItem(t)">🗑</button>
                        </td>
                    </tr>
                    </tbody>
                </table>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { mapApi } from '../mapApi';
import type { DefaultModifierDto, ModifierCategory } from '../types';

defineEmits<{
    (e: 'close'): void;
}>();

const templates = ref<DefaultModifierDto[]>([]);
const loading = ref(false);
const isEditing = ref(false);
const editingId = ref<string | null>(null);

const form = reactive({
    category: 'VEGETATION' as ModifierCategory,
    osmKey: '',
    osmValue: '',
    description: '',
    speedModifierWheeled: 1.0,
    speedModifierTracked: 1.0,
    visibilityMeters: null as number | null,
    coverDefensePercent: 0
});

const loadTemplates = async () => {
    try {
        loading.value = true;
        templates.value = await mapApi.getTemplates();
    } catch (err) {
        console.error('Failed to load templates:', err);
    } finally {
        loading.value = false;
    }
};

const openCreateForm = () => {
    editingId.value = null;
    form.category = 'VEGETATION';
    form.osmKey = '';
    form.osmValue = '';
    form.description = '';
    form.speedModifierWheeled = 1.0;
    form.speedModifierTracked = 1.0;
    form.visibilityMeters = null;
    form.coverDefensePercent = 0;
    isEditing.value = true;
};

const startEdit = (item: DefaultModifierDto) => {
    editingId.value = item.id;
    form.category = item.category;
    form.osmKey = item.osmKey;
    form.osmValue = item.osmValue;
    form.description = item.description;
    form.speedModifierWheeled = item.speedModifierWheeled;
    form.speedModifierTracked = item.speedModifierTracked;
    form.visibilityMeters = item.visibilityMeters;
    form.coverDefensePercent = item.coverDefensePercent;
    isEditing.value = true;
};

const saveForm = async () => {
    try {
        if (editingId.value) {
            await mapApi.updateTemplate(editingId.value, { id: editingId.value, ...form });
        } else {
            await mapApi.createTemplate(form);
        }
        isEditing.value = false;
        await loadTemplates();
    } catch (err) {
        alert('Помилка збереження шаблону: ' + err);
    }
};

const deleteItem = async (item: DefaultModifierDto) => {
    if (confirm(`Видалити шаблон "${item.description || item.osmValue}"?`)) {
        try {
            await mapApi.deleteTemplate(item.id);
            await loadTemplates();
        } catch (err) {
            alert('Помилка видалення: ' + err);
        }
    }
};

onMounted(loadTemplates);
</script>

<style scoped>
.catalog-modal-overlay {
    position: fixed;
    top: 0; left: 0; width: 100vw; height: 100vh;
    background: rgba(0, 0, 0, 0.85);
    backdrop-filter: blur(5px);
    display: flex; align-items: center; justify-content: center;
    z-index: 10000;
}
.catalog-modal-box {
    background: #0b1120;
    border: 1px solid #00a8ff;
    width: 950px;
    max-height: 85vh;
    display: flex;
    flex-direction: column;
    border-radius: 6px;
    box-shadow: 0 8px 32px rgba(0, 0, 0, 0.9);
    overflow: hidden;
}
.modal-top {
    display: flex;
    justify-content: space-between;
    align-items: center;
    border-bottom: 1px solid #1e293b;
    padding: 14px 20px;
    background: #0f172a;
}
.title-wrap h4 { margin: 0; color: #00a8ff; font-size: 13px; font-family: monospace; letter-spacing: 1px; }
.sub-title { font-size: 10px; color: #64748b; font-family: monospace; }
.head-actions { display: flex; align-items: center; gap: 12px; }
.btn-new-template {
    background: #0284c7;
    border: 1px solid #38bdf8;
    color: #fff;
    padding: 6px 12px;
    border-radius: 3px;
    font-size: 11px;
    font-family: monospace;
    font-weight: bold;
    cursor: pointer;
}
.btn-new-template:hover { background: #0369a1; }
.btn-close { background: transparent; border: none; color: #94a3b8; font-size: 18px; cursor: pointer; }

.template-form-panel {
    background: rgba(15, 23, 42, 0.95);
    border-bottom: 1px solid #334155;
    padding: 14px 20px;
}
.form-title { font-size: 11px; color: #f1c40f; font-family: monospace; font-weight: bold; margin-bottom: 8px; }
.form-grid {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 8px;
}
.form-grid label { font-size: 9px; color: #94a3b8; font-family: monospace; display: block; margin-bottom: 2px; }
.input-field {
    width: 100%;
    box-sizing: border-box;
    background: #020617;
    border: 1px solid #334155;
    color: #fff;
    padding: 5px;
    font-size: 11px;
    font-family: monospace;
    border-radius: 2px;
}
.form-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 10px; }
.btn-cancel { background: transparent; border: 1px solid #475569; color: #94a3b8; padding: 4px 10px; font-size: 10px; cursor: pointer; border-radius: 2px; }
.btn-save { background: #047857; border: 1px solid #10b981; color: #fff; padding: 4px 14px; font-size: 10px; font-weight: bold; cursor: pointer; border-radius: 2px; }
.btn-save:disabled { opacity: 0.5; cursor: not-allowed; }

.table-wrap { flex: 1; overflow-y: auto; padding: 12px 20px; }
.empty-msg { text-align: center; color: #64748b; font-size: 12px; font-family: monospace; padding: 40px 0; }
.catalog-table { width: 100%; border-collapse: collapse; font-family: monospace; font-size: 11px; }
.catalog-table th {
    text-align: left;
    padding: 8px;
    background: #0f172a;
    color: #00a8ff;
    border-bottom: 1px solid #334155;
    font-size: 10px;
}
.catalog-table td { padding: 8px; border-bottom: 1px solid #1e293b; color: #e2e8f0; }
.cat-tag { font-size: 8px; padding: 2px 4px; border-radius: 2px; font-weight: bold; }
.cat-tag.road { background: #1e3a8a; color: #93c5fd; }
.cat-tag.vegetation { background: #064e3b; color: #6ee7b7; }
.cat-tag.building { background: #7f1d1d; color: #fca5a5; }
.cat-tag.water { background: #0c4a6e; color: #7dd3fc; }
.cat-tag.soil { background: #78350f; color: #fde68a; }

.num-val { color: #00e676; }
.action-cell { display: flex; gap: 6px; }
.btn-edit { background: #1e293b; border: 1px solid #334155; color: #38bdf8; padding: 2px 6px; cursor: pointer; border-radius: 2px; }
.btn-delete { background: #1e293b; border: 1px solid #334155; color: #ef4444; padding: 2px 6px; cursor: pointer; border-radius: 2px; }
</style>