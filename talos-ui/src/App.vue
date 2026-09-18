<template>
    <div class="app-root">
        <!-- Quick switcher between Map Studio and Live Simulation -->
        <div class="quick-nav">
            <button
                class="nav-tab"
                :class="{ active: currentModule === 'maps' }"
                @click="currentModule = 'maps'"
            >
                КАРТИ ТА ТВД
            </button>
            <button
                class="nav-tab"
                :class="{ active: currentModule === 'sim' }"
                @click="currentModule = 'sim'"
            >
                СИМУЛЯЦІЯ (C2)
            </button>
        </div>

        <!-- Module Views -->
        <MapStudioView
            v-if="currentModule === 'maps'"
            @switch-to-sim="currentModule = 'sim'"
        />
        <TacticalMap
            v-else-if="currentModule === 'sim'"
        />
    </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import MapStudioView from './modules/map-studio/views/MapStudioView.vue';
import TacticalMap from './components/TacticalMap.vue';

// Active system module
const currentModule = ref<'maps' | 'sim'>('maps');
</script>

<style>
.app-root {
    width: 100vw;
    height: 100vh;
    overflow: hidden;
    position: relative;
}
.quick-nav {
    position: fixed;
    top: 10px;
    right: 20px;
    z-index: 99999;
    display: flex;
    gap: 6px;
    background: rgba(15, 23, 42, 0.9);
    padding: 4px;
    border: 1px solid rgba(0, 168, 255, 0.4);
    border-radius: 4px;
}
.nav-tab {
    background: transparent;
    border: none;
    color: #94a3b8;
    padding: 5px 12px;
    font-size: 11px;
    font-family: monospace;
    cursor: pointer;
    border-radius: 3px;
    font-weight: bold;
}
.nav-tab:hover {
    color: #fff;
}
.nav-tab.active {
    background: #0284c7;
    color: #fff;
}
</style>