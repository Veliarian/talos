<template>
    <div class="db-studio-container">
        <!-- Header -->
        <header class="db-header">
            <div class="header-left">
                <button type="button" class="btn-back" @click="$emit('back')">← НАЗАД ДО КАРТ</button>
                <h2>ГОЛОВНА БАЗА ДАНИХ ОБ'ЄКТІВ ТА ШАБЛОНІВ ТТХ</h2>
            </div>
            <button type="button" class="btn-new" @click="startCreate">+ СТВОРИТИ НОВИЙ ТИП ОБ'ЄКТА</button>
        </header>

        <div class="db-workspace">
            <!-- Left List: Layer Priority Stack (Ordered from Top/Highest to Bottom) -->
            <aside class="catalog-list-sidebar">
                <div class="stack-header-hint">
                    <span>▲ НАЙВИЩИЙ ПРІОРИТЕТ (ПЕРЕКРИВАЄ ВСЕ ЗНИЗУ)</span>
                </div>

                <div class="category-filters">
                    <button
                        v-for="cat in (['ALL', 'ROAD', 'BRIDGE', 'RIVER', 'OPEN_WATER', 'VEGETATION', 'BUILDING', 'SOIL'] as const)"
                        :key="cat"
                        type="button"
                        class="cat-filter-btn"
                        :class="{ active: selectedCategory === cat }"
                        @click="selectedCategory = cat"
                    >
                        {{ cat }}
                    </button>
                </div>

                <div class="entries-list">
                    <div
                        v-for="(item, idx) in filteredList"
                        :key="item.id"
                        class="entry-card"
                        :class="{ active: activeItem?.id === item.id }"
                        @click="selectItem(item)"
                    >
                        <div class="card-reorder-actions" @click.stop>
                            <button
                                type="button"
                                class="btn-arrow"
                                :disabled="idx === 0"
                                title="Підняти вище (вищий пріоритет)"
                                @click="movePriority(idx, -1)"
                            >
                                ▲
                            </button>
                            <button
                                type="button"
                                class="btn-arrow"
                                :disabled="idx === filteredList.length - 1"
                                title="Опустити нижче (нижчий пріоритет)"
                                @click="movePriority(idx, 1)"
                            >
                                ▼
                            </button>
                        </div>

                        <div class="card-content">
                            <div class="card-head">
                                <span class="cat-pill" :class="item.category.toLowerCase()">{{ item.category }}</span>
                                <span class="color-dot" :style="{ backgroundColor: item.color2d }"></span>
                                <strong class="tag-title">{{ item.osmKey }}={{ item.osmValue }}</strong>
                            </div>
                            <div class="card-desc">{{ item.description || 'Без опису' }}</div>
                            <div class="card-meta">
                                <span>Ранг пріоритету: <strong>{{ item.movementPriority }}</strong></span>
                                <span>Текстура: <strong>{{ item.texture3d }}</strong></span>
                            </div>
                        </div>
                    </div>
                </div>

                <div class="stack-footer-hint">
                    <span>▼ НАЙНИЖЧИЙ ПРІОРИТЕТ (ФОНОВИЙ ШАР)</span>
                </div>
            </aside>

            <!-- Right Detail Form Area -->
            <main class="editor-main">
                <div v-if="!formState.isActive" class="empty-state">
                    Оберіть об'єкт зі стеку зліва для редагування або натисніть «+ Створити новий тип об'єкта»
                </div>

                <div v-else class="form-container">
                    <div class="form-header">
                        <h3>{{ formState.isNew ? 'СТВОРЕННЯ НОВОГО ТИПУ ОБ\'ЄКТА' : 'РЕДАГУВАННЯ ТИПУ ОБ\'ЄКТА' }}</h3>
                        <div class="header-btns">
                            <button v-if="!formState.isNew" type="button" class="btn-del" @click="deleteActive">🗑 Видалити</button>
                            <button type="button" class="btn-save" @click="saveActive">💾 Зберегти в базу</button>
                        </div>
                    </div>

                    <div class="form-sections-grid">
                        <!-- 1. Identity & OSM Tag Matching -->
                        <div class="section-card">
                            <span class="sec-title">1. ІДЕНТИФІКАЦІЯ ТА ВІЙСЬКОВА КАТЕГОРІЯ</span>
                            <div class="grid-2">
                                <div>
                                    <label>Категорія ТВД:</label>
                                    <select v-model="form.category" class="field-input" @change="initCategoryDefaults">
                                        <option value="ROAD">ROAD (Шляхи сполучення)</option>
                                        <option value="BRIDGE">BRIDGE (Мости, переправи, дамби)</option>
                                        <option value="RIVER">RIVER (Річки, струмки, канали з течією)</option>
                                        <option value="OPEN_WATER">OPEN_WATER (Озера, ставки, моря)</option>
                                        <option value="VEGETATION">VEGETATION (Рослинність та угіддя)</option>
                                        <option value="BUILDING">BUILDING (Споруди та забудова)</option>
                                        <option value="SOIL">SOIL (Базовий ґрунт)</option>
                                    </select>
                                </div>
                                <div>
                                    <label>Назва пресету / Призначення:</label>
                                    <input v-model="form.description" type="text" placeholder="наприклад, Траса державного значення" class="field-input" />
                                </div>
                                <div>
                                    <label>OSM Key (Ключ тегу):</label>
                                    <input v-model="form.osmKey" type="text" placeholder="highway, natural, waterway..." class="field-input" />
                                </div>
                                <div>
                                    <label>OSM Value (Значення тегу):</label>
                                    <input v-model="form.osmValue" type="text" placeholder="primary, bridge, stream, wood..." class="field-input" />
                                </div>
                            </div>
                        </div>

                        <!-- 2. Visual Styling & Relative Priority -->
                        <div class="section-card">
                            <span class="sec-title">2. ВІЗУАЛЬНИЙ СТИЛЬ ТА ПРІОРИТЕТ ПЕРЕКРИТТЯ</span>
                            <div class="grid-3">
                                <div>
                                    <label>2D Колір (Штабний вигляд):</label>
                                    <div class="color-picker-row">
                                        <input v-model="form.color2d" type="color" class="color-box" />
                                        <input v-model="form.color2d" type="text" class="field-input font-mono" />
                                    </div>
                                </div>
                                <div>
                                    <label>3D Текстура / Стиль матеріалу:</label>
                                    <input v-model="form.texture3d" type="text" placeholder="asphalt, concrete, water, pine..." class="field-input" />
                                </div>
                                <div>
                                    <label>Числовий пріоритет (вищий = поверх інших):</label>
                                    <input v-model.number="form.movementPriority" type="number" step="5" class="field-input font-mono" />
                                </div>
                            </div>
                            <span class="hint-text">
                                Підказка: Ви можете змінювати порядок прямо стрілками ▲ / ▼ у списку ліворуч!
                            </span>
                        </div>

                        <!-- 3. Specialized Category-Specific Fields (Pre-designed schemas) -->
                        <div class="section-card highlight-card">
                            <span class="sec-title text-cyan">3. СПЕЦІАЛІЗОВАНІ ХАРАКТЕРИСТИКИ ДЛЯ: {{ form.category }}</span>

                            <!-- A. ROAD -->
                            <div v-if="form.category === 'ROAD'" class="grid-3">
                                <div>
                                    <label>Тип покриття:</label>
                                    <input v-model="roadProps.surfaceType" list="road-surfaces" class="field-input" />
                                    <datalist id="road-surfaces">
                                        <option value="Асфальт капітальний" />
                                        <option value="Бруківка" />
                                        <option value="Грейдер" />
                                        <option value="Ґрунт польовий" />
                                        <option value="Щебінь" />
                                        <option value="Бетонні плити" />
                                        <option value="Гать із колод" />
                                    </datalist>
                                </div>
                                <div>
                                    <label>Ширина полотна (м):</label>
                                    <input v-model.number="roadProps.widthMeters" type="number" step="0.5" class="field-input" />
                                </div>
                                <div>
                                    <label>Кількість смуг:</label>
                                    <input v-model.number="roadProps.lanesCount" type="number" min="1" max="8" class="field-input" />
                                </div>
                                <div>
                                    <label>Односторонній рух:</label>
                                    <select v-model="roadProps.isOneWay" class="field-input">
                                        <option :value="false">Двосторонній</option>
                                        <option :value="true">Односторонній</option>
                                    </select>
                                </div>
                                <div>
                                    <label>Лише для піхоти (стежка):</label>
                                    <select v-model="roadProps.infantryOnly" class="field-input">
                                        <option :value="false">Ні (техніка проходить)</option>
                                        <option :value="true">Так (техніка впреться)</option>
                                    </select>
                                </div>
                                <div>
                                    <label>Захист у кюветі/насипу (%):</label>
                                    <input v-model.number="roadProps.coverDefensePercent" type="number" min="0" max="50" class="field-input" />
                                </div>
                            </div>

                            <!-- B. BRIDGE -->
                            <div v-else-if="form.category === 'BRIDGE'" class="grid-3">
                                <div>
                                    <label>Тип переправи / моста:</label>
                                    <input v-model="bridgeProps.bridgeType" list="bridge-types" class="field-input" />
                                    <datalist id="bridge-types">
                                        <option value="Залізобетонний капітальний" />
                                        <option value="Сталевий балковий/фермовий" />
                                        <option value="Дерев'яний балковий" />
                                        <option value="Понтонна переправа" />
                                        <option value="Насипна дамба з трубою" />
                                    </datalist>
                                </div>
                                <div>
                                    <label>Вантажопідйомність (тонни):</label>
                                    <input v-model.number="bridgeProps.maxWeightTons" type="number" step="5" class="field-input text-amber" />
                                </div>
                                <div>
                                    <label>Ширина моста (м):</label>
                                    <input v-model.number="bridgeProps.widthMeters" type="number" step="0.5" class="field-input" />
                                </div>
                                <div>
                                    <label>Довжина моста (м):</label>
                                    <input v-model.number="bridgeProps.lengthMeters" type="number" step="5" class="field-input" />
                                </div>
                                <div>
                                    <label>Кількість смуг:</label>
                                    <input v-model.number="bridgeProps.lanesCount" type="number" min="1" max="6" class="field-input" />
                                </div>
                                <div>
                                    <label>Стан переправи:</label>
                                    <input v-model="bridgeProps.destructionState" list="destr-states" class="field-input" />
                                    <datalist id="destr-states">
                                        <option value="Цілий" />
                                        <option value="Замінований" />
                                        <option value="Пошкоджений" />
                                        <option value="Зруйнований" />
                                    </datalist>
                                </div>
                            </div>

                            <!-- C. RIVER -->
                            <div v-else-if="form.category === 'RIVER'" class="grid-3">
                                <div>
                                    <label>Тип русла:</label>
                                    <input v-model="riverProps.waterwayType" list="waterway-types" class="field-input" />
                                    <datalist id="waterway-types">
                                        <option value="Судноплавна річка" />
                                        <option value="Гірська річка" />
                                        <option value="Струмок / потік" />
                                        <option value="Меліоративний канал" />
                                    </datalist>
                                </div>
                                <div>
                                    <label>Глибина (м):</label>
                                    <input v-model.number="riverProps.depthMeters" type="number" step="0.1" class="field-input text-cyan" />
                                </div>
                                <div>
                                    <label>Ширина водної перешкоди (м):</label>
                                    <input v-model.number="riverProps.widthMeters" type="number" step="1" class="field-input" />
                                </div>
                                <div>
                                    <label>Швидкість течії (м/с):</label>
                                    <input v-model.number="riverProps.flowSpeedMps" type="number" step="0.1" class="field-input" />
                                </div>
                                <div>
                                    <label>Напрямок течії (азимут 0-360°):</label>
                                    <input v-model.number="riverProps.flowDirectionDegrees" type="number" min="0" max="360" class="field-input" />
                                </div>
                                <div>
                                    <label>Ґрунт дна:</label>
                                    <input v-model="riverProps.bottomType" list="bottom-types" class="field-input" />
                                    <datalist id="bottom-types">
                                        <option value="Тверде кам'янисте" />
                                        <option value="Піщане щільне" />
                                        <option value="В'язкий намул" />
                                        <option value="Торф'яне" />
                                    </datalist>
                                </div>
                                <div>
                                    <label>Наявність броду:</label>
                                    <select v-model="riverProps.isFordable" class="field-input">
                                        <option :value="false">Ні (лише плавом/мостом)</option>
                                        <option :value="true">Так (техніка долає по дну)</option>
                                    </select>
                                </div>
                                <div>
                                    <label>Макс. глибина броду (м):</label>
                                    <input v-model.number="riverProps.maxFordDepthMeters" type="number" step="0.1" class="field-input" />
                                </div>
                            </div>

                            <!-- D. OPEN_WATER -->
                            <div v-else-if="form.category === 'OPEN_WATER'" class="grid-3">
                                <div>
                                    <label>Тип водойми:</label>
                                    <input v-model="openWaterProps.waterBodyType" list="waterbody-types" class="field-input" />
                                    <datalist id="waterbody-types">
                                        <option value="Озеро природне" />
                                        <option value="Ставок рибний" />
                                        <option value="Водосховище глибоководне" />
                                        <option value="Лиман / затока" />
                                        <option value="Морська акваторія" />
                                    </datalist>
                                </div>
                                <div>
                                    <label>Глибина (м):</label>
                                    <input v-model.number="openWaterProps.depthMeters" type="number" step="0.5" class="field-input text-cyan" />
                                </div>
                                <div>
                                    <label>Смуга прибережного очерету (м):</label>
                                    <input v-model.number="openWaterProps.reedBeltWidthMeters" type="number" step="5" class="field-input" />
                                </div>
                                <div>
                                    <label>Льодовий стан:</label>
                                    <input v-model="openWaterProps.iceCoverState" list="ice-states" class="field-input" />
                                    <datalist id="ice-states">
                                        <option value="Без криги" />
                                        <option value="Тонкий лід (не тримає)" />
                                        <option value="Товстий лід (тримає техніку)" />
                                    </datalist>
                                </div>
                            </div>

                            <!-- E. VEGETATION -->
                            <div v-else-if="form.category === 'VEGETATION'" class="grid-3">
                                <div>
                                    <label>Тип рослинності:</label>
                                    <input v-model="vegetationProps.vegetationType" list="veg-types" class="field-input" />
                                    <datalist id="veg-types">
                                        <option value="Хвойний бір" />
                                        <option value="Листяна діброва" />
                                        <option value="Чагарник / підлісок" />
                                        <option value="Фруктовий сад" />
                                        <option value="Виноградник" />
                                        <option value="Очерет високий" />
                                        <option value="Луг / галявина" />
                                        <option value="Рілля зорана" />
                                    </datalist>
                                </div>
                                <div>
                                    <label>Висота крони (м):</label>
                                    <input v-model.number="vegetationProps.heightMeters" type="number" step="0.5" class="field-input text-green" />
                                </div>
                                <div>
                                    <label>Товщина стовбурів (см):</label>
                                    <input v-model.number="vegetationProps.stemDiameterCm" type="number" step="5" class="field-input" />
                                </div>
                                <div>
                                    <label>Щільність крон (%):</label>
                                    <input v-model.number="vegetationProps.densityPercent" type="number" min="0" max="100" class="field-input" />
                                </div>
                                <div>
                                    <label>Видимість усередині (м):</label>
                                    <input v-model.number="vegetationProps.visibilityMeters" type="number" step="5" placeholder="∞" class="field-input" />
                                </div>
                                <div>
                                    <label>Захист від уламків (%):</label>
                                    <input v-model.number="vegetationProps.coverDefensePercent" type="number" min="0" max="95" class="field-input" />
                                </div>
                            </div>

                            <!-- F. BUILDING -->
                            <div v-else-if="form.category === 'BUILDING'" class="grid-3">
                                <div>
                                    <label>Тип споруди:</label>
                                    <input v-model="buildingProps.buildingType" list="bld-types" class="field-input" />
                                    <datalist id="bld-types">
                                        <option value="Житловий приватний" />
                                        <option value="Багатоповерхівка" />
                                        <option value="Промцех / ангар" />
                                        <option value="Склад логістичний" />
                                        <option value="Капітальний ДОТ / бункер" />
                                        <option value="Адмінбудівля" />
                                    </datalist>
                                </div>
                                <div>
                                    <label>Матеріал конструкції:</label>
                                    <input v-model="buildingProps.structureMaterial" list="bld-materials" class="field-input" />
                                    <datalist id="bld-materials">
                                        <option value="Монолітний залізобетон" />
                                        <option value="Цегла повнотіла" />
                                        <option value="Шлакоблок" />
                                        <option value="Металоконструкція" />
                                        <option value="Дерев'яний брус" />
                                    </datalist>
                                </div>
                                <div>
                                    <label>Кількість поверхів:</label>
                                    <input v-model.number="buildingProps.buildingLevels" type="number" min="1" max="30" class="field-input" />
                                </div>
                                <div>
                                    <label>Висота споруди (м):</label>
                                    <input v-model.number="buildingProps.heightMeters" type="number" step="0.5" class="field-input text-amber" />
                                </div>
                                <div>
                                    <label>Захист укриття (%):</label>
                                    <input v-model.number="buildingProps.coverDefensePercent" type="number" min="0" max="95" class="field-input" />
                                </div>
                                <div>
                                    <label>Можливість гарнізону піхоти:</label>
                                    <select v-model="buildingProps.canEnterUnits" class="field-input">
                                        <option :value="true">Так (оборона у вікнах)</option>
                                        <option :value="false">Ні</option>
                                    </select>
                                </div>
                            </div>

                            <!-- G. SOIL -->
                            <div v-else-if="form.category === 'SOIL'" class="grid-3">
                                <div>
                                    <label>Тип ґрунту:</label>
                                    <input v-model="soilProps.soilType" list="soil-types" class="field-input" />
                                    <datalist id="soil-types">
                                        <option value="Чорнозем щільний" />
                                        <option value="Суглинок" />
                                        <option value="Пісок сипучий" />
                                        <option value="Кам'янистий ґрунт" />
                                        <option value="Солончак" />
                                        <option value="Багнюка / розпутиця" />
                                    </datalist>
                                </div>
                                <div>
                                    <label>Несна здатність:</label>
                                    <input v-model="soilProps.bearingCapacity" list="bearing-types" class="field-input" />
                                    <datalist id="bearing-types">
                                        <option value="Твердий" />
                                        <option value="Середній" />
                                        <option value="Слабкий (ризик посадки)" />
                                    </datalist>
                                </div>
                                <div>
                                    <label>Пилоутворення під час руху:</label>
                                    <input v-model="soilProps.dustGeneration" list="dust-types" class="field-input" />
                                    <datalist id="dust-types">
                                        <option value="Високе (демаскує для БПЛА)" />
                                        <option value="Середнє" />
                                        <option value="Відсутнє" />
                                    </datalist>
                                </div>
                            </div>
                        </div>

                        <!-- 4. Universal Mobility Factors -->
                        <div class="section-card">
                            <span class="sec-title">4. КОЕФІЦІЄНТИ ШВИДКОСТІ ПЕРЕСУВАННЯ</span>
                            <div class="grid-3">
                                <div>
                                    <label>Швидкість колісних (0.0 - 1.5):</label>
                                    <input v-model.number="form.speedModifierWheeled" type="number" step="0.05" class="field-input text-green" />
                                </div>
                                <div>
                                    <label>Швидкість гусеничних (0.0 - 1.5):</label>
                                    <input v-model.number="form.speedModifierTracked" type="number" step="0.05" class="field-input text-green" />
                                </div>
                                <div>
                                    <label>Швидкість плаваючої техніки:</label>
                                    <input v-model.number="riverProps.speedModifierAmphibious" type="number" step="0.05" class="field-input text-cyan" />
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </main>
        </div>
    </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue';
