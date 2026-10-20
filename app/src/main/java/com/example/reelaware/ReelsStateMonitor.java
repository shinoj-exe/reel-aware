package com.example.reelaware;

public class ReelsStateMonitor {

    public interface Callback {
        void onEnterReels();
        void onExitReels();
    }

    private static final long EXIT_COOLDOWN_MS = 500L; // Ignore re-entry briefly after exit (animation transition)

    private final Callback callback;
    private boolean inReels = false;
    private long lastExitTs = 0L;

    public ReelsStateMonitor(Callback cb) {
        this.callback = cb;
    }

    /** Call whenever a reels-related event is detected. */
    public void markReelsActivePulse() {
        if (!inReels) {
            // Don't re-enter if we just exited (transition animation may still show Reels)
            if (System.currentTimeMillis() - lastExitTs < EXIT_COOLDOWN_MS) return;
            inReels = true;
            callback.onEnterReels();
        }
    }

    /** Call when you detect you've left Instagram or definitely left reels. */
    public void forceExit() {
        if (inReels) {
            inReels = false;
            lastExitTs = System.currentTimeMillis();
            callback.onExitReels();
        }
    }

    public boolean isInReels() {
        return inReels;
    }
}