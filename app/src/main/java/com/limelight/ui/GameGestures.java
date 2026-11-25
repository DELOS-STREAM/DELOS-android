package com.limelight.ui;

public interface GameGestures {
    void toggleKeyboard();

    default void showGameMenu(){};

    /** Delos: whether the stream menu is open (a controller DELOS drives over USB then drives the menu, not the game). */
    default boolean isStreamMenuOpen() { return false; }

    /** Delos: a key of a controller DELOS drives over USB, for the open stream menu; true when the menu took it. */
    default boolean streamMenuKey(android.view.KeyEvent event) { return false; }
}
