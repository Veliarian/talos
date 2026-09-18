<template>
    <div class="simulator-container" @contextmenu.prevent>
        <!-- Контейнер карти -->
        <div id="cesiumContainer" ref="cesiumContainer"></div>

        <!-- Рамка виділення мишею (Box Select) -->
        <div
            v-if="selectionBox.active"
            class="selection-marquee"
            :style="{
        left: `${selectionBox.left}px`,
        top: `${selectionBox.top}px`,
        width: `${selectionBox.width}px`,
        height: `${selectionBox.height}px`
      }"
        ></div>

        <!-- Тактичний HUD -->
        <div class="tactical-hud">
            <div class="hud-header">
                <span class="pulse-indicator" :class="{ online: isConnected }"></span>
                <h3>TALOS C2 INTERFACE</h3>
            </div>

            <div class="hud-stats">
                <div>СТАТУС: <strong>{{ isConnected ? 'ОНЛАЙН' : 'ПОШУК...' }}</strong></div>
                <div>ВИДІЛЕНО: <strong class="highlight-text">{{ selectedUnitIds.size }} од.</strong></div>
                <div>
                    КОНТАКТИ OPFOR:
                    <strong :style="{ color: units.some(u => u.side === 'OPFOR') ? '#ff3838' : '#8fa3bf' }">
                        {{ units.filter(u => u.side === 'OPFOR').length }} виявлено
                    </strong>
                </div>
            </div>

            <!-- Панель дій над виділеними юнітами -->
            <div v-if="selectedUnitIds.size > 0" class="command-panel">
                <div class="panel-label">КОМАНДИ:</div>
                <div class="btn-group">
                    <button class="c2-btn danger" @click="sendCommand('ORDER_HALT')">СТОП</button>
                    <button class="c2-btn" @click="sendCommand('ORDER_CHANGE_SPEED', { speedKmh: 30 })">30 км/г</button>
                    <button class="c2-btn" @click="sendCommand('ORDER_CHANGE_SPEED', { speedKmh: 60 })">60 км/г</button>
                </div>
                <div class="hint-text">
                    ПКМ по карті — новий маршрут.<br/>
                    Shift + ПКМ — додати точку в чергу.<br/>
                    Shift + Drag ЛКМ — виділити групу рамкою.
                </div>
            </div>

            <!-- Список бойових машин -->
            <div class="unit-list">
                <div
                    v-for="u in units"
                    :key="u.id"
                    class="unit-card"
                    :class="{ selected: selectedUnitIds.has(u.id) }"
                    @click="toggleUnitSelection(u.id, $event)"
                >
                    <div class="unit-header">
                        <span class="unit-title">{{ u.callsign }}</span>
                        <span class="unit-status" :class="{ moving: u.currentSpeedKmh > 1 }">
              {{ u.currentSpeedKmh > 1 ? 'МАРШ' : 'СТОЇТЬ' }}
            </span>
                    </div>
                    <div class="unit-metrics">
                        <span>Швидкість: {{ u.currentSpeedKmh.toFixed(1) }} км/год</span>
                        <span>Точок у черзі: {{ u.waypoints ? u.waypoints.length : 0 }}</span>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref, reactive } from 'vue';
import {
    Viewer,
    Cartesian3,
    Color,
    Entity,
    Transforms,
    HeadingPitchRoll,
    Math as CesiumMath,
    ArcGisMapServerImageryProvider,
    ImageryLayer,
    HeightReference,
    ScreenSpaceEventHandler,
    ScreenSpaceEventType,
    defined,
    SceneTransforms,
    Cartographic,
    PolylineDashMaterialProperty,
    CallbackProperty,
    Terrain,
    Ion,
} from 'cesium';

interface WaypointDto {
    lat: number;
    lon: number;
}

interface UnitDto {
    id: string;
    callsign: string;
    side: string;
    lat: number;
    lon: number;
    altitude: number;
    heading: number;
    baseSpeedKmh: number;
    currentSpeedKmh: number;
    maxOpticsRangeMeters?: number;
    inCover?: boolean;
    visibleTargetIds?: string[];
    waypoints?: WaypointDto[];
}

const cesiumContainer = ref<HTMLDivElement | null>(null);
const isConnected = ref(false);
const units = ref<UnitDto[]>([]);
const selectedUnitIds = ref<Set<string>>(new Set());
const isShiftPressed = ref(false);
let justFinishedBoxSelect = false;

