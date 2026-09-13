package com.limelight.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.Game;
import com.limelight.R;
import com.limelight.binding.input.ControllerHandler;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.ControllerPacket;
import com.limelight.nvstream.input.KeyboardPacket;

public class OnScreenMacroDock {
    private static final String PREF_DOCK_X = "macro_dock_pos_x";
    private static final String PREF_DOCK_Y = "macro_dock_pos_y";
    private static final int CLICK_DRAG_TOLERANCE_DP = 8;

    private final Game game;
    private final FrameLayout rootView;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Vibrator vibrator;

    private LinearLayout container;
    private TextView pillHandle;
    private HorizontalScrollView expandedScrollView;
    private LinearLayout macroButtonsRow;

    private boolean isExpanded = false;
    private boolean isDragging = false;
    private float touchStartX, touchStartY;
    private float dX, dY;

    private boolean turboActive = false;
    private Button turboBtnRef = null;
    private final Runnable turboPulseRunnable = new Runnable() {
        private boolean state = false;
        @Override
        public void run() {
            if (!turboActive || game.isFinishing()) {
                return;
            }
            state = !state;
            pulseControllerFlag(ControllerPacket.A_FLAG, state ? 35 : 0);
            handler.postDelayed(this, 50);
        }
    };

    public OnScreenMacroDock(Game game, FrameLayout rootView) {
        this.game = game;
        this.rootView = rootView;
        this.vibrator = (Vibrator) game.getSystemService(Context.VIBRATOR_SERVICE);
        buildView();
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, game.getResources().getDisplayMetrics());
    }

    @SuppressLint("ClickableViewAccessibility")
    private void buildView() {
        container = new LinearLayout(game);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.START);

        // Background for handle pill
        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setShape(GradientDrawable.RECTANGLE);
        pillBg.setCornerRadius(dpToPx(16));
        pillBg.setColor(0xD0121722);
        pillBg.setStroke(dpToPx(1), 0x55FFFFFF);

        pillHandle = new TextView(game);
        pillHandle.setText("⚡ MACROS");
        pillHandle.setTextColor(0xFFFFFFFF);
        pillHandle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        pillHandle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        pillHandle.setBackground(pillBg);
        pillHandle.setPadding(dpToPx(10), dpToPx(5), dpToPx(10), dpToPx(5));
        pillHandle.setElevation(dpToPx(6));

        // Drag and click logic for the handle
        pillHandle.setOnTouchListener((view, event) -> {
            float rawX = event.getRawX();
            float rawY = event.getRawY();

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    touchStartX = rawX;
                    touchStartY = rawY;
                    dX = container.getX() - rawX;
                    dY = container.getY() - rawY;
                    isDragging = false;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float diffX = Math.abs(rawX - touchStartX);
                    float diffY = Math.abs(rawY - touchStartY);
                    if (diffX > dpToPx(CLICK_DRAG_TOLERANCE_DP) || diffY > dpToPx(CLICK_DRAG_TOLERANCE_DP)) {
                        isDragging = true;
                    }

                    if (isDragging) {
                        float newX = rawX + dX;
                        float newY = rawY + dY;

                        int maxX = Math.max(0, rootView.getWidth() - container.getWidth());
                        int maxY = Math.max(0, rootView.getHeight() - container.getHeight());
                        newX = Math.max(0, Math.min(newX, maxX));
                        newY = Math.max(0, Math.min(newY, maxY));

                        container.setX(newX);
                        container.setY(newY);
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    if (!isDragging) {
                        vibrate(15);
                        toggleExpanded();
                    } else {
                        // Persist position
                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(game);
                        prefs.edit()
                                .putFloat(PREF_DOCK_X, container.getX())
                                .putFloat(PREF_DOCK_Y, container.getY())
                                .apply();
                    }
                    isDragging = false;
                    return true;
            }
            return false;
        });

        // Expanded dock row
        expandedScrollView = new HorizontalScrollView(game);
        expandedScrollView.setHorizontalScrollBarEnabled(false);
        expandedScrollView.setVisibility(View.GONE);

        GradientDrawable dockBg = new GradientDrawable();
        dockBg.setShape(GradientDrawable.RECTANGLE);
        dockBg.setCornerRadius(dpToPx(16));
        dockBg.setColor(0xE610141D);
        dockBg.setStroke(dpToPx(1), 0x66FFFFFF);

        macroButtonsRow = new LinearLayout(game);
        macroButtonsRow.setOrientation(LinearLayout.HORIZONTAL);
        macroButtonsRow.setGravity(Gravity.CENTER_VERTICAL);
        macroButtonsRow.setBackground(dockBg);
        macroButtonsRow.setPadding(dpToPx(6), dpToPx(4), dpToPx(6), dpToPx(4));

        populateMacroButtons();

        expandedScrollView.addView(macroButtonsRow, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        container.addView(pillHandle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        scrollParams.topMargin = dpToPx(4);
        container.addView(expandedScrollView, scrollParams);

        // Add to rootView
        FrameLayout.LayoutParams containerParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        containerParams.leftMargin = dpToPx(16);
        containerParams.topMargin = dpToPx(60);
        rootView.addView(container, containerParams);

        // Restore saved position if available
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(game);
        float savedX = prefs.getFloat(PREF_DOCK_X, -1);
        float savedY = prefs.getFloat(PREF_DOCK_Y, -1);
        if (savedX >= 0 && savedY >= 0) {
            container.post(() -> {
                container.setX(savedX);
                container.setY(savedY);
            });
        }
    }

    private void populateMacroButtons() {
        // ESC (Escape)
        addMacroButton("ESC", () -> sendKeyClick((short) 0x1B, (byte) 0));

        // WIN (Windows Key)
        addMacroButton("WIN", () -> sendKeyClick((short) 0x5B, (byte) 0));

        // TAB
        addMacroButton("TAB", () -> sendKeyClick((short) 0x09, (byte) 0));

        // ALT+TAB
        addMacroButton("ALT+TAB", () -> {
            NvConnection conn = game.getConnection();
            if (conn != null) {
                conn.sendKeyboardInput((short) 0x09, KeyboardPacket.KEY_DOWN, KeyboardPacket.MODIFIER_ALT, (byte) 0);
                handler.postDelayed(() -> {
                    if (conn != null) {
                        conn.sendKeyboardInput((short) 0x09, KeyboardPacket.KEY_UP, KeyboardPacket.MODIFIER_ALT, (byte) 0);
                    }
                }, 60);
            }
        });

        // TASK MGR (Ctrl+Shift+Esc)
        addMacroButton("TASKMGR", () -> {
            NvConnection conn = game.getConnection();
            if (conn != null) {
                byte mods = (byte) (KeyboardPacket.MODIFIER_CTRL | KeyboardPacket.MODIFIER_SHIFT);
                conn.sendKeyboardInput((short) 0x1B, KeyboardPacket.KEY_DOWN, mods, (byte) 0);
                handler.postDelayed(() -> {
                    if (conn != null) {
                        conn.sendKeyboardInput((short) 0x1B, KeyboardPacket.KEY_UP, mods, (byte) 0);
                    }
                }, 60);
            }
        });

        // F5 (Quick Save)
        addMacroButton("F5", () -> sendKeyClick((short) 0x74, (byte) 0));

        // F9 (Quick Load)
        addMacroButton("F9", () -> sendKeyClick((short) 0x78, (byte) 0));

        // L3 (Stick Click)
        addMacroButton("L3", () -> pulseControllerFlag(ControllerPacket.LS_CLK_FLAG, 100));

        // R3 (Stick Click)
        addMacroButton("R3", () -> pulseControllerFlag(ControllerPacket.RS_CLK_FLAG, 100));

        // GUIDE (Home / Xbox / PS)
        addMacroButton("GUIDE", () -> pulseControllerFlag(ControllerPacket.SPECIAL_BUTTON_FLAG, 100));

        // TURBO toggle
        Button turboBtn = createButton("TURBO: OFF");
        turboBtnRef = turboBtn;
        turboBtn.setOnClickListener(v -> {
            vibrate(20);
            turboActive = !turboActive;
            if (turboActive) {
                turboBtn.setText("TURBO: ON");
                turboBtn.setTextColor(0xFF4AE590);
                handler.post(turboPulseRunnable);
                Toast.makeText(game, game.getString(R.string.macro_dock_turbo_on), Toast.LENGTH_SHORT).show();
            } else {
                turboBtn.setText("TURBO: OFF");
                turboBtn.setTextColor(0xFFFFFFFF);
                handler.removeCallbacks(turboPulseRunnable);
                Toast.makeText(game, game.getString(R.string.macro_dock_turbo_off), Toast.LENGTH_SHORT).show();
            }
        });
        macroButtonsRow.addView(turboBtn);

        // REMAP (Opens in-game controller remapping)
        addMacroButton("REMAP", () -> {
            collapse();
            game.showControllerRemappingDialog();
        });

        // MENU (EuropaGleam in-game quick menu)
        addMacroButton("MENU", () -> {
            collapse();
            game.showGameMenu(null);
        });

        // Close button
        Button closeBtn = createButton("✕");
        closeBtn.setTextColor(0xFFFF6E6E);
        closeBtn.setOnClickListener(v -> {
            vibrate(10);
            collapse();
        });
        macroButtonsRow.addView(closeBtn);
    }

    private Button createButton(String text) {
        Button btn = new Button(game);
        btn.setText(text);
        btn.setTextColor(0xFFFFFFFF);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        btn.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        btn.setMinWidth(0);
        btn.setMinHeight(0);
        btn.setMinimumWidth(0);
        btn.setMinimumHeight(0);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dpToPx(10));
        bg.setColor(0x33FFFFFF);
        bg.setStroke(dpToPx(1), 0x22FFFFFF);
        btn.setBackground(bg);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dpToPx(30));
        params.leftMargin = dpToPx(3);
        params.rightMargin = dpToPx(3);
        btn.setLayoutParams(params);
        btn.setPadding(dpToPx(8), 0, dpToPx(8), 0);
        return btn;
    }

    private void addMacroButton(String text, Runnable action) {
        Button btn = createButton(text);
        btn.setOnClickListener(v -> {
            vibrate(15);
            action.run();
        });
        macroButtonsRow.addView(btn);
    }

    private void sendKeyClick(short vkCode, byte modifiers) {
        NvConnection conn = game.getConnection();
        if (conn == null) return;
        conn.sendKeyboardInput(vkCode, KeyboardPacket.KEY_DOWN, modifiers, (byte) 0);
        handler.postDelayed(() -> {
            NvConnection c = game.getConnection();
            if (c != null) {
                c.sendKeyboardInput(vkCode, KeyboardPacket.KEY_UP, modifiers, (byte) 0);
            }
        }, 50);
    }

    private void pulseControllerFlag(int buttonFlag, int durationMs) {
        ControllerHandler handler = game.getControllerHandler();
        if (handler == null) return;

        handler.reportOscState(buttonFlag, (short) 0, (short) 0, (short) 0, (short) 0, (byte) 0, (byte) 0);
        if (durationMs > 0) {
            this.handler.postDelayed(() -> {
                ControllerHandler h = game.getControllerHandler();
                if (h != null) {
                    h.reportOscState(0, (short) 0, (short) 0, (short) 0, (short) 0, (byte) 0, (byte) 0);
                }
            }, durationMs);
        }
    }

    public void toggleExpanded() {
        if (isExpanded) {
            collapse();
        } else {
            expand();
        }
    }

    public void expand() {
        isExpanded = true;
        pillHandle.setText("⚡ MACROS ◀");
        expandedScrollView.setVisibility(View.VISIBLE);
    }

    public void collapse() {
        isExpanded = false;
        pillHandle.setText("⚡ MACROS");
        expandedScrollView.setVisibility(View.GONE);
    }

    public void setVisible(boolean visible) {
        if (container != null) {
            container.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    public boolean isVisible() {
        return container != null && container.getVisibility() == View.VISIBLE;
    }

    public void destroy() {
        turboActive = false;
        handler.removeCallbacks(turboPulseRunnable);
        if (rootView != null && container != null) {
            rootView.removeView(container);
        }
    }

    private void vibrate(int ms) {
        try {
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(ms);
            }
        } catch (Exception ignored) {}
    }
}