import { mapApi } from '../mapApi';
import type {
    SurfaceTemplateResponse,
    ModifierCategory,
    RoadProps,
    BridgeProps,
    RiverProps,
    OpenWaterProps,
    VegetationProps,
    BuildingProps,
    SoilProps
} from '../types';

defineEmits<{ (e: 'back'): void }>();

const templates = ref<SurfaceTemplateResponse[]>([]);
const selectedCategory = ref<string>('ALL');
const activeItem = ref<SurfaceTemplateResponse | null>(null);

const formState = reactive({
    isActive: false,
    isNew: false
});

const form = reactive({
    category: 'ROAD' as ModifierCategory,
    osmKey: '',
    osmValue: '',
    description: '',
    movementPriority: 50,
    color2d: '#f59e0b',
    texture3d: 'asphalt',
    speedModifierWheeled: 1.0,
    speedModifierTracked: 1.0,
    visibilityMeters: null as number | null,
    coverDefensePercent: 0
});

// Category Property Model States
const roadProps = reactive<RoadProps>({
    surfaceType: 'Асфальт капітальний', widthMeters: 8, lanesCount: 2, isOneWay: false, infantryOnly: false, coverDefensePercent: 10
});
const bridgeProps = reactive<BridgeProps>({
    bridgeType: 'Залізобетонний капітальний', maxWeightTons: 60, widthMeters: 9, lengthMeters: 50, lanesCount: 2, coverDefensePercent: 25, destructionState: 'Цілий'
});
const riverProps = reactive<RiverProps>({
    waterwayType: 'Судноплавна річка', depthMeters: 2.5, widthMeters: 30, flowSpeedMps: 1.0, flowDirectionDegrees: 180, bottomType: 'Тверде кам\'янисте', isFordable: false, maxFordDepthMeters: 0.8, speedModifierAmphibious: 0.6
});
const openWaterProps = reactive<OpenWaterProps>({
    waterBodyType: 'Озеро природне', depthMeters: 4.0, bottomType: 'Намул', reedBeltWidthMeters: 10, speedModifierAmphibious: 0.6, iceCoverState: 'Без криги'
});
const vegetationProps = reactive<VegetationProps>({
    vegetationType: 'Хвойний бір', heightMeters: 18, stemDiameterCm: 30, densityPercent: 70, visibilityMeters: 35, coverDefensePercent: 60, speedModifierInfantry: 0.5
});
const buildingProps = reactive<BuildingProps>({
    buildingType: 'Житловий приватний', structureMaterial: 'Цегла повнотіла', buildingLevels: 2, heightMeters: 6.5, coverDefensePercent: 75, canEnterUnits: true, roofType: 'Двосхилий'
});
const soilProps = reactive<SoilProps>({
    soilType: 'Чорнозем щільний', bearingCapacity: 'Твердий', dustGeneration: 'Середнє', coverDefensePercent: 5
});

