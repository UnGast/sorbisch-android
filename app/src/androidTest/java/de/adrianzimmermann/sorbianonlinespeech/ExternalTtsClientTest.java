package de.adrianzimmermann.sorbianonlinespeech;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;

public class ExternalTtsClientTest {
    @Test
    public void bamborakRequestUsesSpeakerInsteadOfLanguage() throws Exception {
        Request request = ExternalTtsClient.request("https://example.org/api/tts/",
                "  Dobry dźeń  ", "hsb", "bamborak", " voice/subspeaker ");
        JSONObject payload = new JSONObject(body(request));

        assertEquals("Dobry dźeń", payload.getString("text"));
        assertEquals("voice/subspeaker", payload.getString("speaker_id"));
        assertEquals("wav", payload.getString("format"));
        assertEquals(3, payload.length());
    }

    @Test(expected = IllegalArgumentException.class)
    public void bamborakRejectsMissingSpeaker() {
        ExternalTtsClient.request("https://example.org/api/tts/", "Text", "hsb", "bamborak", " ");
    }

    @Test
    public void bamborakJsonErrorsAreNotTreatedAsAudio() {
        byte[] error = "{\"errmsg\":\"invalid speaker_id\"}".getBytes(StandardCharsets.UTF_8);
        for (int status : new int[]{200, 400}) {
            try (Response response = response(status, "application/json", error)) {
                ExternalTtsClient.readAudio(response);
                fail("Expected a server error");
            } catch (IOException expected) {
                assertEquals("invalid speaker_id", expected.getMessage());
            }
        }
    }

    @Test
    public void bamborakWavIsReturnedUnchanged() throws Exception {
        byte[] wav = TestUtil.silentWav();
        try (Response response = response(200, "audio/wav", wav)) {
            byte[] audio = ExternalTtsClient.readAudio(response);
            assertArrayEquals(wav, audio);
            assertEquals(48000, PcmWav.parse(audio).sampleRate);
        }
    }

    @Test
    public void simpleRequestEscapesTextAndNormalizesLanguage() throws Exception {
        String text = "  Dobry dźeń: \"quoted\" \\ path\n\t\b\f" + (char) 1 + " end  ";
        Request request = ExternalTtsClient.request(" https://example.org/tts ", text, "hsb-DE", "simple", "");
        String encoded = body(request);
        JSONObject payload = new JSONObject(encoded);

        assertEquals("POST", request.method());
        assertEquals("https://example.org/tts", request.url().toString());
        assertEquals(text.trim(), payload.getString("text"));
        assertEquals("hsb", payload.getString("language"));
        assertFalse(encoded.contains(String.valueOf((char) 1)));
        assertFalse(encoded.contains("\b"));
        assertFalse(encoded.contains("\f"));
    }

    private static String body(Request request) throws IOException {
        Buffer body = new Buffer();
        request.body().writeTo(body);
        return body.readUtf8();
    }

    private static Response response(int status, String type, byte[] data) {
        return new Response.Builder()
                .request(new Request.Builder().url("http://localhost/tts").build())
                .protocol(Protocol.HTTP_1_1).code(status).message("Test")
                .header("Content-Type", type)
                .body(ResponseBody.create(data, MediaType.get(type))).build();
    }
}
