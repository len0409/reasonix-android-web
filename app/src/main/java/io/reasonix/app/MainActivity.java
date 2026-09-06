package io.reasonix.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;

/**
 * Reasonix Android App v2
 *
 * A faithful 1:1 wrapper for the Reasonix web UI. The user provides the server
 * address (the reasonix serve endpoint, e.g. http://192.168.3.43:8787) on first
 * launch; it is persisted and can be changed anytime from the menu. The app then
 * loads the original web page in a WebView — every web-UI feature is preserved.
 */
public class MainActivity extends Activity {

    private static final String PREFS = "reasonix_prefs";
    private static final String KEY_URL = "server_url";

    private WebView webView;
    private View progressView;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean loadFinished = false;
    private boolean loadFailed = false;
    private int retryCount = 0;

    // File-upload support
    private ValueCallback<Uri[]> filePathCallback;
    private static final int REQ_FILE_CHOOSER = 1001;

    private String serverUrl = "";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(Color.parseColor("#1a1a1e"));
        getWindow().setNavigationBarColor(Color.parseColor("#1a1a1e"));

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        serverUrl = prefs.getString(KEY_URL, "");

        // First launch (or cleared): show the address setup screen.
        if (serverUrl == null || serverUrl.trim().isEmpty()) {
            setContentView(buildSetupView());
            return;
        }
        serverUrl = normalizeUrl(serverUrl);