window.addEventListener('keydown', (e) => {
    if (e.key === 'Shift') {
        isShiftPressed.value = true;
        if (viewer) viewer.scene.screenSpaceCameraController.enableInputs = false;
    }
});
window.addEventListener('keyup', (e) => {
    if (e.key === 'Shift') {
        isShiftPressed.value = false;
        if (viewer && !selectionBox.active) {
            viewer.scene.screenSpaceCameraController.enableInputs = true;
        }
    }
});

// Прямокутна рамка виділення
const selectionBox = reactive({
    active: false,
    startX: 0,
    startY: 0,
    left: 0,
    top: 0,
    width: 0,
    height: 0
});

let viewer: Viewer | null = null;
let socket: WebSocket | null = null;
let handler: ScreenSpaceEventHandler | null = null;

const entityMap = new Map<string, Entity>();
let fovConeEntity: Entity | null = null; // Сектор огляду обраної машини
const losLineEntities = new Map<string, Entity>(); // Лінії прямої видимості цілей
const losCoordinatesMap = new Map<string, Cartesian3[]>();

const routeEntities = new Map<string, Entity>();
const routeCoordinatesMap = new Map<string, Cartesian3[]>();

// Побудова конуса огляду для обраного юніта
const updateFovCone = (selectedUnit: UnitDto | undefined) => {
    if (!viewer) return;

    if (!selectedUnit || selectedUnit.side === 'OPFOR') {
        if (fovConeEntity) {
            viewer.entities.remove(fovConeEntity);
            fovConeEntity = null;
        }
        return;
    }

    // Розрахунок точок дуги сектора огляду (±50 градусів, радіус 1800 м)
    const fovAngle = 45.0;
    const rangeMeters = selectedUnit.maxOpticsRangeMeters || 1400.0;
    const centerHeading = selectedUnit.heading;

    const points: Cartesian3[] = [
        Cartesian3.fromDegrees(selectedUnit.lon, selectedUnit.lat, selectedUnit.altitude)
    ];

    for (let offset = -fovAngle; offset <= fovAngle; offset += 5) {
        const angleRad = CesiumMath.toRadians(centerHeading + offset);
        const dNorth = Math.cos(angleRad) * rangeMeters;
        const dEast = Math.sin(angleRad) * rangeMeters;

        const pLat = selectedUnit.lat + dNorth / 111132.0;
        const pLon = selectedUnit.lon + dEast / 71500.0;
        points.push(Cartesian3.fromDegrees(pLon, pLat, selectedUnit.altitude));
    }

    if (!fovConeEntity) {
        fovConeEntity = viewer.entities.add({
            id: 'fov-cone',
            polygon: {
                hierarchy: new CallbackProperty(() => ({ positions: points, holes: [] }), false),
                material: Color.CYAN.withAlpha(0.12),
                outline: true,
                outlineColor: Color.CYAN.withAlpha(0.6),
                heightReference: HeightReference.CLAMP_TO_GROUND
            }
        });
    }
};

// Малювання та динамічне оновлення променів прямої видимості до помічених ворогів
const updateLosLines = (data: UnitDto[]) => {
    // Фіксуємо viewer у локальній константі для проходження валідації TypeScript
    const currentViewer = viewer;
    if (!currentViewer) return;

    const unitMap = new Map(data.map(u => [u.id, u]));
    const activeLosKeys = new Set<string>();

    data.filter(u => u.side === 'BLUFOR').forEach(blufor => {
        const targets = blufor.visibleTargetIds || [];

        targets.forEach(targetId => {
            const enemy = unitMap.get(targetId);
            if (!enemy) return;

            const lineKey = `${blufor.id}->${enemy.id}`;
            activeLosKeys.add(lineKey);

            const startPos = Cartesian3.fromDegrees(blufor.lon, blufor.lat, blufor.altitude + 2.0);
            const endPos = Cartesian3.fromDegrees(enemy.lon, enemy.lat, enemy.altitude + 1.5);

            // Оновлюємо актуальні координати променя для кожного кадру
            losCoordinatesMap.set(lineKey, [startPos, endPos]);

            if (!losLineEntities.has(lineKey)) {
                const losLine = currentViewer.entities.add({
                    id: lineKey,
                    polyline: {
                        positions: new CallbackProperty(() => losCoordinatesMap.get(lineKey) || [], false),
                        width: 2,
                        material: new PolylineDashMaterialProperty({
                            color: Color.RED.withAlpha(0.9),
                            dashLength: 10.0
                        }),
                        clampToGround: true
                    }
                });
                losLineEntities.set(lineKey, losLine);
            }
        });
    });

    // Видаляємо промені, якщо контакт розірвано або ворог знищений/сховався
    for (const [key, line] of losLineEntities.entries()) {
        if (!activeLosKeys.has(key)) {
            currentViewer.entities.remove(line);
            losLineEntities.delete(key);
            losCoordinatesMap.delete(key);
        }
    }
};

