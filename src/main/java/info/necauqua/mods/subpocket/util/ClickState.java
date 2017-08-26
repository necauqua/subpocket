/*
 * Copyright 2017 Anton Bulakh <necauqua@gmail.com>
 * Licensed under MIT, see LICENSE file for details.
 */

package info.necauqua.mods.subpocket.util;

public class ClickState {

    private static final int BUTTON_BITS = 0b00000111;
    private static final int SHIFT_BIT   = 0b00001000;
    private static final int CTRL_BIT    = 0b00010000;
    private static final int ALT_BIT     = 0b00100000;

    private final int button;
    private final boolean shift;
    private final boolean ctrl;
    private final boolean alt;

    public ClickState(int button, boolean shift, boolean ctrl, boolean alt) {
        this.button = button;
        this.shift = shift;
        this.ctrl = ctrl;
        this.alt = alt;
    }

    public ClickState(byte stored) {
        button = stored & BUTTON_BITS;
        shift = (stored & SHIFT_BIT) != 0;
        ctrl = (stored & CTRL_BIT ) != 0;
        alt = (stored & ALT_BIT  ) != 0;
    }

    public int getButton() {
        return button;
    }

    public boolean isShift() {
        return shift;
    }

    public boolean isCtrl() {
        return ctrl;
    }

    public boolean isAlt() {
        return alt;
    }

    public byte toByte() {
        return (byte) ((button & BUTTON_BITS) | (shift ? SHIFT_BIT : 0) | (ctrl ? CTRL_BIT : 0) | (alt ? ALT_BIT : 0));
    }
}
