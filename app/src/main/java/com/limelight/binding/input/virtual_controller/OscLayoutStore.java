package com.limelight.binding.input.virtual_controller;

import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The stored on-screen control layouts of a profile (the preferences {@link VirtualControllerConfigurationLoader#OSC_PREFERENCE} of
 * {@code ProfileLayouts}): one JSON document per layout, under {@code layout.<id>}, holding only the controls the user changed, as
 * {@link OscPlacement}s. The default layout is never stored. {@link #GLOBAL} is the layout of every game; a layout of one game gets
 * an id of its own.
 *
 * <pre>
 * {"version": 2, "id": "global", "elements": {"10": {"enabled": true, "anchorX": "CENTER", "anchorY": "BOTTOM",
 *                                                    "x": -14.0, "y": 1.0, "width": 12.0, "height": 7.0}}}
 * </pre>
 *
 * <p>Version 1 stored every element in absolute pixels under its element id ({@code LEFT}, {@code TOP}, {@code WIDTH}, {@code HEIGHT},
 * {@code ENABLED}, and since phase D the screen they were pixels of, {@code REF_W} and {@code REF_H}); {@link #migrate} converts it
 * once.
 */
public final class OscLayoutStore {
    public static final int VERSION = 2;
    public static final String GLOBAL = "global";
    private static final String KEY_PREFIX = "layout.";

    private OscLayoutStore() {
    }

    static String key(String layoutId) {
        return KEY_PREFIX + layoutId;
    }

    /** The changed controls of layout {@code layoutId}, by element id; empty when there are none (or the document is unreadable). */
    public static Map<Integer, OscPlacement> read(SharedPreferences pref, String layoutId) {
        Map<Integer, OscPlacement> placements = new LinkedHashMap<>();
        String json = pref.getString(key(layoutId), null);
        if (json == null) {
            return placements;
        }
        try {
            JSONObject document = new JSONObject(json);
            if (document.optInt("version") != VERSION) {
                return placements;
            }
            JSONObject elements = document.getJSONObject("elements");
            for (Iterator<String> ids = elements.keys(); ids.hasNext(); ) {
                String id = ids.next();
                JSONObject e = elements.getJSONObject(id);
                placements.put(Integer.parseInt(id), new OscPlacement(
                        OscPlacement.AnchorX.valueOf(e.getString("anchorX")), OscPlacement.AnchorY.valueOf(e.getString("anchorY")),
                        (float) e.getDouble("x"), (float) e.getDouble("y"), (float) e.getDouble("width"), (float) e.getDouble("height"),
                        e.getBoolean("enabled")));
            }
        } catch (JSONException | IllegalArgumentException e) {
            // Unreadable: the default layout
            placements.clear();
        }
        return placements;
    }

    /** Stores {@code placements} as layout {@code layoutId}; none removes the layout. */
    public static void write(SharedPreferences.Editor editor, String layoutId, Map<Integer, OscPlacement> placements) {
        if (placements.isEmpty()) {
            editor.remove(key(layoutId));
            return;
        }
        try {
            JSONObject elements = new JSONObject();
            for (Map.Entry<Integer, OscPlacement> entry : placements.entrySet()) {
                OscPlacement p = entry.getValue();
                elements.put(String.valueOf(entry.getKey()), new JSONObject()
                        .put("enabled", p.enabled)
                        .put("anchorX", p.anchorX.name())
                        .put("anchorY", p.anchorY.name())
                        .put("x", p.x)
                        .put("y", p.y)
                        .put("width", p.width)
                        .put("height", p.height));
            }
            editor.putString(key(layoutId), new JSONObject()
                    .put("version", VERSION)
                    .put("id", layoutId)
                    .put("elements", elements)
                    .toString());
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Converts a version 1 layout (absolute pixels under element ids) into the global layout, once, and removes it. An element with
     * its screen is taken against that screen, and dropped when it is that screen's default. Elements without it come from
     * versions before phase D: those that are the old default of one panel ({@link OscDefaultLayout#oldDefaults}) are dropped, the
     * others are taken against the screen ({@code screenWidth} x {@code screenHeight}, the display metrics those versions laid the
     * controls out on). A control already in the global layout keeps its placement there.
     */
    public static void migrate(SharedPreferences pref, int screenWidth, int screenHeight) {
        Map<Integer, OscPlacement> converted = new LinkedHashMap<>();
        Map<Integer, int[]> unsized = new LinkedHashMap<>();
        Map<Integer, Boolean> unsizedEnabled = new LinkedHashMap<>();
        SharedPreferences.Editor editor = pref.edit();
        boolean found = false;
        for (Map.Entry<String, ?> entry : pref.getAll().entrySet()) {
            if (!entry.getKey().matches("\\d+")) {
                continue;
            }
            found = true;
            editor.remove(entry.getKey());
            try {
                int id = Integer.parseInt(entry.getKey());
                JSONObject c = new JSONObject((String) entry.getValue());
                int[] rect = { c.getInt("LEFT"), c.getInt("TOP"), c.getInt("WIDTH"), c.getInt("HEIGHT") };
                boolean enabled = c.getBoolean("ENABLED");
                int refWidth = c.optInt("REF_W", 0);
                int refHeight = c.optInt("REF_H", 0);
                if (refWidth > 0 && refHeight > 0) {
                    if (!enabled || !OscDefaultLayout.isDefault(id, rect[0], rect[1], rect[2], rect[3], refWidth, refHeight)) {
                        converted.put(id, OscPlacement.fromRect(id, rect, enabled, new OscGeometry.Panel(refWidth, refHeight)));
                    }
                } else {
                    unsized.put(id, rect);
                    unsizedEnabled.put(id, enabled);
                }
            } catch (JSONException | ClassCastException | NumberFormatException e) {
                // A broken entry is dropped
            }
        }
        if (!found) {
            return;
        }
        Map<Integer, int[]> unsizedOn = new LinkedHashMap<>();
        for (Map.Entry<Integer, int[]> e : unsized.entrySet()) {
            if (unsizedEnabled.get(e.getKey())) {
                unsizedOn.put(e.getKey(), e.getValue());
            }
        }
        Set<Integer> oldDefaults = OscDefaultLayout.oldDefaults(unsizedOn);
        if (screenWidth > 0 && screenHeight > 0) {
            OscGeometry.Panel screen = new OscGeometry.Panel(screenWidth, screenHeight);
            for (Map.Entry<Integer, int[]> e : unsized.entrySet()) {
                if (!oldDefaults.contains(e.getKey())) {
                    converted.put(e.getKey(), OscPlacement.fromRect(e.getKey(), e.getValue(), unsizedEnabled.get(e.getKey()), screen));
                }
            }
        }
        converted.putAll(read(pref, GLOBAL));
        write(editor, GLOBAL, converted);
        editor.apply();
    }
}
