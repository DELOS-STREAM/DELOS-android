package com.limelight.binding.input.virtual_controller;

/**
 * Where an on-screen control the user moved, resized or turned off is kept, independent of the panel's resolution and shape. The
 * default layout is never stored ({@link OscDefaultLayout} computes it for every panel); a control the user changed is stored
 * against its anchor, the side the default layout keeps it to ({@link OscDefaultLayout#anchorX}, {@link OscDefaultLayout#anchorY}):
 * the left cluster from the left edge, the right cluster from the right edge, Back, Guide, Start and the trackpad from the
 * horizontal centre; the upper controls from the top edge, the lower ones from the bottom edge. Offsets and sizes are in layout
 * units of the panel ({@link OscGeometry.Panel#unit()}, the unit of the default layout), so a layout made on one panel looks the
 * same on any other, and the middle stays centred. Pure (pixels in, pixels out), checked by {@code OscPlacementTest}.
 */
public final class OscPlacement {
    public enum AnchorX { LEFT, CENTER, RIGHT }

    public enum AnchorY { TOP, BOTTOM }

    public final AnchorX anchorX;
    public final AnchorY anchorY;
    /**
     * From the anchor, in layout units: LEFT from the panel's left edge to the control's, CENTER from the panel's centre to the
     * control's (negative to the left), RIGHT from the control's right edge to the panel's.
     */
    public final float x;
    /** TOP from the panel's top edge to the control's, BOTTOM from the control's bottom edge to the panel's, in layout units. */
    public final float y;
    public final float width;
    public final float height;
    public final boolean enabled;

    public OscPlacement(AnchorX anchorX, AnchorY anchorY, float x, float y, float width, float height, boolean enabled) {
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.enabled = enabled;
    }

    /** Element {@code elementId} at {left, top, width, height} pixels on {@code panel}, against its anchor. */
    public static OscPlacement fromRect(int elementId, int[] rect, boolean enabled, OscGeometry.Panel panel) {
        AnchorX ax = OscDefaultLayout.anchorX(elementId);
        AnchorY ay = OscDefaultLayout.anchorY(elementId);
        float u = panel.unit();
        int left = rect[0];
        int top = rect[1];
        int width = rect[2];
        int height = rect[3];
        float x;
        switch (ax) {
            case LEFT:
                x = left / u;
                break;
            case CENTER:
                x = (left + width / 2f - panel.width / 2f) / u;
                break;
            default:
                x = (panel.width - left - width) / u;
                break;
        }
        float y = ay == AnchorY.TOP ? top / u : (panel.height - top - height) / u;
        return new OscPlacement(ax, ay, x, y, width / u, height / u, enabled);
    }

    /** The rectangle {left, top, width, height} in pixels on {@code panel}, moved onto it (and shrunk when larger than it). */
    public int[] toRect(OscGeometry.Panel panel) {
        float u = panel.unit();
        int w = Math.max(1, Math.round(width * u));
        int h = Math.max(1, Math.round(height * u));
        int left;
        switch (anchorX) {
            case LEFT:
                left = Math.round(x * u);
                break;
            case CENTER:
                left = Math.round(panel.width / 2f + x * u - w / 2f);
                break;
            default:
                left = panel.width - Math.round(x * u) - w;
                break;
        }
        int top = anchorY == AnchorY.TOP ? Math.round(y * u) : panel.height - Math.round(y * u) - h;
        return OscGeometry.clamp(left, top, w, h, panel.width, panel.height);
    }
}
