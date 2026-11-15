package com.limelight.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

public class ArtFileTest {
    private static InputStream text(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void readsKeysValuesAndSkipsComments() throws IOException {
        Map<String, String> data = ArtFile.parse(text("# Delos shortcut\n\n[host_uuid] ABC-1\n[app_name] Half-Life 2\r\n[app_id] 42\n"));
        assertEquals("ABC-1", data.get("host_uuid"));
        assertEquals("Half-Life 2", data.get("app_name"));
        assertEquals("42", data.get("app_id"));
        assertEquals(3, data.size());
    }

    @Test
    public void aMissingStreamIsAnErrorNotACrash() {
        assertThrows(IOException.class, () -> ArtFile.parse(null));
    }

    @Test
    public void aMalformedLineRejectsTheWholeFile() {
        assertThrows(IOException.class, () -> ArtFile.parse(text("[host_uuid] ABC-1\nnot a key line\n")));
        assertThrows(IOException.class, () -> ArtFile.parse(text("[host_uuid ABC-1\n")));
    }

    @Test
    public void aStreamThatFailsHalfWayYieldsNothing() {
        InputStream failing = new InputStream() {
            private final InputStream head = text("[host_uuid] ABC-1\n[app_id] 4");
            @Override public int read() throws IOException {
                int c = head.read();
                if (c == -1) throw new IOException("connection lost");
                return c;
            }
        };
        assertThrows(IOException.class, () -> ArtFile.parse(failing));
    }

    @Test
    public void anOversizedFileIsRefused() throws IOException {
        byte[] exact = new byte[ArtFile.MAX_BYTES];
        Arrays.fill(exact, (byte) '#');
        assertTrue(ArtFile.parse(new ByteArrayInputStream(exact)).isEmpty());

        byte[] tooLarge = new byte[ArtFile.MAX_BYTES + 1];
        Arrays.fill(tooLarge, (byte) '#');
        assertThrows(IOException.class, () -> ArtFile.parse(new ByteArrayInputStream(tooLarge)));
    }
}