Ion.defaultAccessToken = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJub25jZSI6ImlCNWFURWVjdExMSllpd1ciLCJqdGkiOiJiYjc0ZWNiOC00YzA5LTQxZTgtODdlYy0wOTg0NTI1MTg2N2IiLCJpZCI6NDk4NjkzLCJpc3MiOiJodHRwczovL2FwaS5jZXNpdW0uY29tIiwiYXVkIjoidW5kZWZpbmVkX2RlZmF1bHQiLCJpYXQiOjE3ODk2NTE5MDd9.EQfiCt7ECjjOrcoVMPklOIXOfsLGzojK-MltEkH9HOo';

onMounted(async () => {
    if (!cesiumContainer.value) return;

    const arcGisProvider = await ArcGisMapServerImageryProvider.fromUrl(
        'https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer',
        { enablePickFeatures: false }
    );

    viewer = new Viewer(cesiumContainer.value, {
        baseLayer: new ImageryLayer(arcGisProvider),
        terrain: Terrain.fromWorldTerrain({
            requestVertexNormals: true,
            requestWaterMask: true
        }),
        baseLayerPicker: false,
        geocoder: false,
        homeButton: false,
        sceneModePicker: false,
        navigationHelpButton: false,
        animation: false,
        timeline: false,
        fullscreenButton: false,
        infoBox: false,
        selectionIndicator: false
    });

    viewer.scene.globe.depthTestAgainstTerrain = true;

    viewer.scene.globe.enableLighting = false;

    viewer.camera.flyTo({
        destination: Cartesian3.fromDegrees(23.54, 49.95, 3000),
        orientation: {
            heading: CesiumMath.toRadians(35),
            pitch: CesiumMath.toRadians(-22),
            roll: 0.0
        },
        duration: 2.0
    });

    setupMouseInteractions();
    connectWebSocket();
});

const connectWebSocket = () => {
    socket = new WebSocket('ws://localhost:8080/ws/simulation');

    socket.onopen = () => {
        isConnected.value = true;
    };

    socket.onclose = () => {
        isConnected.value = false;
        setTimeout(connectWebSocket, 2000);
    };

    socket.onmessage = (event) => {
        try {
            const incomingUnits: UnitDto[] = JSON.parse(event.data);
            units.value = incomingUnits;
            renderUnits(incomingUnits);
            updateRouteLines(incomingUnits);

            // Оновлюємо конус огляду для вибраної машини
            const selectedId = Array.from(selectedUnitIds.value)[0];
            const selectedUnit = incomingUnits.find(u => u.id === selectedId);
            updateFovCone(selectedUnit);

            // Оновлюємо лінії прямого вогневого/візуального контакту
            updateLosLines(incomingUnits);
        } catch (err) {}
    };
};

// Відправка наказів на бекенд
const sendCommand = (type: string, extra: Record<string, any> = {}) => {
    if (!socket || socket.readyState !== WebSocket.OPEN) return;
    if (selectedUnitIds.value.size === 0) return;

    const payload = {
        type: type,
        unitIds: Array.from(selectedUnitIds.value),
        ...extra
    };

    socket.send(JSON.stringify(payload));
};

