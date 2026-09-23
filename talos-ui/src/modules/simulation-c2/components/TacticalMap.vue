<template>
    <div class="simulator-container" @contextmenu.prevent>
        <!-- 3D Cesium Container -->
        <div id="cesiumContainer" ref="cesiumContainer"></div>

        <!-- Selection Marquee Box -->
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

        <!-- Warning banner if no local maps found on server -->
        <div v-if="!activeMap && isConnected" class="no-map-overlay">
            <div class="alert-box">
                <h3>УВАГА: НЕ ЗНАЙДЕНО ЛОКАЛЬНИХ КАРТ</h3>
                <p>На сервері немає підготовленого бойового квадрата. Перейдіть у розділ «КАРТИ ТА ТВД» і створіть карту.</p>
            </div>
        </div>

        <!-- Tactical C2 HUD Overlay -->
        <aside class="tactical-hud">
            <div class="hud-header">
                <span class="pulse-indicator" :class="{ online: isConnected }"></span>
                <h3>TALOS C2 INTERFACE</h3>
            </div>

            <!-- Active Map Info -->
            <div v-if="activeMap" class="hud-section">
                <div class="section-title">БОЙОВИЙ КВАДРАТ (ТВД):</div>
                <div class="theater-name">{{ activeMap.name }} ({{ activeMap.sizeKm }}×{{ activeMap.sizeKm }} км)</div>
                <div class="theater-coords">ЦЕНТР: {{ activeMap.centerLat.toFixed(3) }}, {{ activeMap.centerLon.toFixed(3) }}</div>
            </div>

            <!-- Basemap Switcher (Satellite / Topo / Tactical) -->
            <div v-if="activeMap && activeMap.layers.length > 0" class="hud-section">
                <div class="section-title">ПІДКЛАДКА КАРТИ (OFFLINE):</div>
                <div class="basemap-group">
                    <button
                        v-for="layer in activeMap.layers"
                        :key="layer.id"
                        type="button"
                        class="basemap-btn"
                        :class="{ active: currentLayerType === layer.layerType }"
                        @click="switchBaseLayer(layer.layerType)"
                    >
                        {{ layer.layerType }}
                    </button>
                </div>
            </div>

            <!-- Status & Contacts Counter -->
            <div class="hud-stats">
                <div>СТАТУС ЗВ’ЯЗКУ: <strong>{{ isConnected ? 'ОНЛАЙН' : 'ПОШУК...' }}</strong></div>
                <div>ВИДІЛЕНО СИЛ: <strong class="highlight-text">{{ selectedUnitIds.size }} од.</strong></div>
                <div>
                    КОНТАКТИ OPFOR:
                    <strong :style="{ color: units.some(u => u.side === 'OPFOR') ? '#ff3838' : '#8fa3bf' }">
                        {{ units.filter(u => u.side === 'OPFOR').length }} виявлено
                    </strong>
                </div>
            </div>

            <!-- Command Panel for selected units -->
            <div v-if="selectedUnitIds.size > 0" class="command-panel">
                <div class="panel-label">КОМАНДИ:</div>
                <div class="btn-group">
                    <button type="button" class="c2-btn danger" @click="sendCommand('ORDER_HALT')">СТОП</button>
                    <button type="button" class="c2-btn" @click="sendCommand('ORDER_CHANGE_SPEED', { speedKmh: 30 })">30 км/г</button>
                    <button type="button" class="c2-btn" @click="sendCommand('ORDER_CHANGE_SPEED', { speedKmh: 60 })">60 км/г</button>
                </div>
            </div>

            <!-- List of Active Friendly Units -->
            <div class="unit-list">
                <div
                    v-for="u in units"
                    :key="u.id"
                    class="unit-card"
                    :class="{
            selected: selectedUnitIds.has(u.id),
            opfor: u.side === 'OPFOR'
          }"
                    @click="toggleUnitSelection(u.id, $event)"
                >
                    <div class="unit-header">
                        <span class="unit-title">{{ u.callsign }}</span>
                        <span class="unit-status" :class="{ moving: u.currentSpeedKmh > 1 }">
              {{ u.currentSpeedKmh > 1 ? 'МАРШ' : 'СТОЇТЬ' }}
            </span>
                    </div>
                    <div class="unit-metrics">
                        <span>Шв: {{ u.currentSpeedKmh.toFixed(1) }} км/год</span>
                        <span>Висота: {{ u.altitude.toFixed(0) }} м</span>
                    </div>
                </div>
            </div>
        </aside>
    </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref, reactive } from 'vue';
