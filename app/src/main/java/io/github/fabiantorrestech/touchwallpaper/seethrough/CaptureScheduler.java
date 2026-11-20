package io.github.fabiantorrestech.touchwallpaper.seethrough;

/**
 * Decides when to capture, from push events only (no polling). Activity marks the screen dirty; a
 * capture is due once the screen has been quiet for a moment, or after a longer wait if it never
 * goes quiet, and never sooner than the system's screenshot rate limit allows. Not thread safe:
 * use it from one thread. Times are in milliseconds from any monotonic clock.
 */
public final class CaptureScheduler {
    /** Returned when nothing needs capturing. */
    public static final long NOT_DUE = -1;

    private final long quietMillis;
    private final long maxWaitMillis;
    private final long minIntervalMillis;

    private boolean dirty;
    private boolean inFlight;
    private boolean hasStarted;
    private long firstActivityAt;
    private long lastActivityAt;
    private long lastStartAt;

    public CaptureScheduler(long quietMillis, long maxWaitMillis, long minIntervalMillis) {
        this.quietMillis = quietMillis;
        this.maxWaitMillis = maxWaitMillis;
        this.minIntervalMillis = minIntervalMillis;
    }

    /** Something on screen may have changed. Returns the delay until a capture is due. */
    public long onActivity(long now) {
        if (!dirty) {
            firstActivityAt = now;
        }
        dirty = true;
        lastActivityAt = now;
        return delayUntilDue(now);
    }

    public boolean isDirty() {
        return dirty;
    }

    /** Milliseconds until a normal capture may start, or {@link #NOT_DUE}. */
    public long delayUntilDue(long now) {
        if (!dirty || inFlight) {
            return NOT_DUE;
        }
        long settled = Math.min(lastActivityAt + quietMillis, firstActivityAt + maxWaitMillis);
        return Math.max(0, Math.max(settled, earliestAllowed()) - now);
    }

    /** Like {@link #delayUntilDue}, but skips the quiet period, for when the screen is turning off. */
    public long delayUntilUrgentDue(long now) {
        if (!dirty || inFlight) {
            return NOT_DUE;
        }
        return Math.max(0, earliestAllowed() - now);
    }

    /** Starts a capture if one is due now. The caller must then call {@link #onFinished}. */
    public boolean tryStart(long now, boolean urgent) {
        long delay = urgent ? delayUntilUrgentDue(now) : delayUntilDue(now);
        if (delay != 0) {
            return false;
        }
        dirty = false;
        inFlight = true;
        hasStarted = true;
        lastStartAt = now;
        return true;
    }

    /** Ends the capture in flight. Returns the delay until the next one is due. */
    public long onFinished(long now, boolean retry) {
        inFlight = false;
        if (retry && !dirty) {
            dirty = true;
            firstActivityAt = now;
            lastActivityAt = now;
        }
        return delayUntilDue(now);
    }

    /** Drops pending work, such as when the screen turns off or the feature is switched off. */
    public void clear() {
        dirty = false;
    }

    private long earliestAllowed() {
        return hasStarted ? lastStartAt + minIntervalMillis : Long.MIN_VALUE / 4;
    }
}
