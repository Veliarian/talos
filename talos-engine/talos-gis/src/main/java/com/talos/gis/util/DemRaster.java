package com.talos.gis.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;

/**
 * Direct binary Float32 DEM raster reader and writer.
 * Bypasses Java AWT ImageIO to prevent altitude values > 1.0m from being clamped to 1.0!
 */
public class DemRaster {

    private final int width;
    private final int height;
    private final float[] data;

    public DemRaster(int width, int height, float[] data) {
        this.width = width;
        this.height = height;
        this.data = data;
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }

    public float getElevation(int x, int y) {
        x = Math.max(0, Math.min(width - 1, x));
        y = Math.max(0, Math.min(height - 1, y));
        return data[y * width + x];
    }

    public void setElevation(int x, int y, float alt) {
        if (x >= 0 && x < width && y >= 0 && y < height) {
            data[y * width + x] = alt;
        }
    }

    /**
     * Reads exact 32-bit float elevation values directly from TIFF file without AWT color model clamping.
     */
    public static DemRaster readFromFile(File file) throws IOException {
        try (FileChannel channel = FileChannel.open(file.toPath(), StandardOpenOption.READ)) {
            ByteBuffer header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            channel.read(header);
            header.flip();

            header.getShort(); // 'II'
            header.getShort(); // 42
            int ifdOffset = header.getInt();

            // Read IFD to extract width and height
            channel.position(ifdOffset);
            ByteBuffer ifdBuf = ByteBuffer.allocate(2 + 10 * 12).order(ByteOrder.LITTLE_ENDIAN);
            channel.read(ifdBuf);
            ifdBuf.flip();
            short numEntries = ifdBuf.getShort();

            int w = 0, h = 0;
            for (int i = 0; i < numEntries; i++) {
                short tag = ifdBuf.getShort();
                ifdBuf.getShort(); // type
                ifdBuf.getInt();   // count
                int val = ifdBuf.getInt();
                if (tag == 256) w = val;
                if (tag == 257) h = val;
            }

            if (w <= 0 || h <= 0) {
                throw new IOException("Invalid DEM TIFF dimensions: " + w + "x" + h);
            }

            // Read raw float32 elevation array from offset 8
            channel.position(8);
            int totalFloats = w * h;
            ByteBuffer dataBuf = ByteBuffer.allocate(totalFloats * 4).order(ByteOrder.LITTLE_ENDIAN);
            channel.read(dataBuf);
            dataBuf.flip();

            float[] floats = new float[totalFloats];
            dataBuf.asFloatBuffer().get(floats);

            return new DemRaster(w, h, floats);
        }
    }

    /**
     * Writes pristine Float32 TIFF file directly to disk.
     */
    public void writeToFile(File file) throws IOException {
        int dataSizeBytes = width * height * 4;
        int ifdOffset = 8 + dataSizeBytes;
        ByteBuffer buffer = ByteBuffer.allocate(ifdOffset + 256).order(ByteOrder.LITTLE_ENDIAN);

        // 1. Header
        buffer.put((byte) 'I');
        buffer.put((byte) 'I');
        buffer.putShort((short) 42);
        buffer.putInt(ifdOffset);

        // 2. Data
        for (float val : data) {
            buffer.putFloat(val);
        }

        // 3. IFD
        buffer.putShort((short) 10);
        putTag(buffer, 256, 4, 1, width);
        putTag(buffer, 257, 4, 1, height);
        putTag(buffer, 258, 3, 1, 32);
        putTag(buffer, 259, 3, 1, 1);
        putTag(buffer, 262, 3, 1, 1);
        putTag(buffer, 273, 4, 1, 8);
        putTag(buffer, 277, 3, 1, 1);
        putTag(buffer, 278, 4, 1, height);
        putTag(buffer, 279, 4, 1, dataSizeBytes);
        putTag(buffer, 339, 3, 1, 3);
        buffer.putInt(0);

        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(buffer.array(), 0, buffer.position());
        }
    }

    private static void putTag(ByteBuffer buf, int tagId, int type, int count, int valueOrOffset) {
        buf.putShort((short) tagId);
        buf.putShort((short) type);
        buf.putInt(count);
        if (type == 3) {
            buf.putShort((short) valueOrOffset);
            buf.putShort((short) 0);
        } else {
            buf.putInt(valueOrOffset);
        }
    }

    /**
     * Bilinear interpolation of elevation.
     * Guarantees C0 spatial continuity across Cesium tile boundaries, eliminating seams, cracks, and steps!
     */
    public float getInterpolatedElevation(double normX, double normY) {
        double fx = normX * (width - 1);
        double fy = normY * (height - 1);

        int x0 = (int) Math.floor(fx);
        int y0 = (int) Math.floor(fy);
        int x1 = Math.min(width - 1, x0 + 1);
        int y1 = Math.min(height - 1, y0 + 1);

        x0 = Math.max(0, Math.min(width - 1, x0));
        y0 = Math.max(0, Math.min(height - 1, y0));

        double dx = fx - x0;
        double dy = fy - y0;

        float h00 = getElevation(x0, y0);
        float h10 = getElevation(x1, y0);
        float h01 = getElevation(x0, y1);
        float h11 = getElevation(x1, y1);

        float hTop = (float) (h00 * (1.0 - dx) + h10 * dx);
        float hBottom = (float) (h01 * (1.0 - dx) + h11 * dx);

        return (float) (hTop * (1.0 - dy) + hBottom * dy);
    }
}