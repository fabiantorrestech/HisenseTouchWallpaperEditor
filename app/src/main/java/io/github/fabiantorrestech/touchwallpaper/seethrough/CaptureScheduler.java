package io.github.fabiantorrestech.touchwallpaper.seethrough;

/**
 * Decides when to capture, from push events only (no polling). Activity marks the screen dirty; a
 * capture is due once the screen has been quiet for a moment, or after a longer wait if it never
 * goes quiet, and never sooner than the system's screenshot rate limit allows. Because the quiet
 * period is short, each capture is followed by one follow-up capture a little later, which catches
 * pages that were still loading. Not thread safe: use it from one thread. Times are in
 * milliseconds from any monotonic clock.
 */
public final class CaptureScheduler {
    /** Returned when nothing needs capturing. */
    public static final long NOT_DUE = -1;

    private final long quietMillis;
    private final long maxWaitMillis;
    private final long minIntervalMillis;
    private final long followUpMillis;

    private boolean dirty;
    private boolean inFlight;
    private boolean hasStarted;
    private long firstActivityAt;
    private long lastActivityAt;
    private long lastStartAt;
    /** A follow-up capture is waiting; it runs only if no new activity comes first. */
    private boolean followUpPending;
    /** Whether the capture in flight (or the last one) is a follow-up. */
    private boolean runningFollowUp;

    public CaptureScheduler(long quietMillis, long maxWaitMillis, long minIntervalMillis, long followUpMillis) {
        this.quietMillis = quietMillis;
        this.maxWaitMillis = maxWaitMillis;
        this.minIntervalMillis = minIntervalMillis;
        this.followUpMillis = followUpMillis;
    }

    /** Something on screen may have changed. Returns the delay until a capture is due. */
    public long onActivity(long now) {
        if (!dirty) {
            firstActivityAt = now;
        }
        dirty = true;
        lastActivityAt = now;
        // The capture this activity leads to brings its own follow-up.
        followUpPending = false;
        return delayUntilDue(now);
    }

    public boolean isDirty() {
        return dirty;
    }

    /** Whether the capture in flight, or the last one, is a follow-up. */
    public boolean isFollowUp() {
        return runningFollowUp;
    }

    /** Milliseconds until a normal or follow-up capture may start, or {@link #NOT_DUE}. */
    public long delayUntilDue(long now) {
        if (inFlight) {
            return NOT_DUE;
        }
        if (dirty) {
            long settled = Math.min(lastActivityAt + quietMillis, firstActivityAt + maxWaitMillis);
            return Math.max(0, Math.max(settled, earliestAllowed()) - now);
        }
        if (followUpPending) {
            return Math.max(0, Math.max(lastStartAt + followUpMillis, earliestAllowed()) - now);
        }
        return NOT_DUE;
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
        runningFollowUp = !dirty;
        dirty = false;
        followUpPending = false;
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
        } else if (!retry && !dirty && !runningFollowUp) {
            // One follow-up per change; a follow-up never asks for another, so this can't loop.
            followUpPending = true;
        }
        return delayUntilDue(now);
    }

    /** Drops pending work, such as when the screen turns off or the feature is switched off. */
    public void clear() {
        dirty = false;
        followUpPending = false;
    }

    private long earliestAllowed() {
        return hasStarted ? lastStartAt + minIntervalMillis : Long.MIN_VALUE / 4;
    }
}
