package com.example.reelaware;

import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.util.Log;

public class ReelDetectionEngine {

    private static final String TAG = "ReelDetectionEngine";

    private static final String PKG_IG = "com.instagram.android";
    private static final String REELS_VIEW_ID = "com.instagram.android:id/clips_viewer_view_pager";
    private static final String VIEWPAGER_CLASS = "androidx.viewpager.widget.ViewPager";

    private static final int MIN_DY = 900;        // tuned from your logs
    private static final long MIN_GAP_MS = 1600L;  // debounce

    private long lastIncrementTs = 0L;
    private int consecutiveNotFound = 0;
    private static final int NOT_FOUND_THRESHOLD = 3;

    public interface Listener {
        void onReelDetected();
        void onReelsSurfacePulse();
        void onReelsSurfaceGone();
    }

    private final Listener listener;

    public ReelDetectionEngine(Listener listener) {
        this.listener = listener;
    }

    /** Call this from your service for each TYPE_VIEW_SCROLLED event */
    public void handleScrollEvent(AccessibilityEvent e) {
        if (e == null) return;
        if (!PKG_IG.equals(e.getPackageName())) return;

        AccessibilityNodeInfo src = e.getSource();
        if (src == null) return;

        try {
            if (!isReelsViewPager(e, src)) return;

            int dy = 0;
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                dy = e.getScrollDeltaY();
            }

            int ady = Math.abs(dy);
            if (ady < MIN_DY) return;

            long now = System.currentTimeMillis();
            if (now - lastIncrementTs < MIN_GAP_MS) {
                Log.d(TAG, "Ignored: debounce window (dy=" + dy + ")");
                return;
            }

            lastIncrementTs = now;
            Log.i(TAG, "Reel increment (dy=" + dy + ")");
            listener.onReelDetected();

        } catch (Exception ex) {
            Log.w(TAG, "Error in handleScrollEvent", ex);
        } finally {
            src.recycle();
        }
    }

    /** Call this for non-scroll events (Window state changed, focus, etc.) to check if we are in Reels */
    public void scan(AccessibilityEvent e) {
        if (e == null) return;
        if (!PKG_IG.equals(e.getPackageName())) return;
        
        AccessibilityNodeInfo source = e.getSource();
        // If source is null, we can't do much (unless we passed the service context to get root)
        if (source == null) return;
        
        AccessibilityNodeInfo root = null;
        try {
             // Try to get window root to search entire screen
             if (android.os.Build.VERSION.SDK_INT >= 21) {
                 android.view.accessibility.AccessibilityWindowInfo win = source.getWindow();
                 if (win != null) {
                     root = win.getRoot();
                 }
             }
             // Fallback: search the source subtree
             if (root == null) {
                 root = source; 
             }
             
             // Search for the Reels ViewPager ID
             java.util.List<AccessibilityNodeInfo> found = 
                 root.findAccessibilityNodeInfosByViewId(REELS_VIEW_ID);
                 
             boolean visibleReelsFound = false;
             if (found != null) {
                 for (AccessibilityNodeInfo n : found) {
                     if (n.isVisibleToUser()) {
                         visibleReelsFound = true;
                     }
                     n.recycle();
                 }
             }

             if (visibleReelsFound) {
                 consecutiveNotFound = 0;
                 Log.d(TAG, "Passive detection: Visible Reels ViewPager found!");
                 listener.onReelsSurfacePulse();
             } else {
                 consecutiveNotFound++;
                 if (consecutiveNotFound >= NOT_FOUND_THRESHOLD) {
                     Log.d(TAG, "Passive detection: Reels ViewPager NOT found (" + consecutiveNotFound + " consecutive)");
                     listener.onReelsSurfaceGone();
                 }
             }
             
        } catch (Exception ex) {
             Log.w(TAG, "Error in scan", ex);
        } finally {
             // We retrieved 'source' ourselves, so we must recycle it.
             // If root != source, we recycle root too.
             if (root != null && root != source) root.recycle();
             source.recycle();
        }
    }

    private boolean isReelsViewPager(AccessibilityEvent e, AccessibilityNodeInfo src) {
        if (e.getClassName() == null) return false;
        if (!e.getClassName().toString().equals(VIEWPAGER_CLASS)) return false;

        String id = src.getViewIdResourceName();
        if (id == null) return false;

        return REELS_VIEW_ID.equals(id);
    }
}