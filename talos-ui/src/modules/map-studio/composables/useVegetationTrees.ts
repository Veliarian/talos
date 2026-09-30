// useVegetationTrees.ts
// Optimized tactical 3D tree instancing with camera move debounce to completely eliminate GC stutter

import { type ShallowRef } from 'vue';
import {
    Viewer,
    CustomDataSource,
    Cartesian2,
    Cartesian3,
    Cartographic,
    HeadingPitchRoll,
    Transforms,
    Math as CesiumMath,
    ShadowMode,
    HeightReference,
    DistanceDisplayCondition
} from 'cesium';
import { generateSectorTreePoints, extractBuildingObstacles, isTreeVegetation, type TreeInstancePoint } from './forestGenerator';

export function useVegetationTrees(viewer: ShallowRef<Viewer | null>) {
    let treeDataSource: CustomDataSource | null = null;
    let cameraMoveRemoveCallback: (() => void) | null = null;
    let debounceTimer: ReturnType<typeof setTimeout> | null = null;

    let lastCenterLon = 0;
    let lastCenterLat = 0;
    let cachedGeoJson: any = null;
    let isProcessing = false;

    // Tactical bubble settings: 550m radius around unit gives full visual density with zero WebGL lag
    const SECTOR_RADIUS_METERS = 550.0;
    const SECTOR_RADIUS_DEG = SECTOR_RADIUS_METERS / 111000.0;
    const MOVE_THRESHOLD_METERS = 220.0;
    const LOD_3D_MODELS = new DistanceDisplayCondition(0.0, 1100.0);

    // Hard ceiling of 200 rich 3D models per active sector sustains constant 60 FPS
    const MAX_SECTOR_3D_TREES = 200;

    const MODEL_URI_DECIDUOUS = '/models/trees/oak.glb';
    const MODEL_URI_PINE = '/models/trees/pine.glb';

    const getGroundCenter = (): { lon: number; lat: number } | null => {
        if (!viewer.value) return null;
        const scene = viewer.value.scene;
        const camera = scene.camera;

        const canvasWidth = scene.canvas.clientWidth;
        const canvasHeight = scene.canvas.clientHeight;

        let ray = camera.getPickRay(new Cartesian2(canvasWidth / 2, canvasHeight / 2));
        let groundCartesian = ray ? scene.globe.pick(ray, scene) : undefined;

        if (!groundCartesian) {
            ray = camera.getPickRay(new Cartesian2(canvasWidth / 2, canvasHeight * 0.75));
            if (ray) groundCartesian = scene.globe.pick(ray, scene);
        }

        if (!groundCartesian) {
            const camCarto = camera.positionCartographic;
            if (camCarto) {
                return {
                    lon: (camCarto.longitude * 180.0) / Math.PI,
                    lat: (camCarto.latitude * 180.0) / Math.PI
                };
            }
            return null;
        }

        const targetCarto = Cartographic.fromCartesian(groundCartesian);
        return {
            lon: (targetCarto.longitude * 180.0) / Math.PI,
            lat: (targetCarto.latitude * 180.0) / Math.PI
        };
    };

    const updateSectorTrees = (force = false) => {
        if (!viewer.value || !cachedGeoJson?.features || isProcessing) return;

        const center = getGroundCenter();
        if (!center) return;

        if (!force) {
            const dLonM = (center.lon - lastCenterLon) * 111000 * Math.cos((center.lat * Math.PI) / 180);
            const dLatM = (center.lat - lastCenterLat) * 111000;
            const dist = Math.sqrt(dLonM * dLonM + dLatM * dLatM);
            if (dist < MOVE_THRESHOLD_METERS) return;
        }

        isProcessing = true;
        lastCenterLon = center.lon;
        lastCenterLat = center.lat;

        if (!treeDataSource && viewer.value) {
            treeDataSource = new CustomDataSource('talos-tactical-3d-trees');
            viewer.value.dataSources.add(treeDataSource);
        }

        if (treeDataSource) {
            treeDataSource.entities.removeAll();
        }

        const buildingObstacles = extractBuildingObstacles(
            cachedGeoJson.features,
            center.lon,
            center.lat,
            SECTOR_RADIUS_DEG
        );

        const forestFeatures = cachedGeoJson.features.filter((f: any) => {
            const props = f.properties || {};
            return props.category === 'VEGETATION' && isTreeVegetation(props.typeValue);
        });

        let totalTreeCount = 0;

        for (const feature of forestFeatures) {
            if (totalTreeCount >= MAX_SECTOR_3D_TREES) break;

            const props = feature.properties || {};
            const density = props.densityPercent || 75;
            const osmValue = props.typeValue || 'wood';

            const points: TreeInstancePoint[] = generateSectorTreePoints(
                feature.geometry,
                center.lon,
                center.lat,
                SECTOR_RADIUS_DEG,
                density,
                osmValue,
                150,
                buildingObstacles
            );

            for (const pt of points) {
                if (totalTreeCount >= MAX_SECTOR_3D_TREES) break;

                const position = Cartesian3.fromDegrees(pt.lon, pt.lat);
                const randomHeading = Math.random() * 360.0;
                const hpr = new HeadingPitchRoll(CesiumMath.toRadians(randomHeading), 0.0, 0.0);
                const orientation = Transforms.headingPitchRollQuaternion(position, hpr);

                const modelUrl = pt.treeType === 'PINE' ? MODEL_URI_PINE : MODEL_URI_DECIDUOUS;

                treeDataSource?.entities.add({
                    position: position,
                    orientation: orientation,
                    model: {
                        uri: modelUrl,
                        scale: pt.scale,
                        shadows: ShadowMode.ENABLED,
                        heightReference: HeightReference.CLAMP_TO_GROUND,
                        enableVerticalExaggeration: false as any,
                        distanceDisplayCondition: LOD_3D_MODELS
                    }
                });

                totalTreeCount++;
            }
        }

        viewer.value?.scene.requestRender();
        isProcessing = false;
    };

    const buildTreeCollection = (geoJson: any, is3D: boolean) => {
        cachedGeoJson = geoJson;
        destroyTrees();

        if (!is3D || !viewer.value) return;

        updateSectorTrees(true);

        // Smooth camera debounce: only trigger sector rebuild after camera movement has rested for 350ms
        if (!cameraMoveRemoveCallback && viewer.value) {
            cameraMoveRemoveCallback = viewer.value.camera.moveEnd.addEventListener(() => {
                if (debounceTimer) clearTimeout(debounceTimer);
                debounceTimer = setTimeout(() => {
                    updateSectorTrees(false);
                }, 350);
            });
        }
    };

    const setTreesVisible = (visible: boolean) => {
        if (treeDataSource) {
            treeDataSource.show = visible;
            viewer.value?.scene.requestRender();
        }
    };

    const destroyTrees = () => {
        if (debounceTimer) {
            clearTimeout(debounceTimer);
            debounceTimer = null;
        }
        if (cameraMoveRemoveCallback) {
            cameraMoveRemoveCallback();
            cameraMoveRemoveCallback = null;
        }
        if (treeDataSource && viewer.value) {
            treeDataSource.entities.removeAll();
            viewer.value.dataSources.remove(treeDataSource);
            treeDataSource = null;
        }
        isProcessing = false;
    };

    return {
        buildTreeCollection,
        setTreesVisible,
        destroyTrees
    };
}