// Налаштування кліків миші та виділення
const setupMouseInteractions = () => {
    if (!viewer) return;

    handler = new ScreenSpaceEventHandler(viewer.scene.canvas);

    // 1. ЛІВИЙ КЛІК: Виділення окремого юніта
    handler.setInputAction((click: any) => {
        // Якщо клік стався в результаті завершення рамки — ігноруємо його!
        if (justFinishedBoxSelect) {
            justFinishedBoxSelect = false;
            return;
        }

        const picked = viewer!.scene.pick(click.position);

        if (defined(picked) && picked.id && entityMap.has(picked.id.id)) {
            if (isShiftPressed.value) {
                // Shift + клік: додати/прибрати з виділення
                if (selectedUnitIds.value.has(picked.id.id)) {
                    selectedUnitIds.value.delete(picked.id.id);
                } else {
                    selectedUnitIds.value.add(picked.id.id);
                }
            } else {
                // Звичайний клік: виділити тільки цей юніт
                selectedUnitIds.value = new Set([picked.id.id]);
            }
        } else {
            // Клік по порожній землі скидає виділення
            if (!isShiftPressed.value) {
                selectedUnitIds.value.clear();
            }
        }
    }, ScreenSpaceEventType.LEFT_CLICK);

    // 2. ПРАВИЙ КЛІК: Призначення точки руху (MOVE)
    handler.setInputAction((click: any) => {
        if (selectedUnitIds.value.size === 0) return;

        const ray = viewer!.camera.getPickRay(click.position);
        if (!ray) return;
        const cartesian = viewer!.scene.globe.pick(ray, viewer!.scene);
        if (!cartesian) return;

        const cartographic = Cartographic.fromCartesian(cartesian);
        const lat = CesiumMath.toDegrees(cartographic.latitude);
        const lon = CesiumMath.toDegrees(cartographic.longitude);

        // Використовуємо надійний референтний стан Shift
        const isQueue = isShiftPressed.value;

        sendCommand('ORDER_MOVE', {
            targetLat: lat,
            targetLon: lon,
            queue: isQueue
        });
    }, ScreenSpaceEventType.RIGHT_CLICK);

    // 3. SHIFT + DRAG (Рамка виділення)
    const canvas = viewer.scene.canvas;

    canvas.addEventListener('mousedown', (e: MouseEvent) => {
        if (e.shiftKey && e.button === 0) {
            e.preventDefault();
            e.stopPropagation();

            selectionBox.active = true;
            selectionBox.startX = e.clientX;
            selectionBox.startY = e.clientY;
            selectionBox.left = e.clientX;
            selectionBox.top = e.clientY;
            selectionBox.width = 0;
            selectionBox.height = 0;
        }
    });

    window.addEventListener('mousemove', (e: MouseEvent) => {
        if (!selectionBox.active) return;

        const currentX = e.clientX;
        const currentY = e.clientY;

        selectionBox.left = Math.min(selectionBox.startX, currentX);
        selectionBox.top = Math.min(selectionBox.startY, currentY);
        selectionBox.width = Math.abs(currentX - selectionBox.startX);
        selectionBox.height = Math.abs(currentY - selectionBox.startY);
    });

    window.addEventListener('mouseup', () => {
        if (!selectionBox.active) return;
        selectionBox.active = false;

        // Відновлюємо керування камерою, якщо Shift уже відпущено
        if (!isShiftPressed.value && viewer) {
            viewer.scene.screenSpaceCameraController.enableInputs = true;
        }

        // Якщо це був мікроклік (< 8 пікселів), а не протягування рамки — пропускаємо
        if (selectionBox.width < 8 && selectionBox.height < 8) {
            return;
        }

        // Активуємо блокування лівого кліку
        justFinishedBoxSelect = true;

        const rect = canvas.getBoundingClientRect();
        const xMin = selectionBox.left - rect.left;
        const xMax = xMin + selectionBox.width;
        const yMin = selectionBox.top - rect.top;
        const yMax = yMin + selectionBox.height;

        const newSelection = new Set<string>();

        units.value.forEach((u) => {
            const pos3d = Cartesian3.fromDegrees(u.lon, u.lat, u.altitude);
            const screenPos = SceneTransforms.worldToWindowCoordinates(viewer!.scene, pos3d);

            if (screenPos) {
                if (
                    screenPos.x >= xMin &&
                    screenPos.x <= xMax &&
                    screenPos.y >= yMin &&
                    screenPos.y <= yMax
                ) {
                    newSelection.add(u.id);
                }
            }
        });

        if (newSelection.size > 0) {
            selectedUnitIds.value = newSelection;
        }
    });
};

