import { ref, shallowRef } from 'vue';
import {
    Viewer,
    Rectangle,
    Color,
    Math as CesiumMath,
    Cartesian3,
    EllipsoidTerrainProvider
} from 'cesium';
import type { MapDetailDto } from '../types';

export function useCesiumViewer(map: MapDetailDto) {
    const viewer = shallowRef<Viewer | null>(null);
    const is3DMode = ref(false);
    const terrainScale = ref(1.5);

    const initViewerInstance = (container: HTMLDivElement): Viewer => {
        if (viewer.value) {
            viewer.value.destroy();
            viewer.value = null;
        }

        const v = new Viewer(container, {
            baseLayer: false,
            creditContainer: document.createElement('div'), // Hide Cesium ion logo
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
        v.scene.globe.baseColor = Color.fromCssColorString('#d4c5a9'); // Tactical Sand Base
        v.scene.globe.depthTestAgainstTerrain = true;
        if (v.scene.skyAtmosphere) v.scene.skyAtmosphere.show = false;
        v.scene.globe.showGroundAtmosphere = false;
        v.scene.backgroundColor = Color.fromCssColorString('#020617');

        const theaterRect = Rectangle.fromDegrees(map.minLon, map.minLat, map.maxLon, map.maxLat);
        v.scene.globe.cartographicLimitRectangle = theaterRect;
        v.scene.screenSpaceCameraController.minimumZoomDistance = 40.0;
        v.scene.screenSpaceCameraController.maximumZoomDistance = Math.max(map.sizeKm * 1800.0, 30000.0);

        // Boundary outline
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

        viewer.value = v;
        return v;
    };

    const fitCamera = (is3d: boolean) => {
        if (!viewer.value) return;
        is3DMode.value = is3d;
        const v = viewer.value;
        const theaterRect = Rectangle.fromDegrees(map.minLon, map.minLat, map.maxLon, map.maxLat);

        if (!is3d) {
            v.scene.screenSpaceCameraController.enableTilt = false;
            v.camera.flyTo({
                destination: theaterRect,
                orientation: { heading: 0, pitch: CesiumMath.toRadians(-90), roll: 0 },
                duration: 0.8
            });
        } else {
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

    const destroyViewer = () => {
        if (viewer.value) {
            viewer.value.destroy();
            viewer.value = null;
        }
    };

    return {
        viewer,
        is3DMode,
        terrainScale,
        initViewerInstance,
        fitCamera,
        destroyViewer
    };
}