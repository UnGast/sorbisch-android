package de.adrianzimmermann.sorbianonlinespeech;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.content.Intent;
import android.webkit.WebView;
import android.widget.TextView;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class InformationActivityTest {
    @Test
    public void legalPagesAndLicenseAnchorsLoad() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        for (String page : new String[]{"privacy", "imprint", "licenses"}) {
            InformationActivity activity = TestUtil.launch(
                    new Intent(instrumentation.getTargetContext(), InformationActivity.class)
                            .putExtra("page", page));
            try {
                if (!"licenses".equals(page)) {
                    TextView body = activity.findViewById(R.id.information_body);
                    assertTrue(body.getText().toString().contains("Stiftung für das sorbische Volk"));
                    continue;
                }
                WebView web = activity.findViewById(R.id.license_webview);
                instrumentation.runOnMainSync(() -> {
                    assertFalse(web.getSettings().getJavaScriptEnabled());
                    assertTrue(web.getSettings().getBlockNetworkLoads());
                    // Scripting is off in the app; the test needs it to inspect the page.
                    web.getSettings().setJavaScriptEnabled(true);
                });
                String allAnchorsResolve = "document.readyState === 'complete' && "
                        + "document.querySelectorAll('tbody tr').length === 9 && "
                        + "Array.from(document.querySelectorAll('a[href^=\"#\"]')).every("
                        + "a => document.getElementById(a.getAttribute('href').substring(1)))";
                TestUtil.waitFor("the license table and its anchor targets", 10000,
                        () -> "true".equals(evaluate(web, allAnchorsResolve)));
            } finally {
                instrumentation.runOnMainSync(activity::finish);
            }
        }
    }

    private static String evaluate(WebView web, String script) throws InterruptedException {
        AtomicReference<String> result = new AtomicReference<>();
        CountDownLatch evaluated = new CountDownLatch(1);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                web.evaluateJavascript(script, value -> {
                    result.set(value);
                    evaluated.countDown();
                }));
        assertTrue(evaluated.await(2, TimeUnit.SECONDS));
        return result.get();
    }
}
