package com.gkc3667.terminal;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.text.InputType;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(12,16,20), GREEN = Color.rgb(92,220,145), FG = Color.rgb(225,232,238);
    private TextView screen;
    private EditText input;
    private ScrollView scroll;
    private Process shell;
    private BufferedWriter shellIn;
    private final ArrayList<String> history = new ArrayList<>();
    private volatile boolean busy = false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        buildUi();
        startShell();
        append("Terminal for Android\nReal command shell: /system/bin/sh\nType 'help' for tips.\n");
        prompt();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("▸  TERMINAL");
        title.setTextColor(GREEN);
        title.setTextSize(17);
        title.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        top.addView(title, new LinearLayout.LayoutParams(0, dp(44), 1));
        Button clear = new Button(this);
        clear.setText("CLEAR");
        clear.setOnClickListener(v -> { screen.setText(""); prompt(); });
        top.addView(clear);
        root.addView(top);

        scroll = new ScrollView(this);
        screen = new TextView(this);
        screen.setTextColor(FG);
        screen.setTextSize(13);
        screen.setTypeface(Typeface.MONOSPACE);
        screen.setTextIsSelectable(true);
        screen.setPadding(dp(4), dp(8), dp(4), dp(12));
        scroll.addView(screen);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        input = new EditText(this);
        input.setSingleLine(true);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.GRAY);
        input.setHint("Enter command…");
        input.setTextSize(14);
        input.setTypeface(Typeface.MONOSPACE);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_SEND) { runCommand(); return true; }
            return false;
        });
        bar.addView(input, new LinearLayout.LayoutParams(0, dp(52), 1));
        Button run = new Button(this);
        run.setText("RUN");
        run.setTextColor(Color.BLACK);
        run.setBackgroundTintList(android.content.res.ColorStateList.valueOf(GREEN));
        run.setOnClickListener(v -> runCommand());
        bar.addView(run);
        root.addView(bar);
        setContentView(root);
    }

    private void startShell() {
        try {
            shell = new ProcessBuilder("/system/bin/sh").redirectErrorStream(true).start();
            shellIn = new BufferedWriter(new OutputStreamWriter(shell.getOutputStream()));
            shellIn.write("cd " + getFilesDir().getAbsolutePath() + "\n");
            shellIn.flush();
        } catch (Exception e) { append("Cannot start shell: " + e.getMessage() + "\n"); }
    }

    private void runCommand() {
        if (busy || shellIn == null) return;
        String cmd = input.getText().toString().trim();
        if (cmd.isEmpty()) return;
        input.setText("");
        if (cmd.equals("clear")) { screen.setText(""); prompt(); return; }
        if (cmd.equals("help")) {
            append("\n$ help\nTry: pwd, ls, cd, mkdir, touch, cat, echo, whoami, id, uname, date, env, getprop, toybox.\nAndroid shell is restricted; this app does not include Debian, apt or sudo.\n");
            prompt(); return;
        }
        history.add(cmd);
        append("\n$ " + cmd + "\n");
        busy = true;
        final String marker = "__TERM_DONE_" + System.nanoTime() + "__";
        new Thread(() -> {
            try {
                shellIn.write(cmd + "\n");
                shellIn.write("printf '" + marker + ":%s\\n' $?\n");
                shellIn.flush();
                BufferedReader reader = new BufferedReader(new InputStreamReader(shell.getInputStream()));
                String line;
                StringBuilder result = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith(marker + ":")) break;
                    result.append(line).append("\n");
                }
                String output = result.toString();
                runOnUiThread(() -> {
                    if (!output.isEmpty()) append(output);
                    prompt();
                    busy = false;
                });
            } catch (Exception e) {
                runOnUiThread(() -> { append("Shell error: " + e.getMessage() + "\n"); busy = false; });
            }
        }, "terminal-command").start();
    }

    private void append(String value) {
        screen.append(value);
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }
    private void prompt() {
        screen.append("\n$ ");
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }

    @Override protected void onDestroy() {
        try { if (shellIn != null) shellIn.close(); } catch (Exception ignored) {}
        if (shell != null) shell.destroy();
        super.onDestroy();
    }
}
