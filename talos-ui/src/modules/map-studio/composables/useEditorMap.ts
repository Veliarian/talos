import { ref, reactive, shallowRef } from 'vue';
import {
    Viewer,
    Cartesian3,
    Rectangle,
    UrlTemplateImageryProvider,
    ImageryLayer,
    GeoJsonDataSource,
    Color,
    Math as CesiumMath,
    createElevationBandMaterial,
    ColorMaterialProperty,
    ConstantProperty,
    EllipsoidTerrainProvider,
    CustomHeightmapTerrainProvider,
    GeographicTilingScheme,
    Entity,
    CallbackProperty,
    Material
} from 'cesium';
import type { MapDetailDto, FeatureStatus } from '../types';

export function useEditorMap(map: MapDetailDto) {
    // shallowRef prevents Vue from creating deep reactive proxies over massive Cesium internals
    const viewer = shallowRef<Viewer | null>(null);
    const is3DMode = ref(false);
    const centerAltitudeDisplay = ref(120);
    const currentLayerType = ref<string>(map.layers?.[0]?.layerType || 'SATELLITE');

    // Layer stack toggles
    const layerStack = reactive({
        elevation: false,
        texture: true,
        objects: true
    });

    let vectorDataSource: GeoJsonDataSource | null = null;
    let currentImageryLayer: ImageryLayer | null = null;

    // Linear drawing state (A -> B)
    const lineStartCartesian = ref<Cartesian3 | null>(null);
    const currentMouseCartesian = ref<Cartesian3 | null>(null);

    const geoTilingScheme = new GeographicTilingScheme();
    const terrainGridCache = new Map<string, Float32Array>();
    const terrainScale = ref(2.5);

    // Shared GPU materials for draw-call batching
    const MAT_ROAD = new ColorMaterialProperty(Color.fromCssColorString('#f59e0b').withAlpha(0.9));
    const MAT_ROAD_DESTROYED = new ColorMaterialProperty(Color.fromCssColorString('#475569').withAlpha(0.6));
    const MAT_ROAD_MINED = new ColorMaterialProperty(Color.fromCssColorString('#dc2626').withAlpha(0.9));
    const MAT_ROAD_CHECKPOINT = new ColorMaterialProperty(Color.fromCssColorString('#f97316').withAlpha(0.95));
    const MAT_FOREST = new ColorMaterialProperty(Color.fromCssColorString('#15803d').withAlpha(0.55));
    const MAT_MEADOW = new ColorMaterialProperty(Color.fromCssColorString('#65a30d').withAlpha(0.25));
    const MAT_SETTLEMENT = new ColorMaterialProperty(Color.fromCssColorString('#64748b').withAlpha(0.35));
    const MAT_BUILDING = new ColorMaterialProperty(Color.fromCssColorString('#ef4444').withAlpha(0.7));
    const MAT_WATER_LINE = new ColorMaterialProperty(Color.fromCssColorString('#0284c7').withAlpha(0.9));
    const MAT_WATER_POLYGON = new ColorMaterialProperty(Color.fromCssColorString('#0284c7').withAlpha(0.65));
    const MAT_RAILWAY = new ColorMaterialProperty(Color.fromCssColorString('#f8fafc').withAlpha(0.8));
    const MAT_SOIL = new ColorMaterialProperty(Color.fromCssColorString('#475569').withAlpha(0.2));

    const CONST_WIDTH_ROAD = new ConstantProperty(3.5);
    const CONST_WIDTH_STREAM = new ConstantProperty(2.5);
    const CONST_WIDTH_RAILWAY = new ConstantProperty(2.0);
    const CONST_WIDTH_CHECKPOINT = new ConstantProperty(6.0);
    const CONST_OUTLINE_FALSE = new ConstantProperty(false);

    const createLocalTerrainProvider = () => {
        return new CustomHeightmapTerrainProvider({
            width: 64,
            height: 64,
            tilingScheme: geoTilingScheme,
            callback: async (x: number, y: number, level: number) => {
                const cacheKey = `${level}_${x}_${y}`;
                if (terrainGridCache.has(cacheKey)) {
                    return terrainGridCache.get(cacheKey)!;
                }

                const rect = geoTilingScheme.tileXYToRectangle(x, y, level);
                const minLat = CesiumMath.toDegrees(rect.south);
                const maxLat = CesiumMath.toDegrees(rect.north);
                const minLon = CesiumMath.toDegrees(rect.west);
                const maxLon = CesiumMath.toDegrees(rect.east);

                // Sample at 64x64 resolution for smooth geometric continuity
                const url = `/api/maps/${map.id}/terrain/grid?minLat=${minLat}&maxLat=${maxLat}&minLon=${minLon}&maxLon=${maxLon}&width=64&height=64`;

                try {
                    const response = await fetch(url);
                    if (!response.ok) return new Float32Array(64 * 64).fill(120.0);
                    const buffer = await response.arrayBuffer();
                    const floatArray = new Float32Array(buffer);
                    terrainGridCache.set(cacheKey, floatArray);
                    centerAltitudeDisplay.value = Math.round(floatArray[2048] || 120.0);
                    return floatArray;
                } catch {
                    return new Float32Array(64 * 64).fill(120.0);
                }
            }
        });
    };

    const invalidateTerrainCache = () => {
        terrainGridCache.clear();
        if (viewer.value && is3DMode.value) {
            viewer.value.terrainProvider = createLocalTerrainProvider();
        }
    };

    const initViewer = (container: HTMLDivElement) => {
        const v = new Viewer(container, {
            baseLayer: false,
            creditContainer: document.createElement('div'),
            terrainProvider: new EllipsoidTerrainProvider(),
            baseLayerPicker: false,
            geocoder: false,
            homeButton: false,
            sceneModePicker: false,
            navigationHelpButton: false,
            animation: false,
            timeline: false,
            fullscreenButton: false,
            infoBox: false,
            selectionIndicator: false,
            skyBox: false
        });

        (v.scene as unknown as { verticalExaggeration: number }).verticalExaggeration = terrainScale.value;
        v.scene.globe.enableLighting = false;
        v.scene.globe.baseColor = Color.fromCssColorString('#1e293b');
        if (v.scene.skyAtmosphere) v.scene.skyAtmosphere.show = false;
        v.scene.globe.showGroundAtmosphere = false;
        v.scene.backgroundColor = Color.fromCssColorString('#020617');

        const theaterRect = Rectangle.fromDegrees(map.minLon, map.minLat, map.maxLon, map.maxLat);
        v.scene.globe.cartographicLimitRectangle = theaterRect;
        v.scene.screenSpaceCameraController.minimumZoomDistance = 40.0;
        v.scene.screenSpaceCameraController.maximumZoomDistance = Math.max(map.sizeKm * 1800.0, 30000.0);

        // Theater Boundary Polyline
        v.entities.add({
            name: 'Theater Boundary Outline',
            polyline: {
                positions: [
                    Cartesian3.fromDegrees(map.minLon, map.minLat),
                    Cartesian3.fromDegrees(map.maxLon, map.minLat),
                    Cartesian3.fromDegrees(map.maxLon, map.maxLat),
                    Cartesian3.fromDegrees(map.minLon, map.maxLat),
                    Cartesian3.fromDegrees(map.minLon, map.minLat)
                ],
                width: 3,
                material: Color.fromCssColorString('#00a8ff'),
                clampToGround: true
            }
        });

        // Rubber Band Polyline preview for Linear Sculpting
        v.entities.add({
            name: 'Linear Trench Preview',
            polyline: {
                positions: new CallbackProperty(() => {
                    if (lineStartCartesian.value && currentMouseCartesian.value) {
                        return [lineStartCartesian.value, currentMouseCartesian.value];
                    }
                    return [];
                }, false),
                width: 4,
                material: Color.fromCssColorString('#ef4444'),
                clampToGround: true
            }
        });

        viewer.value = v;
        fitCamera(false);
    };

    const fitCamera = (is3d: boolean) => {
        if (!viewer.value) return;
        is3DMode.value = is3d;
        const v = viewer.value;

        const theaterRect = Rectangle.fromDegrees(map.minLon, map.minLat, map.maxLon, map.maxLat);

        if (!is3d) {
            // 2D MODE: Pure flat plane without elevation load
            v.terrainProvider = new EllipsoidTerrainProvider();
            v.scene.screenSpaceCameraController.enableTilt = false;
            v.camera.flyTo({
                destination: theaterRect,
                orientation: { heading: 0, pitch: CesiumMath.toRadians(-90), roll: 0 },
                duration: 0.8
            });
        } else {
            // 3D MODE: Mount DEM heightmap
            v.terrainProvider = createLocalTerrainProvider();
            v.scene.screenSpaceCameraController.enableTilt = true;
            v.camera.flyTo({
                destination: Cartesian3.fromDegrees(
                    map.centerLon,
                    map.centerLat - (map.sizeKm / 111.0) * 0.35,
                    map.sizeKm * 850
                ),
                orientation: { heading: 0, pitch: CesiumMath.toRadians(-35), roll: 0 },
                duration: 1.0
            });
        }
    };

    const mountBaseLayer = (layerType: string) => {
        if (!viewer.value) return;
        const v = viewer.value;
        if (currentImageryLayer) {
            v.imageryLayers.remove(currentImageryLayer);
            currentImageryLayer = null;
        }

        const tileUrl = `/api/maps/${map.id}/tiles/${layerType.toLowerCase()}/{z}/{x}/{y}.png`;
        const provider = new UrlTemplateImageryProvider({
            url: tileUrl,
            rectangle: Rectangle.fromDegrees(map.minLon, map.minLat, map.maxLon, map.maxLat),
            minimumLevel: 8,
            maximumLevel: 19
        });

        currentImageryLayer = v.imageryLayers.addImageryProvider(provider);
    };

    const applyLayerStack = () => {
        if (!viewer.value) return;
        const v = viewer.value;

        // 1. Elevation Layer Hypsometric Tint
        if (layerStack.elevation && !layerStack.texture) {
            v.scene.globe.material = createElevationBandMaterial({
                scene: v.scene,
                layers: [{
                    entries: [
                        { height: 0.0, color: Color.fromCssColorString('#022c22') },
                        { height: 90.0, color: Color.fromCssColorString('#047857') },
                        { height: 130.0, color: Color.fromCssColorString('#059669') },
                        { height: 200.0, color: Color.fromCssColorString('#10b981') },
                        { height: 300.0, color: Color.fromCssColorString('#84cc16') },
                        { height: 450.0, color: Color.fromCssColorString('#facc15') },
                        { height: 650.0, color: Color.fromCssColorString('#f97316') },
                        { height: 900.0, color: Color.fromCssColorString('#dc2626') },
                        { height: 1200.0, color: Color.fromCssColorString('#78350f') },
                        { height: 1600.0, color: Color.fromCssColorString('#f8fafc') }
                    ]
                }]
            });
        } else {
            v.scene.globe.material = undefined as unknown as Material;
        }

        // 2. Texture Layer
        if (layerStack.texture) {
            mountBaseLayer(currentLayerType.value);
        } else if (currentImageryLayer) {
            v.imageryLayers.remove(currentImageryLayer);
            currentImageryLayer = null;
        }

        // 3. Objects Layer
        if (vectorDataSource) {
            vectorDataSource.show = layerStack.objects;
        }
    };

    const applyEntityStyling = (entity: Entity) => {
        const category = entity.properties?.category?.getValue();
        const status = entity.properties?.status?.getValue() as FeatureStatus | undefined;
        const typeKey = entity.properties?.typeKey?.getValue();
        const typeValue = entity.properties?.typeValue?.getValue();

        if (category === 'ROAD' && entity.polyline) {
            if (typeKey === 'railway') {
                entity.polyline.material = MAT_RAILWAY;
                entity.polyline.width = CONST_WIDTH_RAILWAY;
                return;
            }
            if (status === 'DESTROYED') entity.polyline.material = MAT_ROAD_DESTROYED;
            else if (status === 'MINED') entity.polyline.material = MAT_ROAD_MINED;
            else if (status === 'CHECKPOINT') {
                entity.polyline.material = MAT_ROAD_CHECKPOINT;
                entity.polyline.width = CONST_WIDTH_CHECKPOINT;
                return;
            } else {
                entity.polyline.material = MAT_ROAD;
            }
            entity.polyline.width = CONST_WIDTH_ROAD;
            return;
        }

        if (category === 'WATER') {
            if (entity.polyline) {
                entity.polyline.material = MAT_WATER_LINE;
                entity.polyline.width = CONST_WIDTH_STREAM;
            } else if (entity.polygon) {
                entity.polygon.material = MAT_WATER_POLYGON;
                entity.polygon.outline = CONST_OUTLINE_FALSE as any;
            }
            return;
        }

        if (category === 'VEGETATION' && entity.polygon) {
            entity.polygon.material = (typeValue === 'wood' || typeValue === 'forest') ? MAT_FOREST : MAT_MEADOW;
            entity.polygon.outline = CONST_OUTLINE_FALSE as any;
            return;
        }

        if (category === 'BUILDING' && entity.polygon) {
            entity.polygon.material = (typeValue === 'residential' || typeValue === 'industrial') ? MAT_SETTLEMENT : MAT_BUILDING;
            entity.polygon.outline = CONST_OUTLINE_FALSE as any;
            return;
        }

        if (entity.polygon) {
            entity.polygon.material = MAT_SOIL;
            entity.polygon.outline = CONST_OUTLINE_FALSE as any;
        }
    };

    const loadVectors = async (geoJson: any) => {
        if (!viewer.value) return;
        vectorDataSource = await GeoJsonDataSource.load(geoJson, { clampToGround: true });
        for (const entity of vectorDataSource.entities.values) {
            applyEntityStyling(entity);
        }
        viewer.value.dataSources.add(vectorDataSource);
    };

    const destroyViewer = () => {
        if (viewer.value) {
            viewer.value.destroy();
            viewer.value = null;
        }
    };

    return {
        viewer,
        is3DMode,
        centerAltitudeDisplay,
        currentLayerType,
        layerStack,
        terrainScale,
        lineStartCartesian,
        currentMouseCartesian,
        initViewer,
        fitCamera,
        mountBaseLayer,
        applyLayerStack,
        applyEntityStyling,
        loadVectors,
        invalidateTerrainCache,
        destroyViewer
    };
}