const loadData = async () => {
    try {
        templates.value = await mapApi.getTemplates();
    } catch (err) {
        console.error('Failed to load templates:', err);
    }
};

onMounted(loadData);

const filteredList = computed(() => {
    if (selectedCategory.value === 'ALL') return templates.value;
    return templates.value.filter(t => t.category === selectedCategory.value);
});

const movePriority = async (index: number, direction: number) => {
    const list = [...filteredList.value];
    const targetIdx = index + direction;
    if (targetIdx < 0 || targetIdx >= list.length) return;

    // Swap positions in list
    const temp = list[index];
    list[index] = list[targetIdx];
    list[targetIdx] = temp;

    // Update priorities
    const orderedIds = list.map(item => item.id);
    try {
        await mapApi.reorderTemplates(orderedIds);
        await loadData();
    } catch (err) {
        alert('Помилка оновлення пріоритетів: ' + err);
    }
};

const selectItem = (item: SurfaceTemplateResponse) => {
    activeItem.value = item;
    formState.isActive = true;
    formState.isNew = false;

    form.category = item.category;
    form.osmKey = item.osmKey;
    form.osmValue = item.osmValue;
    form.description = item.description || '';
    form.movementPriority = item.movementPriority;
    form.color2d = item.color2d || '#f59e0b';
    form.texture3d = item.texture3d || 'default';
    form.speedModifierWheeled = item.speedModifierWheeled;
    form.speedModifierTracked = item.speedModifierTracked;
    form.visibilityMeters = item.visibilityMeters;
    form.coverDefensePercent = item.coverDefensePercent;

    if (item.propertiesJson) {
        try {
            const parsed = JSON.parse(item.propertiesJson);
            if (form.category === 'ROAD') Object.assign(roadProps, parsed);
            else if (form.category === 'BRIDGE') Object.assign(bridgeProps, parsed);
            else if (form.category === 'RIVER') Object.assign(riverProps, parsed);
            else if (form.category === 'OPEN_WATER') Object.assign(openWaterProps, parsed);
            else if (form.category === 'VEGETATION') Object.assign(vegetationProps, parsed);
            else if (form.category === 'BUILDING') Object.assign(buildingProps, parsed);
            else if (form.category === 'SOIL') Object.assign(soilProps, parsed);
        } catch (e) {
            console.warn('Could not parse propertiesJson:', e);
        }
    }
};

