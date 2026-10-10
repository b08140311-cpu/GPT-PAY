package com.motd.payhub;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.widget.Toast;

public class MainActivity extends Activity {
    private WebView web;
    private static final String LINE_PAY = "com.linepaytw.upay";
    private static final String PX_PAY = "com.pxpayplus.app";
    private static final String PAYPAL = "com.paypal.android.p2pmobile";
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(android.graphics.Color.BLACK);
        getWindow().setNavigationBarColor(android.graphics.Color.BLACK);
        web = new WebView(this);
        web.setBackgroundColor(android.graphics.Color.BLACK);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        web.addJavascriptInterface(new AppBridge(), "PayHubAndroid");
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("file".equals(uri.getScheme()) && "android_asset".equals(uri.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (Exception ignored) {}
                return true;
            }
        });
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }
    public class AppBridge {
        @JavascriptInterface public void openApp(String packageName) {
            if (!LINE_PAY.equals(packageName) && !PX_PAY.equals(packageName) && !PAYPAL.equals(packageName)) return;
            runOnUiThread(() -> {
                Intent launch = getPackageManager().getLaunchIntentForPackage(packageName);
                if (launch != null) {
                    try { startActivity(launch); return; } catch (ActivityNotFoundException ignored) {}
                }
                Toast.makeText(MainActivity.this, "未安裝此 App，開啟 Google Play", Toast.LENGTH_SHORT).show();
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + packageName))); }
                catch (Exception e) { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + packageName))); }
            });
        }
    }
    @Override protected void onDestroy() {
        if (web != null) { web.removeJavascriptInterface("PayHubAndroid"); web.destroy(); }
        super.onDestroy();
    }
}