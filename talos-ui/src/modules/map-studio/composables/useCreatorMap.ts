import { ref } from 'vue';
import {
    Viewer,
    Cartesian2,
    Cartesian3,
    Color,
    Rectangle,
    ArcGisMapServerImageryProvider,
    UrlTemplateImageryProvider,
    ImageryLayer,
    ScreenSpaceEventHandler,
    ScreenSpaceEventType,
    Cartographic,
    Math as CesiumMath,
    CallbackProperty,
    CallbackPositionProperty,
    HeightReference
} from 'cesium';
import type { MapCreationRequest } from '../types';

export type PreviewStyle = 'HYBRID' | 'TOPO' | 'VECTOR';
export type InteractionMode = 'CLICK_CENTER' | 'DRAG_BOX';

export function useCreatorMap(form: MapCreationRequest) {
    const mapContainer = ref<HTMLDivElement | null>(null);
    const interactionMode = ref<InteractionMode>('CLICK_CENTER');
    const activeStyle = ref<PreviewStyle>('HYBRID');

    let viewer: Viewer | null = null;
    let handler: ScreenSpaceEventHandler | null = null;
    let isDragging = false;
    let dragStartCarto: Cartographic | null = null;

    /**
     * Calculates WGS84 bounding box coordinates for a metric square.
     * Supports arbitrary dimensions from small tactical plots to entire countries.
     */
    const calculateBbox = (lat: number, lon: number, sizeKm: number) => {
        const halfMeters = (sizeKm * 1000.0) / 2.0;
        const dLat = halfMeters / 111132.95;
        const cosLat = Math.cos(CesiumMath.toRadians(lat));
        const dLon = halfMeters / (111132.95 * (Math.abs(cosLat) > 0.01 ? cosLat : 0.01));

        return {
            minLat: Math.max(-85.0, lat - dLat),
            maxLat: Math.min(85.0, lat + dLat),
            minLon: Math.max(-180.0, lon - dLon),
            maxLon: Math.min(180.0, lon + dLon)
        };
    };

    const initMap = async (container: HTMLDivElement) => {
        mapContainer.value = container;

        viewer = new Viewer(container, {
            baseLayer: false,
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

        await switchPreviewStyle('HYBRID');

        // Bounded Tactical Rectangle Overlay
        viewer.entities.add({
            name: 'Tactical Bounding Box',
            rectangle: {
                coordinates: new CallbackProperty(() => {
                    const b = calculateBbox(form.centerLat, form.centerLon, form.sizeKm);
                    return Rectangle.fromDegrees(b.minLon, b.minLat, b.maxLon, b.maxLat);
                }, false),
                material: Color.fromCssColorString('#00a8ff').withAlpha(0.2),
                outline: true,
                outlineColor: Color.fromCssColorString('#00e676'),
                outlineWidth: 3,
                heightReference: HeightReference.CLAMP_TO_GROUND
            }
        });

        // Center Crosshair Marker
        viewer.entities.add({
            name: 'Center Crosshair',
            position: new CallbackPositionProperty(() => {
                return Cartesian3.fromDegrees(form.centerLon, form.centerLat, 10.0);
            }, false),
            point: {
                pixelSize: 10,
                color: Color.RED,
                outlineColor: Color.WHITE,
                outlineWidth: 2,
                heightReference: HeightReference.CLAMP_TO_GROUND
            }
        });

        flyToCoordinates(form.centerLat, form.centerLon, form.sizeKm);
        setupMouseInteractions();
    };

    const switchPreviewStyle = async (style: PreviewStyle) => {
        if (!viewer) return;
        activeStyle.value = style;
        viewer.imageryLayers.removeAll();

        if (style === 'HYBRID') {
            const satProvider = await ArcGisMapServerImageryProvider.fromUrl(
                'https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer',
                { enablePickFeatures: false }
            );
            viewer.imageryLayers.add(new ImageryLayer(satProvider));

            const labelsProvider = await ArcGisMapServerImageryProvider.fromUrl(
                'https://services.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer',
                { enablePickFeatures: false }
            );
            viewer.imageryLayers.add(new ImageryLayer(labelsProvider));
        } else if (style === 'TOPO') {
            const topoProvider = new UrlTemplateImageryProvider({
                url: 'https://tile.opentopomap.org/{z}/{x}/{y}.png'
            });
            viewer.imageryLayers.add(new ImageryLayer(topoProvider));
        } else if (style === 'VECTOR') {
            const vectorProvider = new UrlTemplateImageryProvider({
                url: 'https://basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png'
            });
            viewer.imageryLayers.add(new ImageryLayer(vectorProvider));
        }
    };

    const setupMouseInteractions = () => {
        if (!viewer) return;
        handler = new ScreenSpaceEventHandler(viewer.scene.canvas);

        // Click to set center
        handler.setInputAction((event: { position: Cartesian2 }) => {
            if (interactionMode.value !== 'CLICK_CENTER') return;

            const ray = viewer!.camera.getPickRay(event.position);
            if (!ray) return;
            const cartesian = viewer!.scene.globe.pick(ray, viewer!.scene);
            if (!cartesian) return;

            const carto = Cartographic.fromCartesian(cartesian);
            form.centerLat = Number(CesiumMath.toDegrees(carto.latitude).toFixed(4));
            form.centerLon = Number(CesiumMath.toDegrees(carto.longitude).toFixed(4));
        }, ScreenSpaceEventType.LEFT_CLICK);

        // Start drag-box
        handler.setInputAction((event: { position: Cartesian2 }) => {
            if (interactionMode.value !== 'DRAG_BOX') return;

            const ray = viewer!.camera.getPickRay(event.position);
            if (!ray) return;
            const cartesian = viewer!.scene.globe.pick(ray, viewer!.scene);
            if (!cartesian) return;

            dragStartCarto = Cartographic.fromCartesian(cartesian);
            isDragging = true;
            viewer!.scene.screenSpaceCameraController.enableInputs = false;
        }, ScreenSpaceEventType.LEFT_DOWN);

        // Dynamic square resizing without artificial upper ceiling
        handler.setInputAction((movement: { endPosition: Cartesian2 }) => {
            if (!isDragging || !dragStartCarto || interactionMode.value !== 'DRAG_BOX') return;

            const ray = viewer!.camera.getPickRay(movement.endPosition);
            if (!ray) return;
            const cartesian = viewer!.scene.globe.pick(ray, viewer!.scene);
            if (!cartesian) return;

            const currentCarto = Cartographic.fromCartesian(cartesian);
            const lat1 = CesiumMath.toDegrees(dragStartCarto.latitude);
            const lon1 = CesiumMath.toDegrees(dragStartCarto.longitude);
            const lat2 = CesiumMath.toDegrees(currentCarto.latitude);
            const lon2 = CesiumMath.toDegrees(currentCarto.longitude);

            const centerLat = (lat1 + lat2) / 2.0;
            const centerLon = (lon1 + lon2) / 2.0;

            const distLatKm = Math.abs(lat2 - lat1) * 111.13;
            const distLonKm = Math.abs(lon2 - lon1) * 111.13 * Math.cos(CesiumMath.toRadians(centerLat));
            const maxSideKm = Math.max(distLatKm, distLonKm);

            form.centerLat = Number(centerLat.toFixed(4));
            form.centerLon = Number(centerLon.toFixed(4));
            // Removed upper cap (can now span country-sized boxes)
            form.sizeKm = Math.max(5, Math.round(maxSideKm));
        }, ScreenSpaceEventType.MOUSE_MOVE);

        // Release drag
        handler.setInputAction(() => {
            if (isDragging) {
                isDragging = false;
                dragStartCarto = null;
                viewer!.scene.screenSpaceCameraController.enableInputs = true;
            }
        }, ScreenSpaceEventType.LEFT_UP);
    };

    /**
     * Smoothly positions camera view scaled to cover the entire square theater.
     */
    const flyToCoordinates = (lat: number, lon: number, sizeKm: number) => {
        if (!viewer) return;
        const b = calculateBbox(lat, lon, sizeKm);
        const rect = Rectangle.fromDegrees(b.minLon, b.minLat, b.maxLon, b.maxLat);

        viewer.camera.flyTo({
            destination: rect,
            duration: 1.0
        });
    };

    const destroyMap = () => {
        if (handler) {
            handler.destroy();
            handler = null;
        }
        if (viewer) {
            viewer.destroy();
            viewer = null;
        }
    };

    return {
        mapContainer,
        interactionMode,
        activeStyle,
        initMap,
        switchPreviewStyle,
        flyToCoordinates,
        destroyMap
    };
}