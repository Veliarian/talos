import { ref, type ShallowRef } from 'vue';
import {
    Viewer,
    Cartesian3,
    Cartographic,
    Math as CesiumMath,
    ScreenSpaceEventHandler,
    ScreenSpaceEventType
} from 'cesium';

export function useSurfaceCamera(viewer: ShallowRef<Viewer | null>) {
    const isGroundMode = ref(false);
    const eyeHeightMeters = 2.0; // Eye-level view (soldier: 1.8m, APC commander: 2.8m)
    const baseSpeedMps = 15.0;   // 15 m/s normal walking/driving
    const sprintSpeedMps = 45.0; // 45 m/s sprint / fast vehicle

    const keyState = {
        forward: false,
        backward: false,
        left: false,
        right: false,
        sprint: false
    };

    let tickListener: (() => void) | null = null;
    let mouseLookHandler: ScreenSpaceEventHandler | null = null;
    let isLooking = false;
    let startMousePosition = { x: 0, y: 0 };

    const onKeyDown = (e: KeyboardEvent) => {
        if (!isGroundMode.value) return;
        const key = e.code;
        if (key === 'KeyW' || key === 'ArrowUp') keyState.forward = true;
        if (key === 'KeyS' || key === 'ArrowDown') keyState.backward = true;
        if (key === 'KeyA' || key === 'ArrowLeft') keyState.left = true;
        if (key === 'KeyD' || key === 'ArrowRight') keyState.right = true;
        if (key === 'ShiftLeft' || key === 'ShiftRight') keyState.sprint = true;
    };

    const onKeyUp = (e: KeyboardEvent) => {
        const key = e.code;
        if (key === 'KeyW' || key === 'ArrowUp') keyState.forward = false;
        if (key === 'KeyS' || key === 'ArrowDown') keyState.backward = false;
        if (key === 'KeyA' || key === 'ArrowLeft') keyState.left = false;
        if (key === 'KeyD' || key === 'ArrowRight') keyState.right = false;
        if (key === 'ShiftLeft' || key === 'ShiftRight') keyState.sprint = false;
    };

    const toggleGroundMode = () => {
        if (!viewer.value) return;
        isGroundMode.value = !isGroundMode.value;

        if (isGroundMode.value) {
            enableGroundMode();
        } else {
            disableGroundMode();
        }
    };

    const enableGroundMode = () => {
        const v = viewer.value;
        if (!v) return;

        // Disable standard Cesium orbit controls so they don't fight WASD
        v.scene.screenSpaceCameraController.enableRotate = false;
        v.scene.screenSpaceCameraController.enableTranslate = false;
        v.scene.screenSpaceCameraController.enableZoom = false;
        v.scene.screenSpaceCameraController.enableTilt = false;

        window.addEventListener('keydown', onKeyDown);
        window.addEventListener('keyup', onKeyUp);

        // Snap camera immediately to eye-level above ground
        clampCameraToGround();

        // Mouse Look Handler
        mouseLookHandler = new ScreenSpaceEventHandler(v.scene.canvas);
        mouseLookHandler.setInputAction((movement: { position: { x: number; y: number } }) => {
            isLooking = true;
            startMousePosition = { ...movement.position };
        }, ScreenSpaceEventType.RIGHT_DOWN);

        mouseLookHandler.setInputAction((movement: { endPosition: { x: number; y: number } }) => {
            if (!isLooking || !v) return;
            const dx = movement.endPosition.x - startMousePosition.x;
            const dy = movement.endPosition.y - startMousePosition.y;
            startMousePosition = { ...movement.endPosition };

            v.camera.lookRight(CesiumMath.toRadians(dx * 0.15));
            v.camera.lookUp(CesiumMath.toRadians(-dy * 0.15));
        }, ScreenSpaceEventType.MOUSE_MOVE);

        mouseLookHandler.setInputAction(() => {
            isLooking = false;
        }, ScreenSpaceEventType.RIGHT_UP);

        // Pre-render loop for smooth WASD movement
        let lastTimestamp = performance.now();
        tickListener = () => {
            const now = performance.now();
            const dt = (now - lastTimestamp) / 1000.0;
            lastTimestamp = now;

            if (isGroundMode.value && v) {
                updateCameraPosition(dt);
            }
        };

        v.scene.preRender.addEventListener(tickListener);
    };

    const disableGroundMode = () => {
        const v = viewer.value;
        if (!v) return;

        v.scene.screenSpaceCameraController.enableRotate = true;
        v.scene.screenSpaceCameraController.enableTranslate = true;
        v.scene.screenSpaceCameraController.enableZoom = true;
        v.scene.screenSpaceCameraController.enableTilt = true;

        window.removeEventListener('keydown', onKeyDown);
        window.removeEventListener('keyup', onKeyUp);

        if (mouseLookHandler) {
            mouseLookHandler.destroy();
            mouseLookHandler = null;
        }

        if (tickListener) {
            v.scene.preRender.removeEventListener(tickListener);
            tickListener = null;
        }
    };

    const updateCameraPosition = (dt: number) => {
        const v = viewer.value;
        if (!v) return;

        const speed = (keyState.sprint ? sprintSpeedMps : baseSpeedMps) * dt;

        if (keyState.forward) v.camera.moveForward(speed);
        if (keyState.backward) v.camera.moveBackward(speed);
        if (keyState.left) v.camera.moveLeft(speed);
        if (keyState.right) v.camera.moveRight(speed);

        clampCameraToGround();
    };

    /**
     * Hardware Ground Clamping: locks camera height strictly to (ground elevation + eye height).
     */
    const clampCameraToGround = () => {
        const v = viewer.value;
        if (!v) return;

        const carto = Cartographic.fromCartesian(v.camera.position);
        const groundHeight = v.scene.globe.getHeight(carto) || 120.0;
        const targetAltitude = groundHeight + eyeHeightMeters;

        v.camera.position = Cartesian3.fromRadians(carto.longitude, carto.latitude, targetAltitude);
    };

    return {
        isGroundMode,
        toggleGroundMode,
        disableGroundMode
    };
}