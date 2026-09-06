package io.reasonix.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

/**
 * Reasonix Android App
 *
 * Loads the Reasonix web UI served by the local `reasonix serve` daemon
 * (http://127.0.0.1:8787) inside a native WebView. All web-UI features are
 * preserved 1:1 since the app renders the original page.
 */
public class MainActivity extends Activity {

    private static final String HOME_URL = "http://127.0.0.1:8787/";
    private WebView webView;
    private View progressView;

    // File-upload support (Chat UI has no uploads, but keep for completeness)
    private ValueCallback<Uri[]> filePathCallback;
    private static final int REQ_FILE_CHOOSER = 1001;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Edge-to-edge dark background to match the web UI
        getWindow().setStatusBarColor(Color.parseColor("#1a1a1e"));
        getWindow().setNavigationBarColor(Color.parseColor("#1a1a1e"));

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
        // Let the page use large in-memory storage for sessions/history
        s.setAppCacheEnabled(true);

        webView.setBackgroundColor(Color.parseColor("#1a1a1e"));
        webView.setWebViewClient(new ReasonixWebViewClient());
        webView.setWebChromeClient(new ReasonixChromeClient());

        // Cookie persistence so /auth token survives restarts
        CookieManager.getInstance().setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        }

        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        // Simple loading spinner overlay
        progressView = new View(this);
        progressView.setBackgroundColor(Color.parseColor("#1a1a1e"));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        root.addView(progressView, lp);

        setContentView(root);

        webView.loadUrl(HOME_URL);
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
            if (progressView != null) progressView.setVisibility(View.VISIBLE);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            if (progressView != null) progressView.setVisibility(View.GONE);
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request,
                                    WebResourceResponse errorResponse) {
            // If the local reasonix server isn't reachable, show a helpful page.
            if (request != null && request.getUrl() != null
                    && HOME_URL.startsWith(request.getUrl().toString())
                    && errorResponse != null) {
                showServerUnreachable();
            }
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            // Local loopback traffic; proceed to keep the UI usable.
            handler.proceed();
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri url = request.getUrl();
            if (url == null) return false;
            String host = url.getHost();
            // Stay inside the local server; hand everything else to the system browser.
            if (host != null && (host.equals("127.0.0.1") || host.equals("localhost"))) {
                return false;
            }
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, url);
                startActivity(i);
            } catch (Exception ignored) {
            }
            return true;
        }
    }

    private class ReasonixChromeClient extends WebChromeClient {
        @Override
        public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
            return true; // suppress noisy console output
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
        String html = "<!doctype html><html><head><meta charset='utf-8'><meta name='viewport' "
                + "content='width=device-width,initial-scale=1'><title>Reasonix</title></head>"
                + "<body style='margin:0;padding:40px 24px;background:#15151a;color:#f0f0f0;"
                + "font-family:-apple-system,sans-serif;text-align:center'>"
                + "<h2 style='margin-bottom:12px'>Reasonix 服务未启动</h2>"
                + "<p style='color:#aaa;line-height:1.7'>请先在 Termux 中启动 reasonix 服务：<br>"
                + "<code style='background:#222;padding:8px 14px;border-radius:6px;display:inline-block;"
                + "margin-top:12px'>reasonix serve --addr 0.0.0.0:8787</code></p>"
                + "<p style='color:#777;font-size:13px;margin-top:24px'>启动后返回本页，下拉或点击重试。</p>"
                + "<button onclick='location.reload()' style='margin-top:12px;padding:10px 28px;"
                + "border:none;border-radius:8px;background:#e08c3a;color:#fff;font-size:15px'>重试</button>"
                + "</body></html>";
        if (webView != null) {
            webView.loadDataWithBaseURL("file:///android_asset/error.html",
                    html, "text/html", "utf-8", null);
        }
    }
}
