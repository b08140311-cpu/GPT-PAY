package com.motd.payhub;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.content.ComponentName;
import android.content.ActivityNotFoundException;
import android.net.Uri;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.webkit.JavascriptInterface;
import android.widget.Toast;
import android.content.SharedPreferences;

public class MainActivity extends Activity {
    private static final int PICK_APP_REQUEST = 3401;
    private WebView web;
    private int currentPickerCard = -1;
    private boolean pickerIsEditing = false;
    private SharedPreferences prefs;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(android.graphics.Color.BLACK);
        getWindow().setNavigationBarColor(android.graphics.Color.BLACK);
        prefs = getSharedPreferences("card_launch_targets", MODE_PRIVATE);
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
                try { startActivity(new Intent(Intent.ACTION_VIEW, request.getUrl())); }
                catch(Exception ignored) {}
                return true;
            }
        });
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }

    private String key(int card) { return "card_" + card; }
    private boolean valid(int card) { return card >= 0 && card < 3; }

    private void chooseInstalledApp(int card, boolean editing) {
        if(!valid(card)) return;
        currentPickerCard = card;
        pickerIsEditing = editing;
        Intent launcher = new Intent(Intent.ACTION_MAIN);
        launcher.addCategory(Intent.CATEGORY_LAUNCHER);
        Intent pick = new Intent(Intent.ACTION_PICK_ACTIVITY);
        pick.putExtra(Intent.EXTRA_INTENT, launcher);
        pick.putExtra(Intent.EXTRA_TITLE, "選擇這張卡要開啟的 App");
        try { startActivityForResult(pick, PICK_APP_REQUEST); }
        catch (Exception e) {
            Toast.makeText(this, "手機無法開啟系統 App 選擇器", Toast.LENGTH_LONG).show();
        }
    }

    private void launchCard(int card) {
        if(!valid(card)) return;
        String flattened = prefs.getString(key(card), null);
        if(flattened == null) { chooseInstalledApp(card, false); return; }
        ComponentName component = ComponentName.unflattenFromString(flattened);
        if(component == null) { prefs.edit().remove(key(card)).apply(); chooseInstalledApp(card,false); return; }
        Intent launch = new Intent(Intent.ACTION_MAIN);
        launch.addCategory(Intent.CATEGORY_LAUNCHER);
        launch.setComponent(component);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try { startActivity(launch); }
        catch(Exception e) {
            prefs.edit().remove(key(card)).apply();
            Toast.makeText(this, "找不到已設定的 App，請重新選擇", Toast.LENGTH_SHORT).show();
            chooseInstalledApp(card, false);
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode != PICK_APP_REQUEST) return;
        final int card = currentPickerCard;
        final boolean editing = pickerIsEditing;
        currentPickerCard = -1;
        if(resultCode != RESULT_OK || data == null || !valid(card)) return;
        ComponentName chosen = data.getComponent();
        if(chosen == null) {
            android.os.Parcelable extra = data.getParcelableExtra(Intent.EXTRA_INTENT);
            if(extra instanceof Intent) chosen = ((Intent)extra).getComponent();
        }
        if(chosen == null) {
            Toast.makeText(this, "未取得 App，請重試", Toast.LENGTH_SHORT).show();
            return;
        }
        prefs.edit().putString(key(card), chosen.flattenToString()).apply();
        String label = chosen.getPackageName();
        try {
            android.content.pm.ActivityInfo info = getPackageManager().getActivityInfo(chosen,0);
            label = info.loadLabel(getPackageManager()).toString();
        } catch(Exception ignored){}
        final String safeLabel = org.json.JSONObject.quote(label);
        final String safePackage = org.json.JSONObject.quote(chosen.getPackageName());
        web.evaluateJavascript("window.payHubTargetSaved&&window.payHubTargetSaved("+card+","+safePackage+","+safeLabel+")",null);
        if(!editing) launchCard(card);
    }

    public class AppBridge {
        @JavascriptInterface public void pickApp(int card, boolean editing) {
            runOnUiThread(() -> chooseInstalledApp(card, editing));
        }
        @JavascriptInterface public void onCardSwipe(int card) {
            runOnUiThread(() -> launchCard(card));
        }
        @JavascriptInterface public void openCard(int card) {
            runOnUiThread(() -> launchCard(card));
        }
    }

    @Override protected void onDestroy() {
        if(web != null){ web.removeJavascriptInterface("PayHubAndroid"); web.destroy(); }
        super.onDestroy();
    }
}
