package com.lenspreset.app;

import android.view.KeyEvent;

import com.sony.scalar.sysutil.ScalarInput;

/**
 * Camera keys to app actions. Every function sits on keys every body has (four-way, centre, MENU,
 * trash, a wheel or dial); bodies number their wheels differently, so every rotary code is a turn.
 * Standard Android key codes map too, for running the app in an emulator; Page Up / Page Down
 * stand in for a counter-clockwise / clockwise wheel turn, Media Play/Pause for the Playback button.
 */
final class Keys {
    private Keys() {
    }

    static final int NONE = 0;
    static final int UP = 1;
    static final int DOWN = 2;
    static final int LEFT = 3;
    static final int RIGHT = 4;
    static final int ENTER = 5;
    static final int MENU = 6;
    static final int TRASH = 7;
    /** A wheel or dial turned clockwise / counter-clockwise. */
    static final int NEXT = 8;
    static final int PREV = 9;
    /** The Playback button: opens and closes the playback screen. */
    static final int PLAY = 10;

    static int action(KeyEvent event) {
        switch (event.getScanCode()) {
            case ScalarInput.ISV_KEY_UP: return UP;
            case ScalarInput.ISV_KEY_DOWN: return DOWN;
            case ScalarInput.ISV_KEY_LEFT: return LEFT;
            case ScalarInput.ISV_KEY_RIGHT: return RIGHT;
            case ScalarInput.ISV_KEY_ENTER: return ENTER;
            case ScalarInput.ISV_KEY_MENU:
            case ScalarInput.ISV_KEY_SK1: return MENU;
            case ScalarInput.ISV_KEY_DELETE:
            case ScalarInput.ISV_KEY_SK2: return TRASH;
            case ScalarInput.ISV_KEY_PLAY: return PLAY;
            case ScalarInput.ISV_DIAL_KURU_CLOCKWISE:
            case ScalarInput.ISV_DIAL_1_CLOCKWISE:
            case ScalarInput.ISV_DIAL_2_CLOCKWISE:
            case ScalarInput.ISV_DIAL_3_CLOCKWISE: return NEXT;
            case ScalarInput.ISV_DIAL_KURU_COUNTERCW:
            case ScalarInput.ISV_DIAL_1_COUNTERCW:
            case ScalarInput.ISV_DIAL_2_COUNTERCW:
            case ScalarInput.ISV_DIAL_3_COUNTERCW: return PREV;
            default: break;
        }
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_UP: return UP;
            case KeyEvent.KEYCODE_DPAD_DOWN: return DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT: return LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return RIGHT;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER: return ENTER;
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_BACK: return MENU;
            case KeyEvent.KEYCODE_DEL: return TRASH;
            case KeyEvent.KEYCODE_PAGE_UP: return PREV;
            case KeyEvent.KEYCODE_PAGE_DOWN: return NEXT;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE: return PLAY;
            default: return NONE;
        }
    }
}
