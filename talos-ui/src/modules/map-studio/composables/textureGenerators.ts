/**
 * Procedural texture and billboard generator for realistic 3D buildings and trees.
 * Generates lightweight canvas textures in-memory with zero network overhead.
 */

// Cache generated canvas data URLs to keep rendering instant
const textureCache = new Map<string, string>();

/**
 * Generates an architectural building facade with floors, window rows, and foundation.
 */
export function getBuildingFacadeTexture(materialType: string, baseColorHex: string): string {
    const cacheKey = `facade_${materialType}_${baseColorHex}`;
    if (textureCache.has(cacheKey)) return textureCache.get(cacheKey)!;

    const canvas = document.createElement('canvas');
    canvas.width = 128;
    canvas.height = 128;
    const ctx = canvas.getContext('2d')!;

    // 1. Base Wall Color
    ctx.fillStyle = baseColorHex || '#cbd5e1';
    ctx.fillRect(0, 0, 128, 128);

    // 2. Subtle architectural panel lines
    ctx.strokeStyle = 'rgba(0, 0, 0, 0.2)';
    ctx.lineWidth = 1;
    ctx.strokeRect(0, 0, 128, 128);

    // 3. Realistic Window Grid (2 floors x 4 windows per 128px tile)
    ctx.fillStyle = '#0f172a'; // Dark glass reflection
    const windowW = 16;
    const windowH = 24;

    for (let floor = 0; floor < 2; floor++) {
        const y = 18 + floor * 54;
        for (let col = 0; col < 4; col++) {
            const x = 12 + col * 28;

            // Window frame
            ctx.fillStyle = 'rgba(255, 255, 255, 0.4)';
            ctx.fillRect(x - 1, y - 1, windowW + 2, windowH + 2);

            // Window glass
            ctx.fillStyle = '#1e293b';
            ctx.fillRect(x, y, windowW, windowH);

            // Glass reflection highlight
            ctx.fillStyle = 'rgba(56, 189, 248, 0.35)';
            ctx.beginPath();
            ctx.moveTo(x, y);
            ctx.lineTo(x + windowW, y + windowH * 0.6);
            ctx.lineTo(x, y + windowH * 0.6);
            ctx.fill();
        }
    }

    const dataUrl = canvas.toDataURL('image/png');
    textureCache.set(cacheKey, dataUrl);
    return dataUrl;
}

/**
 * Generates realistic 3D tree billboards with visible wooden trunks, branches, and leafy foliage.
 */
export function getTreeBillboardTexture(treeType: 'PINE' | 'DECIDUOUS'): string {
    const cacheKey = `tree_${treeType}`;
    if (textureCache.has(cacheKey)) return textureCache.get(cacheKey)!;

    const canvas = document.createElement('canvas');
    canvas.width = 128;
    canvas.height = 192;
    const ctx = canvas.getContext('2d')!;

    if (treeType === 'PINE') {
        // --- CONIFER / PINE TREE (Сосна / Ялина) ---
        // 1. Brown wooden trunk with bark texture
        ctx.fillStyle = '#451a03';
        ctx.beginPath();
        ctx.moveTo(58, 192);
        ctx.lineTo(70, 192);
        ctx.lineTo(67, 30);
        ctx.lineTo(61, 30);
        ctx.fill();

        // 2. Layered needle branches from bottom to top
        const tiers = [
            { y: 155, w: 90, h: 42, color: '#064e3b' },
            { y: 125, w: 78, h: 38, color: '#047857' },
            { y: 95,  w: 64, h: 34, color: '#059669' },
            { y: 65,  w: 48, h: 30, color: '#10b981' },
            { y: 35,  w: 30, h: 26, color: '#34d399' }
        ];

        for (const t of tiers) {
            ctx.fillStyle = t.color;
            ctx.beginPath();
            ctx.moveTo(64, t.y - t.h);
            ctx.lineTo(64 - t.w / 2, t.y);
            ctx.lineTo(64 + t.w / 2, t.y);
            ctx.closePath();
            ctx.fill();

            // Branch detail needles
            ctx.strokeStyle = 'rgba(0, 0, 0, 0.25)';
            ctx.lineWidth = 1.5;
            ctx.stroke();
        }
    } else {
        // --- DECIDUOUS TREE (Листяне дерево / Дуб / Береза) ---
        // 1. Gnarled trunk with spreading branches
        ctx.fillStyle = '#3f2212';
        ctx.beginPath();
        ctx.moveTo(56, 192);
        ctx.lineTo(72, 192);
        ctx.lineTo(68, 100);
        ctx.lineTo(60, 100);
        ctx.fill();

        // Spreading primary branches
        ctx.strokeStyle = '#3f2212';
        ctx.lineWidth = 6;
        ctx.beginPath();
        ctx.moveTo(64, 110);
        ctx.lineTo(38, 75);
        ctx.moveTo(64, 110);
        ctx.lineTo(90, 78);
        ctx.moveTo(64, 90);
        ctx.lineTo(64, 50);
        ctx.stroke();

        // 2. Organic foliage clusters (leaves)
        const clusters = [
            { x: 38, y: 70, r: 28, color: '#14532d' },
            { x: 90, y: 75, r: 26, color: '#15803d' },
            { x: 64, y: 55, r: 32, color: '#166534' },
            { x: 48, y: 40, r: 24, color: '#15803d' },
            { x: 78, y: 42, r: 22, color: '#22c55e' }
        ];

        for (const c of clusters) {
            ctx.fillStyle = c.color;
            ctx.beginPath();
            ctx.arc(c.x, c.y, c.r, 0, Math.PI * 2);
            ctx.fill();

            // Leaf highlights
            ctx.fillStyle = 'rgba(134, 239, 172, 0.25)';
            ctx.beginPath();
            ctx.arc(c.x - c.r * 0.3, c.y - c.r * 0.3, c.r * 0.5, 0, Math.PI * 2);
            ctx.fill();
        }
    }

    const dataUrl = canvas.toDataURL('image/png');
    textureCache.set(cacheKey, dataUrl);
    return dataUrl;
}