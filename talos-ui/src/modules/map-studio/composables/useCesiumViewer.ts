// useCesiumViewer.ts
// Military simulator viewer configuration with atmospheric fog, tactical soil base, and sun shading

import { ref, shallowRef } from 'vue';
import {
    Viewer,
    Rectangle,
    Color,
    Math as CesiumMath,
    Cartesian3,
    EllipsoidTerrainProvider,
    DirectionalLight,
    ShadowMode
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
            creditContainer: document.createElement('div'),
            orderIndependentTranslucency: false,
            terrainProvider: new EllipsoidTerrainProvider(),
            shadows: true,
            terrainShadows: ShadowMode.RECEIVE_ONLY,
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
            skyBox: false,
            requestRenderMode: true,
            maximumRenderTimeChange: Infinity
        });

        (v.scene as unknown as { verticalExaggeration: number }).verticalExaggeration = terrainScale.value;
        v.scene.globe.enableLighting = false;

        // TACTICAL VISUAL OVERHAUL 1: Natural military soil terrain base (replaces sterile bright yellow sand)
        v.scene.globe.baseColor = Color.fromCssColorString('#545744');
        v.scene.globe.depthTestAgainstTerrain = true;
        v.scene.backgroundColor = Color.fromCssColorString('#0b132b');

        // TACTICAL VISUAL OVERHAUL 2: Atmospheric Depth Fog (Steel Beasts horizon blending)
        v.scene.fog.enabled = true;
        v.scene.fog.density = 0.00035; // Soft natural haze across distant horizons
        v.scene.fog.screenSpaceErrorFactor = 2.0;
        v.scene.globe.showGroundAtmosphere = true;

        // TACTICAL VISUAL OVERHAUL 3: Angled directional sun lighting (contrasts roofs vs vertical facades)
        v.scene.light = new DirectionalLight({
            direction: new Cartesian3(-0.6, -0.5, -0.65),
            intensity: 1.5
        });

        v.scene.screenSpaceCameraController.minimumZoomDistance = 15.0;
        v.scene.screenSpaceCameraController.maximumZoomDistance = Math.max(map.sizeKm * 2500.0, 50000.0);

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
                    map.centerLat - (map.sizeKm / 111.0) * 0.45,
                    map.sizeKm * 950
                ),
                orientation: { heading: 0, pitch: CesiumMath.toRadians(-35), roll: 0 },
                duration: 0.8
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