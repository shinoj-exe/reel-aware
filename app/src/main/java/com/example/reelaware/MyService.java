package com.example.reelaware;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

public class MyService extends AccessibilityService {

    private static final String TAG = "MyA11yService";

    private ReelDetectionEngine engine;
    private ReelCounterManager counter;
    private OverlayBubble bubble;
    private ReelsStateMonitor reelsState;

    // ---- Instagram Heuristics ----
    private static final String IG_PKG = "com.instagram.android";
    private static final long MIN_GAP_MS = 1400L;
    private static final long DEFERRED_EXIT_DELAY_MS = 500L;

    // Guard to ensure we only increment once per reels session entry
    private boolean countedOnEnter = false;
    private long lastIncrementTs = 0L;

    // Deferred exit: schedule exit after recycler_view scroll, cancel if reels pulse arrives
    private final Handler exitHandler = new Handler(Looper.getMainLooper());
    private final Runnable pendingExitRunnable = () -> {
        Log.d(TAG, "Deferred exit confirmed (no Reels pulse within window)");
        reelsState.forceExit();
    };

    // ---- Lifecycle ----

    @Override
    public void onServiceConnected() {
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) info = new AccessibilityServiceInfo();

        info.eventTypes = AccessibilityEvent.TYPE_VIEW_FOCUSED
                | AccessibilityEvent.TYPE_VIEW_SCROLLED
                | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                | AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
                | AccessibilityEvent.TYPE_WINDOWS_CHANGED
                | AccessibilityEvent.TYPE_VIEW_CLICKED
                | AccessibilityEvent.TYPE_VIEW_SELECTED;

        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.notificationTimeout = 50;
        info.flags = info.flags
                | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                | AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;

        setServiceInfo(info);

        counter = new ReelCounterManager(this);
        bubble = new OverlayBubble(this);

        reelsState = new ReelsStateMonitor(new ReelsStateMonitor.Callback() {
            @Override
            public void onEnterReels() {
                Log.d(TAG, "onEnterReels() triggered");
                boolean useNotif = counter.isNotificationMode();
                if (useNotif) {
                    updateOngoingNotification(counter.getTodayCount(), counter.getLimit());
                } else {
                    if (Settings.canDrawOverlays(MyService.this)) {
                        bubble.show();
                        bubble.updateText(counter.getTodayCount(), counter.getLimit());
                    } else {
                        Log.w(TAG, "Overlay permission NOT granted");
                    }
                }
                maybeIncrementOnEnter();
            }

            @Override
            public void onExitReels() {
                Log.d(TAG, "onExitReels() triggered - hiding bubble");
                bubble.hide();
                cancelOngoingNotification();
                countedOnEnter = false;
            }
        });

        engine = new ReelDetectionEngine(new ReelDetectionEngine.Listener() {
            @Override
            public void onReelDetected() {
                lastIncrementTs = System.currentTimeMillis();
                reelsState.markReelsActivePulse();

                int c = counter.increment();
                int limit = counter.getLimit();

                if (counter.isNotificationMode()) {
                    updateOngoingNotification(c, limit);
                } else {
                    bubble.updateText(c, limit);
                }

                applyMicroFriction(c, limit);
            }

            @Override
            public void onReelsSurfacePulse() {
                exitHandler.removeCallbacks(pendingExitRunnable); // Cancel any pending deferred exit
                reelsState.markReelsActivePulse();
            }

            @Override
            public void onReelsSurfaceGone() {
                Log.d(TAG, "Reels ViewPager no longer visible — forcing exit");
                reelsState.forceExit();
            }
        });

        Log.i(TAG, "Service connected");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        final int type = event.getEventType();
        final CharSequence pkg = event.getPackageName();

