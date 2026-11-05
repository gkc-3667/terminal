package com.gkc3667.terminal;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.ChannelShell;
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
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.app.Dialog;
import android.text.InputType;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Properties;

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
    private Session sshSession;
    private ChannelShell sshChannel;
    private BufferedWriter sshIn;
    private BufferedReader sshOut;
    private boolean sshConnected = false;
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
        int screenWidthDp = (int)(getResources().getDisplayMetrics().widthPixels /
                getResources().getDisplayMetrics().density);
        final boolean desktopMode = screenWidthDp >= 700;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(desktopMode ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(desktopMode ? 10 : 14), dp(8), dp(desktopMode ? 10 : 14), dp(10));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(desktopMode ? 10 : 0), 0, 0, 0);

        if (desktopMode) {
            LinearLayout sidebar = new LinearLayout(this);
            sidebar.setOrientation(LinearLayout.VERTICAL);
            sidebar.setPadding(dp(12), dp(16), dp(12), dp(12));
            sidebar.setBackground(round(PANEL, 16));
            TextView sideBrand = new TextView(this);
            sideBrand.setText("TP");
            sideBrand.setTextColor(GREEN);
            sideBrand.setTextSize(25);
            sideBrand.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            sideBrand.setGravity(Gravity.CENTER);
            sidebar.addView(sideBrand, new LinearLayout.LayoutParams(-1, dp(52)));
            TextView sideTitle = new TextView(this);
            sideTitle.setText("TERMINAL\\nPRO");
            sideTitle.setTextColor(FG);
            sideTitle.setTextSize(13);
            sideTitle.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            sideTitle.setGravity(Gravity.CENTER);
            sidebar.addView(sideTitle, new LinearLayout.LayoutParams(-1, dp(48)));
            View divider = new View(this);
            divider.setBackgroundColor(Color.rgb(43, 56, 70));
            LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(-1, dp(1));
            dividerLp.topMargin = dp(12); dividerLp.bottomMargin = dp(12);
            sidebar.addView(divider, dividerLp);

            TextView navCaption = new TextView(this);
            navCaption.setText("WORKSPACE");
            navCaption.setTextColor(MUTED);
            navCaption.setTextSize(9);
            navCaption.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            sidebar.addView(navCaption);
            Button navLocal = smallButton("▣  Terminal");
            navLocal.setGravity(Gravity.CENTER_VERTICAL);
            navLocal.setTextColor(GREEN);
            navLocal.setOnClickListener(v -> {
                if (sshConnected) new AlertDialog.Builder(this).setTitle("Đang ở phiên VPS")
                    .setMessage("Ngắt SSH để quay lại terminal thiết bị?")
                    .setNegativeButton("Ở lại", null)
                    .setPositiveButton("Ngắt SSH", (d,w) -> disconnectSsh()).show();
                else statusLabel.setText("  LOCAL SHELL  •  /system/bin/sh");
            });
            LinearLayout.LayoutParams navLp = new LinearLayout.LayoutParams(-1, dp(44));
            navLp.topMargin = dp(8); sidebar.addView(navLocal, navLp);
            Button navSsh = smallButton("⇄  SSH / VPS");
            navSsh.setGravity(Gravity.CENTER_VERTICAL);
            navSsh.setTextColor(BLUE);
            navSsh.setOnClickListener(v -> showSshDialog());
            LinearLayout.LayoutParams navSshLp = new LinearLayout.LayoutParams(-1, dp(44));
            navSshLp.topMargin = dp(5); sidebar.addView(navSsh, navSshLp);
            View spacer = new View(this);
            sidebar.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1));
            TextView sideFooter = new TextView(this);
            sideFooter.setText("ANDROID SHELL\\nSSH CLIENT");
            sideFooter.setTextColor(MUTED);
            sideFooter.setTextSize(9);
            sideFooter.setGravity(Gravity.CENTER);
            sidebar.addView(sideFooter);
            LinearLayout.LayoutParams sideLp = new LinearLayout.LayoutParams(dp(176), -1);
            sideLp.rightMargin = dp(8);
            root.addView(sidebar, sideLp);
        }

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
        Button connect = smallButton("VPS / SSH");
        connect.setOnClickListener(v -> showSshDialog());
        LinearLayout.LayoutParams connectLp = new LinearLayout.LayoutParams(-2, dp(36));
        connectLp.leftMargin = dp(6);
        header.addView(connect, connectLp);
        Button paste = smallButton("PASTE");
        paste.setOnClickListener(v -> pasteInput());
        LinearLayout.LayoutParams pasteLp = new LinearLayout.LayoutParams(-2, dp(36));
        pasteLp.leftMargin = dp(6);
        header.addView(paste, pasteLp);
        Button clear = smallButton("CLEAR");
        clear.setOnClickListener(v -> { output.setText(""); showPrompt(); });
        LinearLayout.LayoutParams clearLp = new LinearLayout.LayoutParams(-2, dp(36));
        clearLp.leftMargin = dp(6);
        header.addView(clear, clearLp);
        content.addView(header);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(0, dp(9), 0, dp(3));
        Button localTab = smallButton("●  LOCAL TERMINAL");
        localTab.setTextColor(GREEN);
        localTab.setOnClickListener(v -> {
            if (sshConnected) {
                new AlertDialog.Builder(this).setTitle("Đang kết nối VPS")
                    .setMessage("Bạn đang ở phiên SSH. Ngắt kết nối để quay về terminal trên thiết bị?")
                    .setNegativeButton("Ở lại", null)
                    .setPositiveButton("Ngắt VPS", (d,w) -> disconnectSsh()).show();
            } else statusLabel.setText("  LOCAL SHELL  •  /system/bin/sh");
        });
        tabs.addView(localTab, new LinearLayout.LayoutParams(0, dp(38), 1));
        Button sshTab = smallButton("⇄  SSH / VPS");
        sshTab.setTextColor(BLUE);
        sshTab.setOnClickListener(v -> showSshDialog());
        LinearLayout.LayoutParams sshTabLp = new LinearLayout.LayoutParams(0, dp(38), 1);
        sshTabLp.leftMargin = dp(7);
        tabs.addView(sshTab, sshTabLp);
        Button discordTab = smallButton("◎  DISCORD");
        discordTab.setTextColor(Color.rgb(190, 165, 255));
        LinearLayout.LayoutParams discordTabLp = new LinearLayout.LayoutParams(0, dp(38), 1);
        discordTabLp.leftMargin = dp(7);
        tabs.addView(discordTab, discordTabLp);
        discordTab.setOnClickListener(v -> openDiscordDesktop());
        content.addView(tabs);

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
        content.addView(info);

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackground(round(Color.rgb(6, 9, 13), 12));
        output = new TextView(this);
        output.setTextColor(FG);
        output.setTextSize(desktopMode ? 15 : 13);
        output.setTypeface(Typeface.MONOSPACE);
        output.setTextIsSelectable(true);
        output.setPadding(dp(12), dp(12), dp(12), dp(16));
        output.setLineSpacing(dp(2), 1.0f);
        scroll.addView(output, new ScrollView.LayoutParams(-1, -2));
        LinearLayout.LayoutParams terminalLp = new LinearLayout.LayoutParams(-1, 0, 1);
        content.addView(scroll, terminalLp);

        pathLabel = new TextView(this);
        pathLabel.setTextColor(BLUE);
        pathLabel.setTextSize(10);
        pathLabel.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        pathLabel.setSingleLine(true);
        pathLabel.setPadding(dp(5), dp(9), dp(5), dp(5));
        content.addView(pathLabel);

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
        content.addView(quickScroll);

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
        content.addView(bar);
        root.addView(content, new LinearLayout.LayoutParams(0, -1, 1));
        setContentView(root);
    }



    private void openDiscordDesktop() {
        Dialog dialog = new Dialog(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(BG);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(10), dp(6), dp(10), dp(6));
        toolbar.setBackgroundColor(PANEL);
        TextView label = new TextView(this);
        label.setText("DISCORD  •  DESKTOP MODE");
        label.setTextColor(FG);
        label.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        label.setTextSize(12);
        toolbar.addView(label, new LinearLayout.LayoutParams(0, dp(42), 1));
        Button reload = smallButton("RELOAD");
        toolbar.addView(reload);
        Button close = smallButton("CLOSE");
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(-2, dp(38));
        closeLp.leftMargin = dp(6);
        toolbar.addView(close, closeLp);
        page.addView(toolbar);

        WebView discord = new WebView(this);
        WebSettings settings = discord.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(false);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        String ua = settings.getUserAgentString()
                .replace("Android", "X11; Linux x86_64")
                .replace("Mobile ", "");
        settings.setUserAgentString(ua);
        discord.setWebViewClient(new WebViewClient());
        discord.setWebChromeClient(new WebChromeClient());
        page.addView(discord, new LinearLayout.LayoutParams(-1, 0, 1));
        reload.setOnClickListener(v -> discord.reload());
        close.setOnClickListener(v -> dialog.dismiss());
        dialog.setContentView(page);
        dialog.setOnDismissListener(d -> discord.destroy());
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(-1, -1);
        }
        discord.loadUrl("https://discord.com/app");
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
        if (busy) return;
        final String cmd = input.getText().toString().trim();
        if (cmd.isEmpty()) return;
        input.setText("");
        hideKeyboard();
        if (cmd.equalsIgnoreCase("ssh disconnect") || cmd.equalsIgnoreCase("vps disconnect")) { disconnectSsh(); return; }
        if (sshConnected) { runSshCommand(cmd); return; }
        if (shellIn == null || shellOut == null) return;
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
                    if (!text.isEmpty()) { output.append(text); trimOutput(); }
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
        writeLine("VPS / SSH  kết nối VPS bằng IP, mật khẩu hoặc SSH Key", FG, false);
        writeLine("ssh disconnect  ngắt kết nối VPS và quay về shell máy", FG, false);
        writeLine("LƯU Ý", GREEN, true);
        writeLine("Đây là shell Android thật (/system/bin/sh), không phải Debian.", MUTED, false);
        writeLine("Nhấn VPS / SSH để kết nối máy chủ từ xa. Khi kết nối, lệnh sẽ chạy trên VPS.", MUTED, false);
        writeLine("Python 3.11 được tích hợp: python --version, python file.py, python -c \"print(123)\"", MUTED, false);
        writeLine("discord.py được đóng gói sẵn: pip list / pip show discord.py", MUTED, false);
        writeLine("pip cài thêm gói tùy ý chưa hỗ trợ; apt và sudo không có.", MUTED, false);
        showPrompt();
    }


    private EditText field(String hint, boolean secret) {
        EditText e = new EditText(this);
        e.setSingleLine(false); e.setTextColor(FG); e.setHintTextColor(MUTED);
        e.setHint(hint); e.setTextSize(14); e.setPadding(dp(10), dp(8), dp(10), dp(8));
        e.setBackground(round(Color.rgb(35, 47, 60), 8));
        if (secret) e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        else e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        return e;
    }

    private void showSshDialog() {
        if (sshConnected) {
            new AlertDialog.Builder(this).setTitle("VPS đang kết nối")
                .setMessage("Mọi lệnh hiện tại đang chạy trên VPS. Bạn muốn ngắt kết nối?")
                .setNegativeButton("Đóng", null)
                .setPositiveButton("Ngắt kết nối", (d, w) -> disconnectSsh()).show();
            return;
        }
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(8), dp(18), dp(8));
        ScrollView sc = new ScrollView(this);
        TextView note = new TextView(this);
        note.setText("Nhập thông tin SSH do nhà cung cấp VPS cấp. Có thể dùng IP hoặc tên miền. Để trống mật khẩu nếu dùng SSH Key.");
        note.setTextColor(MUTED); note.setTextSize(12); form.addView(note);
        EditText host = field("IP hoặc tên miền VPS", false);
        EditText port = field("Cổng SSH (mặc định 22)", false); port.setText("22");
        EditText user = field("Tên đăng nhập (ví dụ root)", false);
        EditText pass = field("Mật khẩu SSH (nếu có)", true);
        EditText key = field("SSH Private Key (dán toàn bộ nội dung nếu dùng key)", false);
        key.setMinLines(3); key.setGravity(Gravity.TOP);
        EditText phrase = field("Passphrase của SSH Key (nếu có)", true);
        Spinner mode = new Spinner(this);
        String[] modes = {"Tự động (mật khẩu hoặc SSH Key)", "Chỉ mật khẩu", "Chỉ SSH Key"};
        mode.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, modes));
        addFormField(form, "Địa chỉ VPS", host); addFormField(form, "Cổng", port);
        addFormField(form, "Tên đăng nhập", user); addFormField(form, "Mật khẩu", pass);
        addFormField(form, "Phương thức đăng nhập", mode);
        addFormField(form, "SSH Key (không bắt buộc)", key);
        addFormField(form, "Key passphrase (nếu có)", phrase);
        sc.addView(form);
        new AlertDialog.Builder(this).setTitle("Kết nối VPS qua SSH")
            .setView(sc).setNegativeButton("Hủy", null)
            .setPositiveButton("Kết nối", (d, w) -> connectSsh(
                host.getText().toString().trim(), port.getText().toString().trim(),
                user.getText().toString().trim(), pass.getText().toString(),
                key.getText().toString(), phrase.getText().toString(), mode.getSelectedItemPosition()))
            .show();
    }

    private void addFormField(LinearLayout parent, String label, View inputView) {
        TextView t = new TextView(this); t.setText(label); t.setTextColor(FG); t.setTextSize(12);
        t.setPadding(0, dp(9), 0, dp(4)); parent.addView(t);
        parent.addView(inputView, new LinearLayout.LayoutParams(-1,
            inputView instanceof EditText ? dp(46) : dp(48)));
    }

    private void connectSsh(String host, String portText, String username, String password,
                            String privateKey, String passphrase, int mode) {
        if (host.isEmpty() || username.isEmpty()) {
            writeLine("SSH: cần nhập địa chỉ VPS và tên đăng nhập.", Color.RED, false); return;
        }
        int portNumber;
        try { portNumber = Integer.parseInt(portText.isEmpty() ? "22" : portText); }
        catch (Exception e) { writeLine("SSH: cổng không hợp lệ.", Color.RED, false); return; }
        if (portNumber < 1 || portNumber > 65535) {
            writeLine("SSH: cổng phải từ 1 đến 65535.", Color.RED, false); return;
        }
        if (password.isEmpty() && privateKey.trim().isEmpty()) {
            writeLine("SSH: hãy nhập mật khẩu hoặc SSH Key.", Color.RED, false); return;
        }
        writeLine("Đang kết nối SSH tới " + host + ":" + portNumber + " ...", BLUE, true);
        statusLabel.setText("  SSH CONNECTING..."); busy = true;
        new Thread(() -> {
            try {
                JSch jsch = new JSch();
                if (!privateKey.trim().isEmpty() && mode != 1) {
                    byte[] keyBytes = privateKey.replace("\\n", "\n").getBytes("UTF-8");
                    if (passphrase.isEmpty()) jsch.addIdentity("terminal-key", keyBytes, null, null);
                    else jsch.addIdentity("terminal-key", keyBytes, null, passphrase.getBytes("UTF-8"));
                }
                Session session = jsch.getSession(username, host, portNumber);
                if (!password.isEmpty() && mode != 2) session.setPassword(password);
                Properties config = new Properties();
                // First connection is convenient, but host key identity is not verified in this mode.
                config.put("StrictHostKeyChecking", "no");
                config.put("PreferredAuthentications", mode == 1 ? "password,keyboard-interactive" :
                    (mode == 2 ? "publickey" : "publickey,password,keyboard-interactive"));
                session.setConfig(config);
                session.connect(18000);
                ChannelShell channel = (ChannelShell) session.openChannel("shell");
                channel.setPtyType("xterm"); channel.setPty(true);
                java.io.InputStream in = channel.getInputStream();
                java.io.OutputStream out = channel.getOutputStream();
                channel.connect(10000);
                BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out));
                synchronized (this) {
                    sshSession = session; sshChannel = channel; sshOut = reader; sshIn = writer; sshConnected = true;
                }
                runOnUiThread(() -> {
                    busy = false; statusLabel.setText("  VPS CONNECTED  •  " + host);
                    writeLine("Đã kết nối VPS. Lệnh tiếp theo sẽ chạy trên máy chủ từ xa.", GREEN, true);
                    writeLine("Gõ 'ssh disconnect' để quay về shell trên thiết bị.", MUTED, false);
                    showPrompt();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    busy = false; statusLabel.setText("  SSH FAILED");
                    writeLine("Không kết nối được VPS: " + e.getMessage(), Color.RED, false);
                    writeLine("Kiểm tra IP, cổng SSH, username, mật khẩu/SSH Key và firewall VPS.", MUTED, false);
                    showPrompt();
                });
            }
        }, "terminal-ssh-connect").start();
    }

    private void runSshCommand(String command) {
        writeLine("[VPS] $ " + command, BLUE, true);
        busy = true; statusLabel.setText("  VPS RUNNING...");
        new Thread(() -> {
            StringBuilder result = new StringBuilder();
            String marker = "__TP_SSH_DONE_" + System.nanoTime() + "__";
            try {
                sshIn.write(command + "\n");
                sshIn.write("printf '\\n" + marker + ":%s\\n' \"$?\"\n");
                sshIn.flush();
                String line;
                while ((line = sshOut.readLine()) != null) {
                    if (line.contains(marker + ":")) break;
                    result.append(line).append("\n");
                }
                String text = result.toString();
                runOnUiThread(() -> {
                    if (!text.isEmpty()) { output.append(text); trimOutput(); }
                    busy = false; statusLabel.setText("  VPS CONNECTED"); showPrompt();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    busy = false; statusLabel.setText("  SSH ERROR");
                    writeLine("Mất kết nối SSH: " + e.getMessage(), Color.RED, false);
                    disconnectSsh();
                });
            }
        }, "terminal-ssh-command").start();
    }

    private void disconnectSsh() {
        try { if (sshChannel != null) sshChannel.disconnect(); } catch (Exception ignored) {}
        try { if (sshSession != null) sshSession.disconnect(); } catch (Exception ignored) {}
        sshChannel = null; sshSession = null; sshIn = null; sshOut = null; sshConnected = false;
        busy = false;
        if (statusLabel != null) statusLabel.setText("  SHELL READY");
        writeLine("Đã ngắt VPS. Bạn đang quay về shell trên thiết bị.", GREEN, false);
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
        trimOutput();
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private void pasteInput() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null && clipboard.hasPrimaryClip() && clipboard.getPrimaryClip().getItemCount() > 0) {
            CharSequence value = clipboard.getPrimaryClip().getItemAt(0).coerceToText(this);
            if (value != null) input.getText().replace(input.getSelectionStart(), input.getSelectionEnd(), value);
            input.requestFocus();
            statusLabel.setText("  PASTED FROM CLIPBOARD");
        } else {
            statusLabel.setText("  CLIPBOARD EMPTY");
        }
    }

    private void trimOutput() {
        // Keep the most recent terminal output so long sessions do not grow without bound.
        int maxChars = 120000;
        if (output.length() > maxChars) {
            String recent = output.getText().subSequence(output.length() - maxChars, output.length()).toString();
            output.setText("[... các dòng cũ đã được lược bớt ...]\\n" + recent);
        }
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
