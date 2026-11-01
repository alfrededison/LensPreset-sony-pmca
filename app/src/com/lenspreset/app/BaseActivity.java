package com.lenspreset.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Rect;

import com.sony.scalar.hardware.avio.DisplayManager;
import com.sony.scalar.sysutil.didep.Gpelibrary;

/**
 * The platform glue every PMCA activity needs, kept as close as possible to the BaseActivity of
 * ma1co/PMCADemo as obs1dium/BetterManual uses it (both MIT) — the combination known to run on the
 * A5100. Differences, each on purpose:
 * <ul>
 *   <li>the framebuffer stays 16-bit, on resume and on pause, as BetterManual's live view activity
 *       and Sony's own Smart Remote keep it. The setting is screen-wide: earlier builds switched to
 *       32-bit, showed a black screen, and left the Application List black after an abnormal exit
 *       (see docs/DEVELOPMENT.md);</li>
 *   <li>no {@code resume_key}: the platform is never asked to reopen the app after power-off;</li>
 *   <li>every Sony call is guarded, so the app also runs (without a camera) in an emulator.</li>
 * </ul>
 */
abstract class BaseActivity extends Activity {
    private DisplayManager displayManager;

    @Override
    protected void onResume() {
        super.onResume();
        setLowColorDepth();
        notifyAppInfo();
        try {
            displayManager = new DisplayManager();
            displayManager.setDisplayStatusListener(new DisplayManager.DisplayEventListener() {
                @Override
                public void onDeviceStatusChanged(int event) {
                    if (event == DisplayManager.EVENT_SWITCH_DEVICE) onDisplayChanged();
                }
            });
        } catch (Throwable t) {
            displayManager = null;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        setLowColorDepth();
        if (displayManager != null) {
            try {
                displayManager.releaseDisplayStatusListener();
                displayManager.finish();
            } catch (Throwable ignored) {
                // Released with the process anyway.
            }
            displayManager = null;
        }
    }

    /** The output switched (LCD, viewfinder, HDMI): the live view may sit elsewhere now. */
    protected void onDisplayChanged() {
    }

    /** Where the live view is drawn on screen, or null when the platform does not say. */
    protected Rect liveViewRect() {
        if (displayManager == null) return null;
        try {
            DisplayManager.VideoRect r = displayManager.getDisplayedVideoRect();
            if (r == null || r.pxRight <= r.pxLeft || r.pxBottom <= r.pxTop) return null;
            return new Rect(r.pxLeft, r.pxTop, r.pxRight, r.pxBottom);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void setLowColorDepth() {
        try {
            Gpelibrary.changeFrameBufferPixel(Gpelibrary.GS_FRAMEBUFFER_TYPE.RGBA4444);
        } catch (Throwable ignored) {
            // Not a Sony body.
        }
    }

    /** Tells the platform this activity is in front, and to close it when USB is plugged in. */
    private void notifyAppInfo() {
        Intent intent = new Intent("com.android.server.DAConnectionManagerService.AppInfoReceive");
        intent.putExtra("package_name", getComponentName().getPackageName());
        intent.putExtra("class_name", getComponentName().getClassName());
        intent.putExtra("pullingback_key", new String[]{"KEY_USB_CONNECT"});
        sendBroadcast(intent);
    }
}
