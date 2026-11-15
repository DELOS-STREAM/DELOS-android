package com.limelight.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Reader of {@code .art} shortcut files: lines of {@code [key] value}, {@code #} comments. The file
 * comes from outside the app, so it is read whole or not at all and never beyond {@link #MAX_BYTES}.
 */
public final class ArtFile {
    /** A shortcut file holds five short values; anything larger is not one. */
    public static final int MAX_BYTES = 64 * 1024;

    private ArtFile() {}

    /**
     * @return the keys and values of the file
     * @throws IOException when there is no stream, it cannot be read, it is too large or a line is malformed
     */
    public static Map<String, String> parse(InputStream in) throws IOException {
        if (in == null) {
            throw new IOException("No .art file content");
        }

        byte[] data = new byte[MAX_BYTES + 1];
        int length = 0;
        int read;
        while (length < data.length && (read = in.read(data, length, data.length - length)) != -1) {
            length += read;
        }
        if (length > MAX_BYTES) {
            throw new IOException(".art file too large");
        }

        Map<String, String> artData = new HashMap<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                new java.io.ByteArrayInputStream(data, 0, length), StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.startsWith("#") || line.isEmpty()) {
                continue; // Skip comments and empty lines
            }

            if (!line.startsWith("[")) {
                throw new IOException("Invalid .art file format");
            }

            int separatorIndex = line.indexOf(' ');
            if (separatorIndex > 0 && separatorIndex < line.length() - 1) {
                String key = line.substring(0, separatorIndex).trim();
                String value = line.substring(separatorIndex + 1).trim();
                if (key.endsWith("]")) {
                    key = key.substring(1, key.length() - 1);
                    artData.put(key, value);
                } else {
                    throw new IOException("Invalid .art file format");
                }
            }
        }
        return artData;
    }
}