        buildWebView();
        webView.loadUrl(serverUrl);
    }

    // ── Server address setup screen ──────────────────────────────
    private View buildSetupView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.parseColor("#15151a"));
        root.setPadding(dp(28), dp(24), dp(28), dp(24));

        TextView title = new TextView(this);
        title.setText("Reasonix");
        title.setTextColor(Color.WHITE);
        title.setTextSize(26);
        title.setGravity(Gravity.CENTER);
        root.addView(title, lpWrap());

        TextView sub = new TextView(this);
        sub.setText("连接你的 Reasonix 服务器");
        sub.setTextColor(Color.parseColor("#aaaaaa"));
        sub.setTextSize(14);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(6), 0, dp(28));
        root.addView(sub, lpWrap());

        TextView hint = new TextView(this);
        hint.setText("输入 reasonix serve 的地址（局域网 IP 变了可随时改）：");
        hint.setTextColor(Color.parseColor("#888888"));
        hint.setTextSize(12);
        root.addView(hint, lpWrap());

        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("http://192.168.3.43:8787");
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.parseColor("#555555"));
        input.setTextSize(15);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        input.setBackgroundColor(Color.parseColor("#232329"));
        LinearLayout.LayoutParams inputLp = lpMatchWrap();
        inputLp.topMargin = dp(8);
        inputLp.bottomMargin = dp(16);
        root.addView(input, inputLp);

        Button connect = new Button(this);
        connect.setText("连接");
        connect.setTextColor(Color.WHITE);
        connect.setBackgroundColor(Color.parseColor("#e08c3a"));
        connect.setPadding(dp(20), dp(10), dp(20), dp(10));
        connect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String url = normalizeUrl(input.getText().toString().trim());
                if (url == null || url.isEmpty()) {
                    Toast.makeText(MainActivity.this, "请输入服务器地址", Toast.LENGTH_SHORT).show();
                    return;
                }
                serverUrl = url;
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putString(KEY_URL, url).apply();
                buildWebView();
                webView.loadUrl(url);
            }
        });
        LinearLayout.LayoutParams btnLp = lpMatchWrap();
        btnLp.topMargin = dp(4);
        root.addView(connect, btnLp);

        TextView tip = new TextView(this);
        tip.setText("提示：在运行 reasonix 的设备上执行 ip addr 查看地址\n"
                + "端口默认 8787，可用 --addr 0.0.0.0:8787 启动");
        tip.setTextColor(Color.parseColor("#666666"));
        tip.setTextSize(11);
        tip.setGravity(Gravity.CENTER);
        tip.setPadding(0, dp(20), 0, 0);
        root.addView(tip, lpWrap());

        return root;
    }

    // ── WebView setup ────────────────────────────────────────────
    private void buildWebView() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#1a1a1e"));

        webView = new WebView(this);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            s.setSafeBrowsingEnabled(false);
        }
        // Desktop UA so the full reasonix desktop UI is rendered 1:1.
        s.setUserAgentString(s.getUserAgentString().replace(
                "Android", "X11; Linux") + " ReasonixApp/2.0");

        webView.setBackgroundColor(Color.parseColor("#1a1a1e"));
        webView.setWebViewClient(new ReasonixWebViewClient());
        webView.setWebChromeClient(new ReasonixChromeClient());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        CookieManager.getInstance().setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        }

        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        progressView = new View(this);
        progressView.setBackgroundColor(Color.parseColor("#1a1a1e"));
        root.addView(progressView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        // Floating "change address" button
        Button changeBtn = new Button(this);
        changeBtn.setText("改地址");
        changeBtn.setTextSize(11);
        changeBtn.setTextColor(Color.WHITE);
        changeBtn.setBackgroundColor(Color.parseColor("#cc000000"));
        changeBtn.setPadding(dp(10), dp(4), dp(10), dp(4));
        changeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showChangeAddressDialog();
            }
        });
        FrameLayout.LayoutParams btnLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        btnLp.gravity = Gravity.TOP | Gravity.END;
        btnLp.topMargin = dp(10);
        btnLp.rightMargin = dp(10);
        root.addView(changeBtn, btnLp);

        setContentView(root);
    }

    private void showChangeAddressDialog() {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(serverUrl);
        input.setHint("http://192.168.3.43:8787");
        int pad = dp(12);
        input.setPadding(pad, pad, pad, pad);
        new AlertDialog.Builder(this)
                .setTitle("更改服务器地址")
                .setMessage("当前：" + serverUrl)
                .setView(input)
                .setPositiveButton("连接", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        String url = normalizeUrl(input.getText().toString().trim());
                        if (url.isEmpty()) {
                            Toast.makeText(MainActivity.this, "地址无效", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        serverUrl = url;
                        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                                .putString(KEY_URL, url).apply();
                        if (webView != null) {
                            webView.loadUrl(url);
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ── Helpers ──────────────────────────────────────────────────
    private static String normalizeUrl(String raw) {
        if (raw == null) return "";
        String u = raw.trim();
        if (u.isEmpty()) return "";
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            u = "http://" + u;
        }
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private LinearLayout.LayoutParams lpWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams lpMatchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }

    @Override
    protected void onPause() {
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) webView.destroy();
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_FILE_CHOOSER) {
            if (filePathCallback == null) {
                super.onActivityResult(requestCode, resultCode, data);
                return;
            }
            Uri[] results = null;
            if (resultCode == Activity.RESULT_OK) {
                if (data != null) {
                    String dataString = data.getDataString();
                    if (dataString != null) {
                        results = new Uri[]{Uri.parse(dataString)};
                    } else if (data.getClipData() != null) {
                        int n = data.getClipData().getItemCount();
                        results = new Uri[n];
                        for (int i = 0; i < n; i++) {
                            results[i] = data.getClipData().getItemAt(i).getUri();
                        }
                    }
                }
            }
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private class ReasonixWebViewClient extends WebViewClient {
        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            loadFinished = false;
            loadFailed = false;
            retryCount = 0;
            if (progressView != null) progressView.setVisibility(View.VISIBLE);
            handler.removeCallbacks(hideProgressRunnable);
            handler.postDelayed(hideProgressRunnable, 10000);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            loadFinished = true;
            hideProgress();
        }

        private final Runnable hideProgressRunnable = new Runnable() {
            @Override
            public void run() {
                hideProgress();
            }
        };

        private void hideProgress() {
            if (progressView != null) progressView.setVisibility(View.GONE);
            handler.removeCallbacks(hideProgressRunnable);
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request,
                                    WebResourceError error) {
            hideProgress();
            if (request != null && request.isForMainFrame()) {
                loadFailed = true;
                if (retryCount < 3) {
                    retryCount++;
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (webView != null && !loadFinished) {
                                webView.loadUrl(serverUrl);
                            }
                        }
                    }, 1500);
                } else {
                    showServerUnreachable();
                }
            }
        }

        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest request,
                                        WebResourceResponse errorResponse) {
            if (request != null && request.isForMainFrame()) {
                loadFailed = true;
                hideProgress();
            }
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            handler.proceed();
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri url = request.getUrl();
            if (url == null) return false;
            String host = url.getHost();
            if (host != null && serverUrl.contains(host)) {
                return false;
            }
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, url);
                startActivity(i);
            } catch (Exception ignored) {
            }
            return true;
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            String host = request != null && request.getUrl() != null
                    ? request.getUrl().getHost() : null;
            if (host != null && (host.equals("fonts.googleapis.com") || host.equals("fonts.gstatic.com"))) {
                return new WebResourceResponse("text/plain", "utf-8",
                        new ByteArrayInputStream(new byte[0]));
            }
            return super.shouldInterceptRequest(view, request);
        }
    }

    private class ReasonixChromeClient extends WebChromeClient {
        @Override
        public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
            return true;
        }

        @Override
        public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                                         FileChooserParams fileChooserParams) {
            MainActivity.this.filePathCallback = filePathCallback;
            Intent intent = fileChooserParams.createIntent();
            try {
                startActivityForResult(intent, REQ_FILE_CHOOSER);
            } catch (Exception e) {
                MainActivity.this.filePathCallback = null;
                return false;
            }
            return true;
        }
    }

    private void showServerUnreachable() {
        final String base = serverUrl;
        String html = "<!doctype html><html><head><meta charset='utf-8'><meta name='viewport' "
                + "content='width=device-width,initial-scale=1'><title>Reasonix</title>"
                + "<script>function poll(){fetch('/status',{cache:'no-store'}).then(r=>{"
                + "if(r.ok){location.href='" + base + "/';}}).catch(()=>{}).finally(()=>setTimeout(poll,2000));}"
                + "setTimeout(poll,2000);</script></head>"
                + "<body style='margin:0;padding:40px 24px;background:#15151a;color:#f0f0f0;"
                + "font-family:-apple-system,sans-serif;text-align:center'>"
                + "<h2 style='margin-bottom:12px'>无法连接服务器</h2>"
                + "<p style='color:#aaa;line-height:1.7'>当前地址：<code style='background:#222;"
                + "padding:4px 10px;border-radius:6px'>" + base + "</code></p>"
                + "<p style='color:#777;font-size:13px;margin-top:16px'>检查 reasonix 服务是否启动、"
                + "IP 是否变化。连接恢复后本页自动跳转。</p>"
                + "<button onclick='location.reload()' style='margin-top:16px;padding:10px 28px;"
                + "border:none;border-radius:8px;background:#e08c3a;color:#fff;font-size:15px'>重试</button>"
                + "</body></html>";
        if (webView != null) {
            webView.loadDataWithBaseURL(base + "/", html, "text/html", "utf-8", null);
        }
    }
}
