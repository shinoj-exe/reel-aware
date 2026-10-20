package com.example.reelaware;

import android.content.Context;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

public class OverlayBubble {

    private final WindowManager wm;
    private final Context ctx;
    private View bubble;
    private TextView tvCount;
    private TextView tvLimitView;
    private View bubbleRoot;

    private WindowManager.LayoutParams lp;
    
    public OverlayBubble(MyService ctx) {
        this.ctx = ctx;
        wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
    }

    public void show() {
        if (bubble != null) {
            android.util.Log.d("OverlayBubble", "show() called but bubble already exists");
            return;
        }
        android.util.Log.d("OverlayBubble", "Attempting to show overlay...");

        bubble = LayoutInflater.from(ctx).inflate(R.layout.overlay_bubble, null);
        bubbleRoot = bubble.findViewById(R.id.bubbleRoot);
        tvCount = bubble.findViewById(R.id.tvCount);
        tvLimitView = bubble.findViewById(R.id.tvLimit);

        lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, 
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        );

        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = 50;
        lp.y = 200;

        bubble.setOnTouchListener(new Dragger());

        try {
            wm.addView(bubble, lp);
            android.util.Log.d("OverlayBubble", "Overlay added to WindowManager");
        } catch (Exception e) {
            android.util.Log.e("OverlayBubble", "Error adding overlay: " + e.getMessage());
        }
    }

    public void updateText(int count, int limit) {
        if (tvCount != null && tvLimitView != null && bubbleRoot != null) {
            
            if (count >= limit) {
                 // Limit Reached State
                 tvCount.setText("Limit\nReached");
                 tvCount.setTextSize(16f); // Smaller text to fit
                 tvCount.setGravity(Gravity.CENTER);
                 tvCount.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                 tvLimitView.setVisibility(View.GONE);
                 
                 // Solid Red
                 int color = 0xFFFF0000;
                 try {
                    android.graphics.drawable.GradientDrawable bg = (android.graphics.drawable.GradientDrawable) bubbleRoot.getBackground();
                    bg.setColor(color);
                } catch (Exception e) {}
                
            } else {
                // Normal State
                tvCount.setText(String.valueOf(count));
                tvCount.setTextSize(22f); // Restore size
                tvLimitView.setVisibility(View.VISIBLE);
                tvLimitView.setText("/ " + limit);
                
                if (limit <= 0) return;

                float percentage = (float) count / limit;
                int color;

                if (percentage < 0.5f) {
                    color = 0xAA4CAF50; // Green
                } else if (percentage < 0.7f) {
                    color = 0xAAFFC107; // Amber
                } else if (percentage < 0.9f) {
                    color = 0xAAFF9800; // Orange
                } else {
                    color = 0xFFFF0000; // Solid Red (Limit Reached)
                }
                
                try {
                    android.graphics.drawable.GradientDrawable bg = (android.graphics.drawable.GradientDrawable) bubbleRoot.getBackground();
                    bg.setColor(color);
                } catch (Exception e) {}
            }
        }
    }

    public void hide() {
        if (bubble != null) {
            wm.removeView(bubble);
            bubble = null;
        }
    }
    
    public void wiggle(boolean intense) {
        if (bubbleRoot == null) return;
        
        // Cancel any running animation
        bubbleRoot.animate().cancel();
        bubbleRoot.setRotation(0f);
        bubbleRoot.setTranslationX(0f);

        if (intense) {
            // Strong shake: Rotate -15 to 15, duration 100ms, repeat 5 times
            android.animation.ObjectAnimator rot = android.animation.ObjectAnimator.ofFloat(bubbleRoot, "rotation", 0f, -15f, 15f, -15f, 15f, 0f);
            rot.setDuration(500);
            rot.start();
        } else {
            // Gentle wiggle: Rotate -5 to 5, duration 200ms
            android.animation.ObjectAnimator rot = android.animation.ObjectAnimator.ofFloat(bubbleRoot, "rotation", 0f, -5f, 5f, 0f);
            rot.setDuration(300);
            rot.start();
        }
    }

    private class Dragger implements View.OnTouchListener {
        private int lastX, lastY;
        private float touchX, touchY;

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    lastX = lp.x;
                    lastY = lp.y;
                    touchX = e.getRawX();
                    touchY = e.getRawY();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    lp.x = lastX + (int) (e.getRawX() - touchX);
                    lp.y = lastY + (int) (e.getRawY() - touchY);
                    wm.updateViewLayout(bubble, lp);
                    return true;
            }
            return false;
        }
    }
}