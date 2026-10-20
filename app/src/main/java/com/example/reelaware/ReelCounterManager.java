package com.example.reelaware;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.LocalDate;

public class ReelCounterManager {

    private static final String PREF = "reel_counter";
    private static final String KEY_DATE = "current_date";
    private static final String KEY_COUNT = "count";
    private static final String KEY_LIMIT = "limit";
    private static final String KEY_NOTIF_MODE = "notif_mode";
    private static final String KEY_BATCH_SIZE = "batch_size";

    private final SharedPreferences prefs;

    public ReelCounterManager(Context ctx) {
        this.prefs = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        if (!prefs.contains(KEY_LIMIT)) {
            prefs.edit().putInt(KEY_LIMIT, 30).apply();
        }
    }

    // ---- Daily Count ----

    public int getTodayCount() {
        ensureDate();
        return prefs.getInt(KEY_COUNT, 0);
    }

    public int increment() {
        ensureDate();
        int newCount = getTodayCount() + 1;
        prefs.edit().putInt(KEY_COUNT, newCount).apply();
        return newCount;
    }

    public void reset() {
        prefs.edit().putInt(KEY_COUNT, 0).apply();
    }

    // ---- Limit ----

    public int getLimit() {
        return prefs.getInt(KEY_LIMIT, 30);
    }

    public void setLimit(int v) {
        prefs.edit().putInt(KEY_LIMIT, v).apply();
    }

    // ---- Notification Mode ----

    public boolean isNotificationMode() {
        return prefs.getBoolean(KEY_NOTIF_MODE, false);
    }

    public void setNotificationMode(boolean enabled) {
        prefs.edit().putBoolean(KEY_NOTIF_MODE, enabled).apply();
    }

    // ---- Batch Size (Micro-Friction) ----

    public int getBatchSize() {
        return prefs.getInt(KEY_BATCH_SIZE, 5);
    }

    public void setBatchSize(int v) {
        prefs.edit().putInt(KEY_BATCH_SIZE, v).apply();
    }

    // ---- Internal ----

    private void ensureDate() {
        String today = LocalDate.now().toString();
        String stored = prefs.getString(KEY_DATE, "");
        if (!today.equals(stored)) {
            prefs.edit()
                    .putString(KEY_DATE, today)
                    .putInt(KEY_COUNT, 0)
                    .apply();
        }
    }
}