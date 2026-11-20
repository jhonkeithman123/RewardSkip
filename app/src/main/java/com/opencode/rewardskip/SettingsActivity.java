package com.opencode.rewardskip;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Settings screen exposed as the gear icon in LSPosed / Vector's module list.
 *
 * Scope is intentionally NOT edited here: it lives in the framework's root-only
 * database. Use the framework manager's Scope tab (button below) for that.
 * Mode is stored in this module's own SharedPreferences and is read by the hook
 * through XSharedPreferences, with a file fallback for older frameworks.
 */
public class SettingsActivity extends Activity {
    private static final String PREFS = "rewardskip";
    private static final String KEY_MODE = "mode";
    private static final String MODE_INSTANT = "instant";
    private static final String MODE_DELAYED = "delayed";
    private static final String MODE_FILE = "/data/local/tmp/skip_mode.txt";

    private static final String BG = "#0F1117";
    private static final String CARD = "#1C2030";
    private static final String ACCENT = "#6C7BFF";
    private static final String WHITE = "#F2F4FF";
    private static final String MUTED = "#9AA3B2";

    private TextView tvMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("RewardSkip settings");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor(BG));
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = dp(16);
        root.setPadding(pad, dp(24), pad, pad);

        TextView title = new TextView(this);
        title.setText("REWARDSKIP");
        title.setTextColor(Color.parseColor(ACCENT));
        title.setTextSize(20);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setLetterSpacing(2f);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Reward-ad skipper for Xposed / LSPosed / Vector");
        sub.setTextColor(Color.parseColor(MUTED));
        sub.setTextSize(12);
        sub.setPadding(0, dp(4), 0, dp(18));
        root.addView(sub);

        LinearLayout modeCard = card();
        TextView modeLabel = new TextView(this);
        modeLabel.setText("SKIP MODE");
        modeLabel.setTextColor(Color.parseColor(ACCENT));
        modeLabel.setTextSize(11);
        modeLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        modeLabel.setLetterSpacing(1f);
        modeCard.addView(modeLabel);

        tvMode = new TextView(this);
        tvMode.setTextSize(15);
        tvMode.setTextColor(Color.parseColor(WHITE));
        tvMode.setPadding(0, dp(8), 0, dp(12));
        modeCard.addView(tvMode);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(button("INSTANT", v -> setMode(MODE_INSTANT)));
        Button delayed = button("DELAYED (~5s)", v -> setMode(MODE_DELAYED));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(8);
        delayed.setLayoutParams(lp);
        row.addView(delayed);
        modeCard.addView(row);
        root.addView(modeCard);

        LinearLayout scopeCard = card();
        TextView scopeLabel = new TextView(this);
        scopeLabel.setText("GAME SCOPE");
        scopeLabel.setTextColor(Color.parseColor(ACCENT));
        scopeLabel.setTextSize(11);
        scopeLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        scopeLabel.setLetterSpacing(1f);
        scopeCard.addView(scopeLabel);

        TextView scopeInfo = new TextView(this);
        scopeInfo.setText("Scope is stored by the Xposed framework (root-only), so it "
                + "cannot be changed from here. Open your framework manager, select "
                + "RewardSkip, then check the games in its Scope tab.");
        scopeInfo.setTextColor(Color.parseColor(MUTED));
        scopeInfo.setTextSize(13);
        scopeInfo.setPadding(0, dp(8), 0, dp(12));
        scopeCard.addView(scopeInfo);
        scopeCard.addView(button("OPEN FRAMEWORK MANAGER", v -> openManager()));
        root.addView(scopeCard);

        LinearLayout infoCard = card();
        TextView info = new TextView(this);
        info.setText("Changes to mode apply to newly launched games. To see which ad "
                + "SDKs were detected and how many hooks installed, check the "
                + "'RewardSkip self-check' line in the framework log.");
        info.setTextColor(Color.parseColor(MUTED));
        info.setTextSize(12);
        infoCard.addView(info);
        root.addView(infoCard);

        setContentView(root);
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        String mode = readMode();
        tvMode.setText(mode.equals(MODE_DELAYED)
                ? "DELAYED - ad plays ~5s, then reward is granted"
                : "INSTANT - ad skipped, reward granted immediately");
    }

    private String readMode() {
        try {
            SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
            String m = p.getString(KEY_MODE, MODE_INSTANT);
            if (MODE_DELAYED.equals(m) || MODE_INSTANT.equals(m)) return m;
        } catch (Throwable ignore) {
        }
        try {
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(MODE_FILE));
            String s = r.readLine();
            r.close();
            if (s != null && s.trim().equals(MODE_DELAYED)) return MODE_DELAYED;
        } catch (Throwable ignore) {
        }
        return MODE_INSTANT;
    }

    private void setMode(String mode) {
        try {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_MODE, mode).commit();
        } catch (Throwable ignore) {
        }
        // best effort: also update the world-readable file where the hook reads it.
        // Apps normally cannot create files in /data/local/tmp; RamGuard's root
        // daemon handles that when it is installed.
        try {
            java.io.FileOutputStream out = new java.io.FileOutputStream(MODE_FILE);
            out.write(mode.getBytes("UTF-8"));
            out.close();
        } catch (Throwable ignore) {
        }
        refresh();
        Toast.makeText(this, "Mode: " + mode + " (restart the game)", Toast.LENGTH_SHORT).show();
    }

    private void openManager() {
        String[] packages = {"org.lsposed.manager", "de.robv.android.xposed.installer", "com.topjohnwu.magisk"};
        for (String p : packages) {
            Intent i = getPackageManager().getLaunchIntentForPackage(p);
            if (i != null) {
                startActivity(i);
                return;
            }
        }
        Toast.makeText(this, "No Xposed manager found", Toast.LENGTH_SHORT).show();
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor(CARD));
        gd.setCornerRadius(dp(18));
        c.setBackground(gd);
        c.setPadding(dp(18), dp(16), dp(18), dp(16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        c.setLayoutParams(lp);
        return c;
    }

    private Button button(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(12);
        b.setAllCaps(true);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor("#2A3050"));
        gd.setCornerRadius(dp(10));
        b.setBackground(gd);
        b.setOnClickListener(l);
        return b;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
