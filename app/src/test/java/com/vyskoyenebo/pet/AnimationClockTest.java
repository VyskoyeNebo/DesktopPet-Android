package com.vyskoyenebo.pet;

import org.junit.Test;
import static org.junit.Assert.*;

public class AnimationClockTest {
    @Test public void hidingAndDraggingFreezePhaseAcrossRepeatedResume() {
        AnimationClock clock = new AnimationClock();
        clock.resume(1000);
        clock.resume(1100);
        assertEquals(250, clock.elapsed(1250));
        clock.pause(1250);
        clock.pause(3000);
        assertEquals(250, clock.elapsed(5000));
        clock.resume(5000);
        assertEquals(250, clock.elapsed(5000));
        assertEquals(450, clock.elapsed(5200));
        clock.pause(5200);
        clock.resume(9000);
        assertEquals(450, clock.elapsed(9000));
    }
    @Test public void frameWrapAndStaticPoseRemainValid() {
        assertEquals(0, AnimationClock.frame(4000, 12, 48));
        assertEquals(12, AnimationClock.frame(1000, 12, 48));
        assertEquals(0, AnimationClock.frame(1234567, 12, 1));
        assertEquals(0, AnimationClock.frame(-10, 12, 48));
    }
    @Test public void newMotionStartsAtZeroWhilePaused() {
        AnimationClock clock = new AnimationClock();
        clock.resume(100); clock.pause(1100); clock.reset(5000);
        assertEquals(0, clock.elapsed(8000));
        clock.resume(8000);
        assertEquals(200, clock.elapsed(8200));
    }
}
