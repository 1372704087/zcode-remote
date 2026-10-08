package com.zremote.console;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;

/**
 * 扫码 -> 在 App 内打开 Z.ai Code 网页遥控控制台。
 * 二维码由电脑端 Z.ai Code 生成，扫码后即可在手机上给电脑上的 AI 发消息。
 * 界面全屏显示网页，扫码/保活/主题等设置收进右侧滑出的面板。
 */
public class MainActivity extends AppCompatActivity {

    private static final String PREFS = "zremote";
    private static final String KEY_URL = "last_url";
    private static final String KEY_KEEPALIVE = "keepalive";
    private static final String KEY_THEME = "theme";

    /** 面板滑出超过这个比例就判定为打开 */
    private static final float OPEN_THRESHOLD = 0.4f;
    private static final long ANIM_MS = 190L;

    private WebView webView;
    private View emptyView;
    private View scrim;
    private View panel;
    private EdgeDrawerLayout root;
    private TextView hintText;
    private TextView btnKeepAlive;
    private TextView btnThemeDark;
    private TextView btnThemeLight;
    private TextView tvCurrent;
    private EditText etUrl;

    private SharedPreferences prefs;
    private boolean keepAliveOn;
    private boolean serviceStarted;

    private boolean drawerOpen;
    private float panelWidthPx;