const startCreate = () => {
    activeItem.value = null;
    formState.isActive = true;
    formState.isNew = true;

    form.category = 'ROAD';
    form.osmKey = '';
    form.osmValue = '';
    form.description = '';
    form.movementPriority = 50;
    form.color2d = '#f59e0b';
    form.texture3d = 'default';
    form.speedModifierWheeled = 1.0;
    form.speedModifierTracked = 1.0;
    form.visibilityMeters = null;
    form.coverDefensePercent = 0;
};

const initCategoryDefaults = () => {
    if (form.category === 'ROAD') { form.color2d = '#f59e0b'; form.movementPriority = 100; }
    else if (form.category === 'BRIDGE') { form.color2d = '#ffffff'; form.movementPriority = 120; }
    else if (form.category === 'RIVER') { form.color2d = '#0284c7'; form.movementPriority = 85; }
    else if (form.category === 'OPEN_WATER') { form.color2d = '#0369a1'; form.movementPriority = 75; }
    else if (form.category === 'VEGETATION') { form.color2d = '#15803d'; form.movementPriority = 40; }
    else if (form.category === 'BUILDING') { form.color2d = '#ef4444'; form.movementPriority = 70; }
    else if (form.category === 'SOIL') { form.color2d = '#334155'; form.movementPriority = 10; }
};