import {
    Viewer,
    Cartesian2,
    Cartesian3,
    Color,
    Entity,
    Transforms,
    HeadingPitchRoll,
    Math as CesiumMath,
    Rectangle,
    UrlTemplateImageryProvider,
    ImageryLayer,
    HeightReference,
    ScreenSpaceEventHandler,
    ScreenSpaceEventType,
    defined,
    SceneTransforms,
    Cartographic,
    PolylineDashMaterialProperty,
    CallbackProperty,
    PolygonHierarchy,
    ConstantPositionProperty,
    ConstantProperty
} from 'cesium';
import { mapApi } from '@/modules/map-studio/mapApi';
import type { MapDetailDto, LayerType } from '@/modules/map-studio/types';

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
    inCover?: boolean;
    visibleTargetIds?: string[];
    waypoints?: WaypointDto[];
}

const cesiumContainer = ref<HTMLDivElement | null>(null);
const isConnected = ref(false);
const units = ref<UnitDto[]>([]);
const selectedUnitIds = ref<Set<string>>(new Set());

// Active map and baselayer state
const activeMap = ref<MapDetailDto | null>(null);
const currentLayerType = ref<LayerType>('SATELLITE');

// Mouse box selection state
const selectionBox = reactive({
    active: false,
    startX: 0,
    startY: 0,
    left: 0,
    top: 0,
    width: 0,
    height: 0
});

const isShiftPressed = ref(false);
let justFinishedBoxSelect = false;
let isDestroyed = false;
let reconnectTimer: ReturnType<typeof setTimeout> | null = null;

let viewer: Viewer | null = null;
let socket: WebSocket | null = null;
let handler: ScreenSpaceEventHandler | null = null;

const entityMap = new Map<string, Entity>();
const routeEntities = new Map<string, Entity>();
const routeCoordinatesMap = new Map<string, Cartesian3[]>();
let fovConeEntity: Entity | null = null;
let fovConePositions: Cartesian3[] = [];
const losLineEntities = new Map<string, Entity>();
const losCoordinatesMap = new Map<string, Cartesian3[]>();

