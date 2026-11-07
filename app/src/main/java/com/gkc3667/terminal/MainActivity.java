package com.gkc3667.terminal;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.UserInfo;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.ChannelShell;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;
import com.termux.view.TerminalView;
import com.termux.view.TerminalViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
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
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private static int BG = Color.rgb(10, 14, 19);
    private static int PANEL = Color.rgb(18, 25, 33);
    private static int GREEN = Color.rgb(92, 230, 157);
    private static int BLUE = Color.rgb(104, 174, 255);
    private static int FG = Color.rgb(226, 234, 242);
    private static int MUTED = Color.rgb(132, 148, 164);

    private TextView output, pathLabel, statusLabel;
    private EditText input;
    private ScrollView scroll;
    private Process shell;
    private TerminalView terminalView;
    private TerminalSession terminalSession;
    private final TerminalCallbacks terminalCallbacks = new TerminalCallbacks();
    private boolean interactiveInputMode = false;
    private boolean suppressInputWatcher = false;
    private boolean terminalWelcomeWritten = false;
    private Session sshSession;
    private ChannelShell sshChannel;
    private BufferedWriter sshIn;
    private BufferedReader sshOut;
    private java.io.InputStream sshRawIn;
    private java.io.OutputStream sshRawOut;
    private volatile boolean sshReaderRunning = false;
    private boolean sshConnected = false;
    // Coalesce terminal output so high-volume SSH streams do not trigger a layout per packet.
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final java.io.ByteArrayOutputStream terminalPendingBytes = new java.io.ByteArrayOutputStream();
    private boolean terminalFlushScheduled = false;
    private BufferedWriter shellIn;
    private BufferedReader shellOut;
    private final ArrayList<String> history = new ArrayList<>();
    private int historyIndex = 0;
    private volatile boolean busy = false;
    // Keep one Chromium view alive during this app session for faster app switching.
    private WebView pcWebView;
    private String pcWebProfile = "";
    private String pcWebCurrentUrl = "";
    private final ArrayList<String> browserTabs = new ArrayList<>();
    private int browserTabIndex = 0;
    private EditText browserAddress;
    private Dialog pcLibraryDialog;
    private View terminalRoot;
    private String currentPath = "";
    private boolean discordTheme = false;
    private int terminalFontSize = 15;
    private String terminalFont = "mono";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);
        currentPath = getFilesDir().getAbsolutePath();
        loadAppearance();
        loadBrowserProgress();
        buildUi();
        startShell();
        showHomeDashboard();
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
            sideBrand.setText("GK");
            sideBrand.setTextColor(GREEN);
            sideBrand.setTextSize(25);
            sideBrand.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            sideBrand.setGravity(Gravity.CENTER);
            sidebar.addView(sideBrand, new LinearLayout.LayoutParams(-1, dp(52)));
            TextView sideTitle = new TextView(this);
            sideTitle.setText("GKC\\nWORKSPACE");
            sideTitle.setTextColor(FG);
            sideTitle.setTextSize(15);
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
            navCaption.setTextSize(12);
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
            navSsh.setOnClickListener(v -> showSshPage());
            LinearLayout.LayoutParams navSshLp = new LinearLayout.LayoutParams(-1, dp(44));
            navSshLp.topMargin = dp(5); sidebar.addView(navSsh, navSshLp);
            View spacer = new View(this);
            sidebar.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1));
            TextView sideFooter = new TextView(this);
            sideFooter.setText("ANDROID SHELL\\nSSH CLIENT");
            sideFooter.setTextColor(MUTED);
            sideFooter.setTextSize(12);
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
        title.setText("GKC APP");
        title.setTextColor(GREEN);
        title.setTextSize(17);
        title.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        TextView subtitle = new TextView(this);
        subtitle.setText("LOCAL SHELL  /  SESSION ACTIVE");
        subtitle.setTextColor(MUTED);
        subtitle.setTextSize(12);
        subtitle.setTypeface(Typeface.MONOSPACE);
        brand.addView(title);
        brand.addView(subtitle);
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        Button copy = smallButton("COPY");
        copy.setOnClickListener(v -> copyOutput());
        header.addView(copy);
        Button connect = smallButton("VPS / SSH");
        connect.setOnClickListener(v -> showSshPage());
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
        Button appearance = smallButton("Aa  GIAO DIỆN");
        appearance.setOnClickListener(v -> showAppearanceDialog());
        LinearLayout.LayoutParams appearanceLp = new LinearLayout.LayoutParams(-2, dp(36));
        appearanceLp.leftMargin = dp(6);
        header.addView(appearance, appearanceLp);
        content.addView(header);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(0, dp(9), 0, dp(3));
        Button homeTab = smallButton("⌂  TRANG CHỦ");
        homeTab.setTextColor(Color.rgb(190, 165, 255));
        homeTab.setOnClickListener(v -> showHomeDashboard());
        tabs.addView(homeTab, new LinearLayout.LayoutParams(0, dp(40), 1));
        Button pcAppsTab = smallButton("▦  PC APP  •  THƯ VIỆN");
        pcAppsTab.setTextColor(Color.rgb(28, 205, 255));
        LinearLayout.LayoutParams pcAppsLp = new LinearLayout.LayoutParams(0, dp(40), 1);
        pcAppsLp.leftMargin = dp(8);
        tabs.addView(pcAppsTab, pcAppsLp);
        pcAppsTab.setOnClickListener(v -> showPcAppLibrary());
        content.addView(tabs);

        LinearLayout info = new LinearLayout(this);
        info.setGravity(Gravity.CENTER_VERTICAL);
        info.setPadding(dp(4), dp(9), dp(4), dp(7));
        TextView dot = new TextView(this);
        dot.setText("●");
        dot.setTextColor(GREEN);
        dot.setTextSize(13);
        info.addView(dot);
        statusLabel = new TextView(this);
        statusLabel.setText("  SHELL READY");
        statusLabel.setTextColor(MUTED);
        statusLabel.setTextSize(13);
        statusLabel.setTypeface(Typeface.MONOSPACE);
        info.addView(statusLabel);
        content.addView(info);

        FrameLayout terminalFrame = new FrameLayout(this);
        terminalFrame.setBackground(round(discordTheme ? Color.rgb(30, 31, 34) : Color.rgb(6, 9, 13), 12));
        terminalView = new TerminalView(this, null);
        terminalView.setBackgroundColor(discordTheme ? Color.rgb(30, 31, 34) : Color.rgb(6, 9, 13));
        terminalView.setTerminalViewClient(terminalCallbacks);
        terminalView.setTextSize(terminalFontSize);
        terminalView.setTypeface(resolveTerminalTypeface());
        terminalView.setFocusable(false);
        terminalView.setFocusableInTouchMode(false);
        terminalFrame.addView(terminalView, new FrameLayout.LayoutParams(-1, -1));

        // Keep the old transcript view as a fallback for diagnostic messages.
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVisibility(View.GONE);
        scroll.setBackground(round(discordTheme ? Color.rgb(30, 31, 34) : Color.rgb(6, 9, 13), 12));
        output = new TextView(this);
        output.setTextColor(FG);
        output.setTextSize(terminalFontSize);
        output.setTypeface(resolveTerminalTypeface());
        output.setTextIsSelectable(true);
        output.setFontFeatureSettings("kern");
        output.setPadding(dp(12), dp(12), dp(12), dp(16));
        output.setLineSpacing(dp(2), 1.0f);
        scroll.addView(output, new ScrollView.LayoutParams(-1, -2));
        terminalFrame.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams terminalLp = new LinearLayout.LayoutParams(-1, 0, 1);
        content.addView(terminalFrame, terminalLp);

        pathLabel = new TextView(this);
        pathLabel.setTextColor(BLUE);
        pathLabel.setTextSize(13);
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

        LinearLayout sshKeys = new LinearLayout(this);
        sshKeys.setGravity(Gravity.CENTER_VERTICAL);
        sshKeys.setOrientation(LinearLayout.HORIZONTAL);
        String esc = new String(new char[]{27});
        String[][] specialKeys = {
            {"CTRL-C", new String(new char[]{3})}, {"CTRL-D", new String(new char[]{4})},
            {"CTRL-L", new String(new char[]{12})}, {"CTRL-Z", new String(new char[]{26})},
            {"⌫", new String(new char[]{127})}, {"TAB", "\t"}, {"ESC", esc},
            {"HOME", esc + "[H"}, {"END", esc + "[F"},
            {"↑", esc + "[A"}, {"↓", esc + "[B"}, {"←", esc + "[D"}, {"→", esc + "[C"}
        };
        for (String[] k : specialKeys) {
            Button keyButton = smallButton(k[0]);
            keyButton.setOnClickListener(v -> sendTerminalInput(k[1]));
            LinearLayout.LayoutParams kp = new LinearLayout.LayoutParams(-2, dp(34));
            kp.rightMargin = dp(4);
            sshKeys.addView(keyButton, kp);
        }
        Button rawMode = smallButton("INPUT: COMMAND");
        rawMode.setTextColor(BLUE);
        rawMode.setOnClickListener(v -> {
            interactiveInputMode = !interactiveInputMode;
            rawMode.setText(interactiveInputMode ? "INPUT: RAW" : "INPUT: COMMAND");
            rawMode.setTextColor(interactiveInputMode ? GREEN : BLUE);
            input.setHint(interactiveInputMode ? "RAW: ký tự được gửi trực tiếp vào terminal..." :
                (sshConnected ? "Nhập lệnh VPS rồi nhấn RUN/Enter..." : "Nhập lệnh..."));
            input.requestFocus();
            statusLabel.setText(interactiveInputMode ? "  RAW KEYBOARD INPUT ENABLED" : "  COMMAND INPUT ENABLED");
        });
        LinearLayout.LayoutParams rawLp = new LinearLayout.LayoutParams(-2, dp(34));
        rawLp.rightMargin = dp(4);
        sshKeys.addView(rawMode, rawLp);
        HorizontalScrollView sshKeyScroll = new HorizontalScrollView(this);
        sshKeyScroll.setHorizontalScrollBarEnabled(false);
        sshKeyScroll.addView(sshKeys);
        content.addView(sshKeyScroll);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(3), dp(4), dp(3));
        bar.setBackground(round(PANEL, 12));
        input = new EditText(this);
        input.setSingleLine(true);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(MUTED);
        input.setHint("Nhập lệnh...");
        input.setTextSize(terminalFontSize);
        input.setTypeface(resolveTerminalTypeface());
        input.setPadding(dp(5), 0, dp(5), 0);
        input.setBackgroundColor(Color.TRANSPARENT);
        // Cho phép bàn phím Android ghép dấu tiếng Việt (IME composing).
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        input.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_SEND ||
                (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                if (interactiveInputMode) sendTerminalInput("\r");
                else runCommand();
                return true;
            }
            return false;
        });
        input.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (interactiveInputMode) {
                if (event.isCtrlPressed() && keyCode == KeyEvent.KEYCODE_C) { sendTerminalInput("\u0003"); return true; }
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) { sendTerminalInput("\u001b[A"); return true; }
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) { sendTerminalInput("\u001b[B"); return true; }
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) { sendTerminalInput("\u001b[C"); return true; }
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) { sendTerminalInput("\u001b[D"); return true; }
                if (keyCode == KeyEvent.KEYCODE_DEL) { sendTerminalInput("\u007f"); return true; }
                if (keyCode == KeyEvent.KEYCODE_TAB) { sendTerminalInput("\t"); return true; }
                if (keyCode == KeyEvent.KEYCODE_ESCAPE) { sendTerminalInput("\u001b"); return true; }
            } else {
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) { browseHistory(-1); return true; }
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) { browseHistory(1); return true; }
            }
            return false;
        });
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable value) {
                if (!interactiveInputMode || suppressInputWatcher || value.length() == 0) return;
                if (BaseInputConnection.getComposingSpanStart(value) >= 0 ||
                    BaseInputConnection.getComposingSpanEnd(value) >= 0) return;
                String committed = value.toString();
                suppressInputWatcher = true;
                value.clear();
                suppressInputWatcher = false;
                sendTerminalInput(committed);
            }
        });
        bar.addView(input, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button run = new Button(this);
        run.setText("RUN  ▶");
        run.setTextColor(Color.rgb(5, 18, 12));
        run.setTextSize(15);
        run.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        run.setAllCaps(false);
        run.setBackground(round(GREEN, 9));
        run.setOnClickListener(v -> runCommand());
        bar.addView(run, new LinearLayout.LayoutParams(dp(88), dp(42)));
        content.addView(bar);
        root.addView(content, new LinearLayout.LayoutParams(0, -1, 1));
        terminalRoot = root;
        setContentView(root);
    }

    private void showHomeDashboard() {
        int w = (int)(getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density);
        boolean wide = w >= 700;
        final int purple = Color.rgb(177, 70, 255);
        final int cyan = Color.rgb(28, 205, 255);
        final int pink = Color.rgb(255, 65, 220);
        final int mint = Color.rgb(35, 235, 184);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(wide ? 14 : 9), dp(9), dp(wide ? 14 : 9), dp(8));
        root.setBackground(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(8, 5, 27), Color.rgb(19, 8, 48), Color.rgb(7, 10, 30)}));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(13), dp(9), dp(12), dp(9));
        top.setBackground(neonPanel(Color.rgb(20, 13, 48), purple, 16));
        TextView mark = new TextView(this);
        mark.setText("⌁");
        mark.setTextColor(Color.WHITE); mark.setTextSize(27);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(round(Color.rgb(91, 24, 190), 12));
        top.addView(mark, new LinearLayout.LayoutParams(dp(43), dp(43)));
        LinearLayout brandBox = new LinearLayout(this);
        brandBox.setOrientation(LinearLayout.VERTICAL);
        brandBox.setPadding(dp(10), 0, 0, 0);
        TextView brand = new TextView(this);
        brand.setText("GKC APP");
        brand.setTextColor(Color.WHITE); brand.setTextSize(20);
        brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView subBrand = new TextView(this);
        subBrand.setText("POWER YOUR DEVICE");
        subBrand.setTextColor(Color.rgb(183, 154, 255)); subBrand.setTextSize(12);
        subBrand.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        brandBox.addView(brand); brandBox.addView(subBrand);
        top.addView(brandBox, new LinearLayout.LayoutParams(0, -2, 1));
        Button settings = smallButton("⚙");
        settings.setTextSize(20); settings.setTextColor(Color.WHITE);
        settings.setOnClickListener(v -> showAppearanceDialog());
        top.addView(settings, new LinearLayout.LayoutParams(dp(46), dp(42)));
        root.addView(top);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(-1, 0, 1);
        bodyLp.topMargin = dp(9);
        root.addView(body, bodyLp);

        if (wide) {
            LinearLayout side = new LinearLayout(this);
            side.setOrientation(LinearLayout.VERTICAL);
            side.setPadding(dp(9), dp(13), dp(9), dp(12));
            side.setBackground(neonPanel(Color.rgb(17, 12, 42), Color.rgb(91, 42, 190), 18));
            TextView logo = new TextView(this);
            logo.setText("👑\nGKC APP");
            logo.setTextColor(Color.WHITE); logo.setTextSize(18); logo.setGravity(Gravity.CENTER);
            logo.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            side.addView(logo, new LinearLayout.LayoutParams(-1, dp(82)));
            addHomeNav(side, "⌂  Trang Chủ", () -> showHomeDashboard());
            addHomeNav(side, "▦  PC APP", () -> showPcAppLibrary());
            addHomeNav(side, "›_  Terminal", () -> setContentView(terminalRoot));
            addHomeNav(side, "⇄  SSH / VPS", () -> showSshPage());
            addHomeNav(side, "⚙  Cài Đặt", () -> showAppearanceDialog());
            View spacer = new View(this);
            side.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1));
            TextView foot = new TextView(this);
            foot.setText("●  SYSTEM ONLINE");
            foot.setTextColor(mint); foot.setTextSize(13);
            side.addView(foot);
            LinearLayout.LayoutParams sideLp = new LinearLayout.LayoutParams(dp(164), -1);
            sideLp.rightMargin = dp(10); body.addView(side, sideLp);
        }

        ScrollView sc = new ScrollView(this);
        sc.setFillViewport(true);
        sc.setVerticalScrollBarEnabled(false);
        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(dp(2), 0, dp(2), dp(10));
        sc.addView(main);
        body.addView(sc, wide ? new LinearLayout.LayoutParams(0, -1, 1) : new LinearLayout.LayoutParams(-1, -1));

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPadding(dp(15), dp(16), dp(10), dp(15));
        hero.setBackground(neonPanel(Color.rgb(39, 10, 91), purple, 22));
        LinearLayout heroCopy = new LinearLayout(this);
        heroCopy.setOrientation(LinearLayout.VERTICAL);
        TextView eyebrow = new TextView(this);
        eyebrow.setText("✦  YOUR DIGITAL WORKSPACE");
        eyebrow.setTextColor(Color.rgb(213, 171, 255)); eyebrow.setTextSize(12);
        eyebrow.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        TextView heroTitle = new TextView(this);
        heroTitle.setText("Xin chào!\nGKC APP");
        heroTitle.setTextColor(Color.WHITE); heroTitle.setTextSize(wide ? 25 : 20);
        heroTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView heroDesc = new TextView(this);
        heroDesc.setText("Mạnh mẽ  •  Đa năng  •  Siêu mượt");
        heroDesc.setTextColor(Color.rgb(213, 202, 255)); heroDesc.setTextSize(13);
        heroDesc.setPadding(0, dp(5), 0, 0);
        heroCopy.addView(eyebrow); heroCopy.addView(heroTitle); heroCopy.addView(heroDesc);
        hero.addView(heroCopy, new LinearLayout.LayoutParams(0, -2, 1));
        TextView robot = new TextView(this);
        robot.setText("🤖");
        robot.setTextSize(wide ? 66 : 54);
        robot.setGravity(Gravity.CENTER);
        robot.setBackground(round(Color.rgb(75, 24, 150), 50));
        robot.setElevation(0);
        hero.addView(robot, new LinearLayout.LayoutParams(dp(wide ? 124 : 91), dp(wide ? 124 : 91)));
        LinearLayout.LayoutParams heroLp = new LinearLayout.LayoutParams(-1, -2);
        heroLp.bottomMargin = dp(10); main.addView(hero, heroLp);

        LinearLayout welcome = new LinearLayout(this);
        welcome.setGravity(Gravity.CENTER_VERTICAL);
        welcome.setPadding(dp(12), dp(10), dp(12), dp(10));
        welcome.setBackground(neonPanel(Color.rgb(25, 17, 62), Color.rgb(102, 52, 204), 14));
        TextView welcomeIcon = new TextView(this);
        welcomeIcon.setText("⭐"); welcomeIcon.setTextSize(22);
        welcome.addView(welcomeIcon);
        TextView welcomeText = new TextView(this);
        welcomeText.setText("  Chào mừng bạn!\\n  Sẵn sàng khám phá các tính năng tuyệt vời? 🚀");
        welcomeText.setTextColor(Color.WHITE); welcomeText.setTextSize(14);
        welcome.addView(welcomeText, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams welcomeLp = new LinearLayout.LayoutParams(-1, -2);
        welcomeLp.bottomMargin = dp(9); main.addView(welcome, welcomeLp);

        TextView quickTitle = homeSectionTitle("✦  TRUNG TÂM ĐIỀU KHIỂN");
        main.addView(quickTitle);
        LinearLayout cards = new LinearLayout(this);
        cards.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        addHomeCard(cards, "▦", "PC APP", "Discord, YouTube, game và ứng dụng của bạn", cyan, Color.rgb(8, 45, 101), () -> showPcAppLibrary());
        addHomeCard(cards, "›_", "Terminal", "Chạy lệnh Android / Python", purple, Color.rgb(55, 12, 112), () -> setContentView(terminalRoot));
        addHomeCard(cards, "⇄", "SSH / VPS", "Trang kết nối máy chủ riêng", mint, Color.rgb(5, 67, 69), () -> showSshPage());
        LinearLayout.LayoutParams cardsLp = new LinearLayout.LayoutParams(-1, -2);
        cardsLp.bottomMargin = dp(9); main.addView(cards, cardsLp);

        LinearLayout lower = new LinearLayout(this);
        lower.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        LinearLayout device = new LinearLayout(this);
        device.setOrientation(LinearLayout.VERTICAL);
        device.setPadding(dp(12), dp(11), dp(12), dp(11));
        device.setBackground(neonPanel(Color.rgb(18, 16, 52), Color.rgb(103, 47, 199), 16));
        TextView deviceTitle = homeSectionTitle("⚡  THÔNG TIN HỆ THỐNG");
        device.addView(deviceTitle);
        addInfoLine(device, "Thiết bị", Build.MANUFACTURER + " " + Build.MODEL);
        addInfoLine(device, "Kiến trúc", android.text.TextUtils.join(", ", Build.SUPPORTED_ABIS));
        addInfoLine(device, "Android", Build.VERSION.RELEASE + "  •  API " + Build.VERSION.SDK_INT);
        TextView ready = new TextView(this);
        ready.setText("●  HỆ THỐNG SẴN SÀNG");
        ready.setTextColor(mint); ready.setTextSize(13);
        ready.setPadding(dp(8), dp(8), dp(8), dp(2));
        device.addView(ready);
        LinearLayout.LayoutParams deviceLp = new LinearLayout.LayoutParams(wide ? 0 : -1, -2, wide ? 1 : 0);
        deviceLp.setMargins(dp(3), dp(3), dp(3), dp(3));
        lower.addView(device, deviceLp);

        LinearLayout activity = new LinearLayout(this);
        activity.setOrientation(LinearLayout.VERTICAL);
        activity.setPadding(dp(12), dp(11), dp(12), dp(11));
        activity.setBackground(neonPanel(Color.rgb(18, 16, 52), Color.rgb(45, 88, 200), 16));
        TextView activityTitle = homeSectionTitle("◷  HOẠT ĐỘNG GẦN ĐÂY");
        activity.addView(activityTitle);
        addInfoLine(activity, "Trạng thái", "Đã sẵn sàng sử dụng!");
        addInfoLine(activity, "Python", "Python 3.11 tích hợp");
        addInfoLine(activity, "Kết nối", "SSH / VPS có sẵn");
        LinearLayout.LayoutParams activityLp = new LinearLayout.LayoutParams(wide ? 0 : -1, -2, wide ? 1 : 0);
        activityLp.setMargins(dp(3), dp(3), dp(3), dp(3));
        lower.addView(activity, activityLp);
        main.addView(lower);

        TextView footer = new TextView(this);
        footer.setText("✦  GKC APP  •  ĐỈNH CAO CÔNG NGHỆ TRONG TẦM TAY BẠN  ✦");
        footer.setTextColor(Color.rgb(204, 153, 255)); footer.setTextSize(12);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(dp(4), dp(12), dp(4), dp(8));
        main.addView(footer);
        setContentView(root);
    }

    private android.graphics.drawable.GradientDrawable neonPanel(int color, int stroke, int radius) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[]{color, Color.rgb(20, 13, 53)});
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(1), stroke);
        return d;
    }

    private void addHomeNav(LinearLayout parent, String label, Runnable action) {
        Button b = smallButton(label);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setTextColor(Color.rgb(226, 216, 255));
        b.setBackground(neonPanel(Color.rgb(30, 20, 68), Color.rgb(75, 44, 143), 11));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(43));
        lp.topMargin = dp(6); parent.addView(b, lp);
        b.setOnClickListener(v -> action.run());
    }

    private TextView homeSectionTitle(String value) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextColor(Color.WHITE); t.setTextSize(15);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setPadding(dp(7), dp(8), dp(7), dp(7));
        return t;
    }

    private void addHomeCard(LinearLayout parent, String icon, String title, String desc,
                             int glow, int fill, Runnable action) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(13), dp(13), dp(13), dp(13));
        card.setBackground(neonPanel(fill, glow, 17));
        card.setElevation(0);
        TextView ico = new TextView(this);
        ico.setText(icon); ico.setTextSize(27); ico.setTextColor(Color.WHITE);
        ico.setGravity(Gravity.CENTER);
        ico.setBackground(round(Color.rgb(69, 28, 139), 12));
        card.addView(ico, new LinearLayout.LayoutParams(dp(49), dp(43)));
        TextView name = new TextView(this);
        name.setText(title); name.setTextColor(Color.WHITE); name.setTextSize(17);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        name.setPadding(0, dp(8), 0, dp(3));
        card.addView(name);
        TextView sub = new TextView(this);
        sub.setText(desc); sub.setTextColor(Color.rgb(219, 211, 248)); sub.setTextSize(13);
        card.addView(sub);
        TextView open = new TextView(this);
        open.setText("MỞ NGAY  →");
        open.setTextColor(Color.WHITE); open.setTextSize(13);
        open.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        open.setGravity(Gravity.CENTER);
        open.setPadding(dp(8), dp(7), dp(8), dp(7));
        open.setBackground(round(glow, 20));
        LinearLayout.LayoutParams openLp = new LinearLayout.LayoutParams(-2, -2);
        openLp.topMargin = dp(10); card.addView(open, openLp);
        card.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = parent.getOrientation() == LinearLayout.HORIZONTAL
                ? new LinearLayout.LayoutParams(0, -2, 1)
                : new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(dp(3), dp(4), dp(3), dp(4));
        parent.addView(card, lp);
    }

    private void addInfoLine(LinearLayout parent, String key, String value) {
        TextView t = new TextView(this);
        t.setText(key + "     " + value);
        t.setTextColor(Color.rgb(218, 213, 243)); t.setTextSize(14);
        t.setPadding(dp(7), dp(5), dp(7), dp(5));
        parent.addView(t);
    }

    private void loadAppearance() {
        SharedPreferences p = getSharedPreferences("terminal_appearance", MODE_PRIVATE);
        discordTheme = p.getBoolean("discord_theme", false);
        terminalFontSize = p.getInt("font_size", 15);
        terminalFont = p.getString("font_family", "mono");
        applyThemeColors();
    }

    private void applyThemeColors() {
        if (discordTheme) {
            BG = Color.rgb(49, 51, 56);
            PANEL = Color.rgb(43, 45, 49);
            GREEN = Color.rgb(88, 101, 242);
            BLUE = Color.rgb(0, 168, 252);
            FG = Color.rgb(242, 243, 245);
            MUTED = Color.rgb(181, 186, 193);
        } else {
            BG = Color.rgb(10, 14, 19);
            PANEL = Color.rgb(18, 25, 33);
            GREEN = Color.rgb(92, 230, 157);
            BLUE = Color.rgb(104, 174, 255);
            FG = Color.rgb(226, 234, 242);
            MUTED = Color.rgb(132, 148, 164);
        }
    }

    private Typeface resolveTerminalTypeface() {
        if ("sans".equals(terminalFont)) return Typeface.create("sans-serif", Typeface.NORMAL);
        if ("serif".equals(terminalFont)) return Typeface.create("serif", Typeface.NORMAL);
        return Typeface.MONOSPACE;
    }

    private void showAppearanceDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), dp(8));

        TextView themeLabel = new TextView(this);
        themeLabel.setText("GIAO DIỆN");
        themeLabel.setTextColor(FG);
        themeLabel.setTextSize(15);
        form.addView(themeLabel);
        Spinner theme = new Spinner(this);
        theme.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"GKC APP (xanh đen)", "Discord (tím than)"}));
        theme.setSelection(discordTheme ? 1 : 0);
        form.addView(theme, new LinearLayout.LayoutParams(-1, dp(48)));

        TextView fontLabel = new TextView(this);
        fontLabel.setText("PHÔNG CHỮ");
        fontLabel.setTextColor(FG);
        fontLabel.setTextSize(15);
        fontLabel.setPadding(0, dp(12), 0, dp(3));
        form.addView(fontLabel);
        Spinner font = new Spinner(this);
        font.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Monospace (Terminal)", "Sans Serif (Discord)", "Serif"}));
        font.setSelection("sans".equals(terminalFont) ? 1 : ("serif".equals(terminalFont) ? 2 : 0));
        form.addView(font, new LinearLayout.LayoutParams(-1, dp(48)));

        TextView sizeLabel = new TextView(this);
        sizeLabel.setText("CỠ CHỮ: " + terminalFontSize + " SP");
        sizeLabel.setTextColor(FG);
        sizeLabel.setPadding(0, dp(12), 0, dp(2));
        form.addView(sizeLabel);
        SeekBar size = new SeekBar(this);
        size.setMax(14);
        size.setProgress(Math.max(0, Math.min(14, terminalFontSize - 10)));
        size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                sizeLabel.setText("CỠ CHỮ: " + (value + 10) + " SP");
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        form.addView(size);

        TextView preview = new TextView(this);
        preview.setText("Aa Bb 123  •  GKC APP");
        preview.setTextColor(FG);
        preview.setTextSize(terminalFontSize);
        preview.setPadding(dp(10), dp(12), dp(10), dp(12));
        preview.setBackground(round(PANEL, 10));
        form.addView(preview);
        font.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                preview.setTypeface(position == 1 ? Typeface.create("sans-serif", 0) :
                    (position == 2 ? Typeface.create("serif", 0) : Typeface.MONOSPACE));
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                sizeLabel.setText("CỠ CHỮ: " + (value + 10) + " SP");
                preview.setTextSize(value + 10);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });

        new AlertDialog.Builder(this).setTitle("Tùy chỉnh giao diện")
            .setView(form).setNegativeButton("HỦY", null)
            .setPositiveButton("LƯU", (d, w) -> {
                discordTheme = theme.getSelectedItemPosition() == 1;
                terminalFont = font.getSelectedItemPosition() == 1 ? "sans" :
                    (font.getSelectedItemPosition() == 2 ? "serif" : "mono");
                terminalFontSize = size.getProgress() + 10;
                getSharedPreferences("terminal_appearance", MODE_PRIVATE).edit()
                    .putBoolean("discord_theme", discordTheme)
                    .putString("font_family", terminalFont)
                    .putInt("font_size", terminalFontSize).apply();
                recreate();
            }).show();
    }

    private SharedPreferences pcAppPrefs() {
        return getSharedPreferences("pc_app_library", MODE_PRIVATE);
    }

    private JSONArray getPcApps() {
        try { return new JSONArray(pcAppPrefs().getString("apps", "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    private void savePcApps(JSONArray apps) {
        pcAppPrefs().edit().putString("apps", apps.toString()).apply();
    }

    private void addPcApp(String name, String url) {
        try {
            JSONArray apps = getPcApps();
            String baseName = name.trim();
            int duplicateCount = 0;
            for (int i = 0; i < apps.length(); i++) {
                JSONObject existing = apps.optJSONObject(i);
                if (existing != null) { String oldName = existing.optString("name", ""); if (baseName.equalsIgnoreCase(oldName) || oldName.matches(java.util.regex.Pattern.quote(baseName) + " \\(\\d+\\)")) duplicateCount++; }
            }
            String displayName = duplicateCount == 0 ? baseName : baseName + " (" + duplicateCount + ")";
            JSONObject item = new JSONObject();
            item.put("name", displayName);
            item.put("url", url.trim());
            item.put("id", System.currentTimeMillis() + "_" + apps.length());
            apps.put(item);
            savePcApps(apps);
            showPcAppLibrary();
        } catch (Exception e) {
            statusLabel.setText("  KHÔNG THỂ THÊM ỨNG DỤNG");
        }
    }


    private void showAddPcAppDialog() {
        final String[][] catalog = {
            {"Discord", "https://discord.com/app"},
            {"YouTube", "https://www.youtube.com"},
            {"Roblox", "https://www.roblox.com"},
            {"GeForce NOW", "https://play.geforcenow.com"},
            {"Telegram Web", "https://web.telegram.org"},
            {"Spotify", "https://open.spotify.com"},
            {"Minecraft", "https://www.minecraft.net"},
            {"Free Fire", "https://ff.garena.com/"}
        };
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(14), dp(12), dp(14), dp(10));
        page.setBackground(new android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
            new int[]{Color.rgb(8, 6, 27), Color.rgb(24, 10, 53), Color.rgb(7, 13, 32)}));
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Button back = smallButton("⌂  TRANG CHỦ");
        back.setTextColor(Color.WHITE);
        back.setOnClickListener(v -> showHomeDashboard());
        header.addView(back);
        TextView heading = new TextView(this);
        heading.setText("  THÊM ỨNG DỤNG");
        heading.setTextColor(Color.WHITE); heading.setTextSize(20);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(heading, new LinearLayout.LayoutParams(0, dp(44), 1));
        page.addView(header);

        TextView caption = new TextView(this);
        caption.setText("CHỌN ỨNG DỤNG CÓ SẴN");
        caption.setTextColor(Color.rgb(190, 155, 255)); caption.setTextSize(13);
        caption.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        caption.setPadding(dp(4), dp(12), dp(4), dp(8));
        page.addView(caption);

        Button custom = new Button(this);
        custom.setText("＋  THÊM ỨNG DỤNG");
        custom.setAllCaps(false);
        custom.setTextColor(Color.WHITE);
        custom.setTextSize(17);
        custom.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        custom.setGravity(Gravity.CENTER);
        custom.setMinHeight(dp(62));
        custom.setBackground(neonPanel(Color.rgb(112, 58, 220), Color.rgb(190, 145, 255), 14));
        custom.setElevation(0);
        LinearLayout.LayoutParams customLp = new LinearLayout.LayoutParams(-1, dp(62));
        customLp.bottomMargin = dp(10);
        page.addView(custom, customLp);
        custom.setOnClickListener(v -> showCustomPcAppDialog());

        ScrollView sc = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        JSONArray savedApps = getPcApps();
        if (savedApps.length() > 0) {
            TextView savedTitle = new TextView(this);
            savedTitle.setText("ỨNG DỤNG ĐÃ THÊM  •  CHẠM ĐỂ MỞ");
            savedTitle.setTextColor(Color.WHITE);
            savedTitle.setTextSize(13);
            savedTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            savedTitle.setPadding(dp(4), dp(4), dp(4), dp(8));
            list.addView(savedTitle);
            for (int i = 0; i < savedApps.length(); i++) {
                JSONObject saved = savedApps.optJSONObject(i);
                if (saved == null) continue;
                final String savedId = saved.optString("id");
                final String savedName = saved.optString("name", "Ứng dụng");
                final String savedUrl = saved.optString("url", "https://www.google.com");
                Button savedButton = new Button(this);
                savedButton.setText("▣  " + savedName + "     MỞ  →");
                savedButton.setAllCaps(false);
                savedButton.setTextColor(Color.WHITE);
                savedButton.setTextSize(15);
                savedButton.setGravity(Gravity.CENTER_VERTICAL);
                savedButton.setBackground(neonPanel(Color.rgb(18, 57, 87), Color.rgb(28, 205, 255), 12));
                LinearLayout.LayoutParams savedLp = new LinearLayout.LayoutParams(-1, dp(52));
                savedLp.bottomMargin = dp(7);
                list.addView(savedButton, savedLp);
                savedButton.setOnClickListener(v -> openPcApp(savedId, savedName, savedUrl));
                savedButton.setOnLongClickListener(v -> {
                    new AlertDialog.Builder(this).setTitle(savedName)
                        .setItems(new String[]{"Mở ứng dụng", "Sửa tên / link", "Xóa ứng dụng"}, (dialog, choice) -> {
                            if (choice == 0) openPcApp(savedId, savedName, savedUrl);
                            else if (choice == 1) showEditPcAppDialog(savedId, savedName, savedUrl);
                            else if (choice == 2) new AlertDialog.Builder(this)
                                .setMessage("Xóa " + savedName + " khỏi PC APP?")
                                .setNegativeButton("HỦY", null)
                                .setPositiveButton("XÓA", (d, w) -> {
                                    JSONArray current = getPcApps();
                                    JSONArray remaining = new JSONArray();
                                    for (int k = 0; k < current.length(); k++) {
                                        JSONObject item = current.optJSONObject(k);
                                        if (item != null && !item.optString("id").equals(savedId)) remaining.put(item);
                                    }
                                    savePcApps(remaining);
                                    showPcAppLibrary();
                                }).show();
                        }).show();
                    return true;
                });
            }
            TextView catalogTitle = new TextView(this);
            catalogTitle.setText("ỨNG DỤNG CÓ SẴN");
            catalogTitle.setTextColor(Color.rgb(190, 155, 255));
            catalogTitle.setTextSize(13);
            catalogTitle.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            catalogTitle.setPadding(dp(4), dp(12), dp(4), dp(8));
            list.addView(catalogTitle);
        }
        for (String[] app : catalog) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(13), dp(12), dp(13), dp(13));
            row.setBackground(neonPanel(Color.rgb(20, 15, 46), Color.rgb(83, 54, 145), 15));
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
            rowLp.bottomMargin = dp(10);
            list.addView(row, rowLp);

            LinearLayout appInfo = new LinearLayout(this);
            appInfo.setGravity(Gravity.CENTER_VERTICAL);
            TextView icon = new TextView(this);
            icon.setText(app[0].equals("Discord") ? "💬" : app[0].equals("YouTube") ? "▶" :
                app[0].equals("Free Fire") ? "🔥" : app[0].equals("Roblox") ? "◇" : "🌐");
            icon.setTextSize(29); icon.setGravity(Gravity.CENTER);
            appInfo.addView(icon, new LinearLayout.LayoutParams(dp(54), dp(52)));
            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setPadding(dp(12), 0, 0, 0);
            TextView name = new TextView(this);
            name.setText(app[0]); name.setTextColor(Color.WHITE); name.setTextSize(18);
            name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            TextView url = new TextView(this);
            url.setText(app[1]); url.setTextColor(MUTED); url.setTextSize(13);
            info.addView(name); info.addView(url);
            appInfo.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
            row.addView(appInfo);

            Button add = new Button(this);
            add.setText("＋  THÊM ỨNG DỤNG");
            add.setAllCaps(false);
            add.setTextColor(Color.WHITE);
            add.setTextSize(16);
            add.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            add.setGravity(Gravity.CENTER);
            add.setMinHeight(dp(54));
            add.setPadding(dp(10), dp(8), dp(10), dp(8));
            add.setBackground(neonPanel(Color.rgb(105, 54, 201), Color.rgb(172, 125, 255), 13));
            add.setElevation(0);
            LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(-1, dp(54));
            addLp.topMargin = dp(9);
            row.addView(add, addLp);
            add.setOnClickListener(v -> addPcApp(app[0], app[1]));
        }
        sc.addView(list);
        page.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1));
        sc.setOverScrollMode(View.OVER_SCROLL_NEVER);
        setContentView(page);
    }
    private void showCustomPcAppDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(8), dp(18), dp(8));
        EditText name = field("Tên ứng dụng", false);
        EditText url = field("Địa chỉ web (https://...)", false);
        addFormField(form, "Tên hiển thị", name);
        addFormField(form, "Đường dẫn", url);
        new AlertDialog.Builder(this).setTitle("Thêm ứng dụng")
            .setView(form).setNegativeButton("HỦY", null)
            .setPositiveButton("THÊM", (d, w) -> {
                String n = name.getText().toString().trim();
                String u = url.getText().toString().trim();
                if (n.isEmpty() || !(u.startsWith("https://") || u.startsWith("http://"))) {
                    new AlertDialog.Builder(this).setMessage("Hãy nhập tên và link http/https hợp lệ.")
                        .setPositiveButton("OK", null).show();
                } else addPcApp(n, u);
            }).show();
    }

    private void showEditPcAppDialog(String appId, String oldName, String oldUrl) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(8), dp(18), dp(8));
        EditText name = field("Tên ứng dụng", false);
        name.setText(oldName);
        EditText url = field("Địa chỉ web (https://...)", false);
        url.setText(oldUrl);
        addFormField(form, "Tên hiển thị", name);
        addFormField(form, "Đường dẫn", url);
        new AlertDialog.Builder(this).setTitle("Đổi tên hoặc đường dẫn")
            .setView(form).setNegativeButton("HỦY", null)
            .setPositiveButton("LƯU", (d, w) -> {
                String n = name.getText().toString().trim();
                String u = url.getText().toString().trim();
                if (n.isEmpty() || !(u.startsWith("https://") || u.startsWith("http://"))) {
                    new AlertDialog.Builder(this).setMessage("Tên hoặc link không hợp lệ. Link phải bắt đầu bằng http:// hoặc https://.")
                        .setPositiveButton("OK", null).show();
                    return;
                }
                JSONArray current = getPcApps();
                JSONArray updated = new JSONArray();
                for (int j = 0; j < current.length(); j++) {
                    JSONObject item = current.optJSONObject(j);
                    if (item == null) continue;
                    if (item.optString("id").equals(appId)) {
                        try {
                            JSONObject changed = new JSONObject();
                            changed.put("id", appId);
                            changed.put("name", n);
                            changed.put("url", u);
                            updated.put(changed);
                        } catch (Exception ignored) {}
                    } else updated.put(item);
                }
                savePcApps(updated);
                showPcAppLibrary();
            }).show();
    }


    // Skip the separate desktop screen and open the app library immediately.
    private void showPcAppLibrary() { showAddPcAppDialog(); }

    private void showPcDesktop() {
        final int desktop = Color.rgb(17, 83, 132);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10), dp(9), dp(10), dp(8));
        root.setBackgroundColor(desktop);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(10), dp(7), dp(10), dp(7));
        top.setBackground(round(Color.rgb(13, 55, 91), 10));
        TextView title = new TextView(this);
        title.setText("▦  GKC DESKTOP");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        top.addView(title, new LinearLayout.LayoutParams(0, dp(42), 1));
        Button home = smallButton("⌂");
        home.setTextColor(Color.WHITE);
        home.setOnClickListener(v -> showHomeDashboard());
        top.addView(home, new LinearLayout.LayoutParams(dp(42), dp(42)));
        root.addView(top);

        ScrollView sc = new ScrollView(this);
        sc.setFillViewport(true);
        sc.setVerticalScrollBarEnabled(false);
        LinearLayout icons = new LinearLayout(this);
        icons.setOrientation(LinearLayout.VERTICAL);
        icons.setPadding(dp(4), dp(12), dp(4), dp(12));
        sc.addView(icons);
        root.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout row1 = new LinearLayout(this);
        row1.setGravity(Gravity.TOP);
        addDesktopIcon(row1, "🌐", "Chrome", () -> launchChrome());
        addDesktopIcon(row1, "📁", "Ứng dụng", () -> showAddPcAppDialog());
        addDesktopIcon(row1, "›_", "Terminal", () -> setContentView(terminalRoot));
        addDesktopIcon(row1, "⇄", "SSH / VPS", () -> showSshPage());
        icons.addView(row1);

        JSONArray savedApps = getPcApps();
        if (savedApps.length() > 0) {
            TextView label = new TextView(this);
            label.setText("ỨNG DỤNG CỦA BẠN");
            label.setTextColor(Color.WHITE);
            label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            label.setTextSize(13);
            label.setPadding(dp(5), dp(16), dp(5), dp(7));
            icons.addView(label);
            LinearLayout appRow = new LinearLayout(this);
            appRow.setGravity(Gravity.TOP);
            for (int i = 0; i < savedApps.length(); i++) {
                JSONObject app = savedApps.optJSONObject(i);
                if (app == null) continue;
                final String appName = app.optString("name", "Ứng dụng");
                final String appUrl = app.optString("url", "https://www.google.com");
                addDesktopIcon(appRow, "▣", appName, () -> openPcApp(app.optString("id"), appName, appUrl));
                if (appRow.getChildCount() == 4) {
                    icons.addView(appRow);
                    appRow = new LinearLayout(this);
                    appRow.setGravity(Gravity.TOP);
                }
            }
            if (appRow.getChildCount() > 0) icons.addView(appRow);
        }

        LinearLayout taskbar = new LinearLayout(this);
        taskbar.setGravity(Gravity.CENTER_VERTICAL);
        taskbar.setPadding(dp(7), dp(5), dp(7), dp(5));
        taskbar.setBackground(round(Color.rgb(12, 39, 65), 12));
        Button start = smallButton("⊞  BẮT ĐẦU");
        start.setTextColor(Color.WHITE);
        start.setOnClickListener(v -> {
            String[] items = {"Trình duyệt", "Thêm ứng dụng", "Terminal", "SSH / VPS", "Cửa hàng Play"};
            new AlertDialog.Builder(this).setTitle("GKC Desktop").setItems(items, (d, which) -> {
                if (which == 0) launchChrome();
                else if (which == 1) showAddPcAppDialog();
                else if (which == 2) setContentView(terminalRoot);
                else if (which == 3) showSshPage();
                else openPlayStore();
            }).show();
        });
        taskbar.addView(start, new LinearLayout.LayoutParams(-2, dp(42)));
        TextView status = new TextView(this);
        status.setText("  GKC PC  •  ANDROID");
        status.setTextColor(Color.rgb(220, 238, 255));
        status.setTextSize(12);
        taskbar.addView(status, new LinearLayout.LayoutParams(0, -2, 1));
        Button store = smallButton("▶");
        store.setTextColor(Color.WHITE);
        store.setOnClickListener(v -> openPlayStore());
        taskbar.addView(store, new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout.LayoutParams taskLp = new LinearLayout.LayoutParams(-1, -2);
        taskLp.topMargin = dp(5);
        root.addView(taskbar, taskLp);
        setContentView(root);
    }

    private void addDesktopIcon(LinearLayout parent, String symbol, String label, Runnable action) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(3), dp(5), dp(3), dp(5));
        TextView icon = new TextView(this);
        icon.setText(symbol);
        icon.setTextColor(Color.WHITE);
        icon.setTextSize(28);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(round(Color.rgb(22, 104, 157), 12));
        item.addView(icon, new LinearLayout.LayoutParams(dp(54), dp(50)));
        TextView name = new TextView(this);
        name.setText(label);
        name.setTextColor(Color.WHITE);
        name.setTextSize(11);
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(2);
        item.addView(name, new LinearLayout.LayoutParams(-1, dp(34)));
        item.setOnClickListener(v -> action.run());
        parent.addView(item, new LinearLayout.LayoutParams(0, -2, 1));
    }

    private void openPlayStore() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://home"));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps"))); }
            catch (Exception ignored) {
                new AlertDialog.Builder(this).setMessage("Không tìm thấy cửa hàng ứng dụng trên thiết bị.").setPositiveButton("OK", null).show();
            }
        }
    }

    // Open the built-in GKC browser inside the PC desktop; never launch the device's Chrome app.
    private void launchChrome() {
        openPcApp("builtin_chrome", "Chrome", "https://www.google.com");
    }

    private void openPcApp(String appId, String appName, String appUrl) {
        String profileName = "gkc_" + (appId == null ? "unknown" : appId.replaceAll("[^A-Za-z0-9_-]", "_"));

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.rgb(20, 21, 25));

        // Compact centered controls; hide the URL/address field to give Discord more room.
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(4), dp(2), dp(4), dp(2));
        nav.setBackgroundColor(Color.rgb(27, 28, 33));

        Button home = smallButton("⌂");
        Button back = smallButton("←");
        Button forward = smallButton("→");
        Button reload = smallButton("↻");
        for (Button q : new Button[]{home, back, forward, reload}) {
            q.setTextSize(11);
            q.setPadding(0, 0, 0, 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(34), dp(29));
            lp.leftMargin = dp(3);
            lp.rightMargin = dp(3);
            nav.addView(q, lp);
        }
        page.addView(nav, new LinearLayout.LayoutParams(-1, dp(34)));

        WebView web = pcWebView;
        if (web != null && !profileName.equals(pcWebProfile)) {
            try {
                if (web.getParent() instanceof ViewGroup) ((ViewGroup) web.getParent()).removeView(web);
                web.stopLoading();
                web.destroy();
            } catch (Exception ignored) {}
            pcWebView = null;
            pcWebProfile = "";
            web = null;
        }
        if (web == null) {
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                new AlertDialog.Builder(this).setTitle("Cần cập nhật Android System WebView")
                    .setMessage("Thiết bị chưa hỗ trợ hồ sơ trình duyệt riêng. Hãy cập nhật Android System WebView/Chrome trong CH Play. Ứng dụng sẽ không mở bằng hồ sơ dùng chung để tránh lẫn tài khoản.")
                    .setPositiveButton("OK", null).show();
                showPcAppLibrary();
                return;
            }
            web = new WebView(this);
            try {
                WebViewCompat.setProfile(web, profileName);
            } catch (Exception profileError) {
                web.destroy();
                new AlertDialog.Builder(this).setTitle("Không tạo được hồ sơ riêng")
                    .setMessage("Android System WebView chưa hỗ trợ hoặc không tạo được hồ sơ riêng. Hãy cập nhật Android System WebView/Chrome.")
                    .setPositiveButton("OK", null).show();
                showPcAppLibrary();
                return;
            }
            pcWebView = web;
            pcWebProfile = profileName;
            web.setFocusable(true);
            web.setFocusableInTouchMode(true);
            web.setOverScrollMode(View.OVER_SCROLL_NEVER);
            web.setVerticalScrollBarEnabled(false);
            web.setHorizontalScrollBarEnabled(false);
            web.setLayerType(View.LAYER_TYPE_HARDWARE, null);

            WebSettings ws = web.getSettings();
            ws.setJavaScriptEnabled(true);
            ws.setDomStorageEnabled(true);
            ws.setGeolocationEnabled(false);
            ws.setSaveFormData(false);
            ws.setDatabaseEnabled(false);
            ws.setLoadWithOverviewMode(true);
            ws.setUseWideViewPort(true);
            ws.setSupportZoom(true);
            ws.setBuiltInZoomControls(true);
            ws.setDisplayZoomControls(false);
            ws.setTextZoom(90);
            web.setInitialScale(0);
            // Request the desktop site while fitting its wide layout to the phone/tablet screen.
            ws.setUserAgentString("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            ws.setMediaPlaybackRequiresUserGesture(true);
            ws.setLoadsImagesAutomatically(true);
            ws.setCacheMode(WebSettings.LOAD_DEFAULT);
            ws.setJavaScriptCanOpenWindowsAutomatically(false);
            if (Build.VERSION.SDK_INT >= 21) {
                ws.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
                WebViewCompat.getProfile(web).getCookieManager().setAcceptThirdPartyCookies(web, true);
            }
            WebViewCompat.getProfile(web).getCookieManager().setAcceptCookie(true);
            web.setWebViewClient(new WebViewClient() {
                @Override public void onPageFinished(WebView v, String url) {
                    pcWebCurrentUrl = url == null ? "" : url;
                    if (url != null && browserTabIndex >= 0 && browserTabIndex < browserTabs.size()) {
                        browserTabs.set(browserTabIndex, url);
                    }
                    saveBrowserProgress();
                }
                @Override public void onReceivedError(WebView v, android.webkit.WebResourceRequest req,
                                                      android.webkit.WebResourceError err) {
                    if (req.isForMainFrame()) showWebErrorPage(v, err.getDescription().toString());
                }
                @Override public void onReceivedSslError(WebView v, android.webkit.SslErrorHandler h,
                                                         android.net.http.SslError e) {
                    h.cancel();
                    showWebErrorPage(v, "Chứng chỉ bảo mật không hợp lệ. Kết nối đã bị chặn.");
                }
            });
            web.setWebChromeClient(new WebChromeClient());
        }

        if (web.getParent() instanceof ViewGroup) {
            ((ViewGroup) web.getParent()).removeView(web);
        }
        web.resumeTimers();
        web.onResume();
        page.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));

        final WebView active = web;
        home.setOnClickListener(v -> showPcAppLibrary());
        back.setOnClickListener(v -> { if (active.canGoBack()) active.goBack(); });
        forward.setOnClickListener(v -> { if (active.canGoForward()) active.goForward(); });
        reload.setOnClickListener(v -> active.reload());

        setContentView(page);
        String target = appUrl == null || appUrl.trim().isEmpty() ? "https://www.google.com" : appUrl.trim();
        if (!target.equals(pcWebCurrentUrl) || active.getUrl() == null) active.loadUrl(target);
    }

    private void loadBrowserProgress() {
        try {
            SharedPreferences p = getSharedPreferences("gkc_browser_progress", MODE_PRIVATE);
            String raw = p.getString("tabs", "");
            if (raw != null && !raw.isEmpty()) {
                JSONArray a = new JSONArray(raw);
                for (int i = 0; i < a.length() && browserTabs.size() < 5; i++) {
                    String u = a.optString(i, "");
                    if (u.startsWith("http://") || u.startsWith("https://")) browserTabs.add(u);
                }
            }
            browserTabIndex = browserTabs.isEmpty() ? 0 : Math.max(0, Math.min(p.getInt("tab_index", 0), browserTabs.size() - 1));
            String savedPath = p.getString("terminal_path", currentPath);
            if (savedPath != null && savedPath.startsWith("/") && new java.io.File(savedPath).isDirectory()) currentPath = savedPath;
            String savedHistory = p.getString("terminal_history", "");
            if (savedHistory != null && !savedHistory.isEmpty()) {
                JSONArray h = new JSONArray(savedHistory);
                for (int i = Math.max(0, h.length() - 60); i < h.length(); i++) history.add(h.optString(i));
                historyIndex = history.size();
            }
        } catch (Exception ignored) {}
    }

    private void saveBrowserProgress() {
        try {
            JSONArray a = new JSONArray();
            for (int i = 0; i < browserTabs.size() && i < 5; i++) a.put(browserTabs.get(i));
            getSharedPreferences("gkc_browser_progress", MODE_PRIVATE).edit()
                .putString("tabs", a.toString())
                .putInt("tab_index", Math.max(0, browserTabIndex))
                .putString("terminal_path", currentPath)
                .putString("terminal_history", new JSONArray(history).toString())
                .apply();
        } catch (Exception ignored) {}
    }

    @Override protected void onPause() {
        saveBrowserProgress();
        if (pcWebView != null) {
            try { pcWebView.onPause(); pcWebView.pauseTimers(); } catch (Exception ignored) {}
        }
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        if (pcWebView != null && pcWebView.getParent() != null) {
            try { pcWebView.onResume(); pcWebView.resumeTimers(); } catch (Exception ignored) {}
        }
    }

    private void showWebErrorPage(WebView web, String detail) {
        String safe = detail == null ? "Không thể kết nối tới trang web." :
            detail.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        String html = "<html><meta name='viewport' content='width=device-width,initial-scale=1'>" +
            "<body style='margin:0;background:#1e1f22;color:#f2f3f5;font-family:Arial,sans-serif;display:flex;" +
            "align-items:center;justify-content:center;height:100vh;text-align:center'>" +
            "<div style='padding:28px;max-width:520px'><div style='font-size:42px'>☁</div>" +
            "<h2>Không thể tải ứng dụng</h2><p style='color:#b5bac1'>" + safe + "</p>" +
            "<p style='color:#b5bac1'>Kiểm tra mạng rồi thử tải lại. Bạn cũng có thể mở bằng Chrome.</p>" +
            "<button onclick='location.reload()' style='background:#5865f2;color:white;border:0;" +
            "padding:12px 22px;border-radius:8px;font-weight:bold'>THỬ LẠI</button></div></body></html>";
        web.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
    }

    private void openDiscordPc() {
        openPcApp("builtin_discord", "Discord PC", "https://discord.com/app");
    }

    private void openPcApps() {
        openDiscordPc();
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
        b.setTextSize(12);
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
            if (terminalSession != null && terminalSession.isRunning()) {
                if (terminalView != null && terminalView.mTermSession != terminalSession) terminalView.attachSession(terminalSession);
                return;
            }
            String[] args = new String[]{"sh", "-i"};
            String[] env = new String[]{
                "HOME=" + getFilesDir().getAbsolutePath(),
                "TMPDIR=" + getCacheDir().getAbsolutePath(),
                "PATH=/system/bin:/system/xbin",
                "SHELL=/system/bin/sh",
                "TERM=xterm-256color",
                "COLORTERM=truecolor",
                "LANG=C.UTF-8",
                "PS1=$ "
            };
            terminalSession = new TerminalSession("/system/bin/sh", currentPath, args, env, 4000, terminalCallbacks);
            terminalWelcomeWritten = false;
            if (terminalView != null) {
                terminalView.setTextSize(terminalFontSize);
                terminalView.setTypeface(resolveTerminalTypeface());
                terminalView.attachSession(terminalSession);
            }
            statusLabel.setText("  LOCAL PTY  •  /system/bin/sh");
        } catch (Exception e) {
            statusLabel.setText("  PTY ERROR");
            writeLine("Không thể khởi động PTY: " + e.getMessage(), Color.RED, false);
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
        if (sshConnected) { addHistory(cmd); runSshCommand(cmd); return; }
        if (terminalSession == null || !terminalSession.isRunning() || terminalSession.getEmulator() == null) {
            startShell();
            statusLabel.setText("  PTY STARTING — TRY AGAIN");
            return;
        }
        if (cmd.matches("^(python|python3|pip|pip3)(\\s|$).*$")) { runPythonCommand(cmd); return; }
        if ("clear".equals(cmd)) {
            addHistory(cmd);
            terminalSession.reset();
            terminalSession.write("\r");
            showPrompt();
            return;
        }
        if ("help".equals(cmd)) { addHistory(cmd); showHelp(); return; }
        if ("history".equals(cmd)) {
            addHistory(cmd);
            StringBuilder historyText = new StringBuilder();
            for (int i = 0; i < history.size(); i++) historyText.append(i + 1).append("  ").append(history.get(i)).append('\n');
            emitLocalText(historyText.toString());
            return;
        }
        if ("exit".equals(cmd)) {
            addHistory(cmd);
            terminalSession.write("exit\r");
            statusLabel.setText("  SHELL EXITING...");
            return;
        }
        addHistory(cmd);
        terminalSession.write(cmd + "\r");
        statusLabel.setText("  LOCAL PTY  •  RUNNING");
        showPrompt();
    }

    private void runPythonCommand(String cmd) {
        history.add(cmd);
        if (history.size() > 60) history.remove(0);
        historyIndex = history.size();
        saveBrowserProgress();
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
                    if (!result.isEmpty()) emitLocalText("$ " + cmd + "\n" + result + (result.endsWith("\n") ? "" : "\n"));
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
        String help = "GKC TERMINAL HELP\n" +
            "pwd       xem thư mục hiện tại\n" +
            "ls -la    liệt kê tệp và thư mục\n" +
            "cd PATH   chuyển thư mục\n" +
            "mkdir X   tạo thư mục     touch X   tạo tệp\n" +
            "cat X     đọc tệp         rm X      xóa tệp\n" +
            "cp A B    sao chép        mv A B    di chuyển/đổi tên\n" +
            "echo TEXT in văn bản      env       xem biến môi trường\n" +
            "history   xem lịch sử     clear     xóa màn hình\n" +
            "exit      kết thúc phiên shell\n" +
            "VPS / SSH  kết nối VPS bằng IP, mật khẩu hoặc SSH Key\n" +
            "Lưu ý: đây là shell Android, không phải Ubuntu/Debian; không có apt/sudo/root mặc định.\n";
        emitLocalText(help);
    }

    private EditText field(String hint, boolean secret) {
        EditText e = new EditText(this);
        e.setSingleLine(false); e.setTextColor(FG); e.setHintTextColor(MUTED);
        e.setHint(hint); e.setTextSize(16); e.setPadding(dp(10), dp(8), dp(10), dp(8));
        e.setBackground(round(Color.rgb(35, 47, 60), 8));
        if (secret) e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        else e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        return e;
    }


    private void showSshPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(14), dp(12), dp(14), dp(12));
        page.setBackground(new android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
            new int[]{Color.rgb(8, 6, 27), Color.rgb(18, 13, 45), Color.rgb(7, 13, 32)}));
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Button home = smallButton("‹  TRANG CHỦ");
        home.setTextColor(Color.WHITE);
        home.setOnClickListener(v -> showHomeDashboard());
        header.addView(home);
        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setPadding(dp(12), 0, 0, 0);
        TextView title = new TextView(this);
        title.setText("SSH / VPS"); title.setTextColor(Color.WHITE); title.setTextSize(21);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView subtitle = new TextView(this);
        subtitle.setText("REMOTE SERVER CONNECTION");
        subtitle.setTextColor(Color.rgb(157, 188, 255)); subtitle.setTextSize(12);
        subtitle.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        titleBox.addView(title); titleBox.addView(subtitle);
        header.addView(titleBox, new LinearLayout.LayoutParams(0, -2, 1));
        page.addView(header);

        ScrollView sc = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, dp(15), 0, dp(10));
        sc.addView(body);
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(18), dp(18), dp(18), dp(18));
        hero.setBackground(neonPanel(Color.rgb(13, 38, 75), Color.rgb(43, 142, 255), 18));
        TextView icon = new TextView(this);
        icon.setText("⇄"); icon.setTextSize(38); icon.setTextColor(Color.WHITE);
        TextView heading = new TextView(this);
        heading.setText("Kết nối máy chủ từ xa");
        heading.setTextColor(Color.WHITE); heading.setTextSize(20);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView help = new TextView(this);
        help.setText("Kết nối VPS qua SSH bằng địa chỉ IP, cổng, tài khoản và mật khẩu hoặc SSH Key. Khi kết nối xong, lệnh sẽ chạy trên máy chủ.");
        help.setTextColor(Color.rgb(207, 222, 245)); help.setTextSize(15);
        help.setPadding(0, dp(7), 0, dp(13));
        hero.addView(icon); hero.addView(heading); hero.addView(help);
        Button connect = smallButton(sshConnected ? "ĐANG KẾT NỐI  •  MỞ TERMINAL VPS" : "＋  THIẾT LẬP KẾT NỐI SSH");
        connect.setTextColor(Color.WHITE);
        connect.setBackground(round(Color.rgb(36, 117, 218), 11));
        hero.addView(connect, new LinearLayout.LayoutParams(-1, dp(46)));
        connect.setOnClickListener(v -> {
            if (sshConnected) setContentView(terminalRoot);
            else showSshDialog();
        });
        body.addView(hero);
        Button profiles = smallButton("▤  QUẢN LÝ HỒ SƠ VPS");
        profiles.setTextColor(Color.WHITE);
        profiles.setBackground(round(Color.rgb(63, 78, 150), 10));
        LinearLayout.LayoutParams profilesLp = new LinearLayout.LayoutParams(-1, dp(43));
        profilesLp.topMargin = dp(9);
        body.addView(profiles, profilesLp);
        profiles.setOnClickListener(v -> showSshProfiles());

        LinearLayout status = new LinearLayout(this);
        status.setOrientation(LinearLayout.VERTICAL);
        status.setPadding(dp(15), dp(14), dp(15), dp(14));
        status.setBackground(neonPanel(Color.rgb(19, 17, 47), Color.rgb(107, 69, 180), 16));
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2);
        statusLp.topMargin = dp(12);
        body.addView(status, statusLp);
        TextView statusTitle = homeSectionTitle("TRẠNG THÁI KẾT NỐI");
        status.addView(statusTitle);
        addInfoLine(status, "Trạng thái", sshConnected ? "Đã kết nối VPS" : "Chưa kết nối");
        addInfoLine(status, "Cổng mặc định", "22");
        addInfoLine(status, "Bảo mật", "SSH");
        if (sshConnected) {
            Button disconnect = smallButton("NGẮT KẾT NỐI");
            disconnect.setTextColor(Color.WHITE);
            disconnect.setBackground(round(Color.rgb(150, 48, 71), 10));
            LinearLayout.LayoutParams disconnectLp = new LinearLayout.LayoutParams(-1, dp(42));
            disconnectLp.topMargin = dp(9);
            status.addView(disconnect, disconnectLp);
            disconnect.setOnClickListener(v -> { disconnectSsh(); showSshPage(); });
        }
        Button terminal = smallButton("›_  MỞ TERMINAL");
        terminal.setTextColor(Color.WHITE);
        terminal.setBackground(round(Color.rgb(94, 49, 168), 10));
        LinearLayout.LayoutParams terminalLp = new LinearLayout.LayoutParams(-1, dp(42));
        terminalLp.topMargin = dp(12);
        body.addView(terminal, terminalLp);
        terminal.setOnClickListener(v -> setContentView(terminalRoot));
        page.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(page);
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
        note.setText("GKC SSH • Kết nối trực tiếp tới VPS Ubuntu bằng SSH thật. Nhập đúng Host/IP và Port do nhà cung cấp cấp (Pinggy thường dùng port riêng). Mật khẩu và private key không được lưu trong hồ sơ.");
        note.setTextColor(MUTED); note.setTextSize(15); form.addView(note);
        SharedPreferences sshPrefs = getSharedPreferences("gkc_ssh_profiles", MODE_PRIVATE);
        EditText host = field("Host / IP VPS", false);
        host.setHint("Ví dụ: ten-may-chu.run.pinggy-free.link");
        host.setText(sshPrefs.getString("last_host", ""));
        EditText port = field("Port SSH", false);
        port.setText(sshPrefs.getString("last_port", "34071"));
        EditText user = field("Username", false);
        user.setText(sshPrefs.getString("last_user", "root"));
        EditText pass = field("Mật khẩu SSH (không lưu lại)", true);
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
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("GKC SSH • Thêm máy chủ")
            .setView(sc).setNegativeButton("Đóng", null)
            .setPositiveButton("Kết nối", null)
            .setNeutralButton("Lưu hồ sơ", null).create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String h = host.getText().toString().trim();
                String p = port.getText().toString().trim();
                String u = user.getText().toString().trim();
                if (h.isEmpty() || u.isEmpty()) {
                    host.setError("Nhập Host/IP"); user.setError("Nhập username"); return;
                }
                sshPrefs.edit().putString("last_host", h).putString("last_port", p)
                    .putString("last_user", u).apply();
                dialog.dismiss();
                connectSsh(h, p, u, pass.getText().toString(),
                    key.getText().toString(), phrase.getText().toString(), mode.getSelectedItemPosition());
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                String savedHost = host.getText().toString().trim();
                String savedPort = port.getText().toString().trim();
                String savedUser = user.getText().toString().trim();
                if (savedHost.isEmpty() || savedUser.isEmpty()) {
                    host.setError("Nhập Host/IP"); user.setError("Nhập username"); return;
                }
                saveSshProfile(savedHost, savedPort, savedUser);
                sshPrefs.edit().putString("last_host", savedHost).putString("last_port", savedPort)
                    .putString("last_user", savedUser).apply();
                Toast.makeText(this, "Đã lưu hồ sơ VPS. Mật khẩu/SSH key không được lưu.", Toast.LENGTH_SHORT).show();
            });
        });
        dialog.show();
    }

    private void saveSshProfile(String host, String port, String user) {
        SharedPreferences prefs = getSharedPreferences("gkc_ssh_profiles", MODE_PRIVATE);
        JSONArray list;
        try { list = new JSONArray(prefs.getString("profiles", "[]")); }
        catch (Exception e) { list = new JSONArray(); }
        JSONArray next = new JSONArray();
        boolean replaced = false;
        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.optJSONObject(i);
            if (item == null) continue;
            if (host.equals(item.optString("host")) && port.equals(item.optString("port")) && user.equals(item.optString("user"))) {
                if (!replaced) {
                    JSONObject updated = new JSONObject();
                    try { updated.put("host", host); updated.put("port", port); updated.put("user", user); } catch (Exception ignored) {}
                    next.put(updated); replaced = true;
                }
            } else next.put(item);
        }
        if (!replaced) {
            JSONObject item = new JSONObject();
            try { item.put("host", host); item.put("port", port); item.put("user", user); } catch (Exception ignored) {}
            next.put(item);
        }
        prefs.edit().putString("profiles", next.toString()).apply();
    }

    private void showSshProfiles() {
        SharedPreferences prefs = getSharedPreferences("gkc_ssh_profiles", MODE_PRIVATE);
        JSONArray list;
        try { list = new JSONArray(prefs.getString("profiles", "[]")); }
        catch (Exception e) { list = new JSONArray(); }
        ArrayList<JSONObject> entries = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>();
        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.optJSONObject(i);
            if (item == null) continue;
            entries.add(item);
            labels.add(item.optString("user", "root") + " @ " + item.optString("host") + ":" + item.optString("port", "22"));
        }
        if (entries.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("Hồ sơ VPS")
                .setMessage("Chưa có hồ sơ nào. Mở Thiết lập SSH rồi chọn Lưu hồ sơ.")
                .setPositiveButton("OK", null).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle("Chọn hồ sơ VPS")
            .setItems(labels.toArray(new String[0]), (d, which) -> {
                JSONObject item = entries.get(which);
                String h = item.optString("host"), p = item.optString("port", "22"), u = item.optString("user", "root");
                new AlertDialog.Builder(this).setTitle("Hồ sơ VPS")
                    .setMessage(labels.get(which) + "\\nMật khẩu và SSH key cần nhập lại khi kết nối.")
                    .setPositiveButton("Kết nối", (x, w) -> {
                        prefs.edit().putString("last_host", h).putString("last_port", p).putString("last_user", u).apply();
                        showSshDialog();
                    })
                    .setNeutralButton("Xóa hồ sơ", (x, w) -> {
                        JSONArray remaining = new JSONArray();
                        for (int i = 0; i < entries.size(); i++) if (i != which) remaining.put(entries.get(i));
                        prefs.edit().putString("profiles", remaining.toString()).apply();
                        Toast.makeText(this, "Đã xóa hồ sơ VPS.", Toast.LENGTH_SHORT).show();
                        showSshProfiles();
                    })
                    .setNegativeButton("Đóng", null).show();
            }).show();
    }

    private void addFormField(LinearLayout parent, String label, View inputView) {
        TextView t = new TextView(this); t.setText(label); t.setTextColor(FG); t.setTextSize(15);
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
                // Persist SSH host fingerprints in app-private storage. Changed fingerprints
                // are surfaced to the user instead of silently trusting a different server.
                java.io.File knownHosts = new java.io.File(getFilesDir(), "ssh_known_hosts");
                java.io.File knownHostsParent = knownHosts.getParentFile();
                if (knownHostsParent != null && !knownHostsParent.exists() && !knownHostsParent.mkdirs()) {
                    throw new java.io.IOException("Không tạo được thư mục lưu SSH host key.");
                }
                if (!knownHosts.exists() && !knownHosts.createNewFile()) {
                    throw new java.io.IOException("Không tạo được tệp SSH known_hosts.");
                }
                jsch.setKnownHosts(knownHosts.getAbsolutePath());
                if (!privateKey.trim().isEmpty() && mode != 1) {
                    byte[] keyBytes = privateKey.replace("\\n", "\n").getBytes("UTF-8");
                    if (passphrase.isEmpty()) jsch.addIdentity("terminal-key", keyBytes, null, null);
                    else jsch.addIdentity("terminal-key", keyBytes, null, passphrase.getBytes("UTF-8"));
                }
                Session session = jsch.getSession(username, host, portNumber);
                if (!password.isEmpty() && mode != 2) session.setPassword(password);
                Properties config = new Properties();
                // First connection is convenient, but host key identity is not verified in this mode.
                config.put("StrictHostKeyChecking", "ask");
                config.put("PreferredAuthentications", mode == 1 ? "password,keyboard-interactive" :
                    (mode == 2 ? "publickey" : "publickey,password,keyboard-interactive"));
                session.setConfig(config);
                session.setServerAliveInterval(15000);
                session.setServerAliveCountMax(3);
                session.setUserInfo(new UserInfo() {
                    @Override public String getPassword() { return password; }
                    @Override public boolean promptPassword(String message) { return !password.isEmpty(); }
                    @Override public boolean promptPassphrase(String message) { return !passphrase.isEmpty(); }
                    @Override public String getPassphrase() { return passphrase; }
                    @Override public boolean promptYesNo(String message) {
                        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
                        final boolean[] accepted = new boolean[]{false};
                        runOnUiThread(() -> new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Xác minh máy chủ SSH")
                            .setMessage(message + "\\n\\nChỉ chấp nhận nếu đây đúng là VPS của bạn. Nếu không chắc, hãy hủy và xác minh fingerprint với nhà cung cấp.")
                            .setNegativeButton("Hủy", (d,w) -> { accepted[0] = false; latch.countDown(); })
                            .setPositiveButton("Tin cậy máy chủ này", (d,w) -> { accepted[0] = true; latch.countDown(); })
                            .setOnCancelListener(d -> latch.countDown()).show());
                        try { return latch.await(90, java.util.concurrent.TimeUnit.SECONDS) && accepted[0]; }
                        catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
                    }
                    @Override public void showMessage(String message) {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show());
                    }
                });
                session.connect(18000);
                if (terminalSession != null && terminalSession.isRunning()) terminalSession.reset();
                ChannelShell channel = (ChannelShell) session.openChannel("shell");
                channel.setPtyType("xterm-256color");
                channel.setPtySize(100, 32, 900, 576);
                channel.setPty(true);
                java.io.InputStream in = channel.getInputStream();
                java.io.OutputStream out = channel.getOutputStream();
                channel.connect(10000);
                synchronized (this) {
                    sshSession = session; sshChannel = channel; sshRawIn = in; sshRawOut = out;
                    sshIn = new BufferedWriter(new OutputStreamWriter(out, "UTF-8"));
                    sshConnected = true; sshReaderRunning = true;
                }
                runOnUiThread(() -> {
                    busy = false;
                    if (terminalRoot != null) setContentView(terminalRoot);
                    statusLabel.setText("  VPS CONNECTED  •  " + host);
                    input.setHint("Nhập lệnh VPS rồi nhấn RUN/Enter hoặc bật INPUT: RAW cho TUI...");
                    if (terminalView != null) terminalView.postDelayed(this::resizeSshPty, 350);
                    writeLine("Đã kết nối VPS. Terminal nhận dữ liệu trực tiếp từ máy chủ.", GREEN, true);
                    writeLine("Bật INPUT: RAW để dùng vim, nano, top và các chương trình tương tác.", MUTED, false);
                });
                // Read in large blocks and batch UI rendering on a 40 ms cadence.
                byte[] buffer = new byte[8192];
                while (sshReaderRunning && !channel.isClosed()) {
                    int n = in.read(buffer);
                    if (n < 0) break;
                    queueTerminalBytes(buffer, 0, n);
                }
                if (sshReaderRunning) runOnUiThread(() -> {
                    flushTerminalOutput();
                    sshConnected = false; busy = false; statusLabel.setText("  SSH DISCONNECTED");
                    writeLine("Kết nối VPS đã đóng.", Color.RED, false);
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

    // Coalesce local and remote output. TextView updates and auto-scroll happen once per batch.

    private void queueTerminalOutput(String chunk) {
        if (chunk == null || chunk.isEmpty()) return;
        byte[] data = chunk.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        queueTerminalBytes(data, 0, data.length);
    }

    private void queueTerminalBytes(byte[] data, int offset, int length) {
        if (data == null || length <= 0) return;
        boolean schedule = false;
        synchronized (terminalPendingBytes) {
            if (terminalPendingBytes.size() + length > 524288) terminalPendingBytes.reset();
            terminalPendingBytes.write(data, offset, length);
            if (!terminalFlushScheduled) { terminalFlushScheduled = true; schedule = true; }
        }
        if (schedule) mainHandler.postDelayed(this::flushTerminalOutput, 32);
    }

    private void flushTerminalOutput() {
        if (isFinishing()) {
            synchronized (terminalPendingBytes) {
                terminalPendingBytes.reset();
                terminalFlushScheduled = false;
            }
            return;
        }
        final byte[] chunk;
        synchronized (terminalPendingBytes) {
            if (terminalPendingBytes.size() == 0) { terminalFlushScheduled = false; return; }
            // The SSH login banner can arrive before the terminal screen is attached and measured.
            // Keep the raw bytes queued until the VT emulator exists instead of losing that output.
            if (terminalView == null || terminalView.mEmulator == null) {
                mainHandler.postDelayed(this::flushTerminalOutput, 32);
                return;
            }
            chunk = terminalPendingBytes.toByteArray();
            terminalPendingBytes.reset();
            terminalFlushScheduled = false;
        }
        terminalView.mEmulator.append(chunk, chunk.length);
        terminalView.onScreenUpdated();
    }

    private void emitLocalText(String text) {
        if (text == null || text.isEmpty()) return;
        if (sshConnected) { queueTerminalOutput(text); return; }
        TerminalSession active = terminalSession;
        if (active == null || !active.isRunning() || active.getEmulator() == null) {
            if (output != null) output.append(text);
            return;
        }
        byte[] data = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        active.getEmulator().append(data, data.length);
        if (terminalView != null) terminalView.onScreenUpdated();
    }


    private void resizeSshPty() {
        final ChannelShell active = sshChannel;
        if (!sshConnected || active == null || terminalView == null) return;
        terminalView.updateSize();
        TerminalEmulator emulator = terminalView.mEmulator;
        if (emulator == null) return;
        int columns = Math.max(4, Math.min(240, emulator.mColumns));
        int rows = Math.max(4, Math.min(100, emulator.mRows));
        try { active.setPtySize(columns, rows, columns * dp(8), rows * dp(18)); }
        catch (Exception ignored) {}
    }

    private final java.util.concurrent.ExecutorService sshIoExecutor =
            java.util.concurrent.Executors.newSingleThreadExecutor();

    private void runSshCommand(String command) {
        final java.io.OutputStream remote = sshRawOut;
        if (!sshConnected || remote == null) {
            writeLine("SSH chưa kết nối.", Color.RED, false); return;
        }
        statusLabel.setText("  VPS CONNECTED  •  SENDING");
        sshIoExecutor.execute(() -> {
            try {
                remote.write((command + "\r").getBytes("UTF-8"));
                remote.flush();
                runOnUiThread(() -> {
                    if (sshConnected && statusLabel != null) statusLabel.setText("  VPS CONNECTED");
                });
            } catch (Exception e) {
                final String reason = e.getClass().getSimpleName() + ": " + e.getMessage();
                runOnUiThread(() -> {
                    writeLine("Không gửi được lệnh SSH: " + reason, Color.RED, false);
                    disconnectSsh();
                });
            }
        });
    }


    private void sendSshControl(String value) { sendTerminalInput(value); }

    private void sendTerminalInput(String value) {
        if (value == null || value.isEmpty()) return;
        if (sshConnected) {
            final java.io.OutputStream remote = sshRawOut;
            if (remote == null) return;
            sshIoExecutor.execute(() -> {
                try {
                    remote.write(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    remote.flush();
                } catch (Exception e) {
                    final String reason = e.getClass().getSimpleName() + ": " + e.getMessage();
                    runOnUiThread(() -> {
                        writeLine("Lỗi gửi phím SSH: " + reason, Color.RED, false);
                        disconnectSsh();
                    });
                }
            });
        } else if (terminalSession != null && terminalSession.isRunning()) terminalSession.write(value);
    }

    private void disconnectSsh() {
        flushTerminalOutput();
        // Detach state immediately so no new command can target a closing channel.
        sshReaderRunning = false;
        sshConnected = false;
        final ChannelShell oldChannel = sshChannel;
        final Session oldSession = sshSession;
        sshChannel = null; sshSession = null; sshIn = null; sshOut = null;
        sshRawIn = null; sshRawOut = null;
        busy = false;
        // Channel/session teardown may block; keep it off the UI thread too.
        sshIoExecutor.execute(() -> {
            try { if (oldChannel != null) oldChannel.disconnect(); } catch (Exception ignored) {}
            try { if (oldSession != null) oldSession.disconnect(); } catch (Exception ignored) {}
        });
        if (statusLabel != null) statusLabel.setText("  LOCAL PTY  •  DISCONNECTED");
        if (terminalSession != null && terminalSession.isRunning()) {
            terminalSession.reset();
            terminalSession.write("\r");
        }
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
        if (pathLabel != null) pathLabel.setText(" " + currentPath);
    }

    private void writeLine(String value, int color, boolean bold) {
        if (value == null || value.isEmpty()) return;
        String text = value + "\n";
        if (sshConnected) queueTerminalOutput(text);
        else emitLocalText(text);
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

    private void addHistory(String command) {
        history.add(command);
        if (history.size() > 60) history.remove(0);
        historyIndex = history.size();
        saveBrowserProgress();
    }

    private void trimOutput() {
        // Bound TextView memory without allocating a full copy of the existing transcript.
        final int maxChars = 12000;
        int length = output.length();
        if (length > maxChars) {
            int remove = length - maxChars;
            CharSequence text = output.getText();
            while (remove < length && text.charAt(remove) != '\n') remove++;
            output.setText(text.subSequence(Math.min(length, remove + 1), length), TextView.BufferType.SPANNABLE);
        }
    }


    private void copyOutput() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        String transcript = terminalView != null && terminalView.mEmulator != null
            ? terminalView.mEmulator.getScreen().getTranscriptText()
            : (output == null ? "" : output.getText().toString());
        if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("Terminal output", transcript));
        if (statusLabel != null) statusLabel.setText("  COPIED TO CLIPBOARD");
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
        TerminalSession active = terminalSession;
        terminalSession = null;
        terminalWelcomeWritten = false;
        if (active != null) try { active.finishIfRunning(); } catch (Exception ignored) {}
    }

    @Override protected void onDestroy() {
        stopShell();
        if (pcWebView != null) {
            try {
                if (pcWebView.getParent() instanceof ViewGroup) {
                    ((ViewGroup) pcWebView.getParent()).removeView(pcWebView);
                }
                pcWebView.stopLoading();
                pcWebView.destroy();
            } catch (Exception ignored) {}
            pcWebView = null;
        }
        super.onDestroy();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
    private final class TerminalCallbacks implements TerminalSessionClient, TerminalViewClient {
        private void updateFromTerminal(TerminalSession session) {
            if (session == null) return;
            String cwd = session.getCwd();
            if (cwd != null && !cwd.isEmpty() && !cwd.equals(currentPath)) {
                currentPath = cwd;
                if (pathLabel != null) pathLabel.setText(" " + currentPath);
                saveBrowserProgress();
            }
            if (terminalView != null) terminalView.onScreenUpdated();
        }
        @Override public void onTextChanged(TerminalSession session) {
            runOnUiThread(() -> updateFromTerminal(session));
        }
        @Override public void onTitleChanged(TerminalSession session) {
            runOnUiThread(() -> {
                if (statusLabel != null && session.getTitle() != null) statusLabel.setText("  LOCAL PTY  •  " + session.getTitle());
            });
        }
        @Override public void onSessionFinished(TerminalSession session) {
            runOnUiThread(() -> {
                busy = false;
                if (statusLabel != null) statusLabel.setText("  SHELL EXITED  •  " + session.getExitStatus());
            });
        }
        @Override public void onCopyTextToClipboard(TerminalSession session, String text) {
            runOnUiThread(() -> {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("Terminal selection", text));
            });
        }
        @Override public void onPasteTextFromClipboard(TerminalSession session) {
            runOnUiThread(() -> {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard == null || !clipboard.hasPrimaryClip() || clipboard.getPrimaryClip().getItemCount() == 0) return;
                CharSequence value = clipboard.getPrimaryClip().getItemAt(0).coerceToText(MainActivity.this);
                if (value != null && session.getEmulator() != null) session.getEmulator().paste(value.toString());
            });
        }
        @Override public void onBell(TerminalSession session) {}
        @Override public void onColorsChanged(TerminalSession session) {
            runOnUiThread(() -> { if (terminalView != null) terminalView.invalidate(); });
        }
        @Override public void onTerminalCursorStateChange(boolean state) {}
        @Override public Integer getTerminalCursorStyle() { return TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK; }
        @Override public void logError(String tag, String message) {}
        @Override public void logWarn(String tag, String message) {}
        @Override public void logInfo(String tag, String message) {}
        @Override public void logDebug(String tag, String message) {}
        @Override public void logVerbose(String tag, String message) {}
        @Override public void logStackTraceWithMessage(String tag, String message, Exception e) {}
        @Override public void logStackTrace(String tag, Exception e) {}

        @Override public float onScale(float scale) { return scale; }
        @Override public void onSingleTapUp(MotionEvent event) {
            if (input != null) {
                input.requestFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            }
        }
        @Override public boolean shouldBackButtonBeMappedToEscape() { return false; }
        @Override public boolean shouldEnforceCharBasedInput() { return true; }
        @Override public boolean shouldUseCtrlSpaceWorkaround() { return false; }
        @Override public boolean isTerminalViewSelected() { return false; }
        @Override public void copyModeChanged(boolean copyMode) {}
        @Override public boolean onKeyDown(int keyCode, KeyEvent event, TerminalSession session) { return false; }
        @Override public boolean onKeyUp(int keyCode, KeyEvent event) { return false; }
        @Override public boolean onLongPress(MotionEvent event) { return false; }
        @Override public boolean readControlKey() { return false; }
        @Override public boolean readAltKey() { return false; }
        @Override public boolean readShiftKey() { return false; }
        @Override public boolean readFnKey() { return false; }
        @Override public boolean onCodePoint(int codePoint, boolean ctrlDown, TerminalSession session) { return false; }
        @Override public void onEmulatorSet() {
            if (statusLabel != null) statusLabel.setText(sshConnected ? "  VPS CONNECTED" : "  LOCAL PTY  •  READY");
            if (sshConnected && terminalView != null) terminalView.postDelayed(() -> resizeSshPty(), 80);
            TerminalSession active = terminalSession;
            if (!sshConnected && !terminalWelcomeWritten && active != null && active.isRunning()) {
                terminalWelcomeWritten = true;
                active.write("export TERM=xterm-256color; PS1='$ '; printf '%s\\n' 'GKC APP • ANDROID PTY' 'ANSI / VT terminal ready' 'Type help for commands.'\\r");
            }
        }
    }

}
