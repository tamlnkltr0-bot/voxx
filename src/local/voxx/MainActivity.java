package local.voxx;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

/**
 * Remote-friendly settings screen.
 *
 * A single centered card holds the settings on the left and the live status / box check on the
 * right. Every setting is a focusable row: left/right changes it, OK advances it.
 */
public class MainActivity extends Activity {
    private static final int ID_POWER = 0, ID_PRESET = 1, ID_LIFT = 2, ID_BASS = 3;

    // Palette
    private static final int BG_TOP = Color.rgb(10, 16, 29);
    private static final int BG_BOTTOM = Color.rgb(18, 26, 43);
    private static final int PANEL = Color.rgb(23, 32, 51);
    private static final int BORDER = Color.rgb(41, 55, 83);
    private static final int ROW = Color.rgb(31, 42, 65);
    private static final int ROW_FOCUS = Color.rgb(44, 61, 97);
    private static final int ACCENT = Color.rgb(230, 190, 128);
    private static final int TEXT = Color.rgb(232, 238, 248);
    private static final int TEXT_DIM = Color.rgb(140, 157, 189);
    private static final int GREEN = Color.rgb(126, 217, 160);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Params params;
    private TextView powerVal, presetVal, liftVal, bassVal;
    private TextView status, setup;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, new int[]{BG_TOP, BG_BOTTOM}));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(rounded(PANEL, 22, 1, BORDER));
        panel.setPadding(dp(32), dp(18), dp(32), dp(18));
        panel.setElevation(dp(14));

        // Brand lockup (VOXX wordmark + "AUDIO CLARITY" tagline), drawn from the supplied artwork.
        ImageView brand = new ImageView(this);
        brand.setImageResource(R.drawable.logo);
        brand.setAdjustViewBounds(true);
        brand.setScaleType(ImageView.ScaleType.FIT_START);
        LinearLayout.LayoutParams brandLp = new LinearLayout.LayoutParams(
                dp(206), LinearLayout.LayoutParams.WRAP_CONTENT);
        panel.addView(brand, brandLp);

        TextView subtitle = new TextView(this);
        subtitle.setText("Evens out loud action and quiet dialogue for every app. "
                + "Press \u25c0 \u25b6 to change a setting.");
        subtitle.setTextSize(14);
        subtitle.setTextColor(TEXT_DIM);
        subtitle.setPadding(0, dp(8), 0, 0);
        panel.addView(subtitle);

        panel.addView(divider(dp(10), dp(10)));

        // Settings on the left, status and box check on the right.
        LinearLayout columns = new LinearLayout(this);
        columns.setOrientation(LinearLayout.HORIZONTAL);
        panel.addView(columns);

        LinearLayout settings = new LinearLayout(this);
        settings.setOrientation(LinearLayout.VERTICAL);
        columns.addView(settings, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1.15f));

        powerVal = makeSetting(settings, ID_POWER, "VOXX", false);
        presetVal = makeSetting(settings, ID_PRESET, "Evening strength", true);
        liftVal = makeSetting(settings, ID_LIFT, "Voice boost", true);
        bassVal = makeSetting(settings, ID_BASS, "Bass cut", true);

        settings.addView(divider(dp(4), dp(10)));

        makeAction(settings, "Re-attach effect", () -> push(true));
        makeAction(settings, "Check TV box settings",
                () -> setup.setText(SetupCheck.run(this)));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(30), 0, 0, 0);
        columns.addView(info, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        info.addView(sectionLabel("STATUS"));
        status = new TextView(this);
        status.setTextSize(17);
        status.setTextColor(GREEN);
        status.setLineSpacing(dp(2), 1f);
        info.addView(status);

        info.addView(sectionLabel("TV BOX SETTINGS"));
        setup = new TextView(this);
        setup.setText("Not checked yet. Use \"Check TV box settings\".");
        setup.setTextSize(14);
        setup.setTextColor(TEXT_DIM);
        setup.setLineSpacing(dp(4), 1f);
        info.addView(setup);

        // The card is centred when it fits; if the TV viewport is short, it scrolls so no row is
        // ever pushed off-screen (the D-pad scrolls the focused row into view).
        FrameLayout holder = new FrameLayout(this);
        holder.setPadding(0, dp(8), 0, dp(8));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                panelWidth(), LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER;
        holder.addView(panel, lp);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.addView(holder, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        root.addView(scroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        setContentView(root);
        powerVal.requestFocus();
        requestNotificationPermission();
    }

    /** Android 13+ hides the foreground-service notification until this is granted. */
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        params = VoxxService.instance != null
                ? VoxxService.instance.params() : Params.load(this);
        VoxxService.send(this, null);
        refresh();
        handler.postDelayed(this::refresh, 800);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Drop the pending refresh so a finishing activity isn't held alive by the handler.
        handler.removeCallbacksAndMessages(null);
    }

    // ---------------------------------------------------------------------------------------------
    // View building
    // ---------------------------------------------------------------------------------------------

    /** A setting row: label on the left, current value on the right. Returns the value view. */
    private TextView makeSetting(LinearLayout parent, int id, String label, boolean steppable) {
        LinearLayout row = rowShell(label);

        TextView value = new TextView(this);
        value.setTextSize(18);
        value.setTextColor(rowTextColor(ACCENT));
        value.setDuplicateParentStateEnabled(true);
        value.setGravity(Gravity.END);
        row.addView(value);

        row.setOnClickListener(v -> {
            if (id == ID_POWER) {
                params.enabled = !params.enabled;
                push(false);
            } else {
                step(id, 1);
            }
        });
        if (steppable) {
            row.setOnKeyListener((v, code, ev) -> {
                if (ev.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (code == KeyEvent.KEYCODE_DPAD_LEFT) { step(id, -1); return true; }
                if (code == KeyEvent.KEYCODE_DPAD_RIGHT) { step(id, 1); return true; }
                return false;
            });
        }
        addRow(parent, row);
        return value;
    }

    /** An action row: label only, runs the action when activated. */
    private void makeAction(LinearLayout parent, String label, Runnable action) {
        LinearLayout row = rowShell(label);
        row.setOnClickListener(v -> action.run());
        addRow(parent, row);
    }

    private LinearLayout rowShell(String label) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(rowBackground());
        row.setFocusable(true);
        row.setClickable(true);
        row.setPadding(dp(20), dp(10), dp(20), dp(10));

        TextView name = new TextView(this);
        name.setText(label);
        name.setTextSize(18);
        name.setTextColor(rowTextColor(TEXT));
        name.setDuplicateParentStateEnabled(true);
        row.addView(name, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    private void addRow(LinearLayout parent, View row) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(6);
        parent.addView(row, lp);
    }

    private View divider(int top, int bottom) {
        View v = new View(this);
        v.setBackgroundColor(BORDER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(1)));
        lp.topMargin = top;
        lp.bottomMargin = bottom;
        v.setLayoutParams(lp);
        return v;
    }

    private TextView sectionLabel(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(12);
        t.setLetterSpacing(0.12f);
        t.setTextColor(TEXT_DIM);
        t.setPadding(0, dp(4), 0, dp(6));
        return t;
    }

    private Drawable rowBackground() {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, rounded(ROW_FOCUS, 14, 2, ACCENT));
        s.addState(new int[]{android.R.attr.state_pressed}, rounded(ROW_FOCUS, 14, 2, ACCENT));
        s.addState(new int[]{}, rounded(ROW, 14, 0, 0));
        return s;
    }

    private ColorStateList rowTextColor(int base) {
        return new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_focused}, new int[]{}},
                new int[]{Color.WHITE, base});
    }

    private GradientDrawable rounded(int color, float radiusDp, int strokeDp, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) d.setStroke(dp(strokeDp), strokeColor);
        return d;
    }

    private int panelWidth() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        return Math.min((int) (dm.widthPixels * 0.84f), dp(880));
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    // ---------------------------------------------------------------------------------------------
    // Behaviour
    // ---------------------------------------------------------------------------------------------

    /** Steps a setting up or down; stops at either end of the range rather than wrapping. */
    private void step(int id, int dir) {
        if (id == ID_PRESET) {
            int p = params.preset + dir;
            if (p < 0 || p >= Params.PRESET_NAMES.length) return;
            params.applyPreset(p);
        } else if (id == ID_LIFT) {
            float v = params.lift + 1.5f * dir;
            if (v < 0f || v > 9f) return;
            params.lift = v;
        } else if (id == ID_BASS) {
            float v = params.bassCut - 3f * dir;
            if (v < -12f || v > 0f) return;
            params.bassCut = v;
        }
        push(false);
    }

    /** Persists params and hands them to the service. */
    private void push(boolean restart) {
        params.save(this);
        Intent extras = new Intent();
        extras.putExtra("preset", params.preset);
        extras.putExtra("enabled", params.enabled);
        extras.putExtra("comp_thr", params.compThr);
        extras.putExtra("comp_ratio", params.compRatio);
        extras.putExtra("comp_makeup", params.compMakeup);
        extras.putExtra("lift", params.lift);
        extras.putExtra("bass_cut", params.bassCut);
        extras.putExtra(VoxxService.EXTRA_RESTART, restart);
        VoxxService.send(this, extras);
        refresh();
        handler.postDelayed(this::refresh, 800);
    }

    private void refresh() {
        if (VoxxService.instance != null) params = VoxxService.instance.params();
        powerVal.setText(params.enabled ? "On" : "Off");
        presetVal.setText("\u25c0  " + Params.PRESET_NAMES[params.preset] + "  \u25b6");
        liftVal.setText(String.format(Locale.US, "\u25c0  +%.1f dB  \u25b6", params.lift));
        bassVal.setText(params.bassCut == 0f ? "\u25c0  Off  \u25b6"
                : String.format(Locale.US, "\u25c0  %.0f dB  \u25b6", params.bassCut));
        status.setText(VoxxService.instance != null
                ? VoxxService.instance.status() : "Service starting\u2026");
    }
}
