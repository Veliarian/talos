import { ref, type ShallowRef } from 'vue';
import {
    Viewer,
    UrlTemplateImageryProvider,
    ImageryLayer,
    Rectangle
} from 'cesium';
import type { MapDetailDto } from '../types';

export function useBasemapLayers(viewer: ShallowRef<Viewer | null>, map: MapDetailDto) {
    const currentLayerType = ref<string>(map.layers?.[0]?.layerType || 'SATELLITE');
    const isBasemapVisible = ref(true);

    const elevationOpacity = ref(0.0);
    const objectsOpacity = ref(0.0);

    let currentImageryLayer: ImageryLayer | null = null;

    const mountBaseLayer = (layerType: string) => {
        if (!viewer.value) return;
        const v = viewer.value;
        if (currentImageryLayer) {
            v.imageryLayers.remove(currentImageryLayer);
            currentImageryLayer = null;
        }

        currentLayerType.value = layerType;
        const tileUrl = `/api/maps/${map.id}/tiles/${layerType.toLowerCase()}/{z}/{x}/{y}.png`;

        const provider = new UrlTemplateImageryProvider({
            url: tileUrl,
            rectangle: Rectangle.fromDegrees(map.minLon, map.minLat, map.maxLon, map.maxLat),
            minimumLevel: 8,
            maximumLevel: 19
        });

        currentImageryLayer = v.imageryLayers.addImageryProvider(provider);
        updateImageryAppearance('ELEVATION');
    };

    const updateImageryAppearance = (activeMode: 'ELEVATION' | 'OBJECTS') => {
        if (!currentImageryLayer) return;

        if (activeMode === 'ELEVATION') {
            // When in elevation mode, satellite is visible underneath if opacity > 0
            currentImageryLayer.show = isBasemapVisible.value && elevationOpacity.value > 0.01;
            currentImageryLayer.alpha = 1.0;
        } else {
            // In objects mode, opacity controls direct layer alpha over the sand base
            currentImageryLayer.show = isBasemapVisible.value && objectsOpacity.value > 0.01;
            currentImageryLayer.alpha = objectsOpacity.value;
        }
    };

    return {
        currentLayerType,
        isBasemapVisible,
        elevationOpacity,
        objectsOpacity,
        mountBaseLayer,
        updateImageryAppearance
    };
}