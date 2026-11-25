package al.titrashqip.tv;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.webkit.*;
import android.widget.*;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import java.io.*;
import java.net.*;
import android.net.Uri;
import java.util.Collections;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private WebView web;
    private EditText urlBar;
    private FrameLayout root;
    private View fullscreenView;
    private String script;
    private SharedPreferences prefs;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("ts", MODE_PRIVATE);
        script = readAsset("titra.js");

        root = new FrameLayout(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        urlBar = new EditText(this);
        urlBar.setSingleLine(true);
        urlBar.setHint("Shkruaj adresën e faqes së filmit (p.sh. youtube.com)");
        urlBar.setTextColor(Color.WHITE);
        urlBar.setHintTextColor(Color.GRAY);
        urlBar.setBackgroundColor(0xFF222222);
        urlBar.setImeOptions(EditorInfo.IME_ACTION_GO);
        urlBar.setOnEditorActionListener((v, id, e) -> { go(urlBar.getText().toString()); return true; });
        col.addView(urlBar, new LinearLayout.LayoutParams(-1, -2));

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUserAgentString(s.getUserAgentString().replace("; wv", "").replace("Mobile ", ""));
        web.addJavascriptInterface(new Bridge(), "TitraShqip");

        boolean allFrames = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT);
        if (allFrames) WebViewCompat.addDocumentStartJavaScript(web, script, Collections.singleton("*"));

        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView v, String url) {
                urlBar.setText(url);
                prefs.edit().putString("last", url).apply();
                if (!allFrames) v.evaluateJavascript(script, null);
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onShowCustomView(View view, CustomViewCallback cb) {
                fullscreenView = view;
                root.addView(view, new FrameLayout.LayoutParams(-1, -1));
            }
            @Override public void onHideCustomView() {
                if (fullscreenView != null) root.removeView(fullscreenView);
                fullscreenView = null;
            }
        });
        col.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(col);
        setContentView(root);

        go(prefs.getString("last", "https://www.youtube.com/tv"));
        web.requestFocus();
    }

    private void go(String u) {
        u = u.trim();
        if (u.isEmpty()) return;
        if (!u.startsWith("http")) u = u.contains(".") && !u.contains(" ")
            ? "https://" + u : "https://www.google.com/search?q=" + Uri.encode(u);
        web.loadUrl(u);
        web.requestFocus();
    }

    @Override public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_MENU) { urlBar.requestFocus(); urlBar.selectAll(); return true; }
        return super.onKeyDown(code, e);
    }

    @Override public void onBackPressed() {
        if (fullscreenView != null) { web.evaluateJavascript("document.exitFullscreen&&document.exitFullscreen()", null); return; }
        if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }

    private String readAsset(String name) {
        try (InputStream in = getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } catch (IOException e) { return ""; }
    }

    /** Thirret nga JavaScript: përkthen tekstin dhe kthen rezultatin te faqja. */
    class Bridge {
        @JavascriptInterface public void translate(String text, int id) {
            new Thread(() -> {
                String result = text;
                try {
                    URL url = new URL("https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=sq&dt=t&q="
                        + URLEncoder.encode(text, "UTF-8"));
                    HttpURLConnection c = (HttpURLConnection) url.openConnection();
                    c.setConnectTimeout(5000); c.setReadTimeout(5000);
                    BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder(); String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                    JSONArray parts = new JSONArray(sb.toString()).getJSONArray(0);
                    StringBuilder t = new StringBuilder();
                    for (int i = 0; i < parts.length(); i++) t.append(parts.getJSONArray(i).getString(0));
                    result = t.toString();
                } catch (Exception ignored) {}
                String js = "window.__tsDone&&window.__tsDone(" + id + "," + JSONObject.quote(result) + ")";
                runOnUiThread(() -> web.evaluateJavascript(js, null));
            }).start();
        }
    }
}
