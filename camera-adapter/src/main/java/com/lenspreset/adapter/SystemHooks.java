package com.lenspreset.adapter;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Rect;

import com.sony.scalar.hardware.avio.DisplayManager;
import com.sony.scalar.sysutil.didep.Gpelibrary;

/**
 * The parts of Sony's app platform an app is expected to talk to, as PMCADemo and BetterManual do.
 * All of them are best-effort: off a camera, or on firmware without them, they do nothing.
 */
public final class SystemHooks {
    private SystemHooks() {
    }

    /** 32-bit UI colour while the app is in front; the platform default (16-bit) when it leaves. */
    public static void setHighColor(boolean high) {
        try {
            Gpelibrary.changeFrameBufferPixel(high ? Gpelibrary.GS_FRAMEBUFFER_TYPE.ABGR8888
                    : Gpelibrary.GS_FRAMEBUFFER_TYPE.RGBA4444);
        } catch (Throwable ignored) {
            // Not a Sony body.
        }
    }

    /**
     * Tells the platform which activity is in front, and that plugging in USB closes it (so the card
     * is free for the computer, and so USB is always a way out).
     *
     * <p>No {@code resume_key}: BetterManual asks the platform to reopen the app after power-off, but
     * an app that fails at start-up then comes back after every power cycle and the camera is stuck
     * in it (it happened on an A5100). PMCADemo and Recipe Lab leave it out too.
     */
    public static void announce(Activity activity) {
        Intent intent = new Intent("com.android.server.DAConnectionManagerService.AppInfoReceive");
        intent.putExtra("package_name", activity.getComponentName().getPackageName());
        intent.putExtra("class_name", activity.getComponentName().getClassName());
        intent.putExtra("pullingback_key", new String[]{"KEY_USB_CONNECT"});
        activity.sendBroadcast(intent);
    }

    /** Where the live view is drawn on screen, or null when the platform does not say. */
    public static Rect liveViewRect() {
        DisplayManager dm = null;
        try {
            dm = new DisplayManager();
            DisplayManager.VideoRect r = dm.getDisplayedVideoRect();
            if (r == null || r.pxRight <= r.pxLeft || r.pxBottom <= r.pxTop) return null;
            return new Rect(r.pxLeft, r.pxTop, r.pxRight, r.pxBottom);
        } catch (Throwable t) {
            return null;
        } finally {
            if (dm != null) {
                try {
                    dm.finish();
                } catch (Throwable ignored) {
                    // Nothing to release.
                }
            }
        }
    }
}
