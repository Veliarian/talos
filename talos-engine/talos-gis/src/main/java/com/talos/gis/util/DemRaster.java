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
 * Bypasses Java AWT ImageIO to guarantee pristine IEEE 754 Float32 precision
 * and prevent true altitudes from being normalized or clamped.
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

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public float getElevation(int x, int y) {
        int clampedX = Math.clamp(x, 0, width - 1);
        int clampedY = Math.clamp(y, 0, height - 1);
        return data[clampedY * width + clampedX];
    }

    public void setElevation(int x, int y, float alt) {
        if (x >= 0 && x < width && y >= 0 && y < height) {
            data[y * width + x] = alt;
        }
    }

    /**
     * Reads exact 32-bit float elevation values directly from TIFF file without AWT color model clamping.
     * Dynamically sizes IFD buffer to support variable tag counts from AWS and GDAL datasets.
     */
    public static DemRaster readFromFile(File file) throws IOException {
        try (FileChannel channel = FileChannel.open(file.toPath(), StandardOpenOption.READ)) {
            ByteBuffer header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            channel.read(header);
            header.flip();

            header.getShort(); // Byte order mark: 'II' (Little Endian)
            header.getShort(); // Magic number: 42
            int ifdOffset = header.getInt();

            // Read IFD entry count
            channel.position(ifdOffset);
            ByteBuffer numEntriesBuf = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN);
            channel.read(numEntriesBuf);
            numEntriesBuf.flip();
            int numEntries = Short.toUnsignedInt(numEntriesBuf.getShort());

            // Allocate buffer dynamically matching exact number of IFD entries (12 bytes each)
            ByteBuffer ifdBuf = ByteBuffer.allocate(numEntries * 12).order(ByteOrder.LITTLE_ENDIAN);
            channel.read(ifdBuf);
            ifdBuf.flip();

            int w = 0;
            int h = 0;
            long stripOffset = 8; // Default fallback for Talos-generated TIFFs

            for (int i = 0; i < numEntries; i++) {
                int tag = Short.toUnsignedInt(ifdBuf.getShort());
                int type = Short.toUnsignedInt(ifdBuf.getShort());
                int count = ifdBuf.getInt();
                int val = ifdBuf.getInt();

                if (tag == 256) {
                    w = val; // ImageWidth
                } else if (tag == 257) {
                    h = val; // ImageLength (Height)
                } else if (tag == 273) {
                    stripOffset = Integer.toUnsignedLong(val); // StripOffsets
                }
            }

            if (w <= 0 || h <= 0) {
                throw new IOException("Invalid DEM TIFF dimensions: " + w + "x" + h);
            }

            // Read raw IEEE 754 Float32 elevation matrix directly from raster strip offset
            channel.position(stripOffset);
            int totalFloats = w * h;
            ByteBuffer dataBuf = ByteBuffer.allocate(totalFloats * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
            channel.read(dataBuf);
            dataBuf.flip();

            float[] floats = new float[totalFloats];
            dataBuf.asFloatBuffer().get(floats);

            return new DemRaster(w, h, floats);
        }
    }

    /**
     * Writes an uncompressed single-band 32-bit IEEE Float TIFF file directly to disk.
     */
    public void writeToFile(File file) throws IOException {
        int dataSizeBytes = width * height * Float.BYTES;
        int ifdOffset = 8 + dataSizeBytes;
        ByteBuffer buffer = ByteBuffer.allocate(ifdOffset + 256).order(ByteOrder.LITTLE_ENDIAN);

        // 1. Header (Little Endian, Magic 42, IFD Offset)
        buffer.put((byte) 'I');
        buffer.put((byte) 'I');
        buffer.putShort((short) 42);
        buffer.putInt(ifdOffset);

        // 2. Continuous Float32 raster matrix
        for (float val : data) {
            buffer.putFloat(val);
        }

        // 3. Image File Directory (IFD)
        buffer.putShort((short) 10); // 10 Baseline TIFF tags
        putTag(buffer, 256, 4, 1, width);          // ImageWidth
        putTag(buffer, 257, 4, 1, height);         // ImageLength
        putTag(buffer, 258, 3, 1, 32);             // BitsPerSample
        putTag(buffer, 259, 3, 1, 1);              // Compression (1 = uncompressed)
        putTag(buffer, 262, 3, 1, 1);              // PhotometricInterpretation (1 = BlackIsZero)
        putTag(buffer, 273, 4, 1, 8);              // StripOffsets (starts immediately at byte 8)
        putTag(buffer, 277, 3, 1, 1);              // SamplesPerPixel (1 band)
        putTag(buffer, 278, 4, 1, height);         // RowsPerStrip
        putTag(buffer, 279, 4, 1, dataSizeBytes);  // StripByteCounts
        putTag(buffer, 339, 3, 1, 3);              // SampleFormat (3 = IEEE Floating Point)
        buffer.putInt(0);                          // Next IFD Offset (0 = none)

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
     * Smooth bilinear interpolation of elevation.
     * Guarantees continuous spatial surface without edge artifacts or pixel step jumps.
     */
    public float getInterpolatedElevation(double normX, double normY) {
        double fx = Math.clamp(normX, 0.0, 1.0) * (width - 1);
        double fy = Math.clamp(normY, 0.0, 1.0) * (height - 1);

        int x0 = (int) Math.floor(fx);
        int y0 = (int) Math.floor(fy);
        int x1 = Math.min(width - 1, x0 + 1);
        int y1 = Math.min(height - 1, y0 + 1);

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