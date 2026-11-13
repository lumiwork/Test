package de.lumiwork.silentac.check;

import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

public final class Geometry {

    private Geometry() {
    }

    public static double distanceToBox(Vector point, BoundingBox box) {
        double x = clamp(point.getX(), box.getMinX(), box.getMaxX());
        double y = clamp(point.getY(), box.getMinY(), box.getMaxY());
        double z = clamp(point.getZ(), box.getMinZ(), box.getMaxZ());
        return point.distance(new Vector(x, y, z));
    }

    public static BoundingBox fullBox(Block block) {
        return BoundingBox.of(block);
    }

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static float yawDiff(float a, float b) {
        float diff = Math.abs((a - b) % 360f);
        return diff > 180f ? 360f - diff : diff;
    }

    public static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    public static int unpackX(long packed) {
        return (int) (packed >> 38);
    }

    public static int unpackY(long packed) {
        return (int) (packed << 52 >> 52);
    }

    public static int unpackZ(long packed) {
        return (int) (packed << 26 >> 38);
    }

    public static long pack(Block block) {
        return pack(block.getX(), block.getY(), block.getZ());
    }
}
