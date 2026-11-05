package com.lenspreset.app;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.view.Display;

import com.lenspreset.adapter.PlaybackGeometry;
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
 *       (see docs/DEVELOPMENT.md). The one exception is the playback screen, with the camera
 *       closed, as Sony's own picture grid does: {@link #setPlaybackColorDepth};</li>
 *   <li>power-on resume is requested only when the user enables it, and cleared on explicit exit;</li>
 *   <li>every Sony call is guarded, so the app also runs (without a camera) in an emulator.</li>
 * </ul>
 */
abstract class BaseActivity extends Activity {
    private DisplayManager displayManager;
    private boolean keptAwake;
    private boolean exiting;

    @Override
    protected void onResume() {
        super.onResume();
        setLowColorDepth();
        updateAppInfo();
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
        if (keptAwake) setKeepAwake(false);
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

    /**
     * Holds off the camera's auto power-off while this activity is in front, as BetterManual does
     * during a timelapse; onPause always gives it back. Used by the adb test run (tools/camera-test.sh):
     * key presses sent over adb do not count as activity, so the camera would sleep mid-run.
     */
    protected void setKeepAwake(boolean awake) {
        Intent intent = new Intent("com.android.server.DAConnectionManagerService.apo");
        intent.putExtra("apo_info", awake ? "APO/NO" : "APO/NORMAL");
        sendBroadcast(intent);
        keptAwake = awake;
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

    /**
     * Width / height of one UI pixel on the active screen (1.125 for 640x480 on the A5100's 3:2
     * panel), so pictures keep their proportions; 1 when the platform does not say.
     */
    protected double pixelAspect() {
        if (displayManager == null) return 1;
        try {
            Display d = getWindowManager().getDefaultDisplay();
            String device = displayManager.getActiveDevice();
            if (device == null || DisplayManager.DEVICE_ID_NONE.equals(device)) return 1;
            DisplayManager.DeviceInfo info = displayManager.getDeviceInfo(device);
            if (info == null) return 1;
            return PlaybackGeometry.pixelAspect(PlaybackGeometry.screenAspect(info.aspect), d.getWidth(), d.getHeight());
        } catch (Throwable t) {
            return 1;
        }
    }

    /**
     * 32-bit colour while the playback screen is open, as Sony's own picture grid
     * (IndexLayoutGridViewBase, Smart Remote) does; 16-bit again before the live view comes back.
     * Only with the camera closed: 32-bit with the live view left the A5100's screen black. Pause
     * and a crash set 16-bit as well, so an abnormal exit does not leave the Application List black.
     */
    protected static void setPlaybackColorDepth(boolean high) {
        if (!high) {
            setLowColorDepth();
            return;
        }
        try {
            Gpelibrary.changeFrameBufferPixel(Gpelibrary.GS_FRAMEBUFFER_TYPE.ABGR8888);
        } catch (Throwable ignored) {
            // Not a Sony body.
        }
    }

    protected static void setLowColorDepth() {
        try {
            Gpelibrary.changeFrameBufferPixel(Gpelibrary.GS_FRAMEBUFFER_TYPE.RGBA4444);
        } catch (Throwable ignored) {
            // Not a Sony body.
        }
    }

    protected boolean autoReopenEnabled() {
        return false;
    }

    /** Refresh after a settings change; pause must retain this registration for power-on resume. */
    protected void updateAppInfo() {
        sendBroadcast(appInfoIntent(!exiting && autoReopenEnabled()));
    }

    protected boolean isExiting() { return exiting; }

    @Override
    public void finish() {
        if (exiting) return;
        exiting = true;
        // A normal broadcast can lose the race with finish(): Sony resumes the old activity
        // before it consumes the cleared keys. Finish only after the receiver has handled them.
        try {
            sendOrderedBroadcast(appInfoIntent(false), null, new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    BaseActivity.super.finish();
                }
            }, null, 0, null, null);
        } catch (RuntimeException e) {
            super.finish();
        }
    }

    /** USB always closes the app, including when automatic reopening is enabled. */
    private Intent appInfoIntent(boolean reopen) {
        Intent intent = new Intent("com.android.server.DAConnectionManagerService.AppInfoReceive");
        intent.putExtra("package_name", getComponentName().getPackageName());
        intent.putExtra("class_name", getComponentName().getClassName());
        intent.putExtra("pullingback_key", new String[]{"KEY_USB_CONNECT"});
        intent.putExtra("resume_key", reopen
                ? new String[]{"KEY_POWER_SLIDE_PON", "KEY_RELEASE_APO", "KEY_PLAY_APO",
                        "KEY_MEDIA_INOUT_APO", "KEY_LENS_APO", "KEY_ACCESSORY_APO", "KEY_DEDICATED_APO",
                        "KEY_POWER_APO", "KEY_PLAY_PON"}
                : new String[0]);
        return intent;
    }
}
