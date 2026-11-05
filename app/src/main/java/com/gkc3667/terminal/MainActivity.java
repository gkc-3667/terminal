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
import android.content.Intent;
import android.net.Uri;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
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
    // Keep one Chromium view alive during this app session for faster app switching.
    private WebView pcWebView;
    private String pcWebCurrentUrl = "";
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
        buildUi();
        startShell();
        writeLine("GKC APP  •  ANDROID SHELL", GREEN, true);
        writeLine("Phiên shell thật đang chạy trong môi trường Android.", MUTED, false);
        writeLine("Android ABI: " + android.text.TextUtils.join(", ", Build.SUPPORTED_ABIS), BLUE, false);
        writeLine("Android API: " + Build.VERSION.SDK_INT, MUTED, false);
        writeLine("Gõ help để xem lệnh. Thư mục mặc định: " + currentPath, MUTED, false);
        showPrompt();
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
            navSsh.setOnClickListener(v -> showSshPage());
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
        title.setText("GKC APP");
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
        brand.setTextColor(Color.WHITE); brand.setTextSize(19);
        brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView subBrand = new TextView(this);
        subBrand.setText("POWER YOUR DEVICE");
        subBrand.setTextColor(Color.rgb(183, 154, 255)); subBrand.setTextSize(9);
        subBrand.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        brandBox.addView(brand); brandBox.addView(subBrand);
        top.addView(brandBox, new LinearLayout.LayoutParams(0, -2, 1));
        Button settings = smallButton("⚙");
        settings.setTextSize(19); settings.setTextColor(Color.WHITE);
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
            logo.setTextColor(Color.WHITE); logo.setTextSize(17); logo.setGravity(Gravity.CENTER);
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
            foot.setTextColor(mint); foot.setTextSize(10);
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
        eyebrow.setTextColor(Color.rgb(213, 171, 255)); eyebrow.setTextSize(9);
        eyebrow.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        TextView heroTitle = new TextView(this);
        heroTitle.setText("Xin chào!\nGKC APP");
        heroTitle.setTextColor(Color.WHITE); heroTitle.setTextSize(wide ? 25 : 20);
        heroTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView heroDesc = new TextView(this);
        heroDesc.setText("Mạnh mẽ  •  Đa năng  •  Siêu mượt");
        heroDesc.setTextColor(Color.rgb(213, 202, 255)); heroDesc.setTextSize(10);
        heroDesc.setPadding(0, dp(5), 0, 0);
        heroCopy.addView(eyebrow); heroCopy.addView(heroTitle); heroCopy.addView(heroDesc);
        hero.addView(heroCopy, new LinearLayout.LayoutParams(0, -2, 1));
        TextView robot = new TextView(this);
        robot.setText("🤖");
        robot.setTextSize(wide ? 66 : 54);
        robot.setGravity(Gravity.CENTER);
        robot.setBackground(round(Color.rgb(75, 24, 150), 50));
        robot.setElevation(dp(12));
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
        welcomeText.setTextColor(Color.WHITE); welcomeText.setTextSize(11);
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
        ready.setTextColor(mint); ready.setTextSize(10);
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
        footer.setTextColor(Color.rgb(204, 153, 255)); footer.setTextSize(9);
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
        t.setText(value); t.setTextColor(Color.WHITE); t.setTextSize(13);
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
        card.setElevation(dp(5));
        TextView ico = new TextView(this);
        ico.setText(icon); ico.setTextSize(27); ico.setTextColor(Color.WHITE);
        ico.setGravity(Gravity.CENTER);
        ico.setBackground(round(Color.rgb(69, 28, 139), 12));
        card.addView(ico, new LinearLayout.LayoutParams(dp(49), dp(43)));
        TextView name = new TextView(this);
        name.setText(title); name.setTextColor(Color.WHITE); name.setTextSize(15);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        name.setPadding(0, dp(8), 0, dp(3));
        card.addView(name);
        TextView sub = new TextView(this);
        sub.setText(desc); sub.setTextColor(Color.rgb(219, 211, 248)); sub.setTextSize(10);
        card.addView(sub);
        TextView open = new TextView(this);
        open.setText("MỞ NGAY  →");
        open.setTextColor(Color.WHITE); open.setTextSize(10);
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
        t.setTextColor(Color.rgb(218, 213, 243)); t.setTextSize(11);
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
        themeLabel.setTextSize(12);
        form.addView(themeLabel);
        Spinner theme = new Spinner(this);
        theme.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"GKC APP (xanh đen)", "Discord (tím than)"}));
        theme.setSelection(discordTheme ? 1 : 0);
        form.addView(theme, new LinearLayout.LayoutParams(-1, dp(48)));

        TextView fontLabel = new TextView(this);
        fontLabel.setText("PHÔNG CHỮ");
        fontLabel.setTextColor(FG);
        fontLabel.setTextSize(12);
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
            JSONObject item = new JSONObject();
            item.put("name", name.trim());
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
        Button back = smallButton("‹  PC APP");
        back.setTextColor(Color.WHITE);
        back.setOnClickListener(v -> showPcAppLibrary());
        header.addView(back);
        TextView heading = new TextView(this);
        heading.setText("  THÊM ỨNG DỤNG");
        heading.setTextColor(Color.WHITE); heading.setTextSize(19);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(heading, new LinearLayout.LayoutParams(0, dp(44), 1));
        page.addView(header);

        TextView caption = new TextView(this);
        caption.setText("CHỌN ỨNG DỤNG CÓ SẴN");
        caption.setTextColor(Color.rgb(190, 155, 255)); caption.setTextSize(10);
        caption.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        caption.setPadding(dp(4), dp(12), dp(4), dp(8));
        page.addView(caption);

        ScrollView sc = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        for (String[] app : catalog) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(9), dp(10), dp(9));
            row.setBackground(neonPanel(Color.rgb(20, 15, 46), Color.rgb(83, 54, 145), 13));
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
            rowLp.bottomMargin = dp(7);
            list.addView(row, rowLp);
            TextView icon = new TextView(this);
            icon.setText(app[0].equals("Discord") ? "💬" : app[0].equals("YouTube") ? "▶" :
                app[0].equals("Free Fire") ? "🔥" : app[0].equals("Roblox") ? "◇" : "🌐");
            icon.setTextSize(25); icon.setGravity(Gravity.CENTER);
            row.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(46)));
            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setPadding(dp(10), 0, 0, 0);
            TextView name = new TextView(this);
            name.setText(app[0]); name.setTextColor(Color.WHITE); name.setTextSize(14);
            name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            TextView url = new TextView(this);
            url.setText(app[1]); url.setTextColor(MUTED); url.setTextSize(9);
            info.addView(name); info.addView(url);
            row.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
            Button add = smallButton("THÊM");
            add.setTextColor(Color.WHITE);
            add.setBackground(round(Color.rgb(105, 54, 201), 10));
            add.setOnClickListener(v -> addPcApp(app[0], app[1]));
            row.addView(add);
        }
        sc.addView(list);
        page.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1));
        Button custom = smallButton("+  THÊM BẰNG LINK RIÊNG");
        custom.setTextColor(Color.WHITE);
        custom.setBackground(round(Color.rgb(112, 58, 220), 12));
        LinearLayout.LayoutParams customLp = new LinearLayout.LayoutParams(-1, dp(44));
        customLp.topMargin = dp(8);
        page.addView(custom, customLp);
        custom.setOnClickListener(v -> showCustomPcAppDialog());
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


    private void showPcAppLibrary() {
        int widthDp = (int)(getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density);
        int columns = widthDp >= 1100 ? 4 : (widthDp >= 700 ? 3 : (widthDp >= 430 ? 2 : 1));
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(14), dp(12), dp(14), dp(10));
        page.setBackground(new android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
            new int[]{Color.rgb(8, 6, 27), Color.rgb(24, 10, 53), Color.rgb(7, 13, 32)}));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Button home = smallButton("‹  TRANG CHỦ");
        home.setTextColor(Color.WHITE);
        home.setBackground(round(Color.rgb(55, 35, 94), 11));
        home.setOnClickListener(v -> showHomeDashboard());
        header.addView(home);
        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setPadding(dp(12), 0, 0, 0);
        TextView title = new TextView(this);
        title.setText("PC APP");
        title.setTextColor(Color.WHITE); title.setTextSize(21);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView sub = new TextView(this);
        sub.setText("THƯ VIỆN  •  ỨNG DỤNG CỦA BẠN");
        sub.setTextColor(Color.rgb(190, 155, 255)); sub.setTextSize(9);
        sub.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        titleBox.addView(title); titleBox.addView(sub);
        header.addView(titleBox, new LinearLayout.LayoutParams(0, -2, 1));
        page.addView(header);

        Button add = new Button(this);
        add.setText("+  THÊM ỨNG DỤNG");
        add.setAllCaps(false);
        add.setTextColor(Color.WHITE);
        add.setTextSize(20);
        add.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        add.setGravity(Gravity.CENTER);
        add.setMinHeight(dp(132));
        add.setPadding(dp(18), dp(22), dp(18), dp(22));
        add.setBackground(neonPanel(Color.rgb(112, 58, 220), Color.rgb(190, 145, 255), 18));
        add.setElevation(dp(7));
        add.setClickable(true);
        add.setFocusable(true);
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(-1, dp(132));
        addLp.topMargin = dp(12);
        addLp.bottomMargin = dp(4);
        page.addView(add, addLp);
        add.setOnClickListener(v -> showAddPcAppDialog());

        TextView intro = new TextView(this);
        intro.setText("Chọn ứng dụng để mở trong không gian PC. Discord sẽ hiển thị giao diện web máy tính giống video của bạn.");
        intro.setTextColor(Color.rgb(190, 184, 216));
        intro.setTextSize(11);
        intro.setPadding(dp(3), dp(12), dp(3), dp(10));
        page.addView(intro);

        ScrollView sc = new ScrollView(this);
        sc.setFillViewport(false);
        sc.setVerticalScrollBarEnabled(false);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        JSONArray apps = getPcApps();
        if (apps.length() == 0) {
            TextView empty = new TextView(this);
            empty.setText("Chưa có ứng dụng nào. Nhấn + THÊM APP để chọn ứng dụng hoặc tự dán link.");
            empty.setTextColor(MUTED); empty.setTextSize(13); empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(18), dp(42), dp(18), dp(42));
            empty.setBackground(neonPanel(Color.rgb(22, 15, 48), Color.rgb(95, 55, 160), 16));
            content.addView(empty);
        }
        LinearLayout gridRow = null;
        int inRow = 0;
        for (int i = 0; i < apps.length(); i++) {
            JSONObject item = apps.optJSONObject(i);
            if (item == null) continue;
            final String appName = item.optString("name", "Ứng dụng");
            final String appUrl = item.optString("url", "");
            final String appId = item.optString("id", "");
            if (gridRow == null || inRow == columns) {
                gridRow = new LinearLayout(this);
                gridRow.setOrientation(LinearLayout.HORIZONTAL);
                gridRow.setGravity(Gravity.TOP);
                content.addView(gridRow);
                inRow = 0;
            }
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(12), dp(12), dp(12), dp(11));
            card.setBackground(neonPanel(Color.rgb(19, 15, 45), Color.rgb(91, 58, 160), 16));
            card.setElevation(dp(3));

            LinearLayout cardTop = new LinearLayout(this);
            cardTop.setGravity(Gravity.CENTER_VERTICAL);
            TextView appIcon = new TextView(this);
            String lower = appName.toLowerCase(java.util.Locale.ROOT);
            String glyph = lower.contains("discord") ? "💬" : lower.contains("youtube") ? "▶" :
                lower.contains("roblox") ? "◇" : lower.contains("geforce") ? "🎮" :
                lower.contains("telegram") ? "✈" : lower.contains("spotify") ? "♫" :
                lower.contains("minecraft") ? "⛏" : lower.contains("free fire") ? "🔥" : "🌐";
            appIcon.setText(glyph); appIcon.setTextSize(27); appIcon.setGravity(Gravity.CENTER);
            appIcon.setBackground(round(Color.rgb(62, 35, 119), 13));
            cardTop.addView(appIcon, new LinearLayout.LayoutParams(dp(48), dp(46)));
            View topSpacer = new View(this);
            cardTop.addView(topSpacer, new LinearLayout.LayoutParams(0, 1, 1));
            Button menu = smallButton("⋮");
            menu.setTextSize(17);
            cardTop.addView(menu, new LinearLayout.LayoutParams(dp(38), dp(38)));
            menu.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(appName)
                .setItems(new String[]{"Đổi tên / đổi link", "Xóa ứng dụng"}, (d, which) -> {
                    if (which == 0) {
                        showEditPcAppDialog(appId, appName, appUrl);
                    } else {
                        new AlertDialog.Builder(this).setTitle("Xóa " + appName + "?")
                            .setMessage("Ứng dụng sẽ bị xóa khỏi thư viện trên thiết bị.")
                            .setNegativeButton("HỦY", null)
                            .setPositiveButton("XÓA", (confirm, w) -> {
                                JSONArray current = getPcApps();
                                JSONArray kept = new JSONArray();
                                for (int j = 0; j < current.length(); j++) {
                                    JSONObject candidate = current.optJSONObject(j);
                                    if (candidate != null && !candidate.optString("id").equals(appId)) kept.put(candidate);
                                }
                                savePcApps(kept);
                                showPcAppLibrary();
                            }).show();
                    }
                }).show());

            TextView name = new TextView(this);
            name.setText(appName); name.setTextColor(Color.WHITE); name.setTextSize(15);
            name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            name.setPadding(0, dp(9), 0, dp(3));
            TextView url = new TextView(this);
            url.setText(appUrl); url.setTextColor(Color.rgb(160, 151, 190)); url.setTextSize(9);
            url.setSingleLine(true); url.setEllipsize(android.text.TextUtils.TruncateAt.END);
            Button launch = smallButton("MỞ ỨNG DỤNG  →");
            launch.setTextColor(Color.WHITE);
            launch.setBackground(round(Color.rgb(91, 48, 174), 10));
            LinearLayout.LayoutParams launchLp = new LinearLayout.LayoutParams(-1, dp(38));
            launchLp.topMargin = dp(10);
            card.addView(cardTop); card.addView(name); card.addView(url); card.addView(launch, launchLp);
            card.setOnClickListener(v -> openPcApp(appName, appUrl));
            launch.setOnClickListener(v -> openPcApp(appName, appUrl));

            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(0, -2, 1);
            cardLp.setMargins(dp(4), dp(4), dp(4), dp(8));
            gridRow.addView(card, cardLp);
            inRow++;
        }
        if (gridRow != null && inRow < columns) {
            for (int k = inRow; k < columns; k++) {
                View filler = new View(this);
                gridRow.addView(filler, new LinearLayout.LayoutParams(0, dp(1), 1));
            }
        }
        sc.addView(content);
        page.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        Button terminal = smallButton("›_  TERMINAL");
        Button ssh = smallButton("⇄  SSH / VPS");
        Button homeBottom = smallButton("⌂  TRANG CHỦ");
        terminal.setOnClickListener(v -> setContentView(terminalRoot));
        ssh.setOnClickListener(v -> showSshPage());
        homeBottom.setOnClickListener(v -> showHomeDashboard());
        for (Button b : new Button[]{terminal, ssh, homeBottom}) {
            b.setTextColor(Color.WHITE);
            b.setBackground(round(Color.rgb(42, 30, 74), 10));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(42), 1);
            lp.setMargins(dp(4), dp(7), dp(4), 0);
            nav.addView(b, lp);
        }
        page.addView(nav);
        setContentView(page);
    }

    private void openPcApp(String appName, String appUrl) {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.rgb(16, 17, 22));

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(8), dp(5), dp(8), dp(5));
        toolbar.setBackgroundColor(Color.rgb(28, 25, 42));
        Button library = smallButton("‹  PC APP");
        library.setTextColor(Color.WHITE);
        toolbar.addView(library);
        TextView label = new TextView(this);
        label.setText(appName + "  •  DESKTOP");
        label.setTextColor(Color.WHITE);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        label.setTextSize(13);
        label.setPadding(dp(10), 0, dp(5), 0);
        toolbar.addView(label, new LinearLayout.LayoutParams(0, dp(42), 1));
        Button webBack = smallButton("←");
        Button reload = smallButton("↻");
        Button external = smallButton("CHROME ↗");
        for (Button b : new Button[]{webBack, reload, external}) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, dp(38));
            p.leftMargin = dp(5);
            toolbar.addView(b, p);
        }
        page.addView(toolbar);

        WebView web = pcWebView;
        if (web == null) {
            web = new WebView(this);
            pcWebView = web;
            web.setFocusable(true);
            web.setFocusableInTouchMode(true);
            web.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            web.setOverScrollMode(View.OVER_SCROLL_NEVER);
            WebSettings settings = web.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setDatabaseEnabled(true);
            settings.setLoadWithOverviewMode(false);
            settings.setUseWideViewPort(true);
            settings.setSupportZoom(true);
            settings.setBuiltInZoomControls(true);
            settings.setDisplayZoomControls(false);
            settings.setMediaPlaybackRequiresUserGesture(false);
            settings.setLoadsImagesAutomatically(true);
            settings.setCacheMode(WebSettings.LOAD_DEFAULT);
            settings.setJavaScriptCanOpenWindowsAutomatically(true);
            String currentUa = settings.getUserAgentString();
            java.util.regex.Matcher chromeVersion = java.util.regex.Pattern
                .compile("Chrome/([0-9.]+)").matcher(currentUa);
            String version = chromeVersion.find() ? chromeVersion.group(1) : "120.0.0.0";
            settings.setUserAgentString("Mozilla/5.0 (X11; CrOS x86_64 16093.0.0) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/" + version + " Safari/537.36");
            android.webkit.CookieManager cookies = android.webkit.CookieManager.getInstance();
            cookies.setAcceptCookie(true);
            if (Build.VERSION.SDK_INT >= 21) cookies.setAcceptThirdPartyCookies(web, true);
            web.setWebViewClient(new WebViewClient() {
                @Override public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    if (url != null && url.startsWith("https://discord.com/")) {
                        view.evaluateJavascript("(function(){var m=document.querySelector('meta[name=viewport]');" +
                            "if(!m){m=document.createElement('meta');m.name='viewport';document.head.appendChild(m);}" +
                            "m.setAttribute('content','width=1200, initial-scale=0.75, maximum-scale=2.0, user-scalable=yes');})();", null);
                    }
                }
                @Override public void onReceivedError(WebView view, android.webkit.WebResourceRequest request,
                                                       android.webkit.WebResourceError error) {
                    if (request.isForMainFrame()) showWebErrorPage(view, error.getDescription().toString());
                }
                @Override public void onReceivedHttpError(WebView view, android.webkit.WebResourceRequest request,
                                                           android.webkit.WebResourceResponse response) {
                    if (request.isForMainFrame() && response.getStatusCode() >= 400)
                        showWebErrorPage(view, "Máy chủ trả về lỗi HTTP " + response.getStatusCode());
                }
                @Override public void onReceivedSslError(WebView view, android.webkit.SslErrorHandler handler,
                                                          android.net.http.SslError error) {
                    handler.cancel();
                    showWebErrorPage(view, "Chứng chỉ bảo mật của trang web không hợp lệ. Kết nối đã bị chặn.");
                }
            });
            web.setWebChromeClient(new WebChromeClient());
        }
        if (web.getParent() instanceof ViewGroup) {
            ((ViewGroup) web.getParent()).removeView(web);
        }
        web.onResume();
        page.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        final WebView activeWeb = web;
        library.setOnClickListener(v -> {
            activeWeb.onPause();
            android.webkit.CookieManager.getInstance().flush();
            if (activeWeb.getParent() instanceof ViewGroup) {
                ((ViewGroup) activeWeb.getParent()).removeView(activeWeb);
            }
            showPcAppLibrary();
        });
        webBack.setOnClickListener(v -> { if (activeWeb.canGoBack()) activeWeb.goBack(); });
        reload.setOnClickListener(v -> activeWeb.reload());
        external.setOnClickListener(v -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(appUrl))); }
            catch (Exception e) {
                new AlertDialog.Builder(this).setMessage("Không tìm thấy trình duyệt để mở liên kết.")
                    .setPositiveButton("OK", null).show();
            }
        });
        setContentView(page);
        if (!appUrl.equals(pcWebCurrentUrl) || activeWeb.getUrl() == null) {
            pcWebCurrentUrl = appUrl;
            activeWeb.loadUrl(appUrl);
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
        openPcApp("Discord PC", "https://discord.com/app");
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
            StringBuilder historyText = new StringBuilder("$ history\n");
            for (int i = 0; i < history.size(); i++) {
                historyText.append(i + 1).append("  ").append(history.get(i)).append('\n');
            }
            output.append(historyText);
            trimOutput();
            showPrompt();
            return;
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
                    if (result.length() < 12000) result.append(line).append("\n");
                }
                final String text = result.toString();
                final String path = newPath;
                runOnUiThread(() -> {
                    if (!text.isEmpty()) { output.append(text); trimOutput(); }
                    currentPath = path;
                    statusLabel.setText("  SHELL READY  •  EXIT CODE TRACKED");
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
        subtitle.setTextColor(Color.rgb(157, 188, 255)); subtitle.setTextSize(9);
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
        heading.setTextColor(Color.WHITE); heading.setTextSize(19);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView help = new TextView(this);
        help.setText("Kết nối VPS qua SSH bằng địa chỉ IP, cổng, tài khoản và mật khẩu hoặc SSH Key. Khi kết nối xong, lệnh sẽ chạy trên máy chủ.");
        help.setTextColor(Color.rgb(207, 222, 245)); help.setTextSize(12);
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
        int maxChars = 14000;
        if (output.length() > maxChars) {
            int remove = output.length() - maxChars;
            int newline = output.getText().toString().indexOf('\n', remove);
            output.getEditableText().delete(0, newline >= 0 ? newline + 1 : remove);
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
}