        // Force exit if we leave Instagram
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (pkg != null && !pkg.equals(IG_PKG)
                    && !pkg.equals(getPackageName())
                    && !pkg.equals("com.android.systemui")) {
                Log.d(TAG, "Force exit due to window state change. Pkg: " + pkg);
                reelsState.forceExit();
            }
        }

        // Force exit if scrolling a known non-Reels list
        if (type == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            AccessibilityNodeInfo scrollSrc = event.getSource();
            if (scrollSrc != null) {
                String scrollId = scrollSrc.getViewIdResourceName();
                if (scrollId != null) {
                    if (scrollId.equals("com.instagram.android:id/feed_recycler_view")) {
                        Log.d(TAG, "Force exit due to scroll of feed list");
                        reelsState.forceExit();
                    } else if (scrollId.equals("com.instagram.android:id/recycler_view")) {
                        // Deferred exit: schedule exit, cancel if Reels pulse arrives
                        Log.d(TAG, "Deferred exit scheduled due to recycler_view scroll");
                        exitHandler.removeCallbacks(pendingExitRunnable);
                        exitHandler.postDelayed(pendingExitRunnable, DEFERRED_EXIT_DELAY_MS);
                    }
                }
                scrollSrc.recycle();
            }

            engine.handleScrollEvent(event);
        }

        // Detect navigation tab interactions
        AccessibilityNodeInfo src = event.getSource();
        if (src != null) {
            String id = src.getViewIdResourceName();
            if (id != null) {
                if (id.contains("feed_tab") || id.contains("search_tab")
                        || id.contains("profile_tab") || id.contains("direct_tab")
                        || id.contains("creation_tab")) {
                    Log.d(TAG, "Exiting Reels due to tab event (" + type + "): " + id);
                    reelsState.forceExit();
                } else if (id.contains("clips_tab") && type == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                    Log.d(TAG, "Entering Reels via Tab Click");
                    reelsState.markReelsActivePulse();
                }

                // Handle "Back" button click
                if (type == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                    CharSequence desc = src.getContentDescription();
                    if (desc != null && "Back".equals(desc.toString())) {
                        Log.d(TAG, "Exiting Reels due to 'Back' click");
                        reelsState.forceExit();
                    }
                }
            }
            src.recycle();
        }

        // Passive detection: scan for visible Reels ViewPager
        engine.scan(event);
    }

    @Override
    protected boolean onKeyEvent(android.view.KeyEvent event) {
        if (event.getAction() == android.view.KeyEvent.ACTION_UP
                && event.getKeyCode() == android.view.KeyEvent.KEYCODE_BACK) {
            if (reelsState != null && reelsState.isInReels()) {
                Log.d(TAG, "Hardware Back detected - forcing exit from Reels");
                reelsState.forceExit();
                return false;
            }
        }
        return super.onKeyEvent(event);
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "Service interrupted");
        bubble.hide();
    }

    @Override
    public void onDestroy() {
        bubble.hide();
        super.onDestroy();
    }

    // ---- Counting Logic ----

    private void maybeIncrementOnEnter() {
        if (countedOnEnter) return;

        long now = System.currentTimeMillis();
        if (now - lastIncrementTs < MIN_GAP_MS) return;

        int c = counter.increment();
        lastIncrementTs = now;
        countedOnEnter = true;

        if (counter.isNotificationMode()) {
            updateOngoingNotification(c, counter.getLimit());
        } else {
            bubble.updateText(c, counter.getLimit());
        }

        applyMicroFriction(c, counter.getLimit());
        Log.i("REEL_COUNTER", "Incremented on entering Reels");
    }

    private void applyMicroFriction(int count, int limit) {
        int batch = counter.getBatchSize();
        boolean isLimit = (count == limit);
        boolean isBatch = (count % batch == 0);

        if (isLimit) {
            if (!counter.isNotificationMode()) bubble.wiggle(true);
            triggerLimitReachedFeedback();
        } else if (isBatch && count < limit) {
            if (!counter.isNotificationMode()) bubble.wiggle(false);
            triggerHapticBump();
        }
    }

    // ---- Haptic Feedback ----

    private void triggerHapticBump() {
        android.os.Vibrator v = (android.os.Vibrator) getSystemService(android.content.Context.VIBRATOR_SERVICE);
        if (v != null) {
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                v.vibrate(android.os.VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(50);
            }
        }
    }

    private void triggerLimitReachedFeedback() {
        // 1. Vibration (1 second)
        android.os.Vibrator v = (android.os.Vibrator) getSystemService(android.content.Context.VIBRATOR_SERVICE);
        if (v != null) {
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                v.vibrate(android.os.VibrationEffect.createOneShot(1000, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(1000);
            }
        }

        // 2. Toast
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                android.widget.Toast.makeText(getApplicationContext(),
                        "Daily Reel Limit Reached!", android.widget.Toast.LENGTH_LONG).show()
        );

        // 3. High-priority notification
        sendLimitNotification();
    }

    // ---- Notifications ----

    private void sendLimitNotification() {
        String channelId = "reel_limit_channel";
        android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (android.os.Build.VERSION.SDK_INT >= 26) {
            android.app.NotificationChannel channel = new android.app.NotificationChannel(
                    channelId, "Reel Limit Alerts", android.app.NotificationManager.IMPORTANCE_HIGH);
            if (nm != null) nm.createNotificationChannel(channel);
        }

        android.app.Notification.Builder builder;
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            builder = new android.app.Notification.Builder(this, channelId);
        } else {
            builder = new android.app.Notification.Builder(this);
        }

        builder.setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("ReelAware Limit Reached")
                .setContentText("You have reached your daily limit of reels!")
                .setAutoCancel(true);

        if (nm != null) nm.notify(1001, builder.build());
    }

    private void updateOngoingNotification(int count, int limit) {
        String channelId = "reel_ongoing_channel";
        android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (android.os.Build.VERSION.SDK_INT >= 26) {
            android.app.NotificationChannel channel = new android.app.NotificationChannel(
                    channelId, "Reel Counter", android.app.NotificationManager.IMPORTANCE_LOW);
            if (nm != null) nm.createNotificationChannel(channel);
        }

        android.app.Notification.Builder builder;
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            builder = new android.app.Notification.Builder(this, channelId);
        } else {
            builder = new android.app.Notification.Builder(this);
        }

        builder.setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle("ReelAware Active")
                .setContentText("Reels Watched: " + count + " / " + limit)
                .setOngoing(true)
                .setAutoCancel(false);

        if (nm != null) nm.notify(2002, builder.build());
    }

    private void cancelOngoingNotification() {
        android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(2002);
    }
}