// Оновлення положення та кольору техніки
const renderUnits = (data: UnitDto[]) => {
    const currentViewer = viewer;
    if (!currentViewer) return;

    const currentIds = new Set(data.map(u => u.id));

    // 1. ВИДАЛЯЄМО З КАРТИ ЦІЛІ, ЯКІ НЕ ПРИЙШЛИ ВІД СЕРВЕРА (ТУМАН ВІЙНИ)
    for (const [id, entity] of entityMap.entries()) {
        if (!currentIds.has(id)) {
            currentViewer.entities.remove(entity);
            entityMap.delete(id);
            selectedUnitIds.value.delete(id);
        }
    }

    // 2. ВІДОБРАЖАЄМО ЮНІТИ
    data.forEach((u) => {
        const position = Cartesian3.fromDegrees(u.lon, u.lat, u.altitude);
        const headingRad = CesiumMath.toRadians(u.heading - 90);
        const orientation = Transforms.headingPitchRollQuaternion(position, new HeadingPitchRoll(headingRad, 0, 0));
        const isSelected = selectedUnitIds.value.has(u.id);

        // Чітке розділення кольорів: BLUFOR - синій, OPFOR - червоний!
        const isOpfor = u.side === 'OPFOR';
        let baseColor = isOpfor ? Color.fromCssColorString('#ff3838') : Color.fromCssColorString('#00a8ff');
        if (isSelected) baseColor = Color.YELLOW;

        if (!entityMap.has(u.id)) {
            const entity = currentViewer.entities.add({
                id: u.id,
                name: u.callsign,
                position: position,
                orientation: orientation,
                point: {
                    pixelSize: isSelected ? 18 : (isOpfor ? 15 : 13),
                    color: baseColor,
                    outlineColor: Color.WHITE,
                    outlineWidth: 2,
                    heightReference: HeightReference.CLAMP_TO_GROUND
                },
                label: {
                    text: u.callsign,
                    font: isOpfor ? 'bold 12px monospace' : '12px monospace',
                    fillColor: isOpfor ? Color.fromCssColorString('#ff4d4d') : Color.WHITE,
                    outlineColor: Color.BLACK,
                    outlineWidth: 3,
                    style: 2,
                    pixelOffset: new Cartesian3(0, -22, 0),
                    heightReference: HeightReference.CLAMP_TO_GROUND,
                    disableDepthTestDistance: Number.POSITIVE_INFINITY
                }
            });
            entityMap.set(u.id, entity);
        } else {
            const entity = entityMap.get(u.id)!;
            entity.position = position as any;
            entity.orientation = orientation as any;

            if (entity.point) {
                entity.point.color = baseColor as any;
                entity.point.pixelSize = (isSelected ? 18 : (isOpfor ? 15 : 13)) as any;
            }
        }
    });
};

// Малювання та динамічне оновлення ліній маршрутів
const updateRouteLines = (data: UnitDto[]) => {
    const currentViewer = viewer;
    if (!currentViewer) return;

    data.forEach((u) => {
        const waypoints = u.waypoints || [];
        const lineId = `route-${u.id}`;

        // Якщо точок немає — видаляємо лінію з карти
        if (waypoints.length === 0) {
            if (routeEntities.has(lineId)) {
                currentViewer.entities.remove(routeEntities.get(lineId)!);
                routeEntities.delete(lineId);
                routeCoordinatesMap.delete(lineId);
            }
            return;
        }

        // Формуємо актуальний шлях: [Поточне положення БТР -> Точка 1 -> Точка 2 -> ...]
        const currentPositions: Cartesian3[] = [
            Cartesian3.fromDegrees(u.lon, u.lat, u.altitude)
        ];

        waypoints.forEach(wp => {
            currentPositions.push(Cartesian3.fromDegrees(wp.lon, wp.lat, u.altitude));
        });

        // Оновлюємо координати в карті (CallbackProperty прочитає їх щокадру)
        routeCoordinatesMap.set(lineId, currentPositions);

        // Якщо лінія ще не створена на карті — створюємо її
        if (!routeEntities.has(lineId)) {
            const line = currentViewer.entities.add({
                id: lineId,
                polyline: {
                    positions: new CallbackProperty(() => {
                        return routeCoordinatesMap.get(lineId) || [];
                    }, false),
                    width: 3,
                    material: new PolylineDashMaterialProperty({
                        color: Color.YELLOW.withAlpha(0.85),
                        dashLength: 16.0
                    }),
                    clampToGround: true
                }
            });
            routeEntities.set(lineId, line);
        }
    });
};

