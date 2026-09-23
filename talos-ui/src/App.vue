<template>
    <div class="app-root">
        <!-- Quick switcher between Map Studio and Tactical C2 Simulation -->
        <header class="quick-nav" role="navigation" aria-label="Module Switcher">
            <button
                class="nav-tab"
                :class="{ active: currentModule === 'maps' }"
                type="button"
                @click="currentModule = 'maps'"
            >
                КАРТИ ТА ТВД
            </button>
            <button
                class="nav-tab"
                :class="{ active: currentModule === 'sim' }"
                type="button"
                @click="currentModule = 'sim'"
            >
                СИМУЛЯЦІЯ (C2)
            </button>
        </header>

        <!-- Main Module Display -->
        <main class="module-container">
            <MapStudioView
                v-if="currentModule === 'maps'"
                @switch-to-sim="currentModule = 'sim'"
            />
            <TacticalMap
                v-else-if="currentModule === 'sim'"
            />
        </main>
    </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import MapStudioView from '@/modules/map-studio/views/MapStudioView.vue';
import TacticalMap from '@/modules/simulation-c2/components/TacticalMap.vue';

// Active functional module state
const currentModule = ref<'maps' | 'sim'>('maps');
</script>

<style scoped>
.app-root {
    width: 100vw;
    height: 100vh;
    overflow: hidden;
    position: relative;
    background-color: #0b0f19;
}

.module-container {
    width: 100%;
    height: 100%;
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
    backdrop-filter: blur(4px);
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
    transition: all 0.15s ease-in-out;
}

.nav-tab:hover {
    color: #ffffff;
}

.nav-tab.active {
    background: #0284c7;
    color: #ffffff;
}
</style>