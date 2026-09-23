/**
 * Coordinate conversion utilities for TALOS tactical modules.
 * Supports WGS84 Decimal Degrees (DD), Degrees Minutes Seconds (DMS), and NATO MGRS approximation.
 */

export interface DmsCoordinate {
    degrees: number;
    minutes: number;
    seconds: number;
    hemisphere: 'N' | 'S' | 'E' | 'W';
}

export const coordConverter = {
    /**
     * Converts Decimal Degrees (DD) to normalized Degrees Minutes Seconds (DMS).
     */
    toDms(dd: number, isLatitude: boolean): DmsCoordinate {
        const absDd = Math.abs(dd);
        let degrees = Math.floor(absDd);
        const minutesRaw = (absDd - degrees) * 60;
        let minutes = Math.floor(minutesRaw);
        let seconds = Math.round((minutesRaw - minutes) * 60 * 100) / 100;

        if (seconds >= 60) {
            seconds = 0;
            minutes += 1;
        }
        if (minutes >= 60) {
            minutes = 0;
            degrees += 1;
        }

        const hemisphere = isLatitude ? (dd >= 0 ? 'N' : 'S') : (dd >= 0 ? 'E' : 'W');
        return { degrees, minutes, seconds, hemisphere };
    },

    /**
     * Converts DMS coordinate back to Decimal Degrees.
     */
    fromDms(degrees: number, minutes: number, seconds: number, hemisphere: 'N' | 'S' | 'E' | 'W'): number {
        let dd = Math.abs(degrees) + (minutes / 60.0) + (seconds / 3600.0);
        if (hemisphere === 'S' || hemisphere === 'W') {
            dd = -dd;
        }
        return Number(dd.toFixed(6));
    },

    /**
     * Formats latitude and longitude pair into displayable DMS string.
     */
    formatDms(lat: number, lon: number): string {
        const latDms = this.toDms(lat, true);
        const lonDms = this.toDms(lon, false);
        return `${latDms.degrees}°${latDms.minutes}'${latDms.seconds.toFixed(2)}"${latDms.hemisphere} ${lonDms.degrees}°${lonDms.minutes}'${lonDms.seconds.toFixed(2)}"${lonDms.hemisphere}`;
    },

    /**
     * Estimates NATO MGRS string representation for theater center.
     */
    toMgrsEstimate(lat: number, lon: number): string {
        const zoneNumber = Math.floor((lon + 180) / 6) + 1;
        const eastingMeters = Math.floor(((lon + 180) % 6) * 100000);
        const northingMeters = Math.floor(Math.abs(lat) * 111000) % 100000;
        const band = lat >= 0 ? 'U' : 'T';

        const eastStr = String(eastingMeters).padStart(5, '0').slice(0, 4);
        const northStr = String(northingMeters).padStart(5, '0').slice(0, 4);

        return `${zoneNumber}${band} QA ${eastStr} ${northStr}`;
    },

    /**
     * Parses an MGRS estimated string back to approximate DD coordinates.
     */
    fromMgrsEstimate(mgrsStr: string): { lat: number; lon: number } | null {
        try {
            const cleaned = mgrsStr.trim().replace(/\s+/g, ' ');
            const parts = cleaned.split(' ');
            if (parts.length < 4) return null;

            const zoneBand = parts[0];
            const zoneNumber = parseInt(zoneBand.slice(0, -1), 10);
            const easting = parseInt(parts[2], 10);
            const northing = parseInt(parts[3], 10);

            const lon = (zoneNumber - 1) * 6 - 180 + (easting / 10000.0);
            const lat = northing / 1000.0 / 111.0 * 100.0;

            if (isNaN(lat) || isNaN(lon)) return null;
            return { lat: Number(lat.toFixed(4)), lon: Number(lon.toFixed(4)) };
        } catch {
            return null;
        }
    }
};