onMounted(async () => {
    if (!cesiumContainer.value) return;

    // 1. Fetch available offline maps from local server
    try {
        const maps = await mapApi.getAllMaps();
        const readyMap = maps.find(m => m.status === 'READY') || maps[0];
        if (readyMap) {
            activeMap.value = readyMap;
            if (readyMap.layers.length > 0) {
                currentLayerType.value = readyMap.layers[0].layerType;
            }
        }
    } catch (err) {
        console.error('[TALOS C2] Could not load local maps:', err);
    }

    // 2. Initialize Cesium Viewer without default online world basemap
    viewer = new Viewer(cesiumContainer.value, {
        baseLayer: false, // Disables external Cesium Ion / Bing maps
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

    viewer.scene.globe.enableLighting = false;

    // 3. Apply strict theater boundaries and load local offline tiles
    if (activeMap.value) {
        applyTheaterBounds(activeMap.value);
    } else {
        viewer.camera.flyTo({
            destination: Cartesian3.fromDegrees(23.58, 49.98, 5000),
            duration: 1.0
        });
    }

    setupKeyboardListeners();
    setupMouseInteractions();
    connectWebSocket();
});

/**
 * Clamps the 3D globe to the exact bounding box and sets up local tile streaming.
 */
const applyTheaterBounds = (map: MapDetailDto) => {
    if (!viewer) return;

    const theaterRect = Rectangle.fromDegrees(
        map.minLon,
        map.minLat,
        map.maxLon,
        map.maxLat
    );

    // Limit globe to operational theater envelope
    viewer.scene.globe.cartographicLimitRectangle = theaterRect;
    viewer.scene.screenSpaceCameraController.minimumZoomDistance = 50.0;
    viewer.scene.screenSpaceCameraController.maximumZoomDistance = 35000.0;

    switchBaseLayer(currentLayerType.value);

    viewer.camera.flyTo({
        destination: Cartesian3.fromDegrees(map.centerLon, map.centerLat - 0.05, 4500),
        orientation: {
            heading: CesiumMath.toRadians(0),
            pitch: CesiumMath.toRadians(-35),
            roll: 0.0
        },
        duration: 2.0
    });
};

/**
 * Switches between local baselayers (SATELLITE, TOPOGRAPHIC, TACTICAL).
 */
const switchBaseLayer = (layerType: LayerType) => {
    if (!viewer || !activeMap.value) return;

    currentLayerType.value = layerType;
    viewer.imageryLayers.removeAll();

    const layerMeta = activeMap.value.layers.find(l => l.layerType === layerType);
    const minZ = layerMeta ? layerMeta.minZoom : 10;
    const maxZ = layerMeta ? layerMeta.maxZoom : 16;

    // Stream tiles via relative proxy URL
    const tileUrl = `/api/maps/${activeMap.value.id}/tiles/${layerType.toLowerCase()}/{z}/{x}/{y}.png`;

    const provider = new UrlTemplateImageryProvider({
        url: tileUrl,
        rectangle: Rectangle.fromDegrees(
            activeMap.value.minLon,
            activeMap.value.minLat,
            activeMap.value.maxLon,
            activeMap.value.maxLat
        ),
        minimumLevel: minZ,
        maximumLevel: maxZ
    });

    viewer.imageryLayers.add(new ImageryLayer(provider));
    console.log(`[TALOS C2] Switched offline baselayer to: ${layerType}`);
};

const setupKeyboardListeners = () => {
    window.addEventListener('keydown', (e: KeyboardEvent) => {
        if (e.key === 'Shift') {
            isShiftPressed.value = true;
            if (viewer) viewer.scene.screenSpaceCameraController.enableInputs = false;
        }
    });

    window.addEventListener('keyup', (e: KeyboardEvent) => {
        if (e.key === 'Shift') {
            isShiftPressed.value = false;
            if (viewer && !selectionBox.active) {
                viewer.scene.screenSpaceCameraController.enableInputs = true;
            }
        }
    });
};

const setupMouseInteractions = () => {
    if (!viewer) return;

    handler = new ScreenSpaceEventHandler(viewer.scene.canvas);

    // 1. LEFT CLICK: Single unit selection
    handler.setInputAction((event: { position: Cartesian2 }) => {
        if (justFinishedBoxSelect) {
            justFinishedBoxSelect = false;
            return;
        }

        const picked = viewer!.scene.pick(event.position);

        if (defined(picked) && picked.id && entityMap.has(picked.id.id)) {
            const pickedId = picked.id.id;
            if (isShiftPressed.value) {
                if (selectedUnitIds.value.has(pickedId)) {
                    selectedUnitIds.value.delete(pickedId);
                } else {
                    selectedUnitIds.value.add(pickedId);
                }
            } else {
                selectedUnitIds.value = new Set([pickedId]);
            }
        } else {
            if (!isShiftPressed.value) {
                selectedUnitIds.value.clear();
            }
        }
    }, ScreenSpaceEventType.LEFT_CLICK);

    // 2. RIGHT CLICK: Tactical order waypoint assignment
    handler.setInputAction((event: { position: Cartesian2 }) => {
        if (selectedUnitIds.value.size === 0) return;

        const ray = viewer!.camera.getPickRay(event.position);
        if (!ray) return;
        const cartesian = viewer!.scene.globe.pick(ray, viewer!.scene);
        if (!cartesian) return;

        const cartographic = Cartographic.fromCartesian(cartesian);
        const lat = CesiumMath.toDegrees(cartographic.latitude);
        const lon = CesiumMath.toDegrees(cartographic.longitude);

        sendCommand('ORDER_MOVE', {
            targetLat: lat,
            targetLon: lon,
            queue: isShiftPressed.value
        });
    }, ScreenSpaceEventType.RIGHT_CLICK);

    // 3. SHIFT + DRAG: Marquee Box Selection
    const canvas = viewer.scene.canvas;

    canvas.addEventListener('mousedown', (e: MouseEvent) => {
        if (e.shiftKey && e.button === 0) {
            e.preventDefault();
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

        if (!isShiftPressed.value && viewer) {
            viewer.scene.screenSpaceCameraController.enableInputs = true;
        }

        if (selectionBox.width < 8 && selectionBox.height < 8) return;

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
                if (screenPos.x >= xMin && screenPos.x <= xMax && screenPos.y >= yMin && screenPos.y <= yMax) {
                    newSelection.add(u.id);
                }
            }
        });

        if (newSelection.size > 0) {
            selectedUnitIds.value = newSelection;
        }
    });
};

