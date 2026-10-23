package com.vyskoyenebo.pet;

final class PetGeometry {
    static int[] fit(int sourceW, int sourceH, int requested, int areaW, int areaH) {
        double scale = Math.min((double)Math.max(1, requested) / Math.max(sourceW, sourceH),
            Math.min((double)Math.max(1, areaW) / sourceW, (double)Math.max(1, areaH) / sourceH));
        return new int[]{Math.max(1, Math.min(areaW, (int)Math.floor(sourceW * scale))),
            Math.max(1, Math.min(areaH, (int)Math.floor(sourceH * scale)))};
    }
    static int clamp(int value, int size, int area) { return Math.max(0, Math.min(value, Math.max(0, area - size))); }
    static int position(float fraction, int size, int area) {
        if (Float.isNaN(fraction) || Float.isInfinite(fraction)) fraction = 0.5f;
        return Math.round(Math.max(0, Math.min(1, fraction)) * Math.max(0, area - size));
    }
    static float fraction(int position, int size, int area) { return area <= size ? 0 : Math.max(0, Math.min(1, (float)position / (area - size))); }
}
