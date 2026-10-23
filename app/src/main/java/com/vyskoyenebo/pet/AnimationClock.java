package com.vyskoyenebo.pet;

/** Monotonic time is supplied by the caller, so screen-off and drag retain phase. */
final class AnimationClock {
    private long accumulated, started;
    private boolean running;
    synchronized void resume(long now) { if (!running) { started = now; running = true; } }
    synchronized void pause(long now) { if (running) { accumulated += Math.max(0, now - started); running = false; } }
    synchronized void reset(long now) { accumulated = 0; started = now; }
    synchronized long elapsed(long now) { return accumulated + (running ? Math.max(0, now - started) : 0); }
    static int frame(long ms, int fps, int count) { return (int)((Math.max(0, ms) * fps / 1000) % Math.max(1, count)); }
}