const connectWebSocket = () => {
    if (isDestroyed) return;

    // Dynamically resolve protocol and host for robust execution in all environments
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const socketUrl = `${protocol}//${window.location.host}/ws/simulation`;

    socket = new WebSocket(socketUrl);

    socket.onopen = () => {
        isConnected.value = true;
        console.log('[TALOS C2] WebSocket telemetry stream connected');
    };

    socket.onclose = () => {
        isConnected.value = false;
        if (!isDestroyed) {
            reconnectTimer = setTimeout(connectWebSocket, 2000);
        }
    };

    socket.onmessage = (event) => {
        try {
            const incomingUnits: UnitDto[] = JSON.parse(event.data);
            units.value = incomingUnits;
            renderUnits(incomingUnits);
            updateRouteLines(incomingUnits);

            const selectedId = Array.from(selectedUnitIds.value)[0];
            const selectedUnit = incomingUnits.find(u => u.id === selectedId);
            updateFovCone(selectedUnit);
            updateLosLines(incomingUnits);
        } catch (err) {
            console.error('[TALOS C2] Telemetry message decode error:', err);
        }
    };
};

const sendCommand = (type: string, extra: Record<string, unknown> = {}) => {
    if (!socket || socket.readyState !== WebSocket.OPEN) return;
    if (selectedUnitIds.value.size === 0) return;

    const payload = {
        type,
        unitIds: Array.from(selectedUnitIds.value),
        ...extra
    };
    socket.send(JSON.stringify(payload));
};