const saveActive = async () => {
    if (!form.osmKey || !form.osmValue) {
        alert('Заповніть OSM Ключ та Значення!');
        return;
    }

    let propertiesObj: any = {};
    if (form.category === 'ROAD') propertiesObj = roadProps;
    else if (form.category === 'BRIDGE') propertiesObj = bridgeProps;
    else if (form.category === 'RIVER') propertiesObj = riverProps;
    else if (form.category === 'OPEN_WATER') propertiesObj = openWaterProps;
    else if (form.category === 'VEGETATION') propertiesObj = vegetationProps;
    else if (form.category === 'BUILDING') propertiesObj = buildingProps;
    else if (form.category === 'SOIL') propertiesObj = soilProps;

    const payload = {
        ...form,
        propertiesJson: JSON.stringify(propertiesObj)
    };

    try {
        if (formState.isNew) {
            const created = await mapApi.createTemplate(payload);
            await loadData();
            selectItem(created);
        } else if (activeItem.value) {
            const updated = await mapApi.updateTemplate(activeItem.value.id, payload);
            await loadData();
            selectItem(updated);
        }
        alert('Шаблон збережено в головну базу даних!');
    } catch (err) {
        alert('Помилка: ' + err);
    }
};

const deleteActive = async () => {
    if (!activeItem.value) return;
    if (confirm(`Видалити "${activeItem.value.description || activeItem.value.osmValue}" з головної бази?`)) {
        try {
            await mapApi.deleteTemplate(activeItem.value.id);
            formState.isActive = false;
            activeItem.value = null;
            await loadData();
        } catch (err) {
            alert('Помилка: ' + err);
        }
    }
};
</script>