const toggleUnitSelection = (id: string, event: MouseEvent) => {
    if (event.shiftKey) {
        if (selectedUnitIds.value.has(id)) selectedUnitIds.value.delete(id);
        else selectedUnitIds.value.add(id);
    } else {
        selectedUnitIds.value = new Set([id]);
    }
};

onUnmounted(() => {
    if (handler) handler.destroy();
    if (socket) socket.close();
    if (viewer) viewer.destroy();
});
</script>

<style scoped>
.simulator-container {
    position: relative;
    width: 100vw;
    height: 100vh;
    user-select: none;
}

#cesiumContainer {
    width: 100%;
    height: 100%;
}

/* Рамка виділення мишею */
.selection-marquee {
    position: absolute;
    border: 1px dashed #00e676;
    background-color: rgba(0, 230, 118, 0.15);
    pointer-events: none;
    z-index: 9999;
}

.tactical-hud {
    position: absolute;
    top: 20px;
    left: 20px;
    width: 320px;
    background: rgba(10, 15, 24, 0.9);
    backdrop-filter: blur(8px);
    border: 1px solid rgba(0, 168, 255, 0.3);
    border-radius: 6px;
    color: #e0e6ed;
    padding: 16px;
    font-family: 'Courier New', Courier, monospace;
    box-shadow: 0 8px 32px rgba(0, 0, 0, 0.7);
    pointer-events: auto;
}

.hud-header {
    display: flex;
    align-items: center;
    gap: 10px;
    border-bottom: 1px solid rgba(0, 168, 255, 0.2);
    padding-bottom: 8px;
    margin-bottom: 10px;
}

.hud-header h3 {
    margin: 0;
    font-size: 15px;
    letter-spacing: 1.5px;
    color: #00a8ff;
}

.pulse-indicator {
    width: 10px;
    height: 10px;
    border-radius: 50%;
    background-color: #ff3838;
}

.pulse-indicator.online {
    background-color: #00e676;
    box-shadow: 0 0 8px #00e676;
}

.hud-stats {
    font-size: 12px;
    line-height: 1.5;
    margin-bottom: 12px;
    color: #8fa3bf;
}

.highlight-text {
    color: #f1c40f;
}

/* Командна панель */
.command-panel {
    background: rgba(0, 168, 255, 0.08);
    border: 1px solid rgba(0, 168, 255, 0.3);
    border-radius: 4px;
    padding: 10px;
    margin-bottom: 12px;
}

.panel-label {
    font-size: 11px;
    color: #00a8ff;
    font-weight: bold;
    margin-bottom: 6px;
}

.btn-group {
    display: flex;
    gap: 6px;
    margin-bottom: 8px;
}

.c2-btn {
    flex: 1;
    background: #1e293b;
    border: 1px solid #3b82f6;
    color: #fff;
    padding: 6px;
    font-size: 11px;
    font-family: monospace;
    cursor: pointer;
    border-radius: 3px;
    transition: all 0.2s;
}

.c2-btn:hover {
    background: #3b82f6;
}

.c2-btn.danger {
    border-color: #ef4444;
    background: rgba(239, 68, 68, 0.2);
}

.c2-btn.danger:hover {
    background: #ef4444;
}

.hint-text {
    font-size: 10px;
    color: #94a3b8;
    line-height: 1.4;
}

.unit-list {
    max-height: 45vh;
    overflow-y: auto;
}

.unit-card {
    background: rgba(255, 255, 255, 0.03);
    border-left: 3px solid #00a8ff;
    padding: 8px;
    margin-bottom: 6px;
    cursor: pointer;
    transition: background 0.2s;
}

.unit-card:hover {
    background: rgba(0, 168, 255, 0.15);
}

.unit-card.selected {
    background: rgba(241, 196, 15, 0.15);
    border-left-color: #f1c40f;
}

.unit-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
}

.unit-title {
    font-weight: bold;
    font-size: 12px;
    color: #fff;
}

.unit-status {
    font-size: 10px;
    color: #94a3b8;
}

.unit-status.moving {
    color: #00e676;
}

.unit-metrics {
    display: flex;
    justify-content: space-between;
    font-size: 10px;
    color: #7f8c8d;
    margin-top: 4px;
}
</style>