const renderUnits = (data: UnitDto[]) => {
    const currentViewer = viewer;
    if (!currentViewer) return;

    const currentIds = new Set(data.map(u => u.id));

    for (const [id, entity] of entityMap.entries()) {
        if (!currentIds.has(id)) {
            currentViewer.entities.remove(entity);
            entityMap.delete(id);
            selectedUnitIds.value.delete(id);
        }
    }

    data.forEach((u) => {
        const position = Cartesian3.fromDegrees(u.lon, u.lat, u.altitude);
        const headingRad = CesiumMath.toRadians(u.heading - 90);
        const orientation = Transforms.headingPitchRollQuaternion(position, new HeadingPitchRoll(headingRad, 0, 0));
        const isSelected = selectedUnitIds.value.has(u.id);
        const isOpfor = u.side === 'OPFOR';

        let baseColor = isOpfor ? Color.fromCssColorString('#ff3838') : Color.fromCssColorString('#00a8ff');
        if (isSelected) baseColor = Color.YELLOW;

        if (!entityMap.has(u.id)) {
            const entity = currentViewer.entities.add({
                id: u.id,
                name: u.callsign,
                position: new ConstantPositionProperty(position),
                orientation: new ConstantProperty(orientation),
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
            (entity.position as ConstantPositionProperty).setValue(position);
            (entity.orientation as ConstantProperty).setValue(orientation);

            if (entity.point) {
                entity.point.color = new ConstantProperty(baseColor);
                entity.point.pixelSize = new ConstantProperty(isSelected ? 18 : (isOpfor ? 15 : 13));
            }
        }
    });
};

const updateRouteLines = (data: UnitDto[]) => {
    const currentViewer = viewer;
    if (!currentViewer) return;

    data.forEach((u) => {
        const waypoints = u.waypoints || [];
        const lineId = `route-${u.id}`;

        if (waypoints.length === 0) {
            if (routeEntities.has(lineId)) {
                currentViewer.entities.remove(routeEntities.get(lineId)!);
                routeEntities.delete(lineId);
                routeCoordinatesMap.delete(lineId);
            }
            return;
        }

        const currentPositions: Cartesian3[] = [
            Cartesian3.fromDegrees(u.lon, u.lat, u.altitude)
        ];

        waypoints.forEach(wp => {
            currentPositions.push(Cartesian3.fromDegrees(wp.lon, wp.lat, u.altitude));
        });

        routeCoordinatesMap.set(lineId, currentPositions);

        if (!routeEntities.has(lineId)) {
            const line = currentViewer.entities.add({
                id: lineId,
                polyline: {
                    positions: new CallbackProperty(() => routeCoordinatesMap.get(lineId) || [], false),
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

const updateFovCone = (selectedUnit: UnitDto | undefined) => {
    const currentViewer = viewer;
    if (!currentViewer) return;

    if (!selectedUnit || selectedUnit.side === 'OPFOR') {
        if (fovConeEntity) {
            currentViewer.entities.remove(fovConeEntity);
            fovConeEntity = null;
            fovConePositions = [];
        }
        return;
    }

    const fovAngle = 45.0;
    const rangeMeters = 1400.0;
    const centerHeading = selectedUnit.heading;

    fovConePositions = [
        Cartesian3.fromDegrees(selectedUnit.lon, selectedUnit.lat, selectedUnit.altitude)
    ];

    for (let offset = -fovAngle; offset <= fovAngle; offset += 5) {
        const angleRad = CesiumMath.toRadians(centerHeading + offset);
        const dNorth = Math.cos(angleRad) * rangeMeters;
        const dEast = Math.sin(angleRad) * rangeMeters;

        const pLat = selectedUnit.lat + dNorth / 111132.0;
        const pLon = selectedUnit.lon + dEast / 71500.0;
        fovConePositions.push(Cartesian3.fromDegrees(pLon, pLat, selectedUnit.altitude));
    }

    if (!fovConeEntity) {
        fovConeEntity = currentViewer.entities.add({
            id: 'fov-cone',
            polygon: {
                hierarchy: new CallbackProperty(() => new PolygonHierarchy(fovConePositions), false),
                material: Color.CYAN.withAlpha(0.12),
                outline: true,
                outlineColor: Color.CYAN.withAlpha(0.6),
                heightReference: HeightReference.CLAMP_TO_GROUND
            }
        });
    }
};

const updateLosLines = (data: UnitDto[]) => {
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

    for (const [key, line] of losLineEntities.entries()) {
        if (!activeLosKeys.has(key)) {
            currentViewer.entities.remove(line);
            losLineEntities.delete(key);
            losCoordinatesMap.delete(key);
        }
    }
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
    isDestroyed = true;
    if (reconnectTimer) clearTimeout(reconnectTimer);
    if (handler) handler.destroy();
    if (socket) {
        socket.onclose = null;
        socket.close();
    }
    if (viewer) viewer.destroy();
});
</script>

<style scoped>
.simulator-container {
    position: relative;
    width: 100vw;
    height: 100vh;
    user-select: none;
    background: #000;
}

#cesiumContainer {
    width: 100%;
    height: 100%;
}

.selection-marquee {
    position: absolute;
    border: 1px dashed #00e676;
    background-color: rgba(0, 230, 118, 0.15);
    pointer-events: none;
    z-index: 9999;
}

.no-map-overlay {
    position: absolute;
    top: 40%;
    left: 50%;
    transform: translate(-50%, -50%);
    z-index: 1000;
}

.alert-box {
    background: rgba(15, 23, 42, 0.95);
    border: 1px solid #ef4444;
    padding: 24px;
    border-radius: 8px;
    text-align: center;
    color: #fff;
    box-shadow: 0 0 20px rgba(239, 68, 68, 0.4);
}

.alert-box h3 {
    margin: 0 0 10px 0;
    color: #ef4444;
    font-family: monospace;
}

.alert-box p {
    margin: 0;
    font-size: 13px;
    color: #94a3b8;
}

.tactical-hud {
    position: absolute;
    top: 20px;
    left: 20px;
    width: 320px;
    background: rgba(10, 15, 24, 0.92);
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

.hud-section {
    background: rgba(255, 255, 255, 0.04);
    border: 1px solid rgba(0, 168, 255, 0.2);
    border-radius: 4px;
    padding: 8px;
    margin-bottom: 10px;
}

.section-title {
    font-size: 10px;
    color: #64748b;
    margin-bottom: 4px;
}

.theater-name {
    font-weight: bold;
    color: #38bdf8;
    font-size: 12px;
}

.theater-coords {
    font-size: 10px;
    color: #94a3b8;
    margin-top: 2px;
}

.basemap-group {
    display: flex;
    gap: 4px;
}

.basemap-btn {
    flex: 1;
    background: #1e293b;
    border: 1px solid #475569;
    color: #94a3b8;
    padding: 4px 6px;
    font-size: 9px;
    cursor: pointer;
    border-radius: 3px;
    font-family: monospace;
}

.basemap-btn:hover {
    color: #fff;
}

.basemap-btn.active {
    background: #0284c7;
    border-color: #38bdf8;
    color: #fff;
    font-weight: bold;
}

.hud-stats {
    font-size: 11px;
    line-height: 1.5;
    margin-bottom: 10px;
    color: #8fa3bf;
}

.highlight-text {
    color: #f1c40f;
}

.command-panel {
    background: rgba(0, 168, 255, 0.08);
    border: 1px solid rgba(0, 168, 255, 0.3);
    border-radius: 4px;
    padding: 10px;
    margin-bottom: 10px;
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
}

.c2-btn.danger {
    border-color: #ef4444;
    background: rgba(239, 68, 68, 0.2);
}

.unit-list {
    max-height: 40vh;
    overflow-y: auto;
}

.unit-card {
    background: rgba(255, 255, 255, 0.03);
    border-left: 3px solid #00a8ff;
    padding: 6px 8px;
    margin-bottom: 4px;
    cursor: pointer;
}

.unit-card.opfor {
    border-left-color: #ff3838;
}

.unit-card.selected {
    background: rgba(241, 196, 15, 0.15);
    border-left-color: #f1c40f;
}

.unit-header {
    display: flex;
    justify-content: space-between;
}

.unit-title {
    font-weight: bold;
    font-size: 11px;
    color: #fff;
}

.unit-status {
    font-size: 9px;
    color: #94a3b8;
}

.unit-status.moving {
    color: #00e676;
}

.unit-metrics {
    display: flex;
    justify-content: space-between;
    font-size: 10px;
    color: #64748b;
    margin-top: 2px;
}
</style>