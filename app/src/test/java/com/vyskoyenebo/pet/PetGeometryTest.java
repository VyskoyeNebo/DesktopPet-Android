package com.vyskoyenebo.pet;

import org.junit.Test;
import static org.junit.Assert.*;

public class PetGeometryTest {
    @Test public void oversizedLandscapeAndPortraitFitAndPreserveShape() {
        int[] landscape = PetGeometry.fit(1024, 641, 1536, 360, 760);
        assertEquals(360, landscape[0]);
        assertTrue(Math.abs((double)landscape[0] / landscape[1] - 1024.0 / 641) < .02);
        int[] portrait = PetGeometry.fit(655, 1024, 1536, 760, 300);
        assertTrue(portrait[0] <= 760); assertEquals(300, portrait[1]);
    }
    @Test public void everySizeRemainsReachableAfterRotation() {
        for (int size : new int[]{96, 128, 192, 256, 384, 512}) {
            int[] dims = PetGeometry.fit(951, 1024, size * 4, 480, 320);
            assertTrue(dims[0] > 0 && dims[0] <= 480);
            assertTrue(dims[1] > 0 && dims[1] <= 320);
            assertEquals(0, PetGeometry.clamp(-500, dims[0], 480));
            int right = PetGeometry.clamp(10000, dims[0], 480);
            assertTrue(right + dims[0] <= 480);
            assertEquals(right, PetGeometry.position(PetGeometry.fraction(right, dims[0], 480), dims[0], 480));
        }
    }
    @Test public void tinyOrCorruptSavedPositionCannotLoseWindow() {
        assertArrayEquals(new int[]{1, 1}, PetGeometry.fit(1024, 1024, 999, 1, 1));
        assertEquals(150, PetGeometry.position(Float.NaN, 100, 400));
        assertEquals(0, PetGeometry.clamp(99, 500, 400));
        assertEquals(0, PetGeometry.fraction(90, 500, 400), 0);
    }
}
