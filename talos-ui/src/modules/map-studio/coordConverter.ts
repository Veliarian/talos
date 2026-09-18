/**
 * Coordinate conversion utilities for TALOS Map Studio.
 * Supports WGS84 Decimal Degrees (DD), Degrees Minutes Seconds (DMS), and NATO MGRS approximation.
 */

export interface DmsCoordinate {
    degrees: number;
    minutes: number;
    seconds: number;
    hemisphere: 'N' | 'S' | 'E' | 'W';
}

export const coordConverter = {
    // Convert Decimal Degrees to DMS format
    toDms(dd: number, isLatitude: boolean): DmsCoordinate {
        const absDd = Math.abs(dd);
        const degrees = Math.floor(absDd);
        const minutesNotTruncated = (absDd - degrees) * 60;
        const minutes = Math.floor(minutesNotTruncated);
        const seconds = Math.round((minutesNotTruncated - minutes) * 60 * 100) / 100;
        const hemisphere = isLatitude ? (dd >= 0 ? 'N' : 'S') : dd >= 0 ? 'E' : 'W';

        return { degrees, minutes, seconds, hemisphere };
    },

    // Convert DMS to Decimal Degrees
    fromDms(dms: DmsCoordinate): number {
        let dd = dms.degrees + dms.minutes / 60 + dms.seconds / 3600;
        if (dms.hemisphere === 'S' || dms.hemisphere === 'W') {
            dd = -dd;
        }
        return dd;
    },

    // Format DMS string for display
    formatDms(lat: number, lon: number): string {
        const latDms = this.toDms(lat, true);
        const lonDms = this.toDms(lon, false);
        return `${latDms.degrees}°${latDms.minutes}'${latDms.seconds}"${latDms.hemisphere} ${lonDms.degrees}°${lonDms.minutes}'${lonDms.seconds}"${lonDms.hemisphere}`;
    },

    // Quick MGRS string representation for theater center (simplified grid format)
    toMgrsEstimate(lat: number, lon: number): string {
        const zoneNumber = Math.floor((lon + 180) / 6) + 1;
        const eastingMeters = Math.floor(((lon + 180) % 6) * 100000);
        const northingMeters = Math.floor(Math.abs(lat) * 111000) % 100000;
        const band = lat >= 0 ? 'U' : 'T';
        return `${zoneNumber}${band} QA ${String(eastingMeters).slice(0, 4)} ${String(northingMeters).slice(0, 4)}`;
    }
};