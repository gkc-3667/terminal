package com.gkc3667.terminal;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.text.InputType;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(10, 14, 19);
    private static final int PANEL = Color.rgb(18, 25, 33);
    private static final int GREEN = Color.rgb(92, 230, 157);
    private static final int BLUE = Color.rgb(104, 174, 255);
    private static final int FG = Color.rgb(226, 234, 242);
    private static final int MUTED = Color.rgb(132, 148, 164);

    private TextView output, pathLabel, statusLabel;
    private EditText input;
    private ScrollView scroll;
    private Process shell;
    private BufferedWriter shellIn;
    private BufferedReader shellOut;
    private final ArrayList<String> history = new ArrayList<>();
    private int historyIndex = 0;
    private volatile boolean busy = false;
    private String currentPath = "";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);
        currentPath = getFilesDir().getAbsolutePath();
        buildUi();
        startShell();
        writeLine("TERMINAL PRO  •  ANDROID SHELL", GREEN, true);
        writeLine("Phiên shell thật đang chạy trong môi trường Android.", MUTED, false);
        writeLine("Android ABI: " + android.text.TextUtils.join(", ", Build.SUPPORTED_ABIS), BLUE, false);
        writeLine("Android API: " + Build.VERSION.SDK_INT, MUTED, false);
        writeLine("Gõ help để xem lệnh. Thư mục mặc định: " + currentPath, MUTED, false);
        showPrompt();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(14), dp(8), dp(14), dp(10));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(8), dp(12), dp(8));
        header.setBackground(round(PANEL, 14));
        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText("TERMINAL PRO");
        title.setTextColor(GREEN);
        title.setTextSize(16);
        title.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        TextView subtitle = new TextView(this);
        subtitle.setText("LOCAL SHELL  /  SESSION ACTIVE");
        subtitle.setTextColor(MUTED);
        subtitle.setTextSize(9);
        subtitle.setTypeface(Typeface.MONOSPACE);
        brand.addView(title);
        brand.addView(subtitle);
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        Button copy = smallButton("COPY");
        copy.setOnClickListener(v -> copyOutput());
        header.addView(copy);
        Button clear = smallButton("CLEAR");
        clear.setOnClickListener(v -> { output.setText(""); showPrompt(); });
        LinearLayout.LayoutParams clearLp = new LinearLayout.LayoutParams(-2, dp(36));
        clearLp.leftMargin = dp(6);
        header.addView(clear, clearLp);
        root.addView(header);

        LinearLayout info = new LinearLayout(this);
        info.setGravity(Gravity.CENTER_VERTICAL);
        info.setPadding(dp(4), dp(9), dp(4), dp(7));
        TextView dot = new TextView(this);
        dot.setText("●");
        dot.setTextColor(GREEN);
        dot.setTextSize(10);
        info.addView(dot);
        statusLabel = new TextView(this);
        statusLabel.setText("  SHELL READY");
        statusLabel.setTextColor(MUTED);
        statusLabel.setTextSize(10);
        statusLabel.setTypeface(Typeface.MONOSPACE);
        info.addView(statusLabel);
        root.addView(info);

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackground(round(Color.rgb(6, 9, 13), 12));
        output = new TextView(this);
        output.setTextColor(FG);
        output.setTextSize(13);
        output.setTypeface(Typeface.MONOSPACE);
        output.setTextIsSelectable(true);
        output.setPadding(dp(12), dp(12), dp(12), dp(16));
        output.setLineSpacing(dp(2), 1.0f);
        scroll.addView(output, new ScrollView.LayoutParams(-1, -2));
        LinearLayout.LayoutParams terminalLp = new LinearLayout.LayoutParams(-1, 0, 1);
        root.addView(scroll, terminalLp);

        pathLabel = new TextView(this);
        pathLabel.setTextColor(BLUE);
        pathLabel.setTextSize(10);
        pathLabel.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        pathLabel.setSingleLine(true);
        pathLabel.setPadding(dp(5), dp(9), dp(5), dp(5));
        root.addView(pathLabel);

        HorizontalScrollView quickScroll = new HorizontalScrollView(this);
        quickScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout quick = new LinearLayout(this);
        quick.setGravity(Gravity.CENTER_VERTICAL);
        addQuick(quick, "pwd");
        addQuick(quick, "ls -la");
        addQuick(quick, "whoami");
        addQuick(quick, "uname -a");
        addQuick(quick, "python --version");
        quickScroll.addView(quick);
        root.addView(quickScroll);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(3), dp(4), dp(3));
        bar.setBackground(round(PANEL, 12));
        input = new EditText(this);
        input.setSingleLine(true);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(MUTED);
        input.setHint("Nhập lệnh...");
        input.setTextSize(14);
        input.setTypeface(Typeface.MONOSPACE);
        input.setPadding(dp(5), 0, dp(5), 0);
        input.setBackgroundColor(Color.TRANSPARENT);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_SEND ||
                (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                runCommand();
                return true;
            }
            return false;
        });
        input.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                browseHistory(-1); return true;
            }
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                browseHistory(1); return true;
            }
            return false;
        });
        bar.addView(input, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button run = new Button(this);
        run.setText("RUN  ▶");
        run.setTextColor(Color.rgb(5, 18, 12));
        run.setTextSize(12);
        run.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        run.setAllCaps(false);
        run.setBackground(round(GREEN, 9));
        run.setOnClickListener(v -> runCommand());
        bar.addView(run, new LinearLayout.LayoutParams(dp(88), dp(42)));
        root.addView(bar);
        setContentView(root);
    }

    private void addQuick(LinearLayout row, String command) {
        Button b = smallButton(command);
        b.setOnClickListener(v -> { input.setText(command); runCommand(); });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(32));
        lp.rightMargin = dp(6);
        lp.bottomMargin = dp(7);
        row.addView(b, lp);
    }

    private Button smallButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(9);
        b.setTextColor(FG);
        b.setAllCaps(false);
        b.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(9), 0, dp(9), 0);
        b.setBackground(round(Color.rgb(35, 47, 60), 9));
        return b;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private void startShell() {
        try {
            shell = new ProcessBuilder("/system/bin/sh").redirectErrorStream(true).start();
            shellIn = new BufferedWriter(new OutputStreamWriter(shell.getOutputStream()));
            shellOut = new BufferedReader(new InputStreamReader(shell.getInputStream()));
            shellIn.write("cd " + quote(currentPath) + "\n");
            shellIn.flush();
            statusLabel.setText("  SHELL READY  •  /system/bin/sh");
        } catch (Exception e) {
            statusLabel.setText("  SHELL ERROR");
            writeLine("Không thể khởi động shell: " + e.getMessage(), Color.RED, false);
        }
    }

    private String quote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private void runCommand() {
        if (busy || shellIn == null || shellOut == null) return;
        final String cmd = input.getText().toString().trim();
        if (cmd.isEmpty()) return;
        input.setText("");
        hideKeyboard();
        if (cmd.matches("^(python|python3|pip|pip3)(\\s|$).*$")) { runPythonCommand(cmd); return; }
        if ("clear".equals(cmd)) { output.setText(""); showPrompt(); return; }
        if ("help".equals(cmd)) { showHelp(); return; }
        if ("history".equals(cmd)) {
            writeLine("$ history", BLUE, true);
            for (int i = 0; i < history.size(); i++) writeLine((i + 1) + "  " + history.get(i), FG, false);
            showPrompt(); return;
        }
        if ("exit".equals(cmd)) {
            stopShell();
            startShell();
            writeLine("Đã mở phiên shell mới.", GREEN, false);
            showPrompt(); return;
        }
        history.add(cmd);
        historyIndex = history.size();
        writeLine("$ " + cmd, BLUE, true);
        busy = true;
        statusLabel.setText("  RUNNING...");
        final String marker = "__TP_DONE_" + System.nanoTime() + "__";
        new Thread(() -> {
            StringBuilder result = new StringBuilder();
            String newPath = currentPath;
            try {
                shellIn.write(cmd + "\n");
                shellIn.write("printf '\\n" + marker + ":%s:%s\\n' \"$?\" \"$PWD\"\n");
                shellIn.flush();
                String line;
                while ((line = shellOut.readLine()) != null) {
                    if (line.startsWith(marker + ":")) {
                        String[] parts = line.split(":", 3);
                        if (parts.length >= 3) newPath = parts[2];
                        break;
                    }
                    result.append(line).append("\n");
                }
                final String text = result.toString();
                final String path = newPath;
                runOnUiThread(() -> {
                    if (!text.isEmpty()) output.append(text);
                    currentPath = path;
                    statusLabel.setText("  SHELL READY");
                    busy = false;
                    showPrompt();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    writeLine("Lỗi shell: " + e.getMessage(), Color.RED, false);
                    statusLabel.setText("  SHELL ERROR");
                    busy = false;
                });
            }
        }, "terminal-command").start();
    }

    private void runPythonCommand(String cmd) {
        history.add(cmd);
        historyIndex = history.size();
        writeLine("$ " + cmd, BLUE, true);
        busy = true;
        statusLabel.setText("  PYTHON RUNNING...");
        final String path = currentPath;
        new Thread(() -> {
            try {
                if (!Python.isStarted()) Python.start(new AndroidPlatform(getApplicationContext()));
                String result = Python.getInstance()
                        .getModule("terminal_runner")
                        .callAttr("run_command", cmd, path)
                        .toString();
                runOnUiThread(() -> {
                    if (!result.isEmpty()) {
                        output.append(result);
                        if (!result.endsWith("\n")) output.append("\n");
                    }
                    statusLabel.setText("  PYTHON READY");
                    busy = false;
                    showPrompt();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    writeLine("Python error: " + e.getMessage(), Color.RED, false);
                    statusLabel.setText("  PYTHON ERROR");
                    busy = false;
                    showPrompt();
                });
            }
        }, "terminal-python").start();
    }

    private void showHelp() {
        writeLine("$ help", BLUE, true);
        writeLine("LỆNH CƠ BẢN", GREEN, true);
        writeLine("pwd       xem thư mục hiện tại", FG, false);
        writeLine("ls -la    liệt kê tệp và thư mục", FG, false);
        writeLine("cd PATH   chuyển thư mục (được giữ giữa các lệnh)", FG, false);
        writeLine("mkdir X   tạo thư mục     touch X   tạo tệp", FG, false);
        writeLine("cat X     đọc tệp         rm X      xóa tệp", FG, false);
        writeLine("cp A B    sao chép        mv A B    di chuyển/đổi tên", FG, false);
        writeLine("echo TEXT in văn bản      env       xem biến môi trường", FG, false);
        writeLine("history   xem lịch sử     clear     xóa màn hình", FG, false);
        writeLine("exit      khởi động phiên shell mới", FG, false);
        writeLine("LƯU Ý", GREEN, true);
        writeLine("Đây là shell Android thật (/system/bin/sh), không phải Debian.", MUTED, false);
        writeLine("Python 3.11 được tích hợp: python --version, python file.py, python -c \"print(123)\"", MUTED, false);\n        writeLine("discord.py được đóng gói sẵn: pip list / pip show discord.py", MUTED, false);\n        writeLine("pip cài thêm gói tùy ý chưa hỗ trợ; apt và sudo không có.", MUTED, false);
        showPrompt();
    }

    private void browseHistory(int direction) {
        if (history.isEmpty()) return;
        historyIndex = Math.max(0, Math.min(history.size(), historyIndex + direction));
        input.setText(historyIndex == history.size() ? "" : history.get(historyIndex));
        input.setSelection(input.length());
    }

    private void showPrompt() {
        pathLabel.setText(" " + currentPath);
        output.append("\n");
        output.append(currentPath + " $ ");
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private void writeLine(String value, int color, boolean bold) {
        android.text.SpannableString s = new android.text.SpannableString(value + "\n");
        s.setSpan(new android.text.style.ForegroundColorSpan(color), 0, s.length(),
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (bold) s.setSpan(new android.text.style.StyleSpan(Typeface.BOLD), 0, s.length(),
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        output.append(s);
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private void copyOutput() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("Terminal output", output.getText()));
        statusLabel.setText("  COPIED TO CLIPBOARD");
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        input.clearFocus();
    }

    private void stopShell() {
        try { if (shellIn != null) shellIn.close(); } catch (Exception ignored) {}
        try { if (shellOut != null) shellOut.close(); } catch (Exception ignored) {}
        if (shell != null) shell.destroy();
        shell = null; shellIn = null; shellOut = null;
    }

    @Override protected void onDestroy() {
        stopShell();
        super.onDestroy();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
