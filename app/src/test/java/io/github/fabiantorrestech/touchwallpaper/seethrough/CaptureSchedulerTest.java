package io.github.fabiantorrestech.touchwallpaper.seethrough;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CaptureSchedulerTest {
    private static final long QUIET = 1200;
    private static final long MAX_WAIT = 10000;
    private static final long INTERVAL = 1100;
    private static final long FOLLOW_UP = 1500;

    private final CaptureScheduler scheduler = new CaptureScheduler(QUIET, MAX_WAIT, INTERVAL, FOLLOW_UP);

    @Test
    public void nothingIsDueWithoutActivity() {
        assertEquals(CaptureScheduler.NOT_DUE, scheduler.delayUntilDue(0));
        assertFalse(scheduler.tryStart(5000, false));
    }

    @Test
    public void captureIsDueOnceTheScreenIsQuiet() {
        assertEquals(QUIET, scheduler.onActivity(1000));
        assertFalse(scheduler.tryStart(1000 + QUIET - 1, false));
        assertTrue(scheduler.tryStart(1000 + QUIET, false));
    }

    @Test
    public void newActivityPushesTheCaptureBack() {
        scheduler.onActivity(1000);
        assertEquals(QUIET, scheduler.onActivity(1500));
        assertFalse(scheduler.tryStart(2200, false));
        assertTrue(scheduler.tryStart(2700, false));
    }

    @Test
    public void aScreenThatNeverSettlesIsCapturedAfterTheMaxWait() {
        long now = 0;
        scheduler.onActivity(now);
        while (now < MAX_WAIT) {
            now += 500;
            scheduler.onActivity(now);
        }
        assertEquals(0, scheduler.delayUntilDue(MAX_WAIT));
        assertTrue(scheduler.tryStart(MAX_WAIT, false));
    }

    @Test
    public void capturesRespectTheSystemRateLimit() {
        scheduler.onActivity(0);
        assertTrue(scheduler.tryStart(QUIET, false));
        scheduler.onFinished(QUIET + 50, false);
        scheduler.onActivity(QUIET + 60);
        // Quiet again at QUIET + 60 + QUIET, which is later than the rate limit here.
        assertEquals(QUIET, scheduler.delayUntilDue(QUIET + 60));
        assertEquals(QUIET + INTERVAL - (QUIET + 60), scheduler.delayUntilUrgentDue(QUIET + 60));
    }

    @Test
    public void onlyOneCaptureRunsAtATime() {
        scheduler.onActivity(0);
        assertTrue(scheduler.tryStart(QUIET, false));
        scheduler.onActivity(QUIET + 10);
        assertEquals(CaptureScheduler.NOT_DUE, scheduler.delayUntilDue(QUIET + 5000));
        assertFalse(scheduler.tryStart(QUIET + 5000, false));
        // Activity during the capture is kept, so the change is captured next.
        long next = scheduler.onFinished(QUIET + 100, false);
        assertTrue(next >= 0);
        assertTrue(scheduler.isDirty());
    }

    @Test
    public void aRefusedCaptureIsRetriedAfterTheRateLimit() {
        scheduler.onActivity(0);
        assertTrue(scheduler.tryStart(QUIET, false));
        long delay = scheduler.onFinished(QUIET + 10, true);
        assertTrue(scheduler.isDirty());
        assertEquals(QUIET, delay);
    }

    @Test
    public void urgentCaptureSkipsTheQuietPeriod() {
        scheduler.onActivity(1000);
        assertEquals(0, scheduler.delayUntilUrgentDue(1001));
        assertTrue(scheduler.tryStart(1001, true));
    }

    @Test
    public void clearDropsPendingWork() {
        scheduler.onActivity(0);
        scheduler.clear();
        assertFalse(scheduler.isDirty());
        assertEquals(CaptureScheduler.NOT_DUE, scheduler.delayUntilDue(QUIET));
    }
}