    /** 扫码结果回调：拿到二维码里的链接后直接加载 */
    private final ActivityResultLauncher<Intent> scanLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
                    toast("已取消扫码");
                    return;
                }
                String contents = result.getData().getStringExtra(ScanActivity.EXTRA_RESULT);
                if (contents == null) {
                    toast("没有读到二维码内容");
                    return;
                }
                String url = contents.trim();
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    toast("二维码内容不是有效的网址");
                    return;
                }
                prefs.edit().putString(KEY_URL, url).apply();
                openUrl(url);
            });

    /** Android 13+ 通知权限：保活通知需要它才能显示 */
    private final ActivityResultLauncher<String> notifPermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                startKeepAliveService();
                updateKeepAliveUi();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        root = findViewById(R.id.root);
        webView = findViewById(R.id.webview);
        emptyView = findViewById(R.id.empty_view);
        scrim = findViewById(R.id.scrim);
        panel = findViewById(R.id.panel);
        hintText = findViewById(R.id.hint_text);
        btnKeepAlive = findViewById(R.id.btn_keepalive);
        btnThemeDark = findViewById(R.id.btn_theme_dark);
        btnThemeLight = findViewById(R.id.btn_theme_light);
        tvCurrent = findViewById(R.id.tv_current);
        etUrl = findViewById(R.id.et_url);

        TextView btnScan = findViewById(R.id.btn_scan);
        TextView btnRefresh = findViewById(R.id.btn_refresh);
        TextView btnScanHint = findViewById(R.id.btn_scan_hint);
        TextView btnOpenUrl = findViewById(R.id.btn_open_url);
        TextView btnDisconnect = findViewById(R.id.btn_disconnect);
        View btnHandle = findViewById(R.id.btn_handle);
        View btnClosePanel = findViewById(R.id.btn_close_panel);

        setupWebView();
        setupDrawer();

        keepAliveOn = prefs.getBoolean(KEY_KEEPALIVE, true);
        updateKeepAliveUi();
        updateThemeUi();
        updateCurrentUrl();

        btnThemeDark.setOnClickListener(v -> setDarkTheme(true));
        btnThemeLight.setOnClickListener(v -> setDarkTheme(false));

        btnScan.setOnClickListener(v -> startScan());
        btnScanHint.setOnClickListener(v -> startScan());
        btnHandle.setOnClickListener(v -> openDrawer());
        btnClosePanel.setOnClickListener(v -> closeDrawer());
        scrim.setOnClickListener(v -> closeDrawer());

        btnRefresh.setOnClickListener(v -> {
            if (webView.getUrl() == null) {
                startScan();
            } else {
                webView.reload();
            }
        });

        btnOpenUrl.setOnClickListener(v -> {
            String url = etUrl.getText().toString().trim();
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                toast("请输入 http(s) 开头的链接");
                return;
            }
            etUrl.setText("");
            prefs.edit().putString(KEY_URL, url).apply();
            openUrl(url);
            closeDrawer();
        });

        btnDisconnect.setOnClickListener(v -> {
            prefs.edit().remove(KEY_URL).apply();
            webView.loadUrl("about:blank");
            showEmpty();
            closeDrawer();
        });

        btnKeepAlive.setOnClickListener(v -> {
            boolean target = !keepAliveOn;
            if (target) {
                toast("已开启保活：息屏后连接不易断，会略增耗电");
            }
            applyKeepAlive(target);
        });

        // 面板宽度固定：常驻可见但整体平移到屏幕外，之后再平移进来，避免首帧闪一下
        panelWidthPx = getResources().getDimension(R.dimen.panel_width);
        panel.setTranslationX(panelWidthPx);
        panel.setVisibility(View.VISIBLE);

        // 记住上一次的连接地址，重开 App 可直接重连
        String saved = prefs.getString(KEY_URL, null);
        if (saved != null) {
            openUrl(saved);
        } else {
            showEmpty();
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        webView.setBackgroundColor(ContextCompat.getColor(this, R.color.bg));

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        // 遥控控制台依赖 WebSocket 长连接，允许混合内容以提升兼容性
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webView, true);

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false; // 站内跳转都在 App 内完成
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (url != null && !"about:blank".equals(url)) {
                    hideEmpty();
                    // 连接成功后按开关自动保活
                    if (keepAliveOn && !serviceStarted) {
                        startKeepAliveService();
                    }
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    toast("加载失败：请检查网络，或重新扫码连接");
                }
            }
        });
    }

    // ---------------- 侧滑设置面板 ----------------

    private void setupDrawer() {
        root.setOnEdgeDragListener(new EdgeDrawerLayout.OnEdgeDragListener() {
            @Override
            public void onEdgeDragStart() {
                scrim.setVisibility(View.VISIBLE);
                scrim.setAlpha(1f - panel.getTranslationX() / panelWidthPx);
            }

            @Override
            public void onEdgeDragMove(float dx) {
                applyDrag(panelWidthPx + dx);
            }

            @Override
            public void onEdgeDragEnd(float dx) {
                if (panelWidthPx + dx < panelWidthPx * OPEN_THRESHOLD) {
                    openDrawer();
                } else {
                    closeDrawer();
                }
            }
        });
    }

    /** 拖动过程中按位移同步面板位置与遮罩透明度 */
    private void applyDrag(float offset) {
        float x = Math.max(0f, Math.min(panelWidthPx, offset));
        panel.setTranslationX(x);
        scrim.setAlpha(1f - x / panelWidthPx);
    }

    private void openDrawer() {
        if (drawerOpen) {
            return;
        }
        drawerOpen = true;
        root.setEdgeEnabled(false);
        updateCurrentUrl();
        scrim.setVisibility(View.VISIBLE);
        scrim.animate().alpha(1f).setDuration(ANIM_MS).start();
        panel.animate().translationX(0f).setDuration(ANIM_MS)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    /** 关闭面板；即使只是拖到一半又松手，也要把遮罩一起收干净 */
    private void closeDrawer() {
        drawerOpen = false;
        root.setEdgeEnabled(true);
        hideKeyboard();
        panel.animate().translationX(panelWidthPx).setDuration(ANIM_MS)
                .setInterpolator(new DecelerateInterpolator()).start();
        scrim.animate().alpha(0f).setDuration(ANIM_MS)
                .withEndAction(() -> scrim.setVisibility(View.GONE)).start();
    }

    private void hideKeyboard() {
        etUrl.clearFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        View focused = getCurrentFocus();
        if (imm != null && focused != null) {
            imm.hideSoftInputFromWindow(focused.getWindowToken(), 0);
        }
    }

    // ---------------- 连接 ----------------

    private void startScan() {
        scanLauncher.launch(new Intent(this, ScanActivity.class));
    }

    private void openUrl(String url) {
        hideEmpty();
        updateCurrentUrl(url);
        webView.loadUrl(url);
    }

    private void updateCurrentUrl() {
        updateCurrentUrl(prefs.getString(KEY_URL, null));
    }

    private void updateCurrentUrl(String url) {
        tvCurrent.setText(url == null ? getString(R.string.current_none)
                : getString(R.string.current_prefix) + url);
    }

    // ---------------- 保活 ----------------

    private void applyKeepAlive(boolean on) {
        keepAliveOn = on;
        prefs.edit().putBoolean(KEY_KEEPALIVE, on).apply();
        updateKeepAliveUi();

        if (!on) {
            stopKeepAliveService();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        } else {
            startKeepAliveService();
        }
    }

    private void startKeepAliveService() {
        startForegroundService(new Intent(this, KeepAliveService.class));
        serviceStarted = true;
    }

    private void stopKeepAliveService() {
        stopService(new Intent(this, KeepAliveService.class));
        serviceStarted = false;
    }

    private void updateKeepAliveUi() {
        btnKeepAlive.setText(keepAliveOn ? R.string.keepalive_on : R.string.keepalive_off);
        btnKeepAlive.setBackgroundResource(keepAliveOn
                ? R.drawable.ripple_pill_active : R.drawable.ripple_pill_ghost);
        btnKeepAlive.setTextColor(ContextCompat.getColor(this,
                keepAliveOn ? R.color.text_primary : R.color.text_secondary));
    }

    // ---------------- 主题（黑 / 白） ----------------

    private boolean isNightMode() {
        int mode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    private void setDarkTheme(boolean dark) {
        if (dark == isNightMode()) {
            return;
        }
        prefs.edit().putString(KEY_THEME, dark ? "dark" : "light").apply();
        // 切换会重建 Activity，WebView 会重新加载并重连中继
        AppCompatDelegate.setDefaultNightMode(
                dark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
    }

    private void updateThemeUi() {
        boolean dark = isNightMode();
        btnThemeDark.setBackgroundResource(dark
                ? R.drawable.ripple_segment_on : R.drawable.ripple_segment_off);
        btnThemeLight.setBackgroundResource(dark
                ? R.drawable.ripple_segment_off : R.drawable.ripple_segment_on);
        btnThemeDark.setTextColor(ContextCompat.getColor(this,
                dark ? R.color.text_primary : R.color.text_secondary));
        btnThemeLight.setTextColor(ContextCompat.getColor(this,
                dark ? R.color.text_secondary : R.color.text_primary));
    }

    // ---------------- UI ----------------

    private void showEmpty() {
        hintText.setText(R.string.hint_connect);
        emptyView.setVisibility(View.VISIBLE);
    }

    private void hideEmpty() {
        emptyView.setVisibility(View.GONE);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBackPressed() {
        if (drawerOpen) {
            closeDrawer();
        } else if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();

        // 用户可能在通知栏点了「断开保活」，回到前台时同步开关状态
        boolean saved = prefs.getBoolean(KEY_KEEPALIVE, true);
        if (saved != keepAliveOn) {
            keepAliveOn = saved;
            serviceStarted = saved;
            updateKeepAliveUi();
        }
    }

    @Override
    protected void onPause() {
        webView.onPause();
        super.onPause();
    }
}