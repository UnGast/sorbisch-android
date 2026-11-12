package de.adrianzimmermann.sorbianonlinespeech;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class InformationActivity extends LocalizedActivity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_information);
        findViewById(R.id.information_back).setOnClickListener(v -> finish());
        String page = getIntent().getStringExtra("page");
        boolean licenses = "licenses".equals(page);
        boolean imprint = "imprint".equals(page);
        int title = licenses ? R.string.licenses_title : imprint ? R.string.imprint_title : R.string.privacy_title;
        setTitle(title);
        ((TextView) findViewById(R.id.information_title)).setText(title);
        TextView body = findViewById(R.id.information_body);
        try {
            if (licenses) {
                StringBuilder html = new StringBuilder(readAsset("information/licenses.html"));
                html.append("<details><summary>Diktuj · MIT</summary><pre>")
                        .append(TextUtils.htmlEncode(readAsset("licenses/LICENSE"))).append("</pre></details>")
                        .append(readAsset("information/license-components.html"));
                String[] components = getAssets().list("licenses/third_party");
                if (components != null) {
                    Arrays.sort(components);
                    for (String component : components) {
                        html.append("<h2 id=\"").append(component).append("\">")
                                .append(TextUtils.htmlEncode(component)).append("</h2>");
                        String[] files = getAssets().list("licenses/third_party/" + component);
                        if (files == null) continue;
                        Arrays.sort(files);
                        for (String file : files) {
                            html.append("<h3>").append(TextUtils.htmlEncode(file)).append("</h3><pre>")
                                    .append(TextUtils.htmlEncode(
                                            readAsset("licenses/third_party/" + component + "/" + file)))
                                    .append("</pre>");
                        }
                        html.append("<p><a href=\"#components\">↑</a></p>");
                    }
                }
                WebView web = findViewById(R.id.license_webview);
                web.getSettings().setAllowFileAccess(false);
                web.getSettings().setAllowContentAccess(false);
                web.getSettings().setBlockNetworkLoads(true);
                web.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                        Uri uri = request.getUrl();
                        if ("diktuj.invalid".equals(uri.getHost()) && "/licenses/".equals(uri.getPath())) {
                            return false; // anchor inside the license page
                        }
                        if ("https".equals(uri.getScheme())) {
                            try {
                                startActivity(new Intent(Intent.ACTION_VIEW, uri));
                            } catch (ActivityNotFoundException ignored) {
                            }
                        }
                        return true;
                    }
                });
                web.loadDataWithBaseURL("https://diktuj.invalid/licenses/", html.append("</body></html>").toString(),
                        "text/html", "UTF-8", null);
                findViewById(R.id.information_scroll).setVisibility(View.GONE);
                web.setVisibility(View.VISIBLE);
            } else {
                String html = readAsset("information/de/" + (imprint ? "imprint" : "privacy") + ".html")
                        .replace("{{recognizer_url}}", TextUtils.htmlEncode(SpeechSettings.serverUrl(this)))
                        .replace("{{tts_url}}", TextUtils.htmlEncode(SpeechSettings.ttsUrl(this)));
                body.setText(Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY));
                body.setMovementMethod(LinkMovementMethod.getInstance());
            }
        } catch (IOException error) {
            body.setText(R.string.information_unavailable);
        }
    }

    @Override
    protected void onDestroy() {
        ((WebView) findViewById(R.id.license_webview)).destroy();
        super.onDestroy();
    }

    private String readAsset(String name) throws IOException {
        try (InputStream input = getAssets().open(name);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