<style scoped>
.db-studio-container { width: 100vw; height: 100vh; background: #020617; display: flex; flex-direction: column; overflow: hidden; font-family: monospace; }
.db-header {
    height: 52px; background: rgba(15, 23, 42, 0.98); border-bottom: 1px solid rgba(0, 168, 255, 0.3);
    padding: 0 20px; display: flex; justify-content: space-between; align-items: center; z-index: 100;
}
.header-left { display: flex; align-items: center; gap: 16px; }
.header-left h2 { margin: 0; font-size: 13px; color: #00a8ff; letter-spacing: 1px; }
.btn-back { background: transparent; border: 1px solid #475569; color: #cbd5e1; padding: 6px 12px; border-radius: 4px; cursor: pointer; }
.btn-new { background: #0284c7; border: 1px solid #38bdf8; color: #fff; padding: 6px 14px; font-weight: bold; border-radius: 4px; cursor: pointer; }

.db-workspace { display: flex; flex: 1; overflow: hidden; }

/* Left Priority Stack Sidebar */
.catalog-list-sidebar { width: 380px; background: rgba(15, 23, 42, 0.95); border-right: 1px solid #334155; display: flex; flex-direction: column; }
.stack-header-hint, .stack-footer-hint {
    background: #090d16; padding: 6px 10px; font-size: 8px; color: #00a8ff; letter-spacing: 0.5px; text-align: center; border-bottom: 1px solid #1e293b;
}
.stack-footer-hint { border-top: 1px solid #1e293b; border-bottom: none; color: #64748b; }

.category-filters { display: flex; flex-wrap: wrap; gap: 4px; padding: 10px; border-bottom: 1px solid #1e293b; }
.cat-filter-btn { background: #1e293b; border: 1px solid #334155; color: #94a3b8; font-size: 8px; padding: 3px 6px; cursor: pointer; border-radius: 2px; }
.cat-filter-btn.active { background: #0284c7; color: #fff; border-color: #38bdf8; }

.entries-list { flex: 1; overflow-y: auto; padding: 8px; display: flex; flex-direction: column; gap: 6px; }
.entry-card {
    background: rgba(0, 0, 0, 0.3); border: 1px solid #334155; padding: 6px 8px; border-radius: 4px; cursor: pointer;
    display: flex; gap: 8px; align-items: center;
}
.entry-card:hover { border-color: #00a8ff; }
.entry-card.active { border-color: #f1c40f; background: rgba(241, 196, 15, 0.1); }

.card-reorder-actions { display: flex; flex-direction: column; gap: 2px; }
.btn-arrow {
    background: #1e293b; border: 1px solid #475569; color: #cbd5e1; font-size: 8px;
    padding: 1px 4px; border-radius: 2px; cursor: pointer;
}
.btn-arrow:hover:not(:disabled) { background: #0284c7; color: #fff; }
.btn-arrow:disabled { opacity: 0.2; cursor: not-allowed; }

.card-content { flex: 1; min-width: 0; }
.card-head { display: flex; align-items: center; gap: 6px; margin-bottom: 2px; }
.cat-pill { font-size: 7px; padding: 1px 3px; border-radius: 2px; font-weight: bold; background: #0284c7; color: #fff; }
.color-dot { width: 8px; height: 8px; border-radius: 50%; }
.tag-title { font-size: 10px; color: #fff; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.card-desc { font-size: 9px; color: #64748b; margin-bottom: 3px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.card-meta { display: flex; justify-content: space-between; font-size: 8px; color: #94a3b8; }
.card-meta strong { color: #f8fafc; }

/* Right Editor Main */
.editor-main { flex: 1; overflow-y: auto; padding: 20px; background: #020617; }
.empty-state { text-align: center; color: #64748b; font-size: 12px; margin-top: 150px; }
.form-container { max-width: 900px; margin: 0 auto; display: flex; flex-direction: column; gap: 14px; }
.form-header { display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid #334155; padding-bottom: 10px; }
.form-header h3 { margin: 0; font-size: 13px; color: #00a8ff; }
.header-btns { display: flex; gap: 8px; }
.btn-del { background: transparent; border: 1px solid #ef4444; color: #ef4444; padding: 5px 12px; cursor: pointer; border-radius: 3px; }
.btn-save { background: #047857; border: 1px solid #10b981; color: #fff; padding: 5px 14px; font-weight: bold; cursor: pointer; border-radius: 3px; }

.form-sections-grid { display: flex; flex-direction: column; gap: 12px; }
.section-card { background: rgba(15, 23, 42, 0.85); border: 1px solid #334155; border-radius: 4px; padding: 14px; display: flex; flex-direction: column; gap: 10px; }
.section-card.highlight-card { border-color: rgba(0, 168, 255, 0.4); background: rgba(15, 23, 42, 0.95); }
.sec-title { font-size: 10px; font-weight: bold; color: #00a8ff; letter-spacing: 0.5px; }
.text-cyan { color: #38bdf8 !important; }
.text-amber { color: #f1c40f !important; }
.text-green { color: #00e676 !important; }

.grid-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.grid-3 { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 10px; }

label { font-size: 9px; color: #94a3b8; display: block; margin-bottom: 4px; }
.field-input { width: 100%; box-sizing: border-box; background: #0b1120; border: 1px solid #334155; color: #fff; padding: 6px; font-size: 11px; font-family: monospace; border-radius: 3px; }
.color-picker-row { display: flex; gap: 6px; align-items: center; }
.color-box { width: 32px; height: 28px; padding: 0; border: none; background: transparent; cursor: pointer; }
.hint-text { font-size: 9px; color: #64748b; }
</style>