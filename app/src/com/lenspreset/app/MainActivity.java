package com.lenspreset.app;

import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import com.lenspreset.R;
import com.lenspreset.adapter.ApplyReport;
import com.lenspreset.adapter.DeviceIdentity;
import com.lenspreset.adapter.ProbeReport;
import com.lenspreset.adapter.SonyCamera;
import com.lenspreset.core.engine.Conditions;
import com.lenspreset.core.engine.Hud;
import com.lenspreset.core.engine.Message;
import com.lenspreset.core.engine.PresetEngine;
import com.lenspreset.core.engine.Recommendation;
import com.lenspreset.core.i18n.I18n;
import com.lenspreset.core.model.CameraProfile;
import com.lenspreset.core.model.LensProfile;
import com.lenspreset.core.model.Preset;
import com.lenspreset.core.profile.Matcher;
import com.lenspreset.core.profile.ProfileSet;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The one screen: live view with the overlay, and lists drawn over it. Pick a situation, and the
 * engine works out settings for this body and lens; the camera adapter applies them. Settings
 * apply while the app is open (roadmap option (a)); shoot from inside the app.
 */
public final class MainActivity extends BaseActivity implements SurfaceHolder.Callback, SonyCamera.Listener {
    private enum Screen { LIVE, PRESETS, OPTIONS, MENU, LENSES, LANGUAGES, TEXT }

    /** Re-apply this long after the zoom stops moving. */
    private static final int ZOOM_SETTLE_MS = 800;

    private final Handler handler = new Handler();
    private Prefs prefs;
    private CardStore card;
    private Map<String, Map<String, String>> tables;
    private I18n i18n;
    private ProfileSet profiles;

    private DeviceIdentity device;
    private CameraProfile camera;
    private LensProfile lens;
    private boolean lensChosenByUser;
    private SonyCamera.LensReading lensReading;
    private Preset preset;
    private Recommendation rec;
    private double appliedFocal = -1;

    private SonyCamera sony;
    private SurfaceView surface;
    private OverlayView overlay;
    private ListPanel panel;
    private Screen screen = Screen.LIVE;
    private Preset optionsPreset;
    /** Start-up failed: the screen shows why and any key exits. */
    private boolean failed;
    /** Language tables and profiles are in (see startLoading); until then keys are ignored. */
    private boolean loaded;
    private boolean resumed;
    /** Delivers the loading thread's result; unlike {@link #handler}, onPause does not clear it. */
    private final Handler loadHandler = new Handler();
    /** The APK's profiles, kept for MENU > Reload so it rereads only the card. */
    private List<ProfileSet.Entry> bundledProfiles = Collections.emptyList();
    /** LENSPRST/DEBUG.TXT switches; see CardStore.debugSwitches. */
    private Set<String> debug = Collections.emptySet();
    /** Key presses written to the log so far; only the first few of a session are. */
    private int keysLogged;
    private static final int KEYS_TO_LOG = 30;

    private final Runnable reapplyAfterZoom = new Runnable() {
        @Override
        public void run() {
            if (screen == Screen.LIVE) apply();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        card = new CardStore(getFilesDir());
        installCrashLog();
        debug = card.debugSwitches();
        card.setCardLog(debug.contains("card-log"));

        setContentView(R.layout.main);
        surface = (SurfaceView) findViewById(R.id.surface);
        // Required for camera preview on Android 2.x.
        surface.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        overlay = (OverlayView) findViewById(R.id.overlay);
        panel = (ListPanel) findViewById(R.id.panel);

        trace("create " + appVersion() + (debug.isEmpty() ? "" : " debug switches " + debug));
        overlay.setOnFirstDraw(new Runnable() {
            @Override
            public void run() {
                trace("first draw " + overlay.getWidth() + "x" + overlay.getHeight());
            }
        });
        startLoading();
    }

    /**
     * Language tables and profiles load on a thread of their own: on an A5100 they took 10 s, and
     * reading them in onCreate kept the live view off the screen until they were done. The camera
     * opens in onResume meanwhile, as in BetterManual, whose onCreate is as light; the overlay and the
     * keys start working once {@link #onLoaded} has run.
     */
    private void startLoading() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Map<String, Map<String, String>> t;
                ProfileSet set;
                final List<ProfileSet.Entry> fromApk;
                try {
                    long start = SystemClock.uptimeMillis();
                    Assets.Loaded bundle;
                    try {
                        bundle = Assets.load(getAssets());
                    } catch (IOException e) {
                        // A broken build: no words and no profiles, but the keys still exit.
                        card.log(Collections.singletonList("bundle unreadable: " + e.getMessage()));
                        Map<String, Map<String, String>> none = new LinkedHashMap<String, Map<String, String>>();
                        none.put(I18n.FALLBACK, new HashMap<String, String>());
                        bundle = new Assets.Loaded(none, new ArrayList<ProfileSet.Entry>());
                    }
                    t = bundle.languages;
                    fromApk = bundle.profiles;
                    long bundled = SystemClock.uptimeMillis();
                    List<ProfileSet.Entry> entries = new ArrayList<ProfileSet.Entry>(fromApk);
                    entries.addAll(card.profiles());
                    long cardDone = SystemClock.uptimeMillis();
                    set = ProfileSet.load(entries);
                    trace("loaded: bundle " + (bundled - start) + "ms, card " + (cardDone - bundled)
                            + "ms, parsing " + (SystemClock.uptimeMillis() - cardDone) + "ms");
                } catch (final Throwable e) {
                    loadHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            startupFailed("loading", e);
                        }
                    });
                    return;
                }
                final Map<String, Map<String, String>> loadedTables = t;
                final ProfileSet loadedProfiles = set;
                loadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        bundledProfiles = fromApk;
                        onLoaded(loadedTables, loadedProfiles);
                    }
                });
            }
        }, "load").start();
    }

    private void onLoaded(Map<String, Map<String, String>> loadedTables, ProfileSet loadedProfiles) {
        if (failed) return;
        tables = loadedTables;
        setLanguage();
        profiles = loadedProfiles;
        logProfiles();
        loaded = true;
        if (resumed) {
            try {
                finishResume();
            } catch (Throwable t) {
                startupFailed("resume", t);
            }
        }
    }

    /**
     * One line in LOG.TXT before each start-up step that touches the camera or the card: when the app
     * hangs on a body, the last line says where.
     */
    private void trace(String step) {
        // The camera's clock reads 1970 to apps; uptime orders the lines and shows the gaps.
        card.log(Collections.singletonList("boot +" + SystemClock.uptimeMillis() + "ms: " + step));
    }

    /**
     * Start-up failed: log it, say so on screen, and keep the keys working (any of centre, MENU or
     * trash exits) instead of leaving a black screen.
     */
    private void startupFailed(String step, Throwable t) {
        failed = true;
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        List<String> lines = new ArrayList<String>();
        lines.add("start-up failed in " + step);
        for (String l : sw.toString().split("\n")) lines.add("  " + l.trim());
        card.log(lines);
        List<OverlayView.Line> shown = new ArrayList<OverlayView.Line>();
        shown.add(new OverlayView.Line(ApplyReport.describe(t), Message.Level.WARN));
        shown.add(new OverlayView.Line("Details: the app log (MENU > Save log to card on a later start). MENU: exit.",
                Message.Level.INFO));
        overlay.show("LensPreset", "Start-up failed (" + step + ")", Collections.<String>emptyList(), shown,
                Collections.<String>emptyList());
        overlay.setLegend("");
        panel.hide();
    }

    private void setLanguage() {
        List<String> langs = new ArrayList<String>(tables.keySet());
        i18n = I18n.of(tables, I18n.resolve(prefs.language(), Locale.getDefault().getLanguage(), langs));
    }

    /** Bundled profiles plus the card's again (MENU > Reload): the card may have changed. */
    private void loadProfiles() {
        List<ProfileSet.Entry> entries = new ArrayList<ProfileSet.Entry>(bundledProfiles);
        entries.addAll(card.profiles());
        profiles = ProfileSet.load(entries);
        logProfiles();
    }

    private void logProfiles() {
        List<String> log = new ArrayList<String>();
        log.add("profiles: " + profiles.cameras().size() + " cameras, " + profiles.interchangeableLenses().size()
                + " lenses, " + profiles.presets().size() + " presets; card overrides " + profiles.overridden());
        for (String p : profiles.problems()) log.add("profile problem: " + p);
        card.log(log);
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        if (failed) return;
        try {
            resume();
        } catch (Throwable t) {
            startupFailed("resume", t);
        }
    }

    /**
     * The order of BetterManual's ManualActivity: open the camera, register the surface callback,
     * start direct shutter; the preview starts in surfaceCreated. BaseActivity has already set the
     * colour depth, announced the app and opened the DisplayManager.
     */
    private void resume() {
        device = DeviceIdentity.read();
        sony = null;
        if (!debug.contains("no-camera")) {
            trace("camera open (model " + device.model + ")");
            try {
                sony = SonyCamera.open(this);
            } catch (Throwable t) {
                card.log(Collections.singletonList("camera open failed: " + ApplyReport.describe(t)));
            }
        }
        surface.getHolder().addCallback(this);
        if (sony != null) {
            trace("direct shutter");
            sony.startDirectShutter();
        }
        trace("live view rect");
        overlay.setVideoRect(liveViewRect());
        if (loaded) finishResume();
        else trace("waiting for profiles");
    }

    /** What needs the profiles: which body and lens this is, the overlay, touch. */
    private void finishResume() {
        trace("identity");
        camera = Matcher.camera(profiles, device.model);
        overlay.setOnTouchListener(camera != null && camera.controls.touchscreen ? new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_UP && screen == Screen.LIVE) openPresets();
                return true;
            }
        } : null);
        panel.setTapListener(camera != null && camera.controls.touchscreen ? new ListPanel.TapListener() {
            @Override
            public void onTap(int index) {
                handle(Keys.ENTER);
            }
        } : null);

        trace("lens");
        detectLens();
        preset = profiles.preset(prefs.preset());
        if (preset == null && !profiles.presets().isEmpty()) preset = profiles.presets().get(0);
        card.log(Collections.singletonList("open: model=" + device.model + " firmware=" + device.firmware
                + " camera=" + (camera == null ? "none" : camera.id) + " lens=" + (lens == null ? "none" : lens.id)
                + " reported=" + (lensReading == null ? "none" : lensReading.name + "/" + lensReading.id)
                + (sony != null && sony.lensProblem() != null ? " (" + sony.lensProblem() + ")" : "")));
        // Nothing is applied on opening: settings change only when the user picks a situation.
        showLive();
        if (sony == null) overlay.setStatus(i18n.t("apply.no_camera"), true);
        trace("ready");
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        trace("pause");
        handler.removeCallbacksAndMessages(null);
        surface.getHolder().removeCallback(this);
        if (sony != null) {
            sony.close();
            sony = null;
        }
    }

    @Override
    protected void onDisplayChanged() {
        overlay.setVideoRect(liveViewRect());
    }

    @Override
    protected void onStop() {
        super.onStop();
        trace("stop");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        trace("destroy");
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        trace("window focus " + hasFocus);
    }

    private void startPreview() {
        if (sony == null || debug.contains("no-preview")) return;
        try {
            sony.startPreview(surface.getHolder());
        } catch (Throwable t) {
            // Possibly before the language tables have loaded (see startLoading).
            overlay.setStatus(i18n != null ? i18n.t("ui.preview_failed") : "Live view could not start", true);
            card.log(Collections.singletonList("preview failed: " + ApplyReport.describe(t)));
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (failed) return;
        trace("surface created, preview");
        startPreview();
        trace("preview started");
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
    }

    // ---- what the camera reports ----

    @Override
    public void onLiveChanged(boolean lensChanged) {
        if (!loaded || !resumed) return;
        if (lensChanged) {
            detectLens();
            refresh();
            overlay.setStatus(i18n.t("ui.lens_changed"));
            return;
        }
        if (lens == null || !lens.zoom || rec == null || appliedFocal < 0) return;
        Double focal = focalNow();
        if (focal != null && Math.abs(focal - appliedFocal) / appliedFocal > 0.05) {
            refresh();
            handler.removeCallbacks(reapplyAfterZoom);
            handler.postDelayed(reapplyAfterZoom, ZOOM_SETTLE_MS);
        }
    }

    private Double focalNow() {
        if (sony == null || lens == null) return null;
        return Matcher.focalMm(sony.focalRaw(), lensReading == null ? 0 : lensReading.focalWideRaw, lens);
    }

    private void detectLens() {
        lensReading = sony == null ? null : sony.readLens();
        lensChosenByUser = false;
        if (camera != null && camera.fixedLens != null) {
            lens = profiles.lens(camera.fixedLens);
            return;
        }
        String choice = prefs.lens();
        if (!Prefs.AUTO.equals(choice) && profiles.lens(choice) != null) {
            lens = profiles.lens(choice);
            lensChosenByUser = true;
            return;
        }
        lens = lensReading == null ? null : Matcher.lens(profiles, lensReading.name, lensReading.id);
    }

    // ---- computing and applying ----

    private Conditions conditions(Preset p) {
        Double focal = lens != null && lens.zoom ? focalNow() : null;
        Double widest = sony == null ? null : sony.widestAperture();
        return new Conditions(focal, widest, prefs.overrides(p));
    }

    /** Recomputes the recommendation and redraws the overlay. */
    private void refresh() {
        if (camera == null || preset == null || lens == null) {
            rec = null;
            List<OverlayView.Line> lines = new ArrayList<OverlayView.Line>();
            if (lens == null) lines.add(new OverlayView.Line(i18n.t("ui.no_lens"), Message.Level.WARN));
            if (preset == null) lines.add(new OverlayView.Line(i18n.t("ui.no_presets"), Message.Level.WARN));
            overlay.show(preset == null ? i18n.t("ui.title") : i18n.pick(preset.name), subtitle(),
                    Collections.<String>emptyList(), lines, Collections.<String>emptyList());
            return;
        }
        rec = PresetEngine.compute(camera, lens, preset, conditions(preset));
        List<OverlayView.Line> lines = new ArrayList<OverlayView.Line>();
        for (Message m : rec.messages) lines.add(new OverlayView.Line(i18n.t(m.key, m.args), m.level));
        overlay.show(i18n.pick(preset.name), subtitle(), Hud.chips(rec, i18n), lines, rec.overlays);
    }

    private String subtitle() {
        StringBuilder sb = new StringBuilder(camera == null ? "" : camera.name);
        if (lens != null) {
            sb.append(" · ").append(lens.name);
            if (lensChosenByUser) sb.append(' ').append(i18n.t("ui.lens_chosen"));
            if (rec != null && lens.zoom) sb.append(" · ").append(Math.round(rec.focalMm)).append("mm");
        }
        return sb.toString();
    }

    private void apply() {
        refresh();
        if (rec == null || sony == null) return;
        final Recommendation applying = rec;
        // The report can arrive seconds later, after a lens change or a reload: log what was applied.
        final String what = "apply " + preset.id + " camera=" + camera.id + " lens=" + lens.id;
        appliedFocal = applying.focalMm;
        overlay.setStatus(i18n.t("apply.applying"));
        sony.apply(applying, new SonyCamera.ApplyCallback() {
            @Override
            public void onApplied(ApplyReport report) {
                List<ApplyReport.Item> problems = report.problems();
                if (problems.isEmpty()) {
                    overlay.setStatus(i18n.t("apply.done"));
                } else {
                    Map<String, String> args = new HashMap<String, String>();
                    args.put("ok", Integer.toString(report.okCount()));
                    args.put("total", Integer.toString(report.items().size()));
                    StringBuilder missing = new StringBuilder();
                    for (ApplyReport.Item i : problems) {
                        if (missing.length() > 0) missing.append(", ");
                        missing.append(i18n.t("apply.item." + i.key));
                    }
                    args.put("items", missing.toString());
                    overlay.setStatus(i18n.t("apply.partial", args), true);
                }
                List<String> log = new ArrayList<String>();
                log.add(what + " focal=" + Math.round(applying.focalMm));
                for (String l : report.logLines()) log.add("  " + l);
                card.log(log);
            }
        });
    }

    private void choosePreset(Preset p) {
        preset = p;
        prefs.setPreset(p.id);
        showLive();
        apply();
    }

    // ---- keys ----

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        int action = Keys.action(event);
        if (keysLogged < KEYS_TO_LOG) {
            keysLogged++;
            trace("key scan=" + event.getScanCode() + " code=" + keyCode + " action=" + action
                    + " repeat=" + event.getRepeatCount() + " screen=" + screen);
        }
        if (action == Keys.NONE) return super.onKeyDown(keyCode, event);
        boolean single = action == Keys.ENTER || action == Keys.MENU || action == Keys.TRASH;
        if (!(single && event.getRepeatCount() > 0)) handle(action);
        return true;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return Keys.action(event) != Keys.NONE || super.onKeyUp(keyCode, event);
    }

    private void handle(int action) {
        if (failed) {
            if (action == Keys.ENTER || action == Keys.MENU || action == Keys.TRASH) finish();
            return;
        }
        if (!loaded) return;
        switch (screen) {
            case LIVE: handleLive(action); break;
            case PRESETS: handlePresets(action); break;
            case OPTIONS: handleOptions(action); break;
            case MENU: handleMenu(action); break;
            case LENSES: handleLenses(action); break;
            case LANGUAGES: handleLanguages(action); break;
            default: if (action == Keys.ENTER || action == Keys.MENU || action == Keys.TRASH) openMenu(); break;
        }
    }

    private void handleLive(int action) {
        List<Preset> all = profiles.presets();
        switch (action) {
            case Keys.UP:
            case Keys.PREV:
            case Keys.DOWN:
            case Keys.NEXT: {
                if (all.isEmpty()) return;
                int step = action == Keys.UP || action == Keys.PREV ? -1 : 1;
                int idx = preset == null ? 0 : all.indexOf(preset);
                choosePreset(all.get(((idx + step) % all.size() + all.size()) % all.size()));
                break;
            }
            case Keys.LEFT:
            case Keys.RIGHT:
                if (preset != null && !preset.adjust.isEmpty()) {
                    String key = preset.adjust.keySet().iterator().next();
                    stepAdjust(preset, key, action == Keys.RIGHT ? 1 : -1);
                    apply();
                }
                break;
            case Keys.ENTER: openPresets(); break;
            case Keys.MENU: openMenu(); break;
            case Keys.TRASH: overlay.toggleHints(); break;
            default: break;
        }
    }

    private void stepAdjust(Preset p, String key, int steps) {
        Preset.Adjust a = p.adjust.get(key);
        Integer cur = prefs.overrides(p).get(key);
        prefs.setOverride(p, key, a.step(cur == null ? a.defaultValue : cur, steps));
    }

    // ---- lists ----

    private void showLive() {
        screen = Screen.LIVE;
        panel.hide();
        overlay.setUnderPanel(false);
        String legend = i18n.t("ui.legend.live");
        if (camera != null && camera.controls.touchscreen) legend += " · " + i18n.t("ui.legend.touch");
        overlay.setLegend(legend);
        refresh();
    }

    private void openPresets() {
        overlay.setUnderPanel(true);
        screen = Screen.PRESETS;
        List<ListPanel.Row> rows = new ArrayList<ListPanel.Row>();
        List<Preset> all = profiles.presets();
        for (Preset p : all) {
            rows.add(new ListPanel.Row(i18n.pick(p.name), null, i18n.pick(p.description), p == preset));
        }
        panel.show(i18n.t("ui.presets"), rows, Math.max(0, all.indexOf(preset)), i18n.t("ui.legend.list"));
    }

    private void handlePresets(int action) {
        if (action == Keys.UP || action == Keys.PREV) panel.move(-1);
        else if (action == Keys.DOWN || action == Keys.NEXT) panel.move(1);
        else if (action == Keys.MENU || action == Keys.TRASH) showLive();
        else if (action == Keys.ENTER && panel.size() > 0) {
            Preset p = profiles.presets().get(panel.selected());
            if (p.adjust.isEmpty()) choosePreset(p);
            else openOptions(p, 0);
        }
    }

    private void openOptions(Preset p, int selected) {
        overlay.setUnderPanel(true);
        screen = Screen.OPTIONS;
        optionsPreset = p;
        Map<String, Integer> values = prefs.overrides(p);
        List<ListPanel.Row> rows = new ArrayList<ListPanel.Row>();
        for (Map.Entry<String, Preset.Adjust> e : p.adjust.entrySet()) {
            Integer v = values.get(e.getKey());
            int value = v != null && e.getValue().allows(v) ? v : e.getValue().defaultValue;
            rows.add(new ListPanel.Row(i18n.t("ui.adjust." + e.getKey()), adjustLabel(e.getKey(), value), null, false));
        }
        rows.add(new ListPanel.Row(i18n.t("ui.apply"), null, null, false));
        panel.show(i18n.pick(p.name), rows, selected, i18n.t("ui.legend.options"));
    }

    private String adjustLabel(String key, int value) {
        if (Preset.Adjust.SELF_TIMER.equals(key)) {
            if (value == 0) return i18n.t("ui.off");
            Map<String, String> args = new HashMap<String, String>();
            args.put("s", Integer.toString(value));
            return i18n.t("ui.seconds", args);
        }
        return Integer.toString(value);
    }

    private void handleOptions(int action) {
        List<String> keys = new ArrayList<String>(optionsPreset.adjust.keySet());
        int row = panel.selected();
        if (action == Keys.UP || action == Keys.PREV) panel.move(-1);
        else if (action == Keys.DOWN || action == Keys.NEXT) panel.move(1);
        else if ((action == Keys.LEFT || action == Keys.RIGHT) && row < keys.size()) {
            stepAdjust(optionsPreset, keys.get(row), action == Keys.RIGHT ? 1 : -1);
            openOptions(optionsPreset, row);
        } else if (action == Keys.ENTER) choosePreset(optionsPreset);
        else if (action == Keys.MENU || action == Keys.TRASH) openPresets();
    }

    private static final int MENU_LENS = 0;
    private static final int MENU_LANGUAGE = 1;
    private static final int MENU_PROBE = 2;
    private static final int MENU_SAVE_LOG = 3;
    private static final int MENU_RELOAD = 4;
    private static final int MENU_PROBLEMS = 5;
    private static final int MENU_ABOUT = 6;
    private static final int MENU_EXIT = 7;

    private void openMenu() {
        overlay.setUnderPanel(true);
        screen = Screen.MENU;
        List<ListPanel.Row> rows = new ArrayList<ListPanel.Row>();
        String lensName = lens == null ? i18n.t("lens.none") : lens.name;
        rows.add(new ListPanel.Row(i18n.t("menu.lens"), null,
                Prefs.AUTO.equals(prefs.lens()) ? i18n.t("lens.auto") + ": " + lensName : lensName, false));
        rows.add(new ListPanel.Row(i18n.t("menu.language"), null,
                Prefs.AUTO.equals(prefs.language()) ? i18n.t("menu.auto") : i18n.languageName(i18n.language()), false));
        rows.add(new ListPanel.Row(i18n.t("menu.probe"), null, i18n.t("menu.probe_detail"), false));
        rows.add(new ListPanel.Row(i18n.t("menu.save_log"), null, i18n.t("menu.save_log_detail"), false));
        rows.add(new ListPanel.Row(i18n.t("menu.reload"), null, null, false));
        Map<String, String> n = new HashMap<String, String>();
        n.put("n", Integer.toString(profiles.problems().size()));
        rows.add(new ListPanel.Row(i18n.t("menu.problems", n), null, null, false));
        rows.add(new ListPanel.Row(i18n.t("menu.about"), null, null, false));
        rows.add(new ListPanel.Row(i18n.t("menu.exit"), null, null, false));
        panel.show(i18n.t("menu.title"), rows, 0, i18n.t("ui.legend.menu"));
    }

    private void handleMenu(int action) {
        if (action == Keys.UP || action == Keys.PREV) panel.move(-1);
        else if (action == Keys.DOWN || action == Keys.NEXT) panel.move(1);
        else if (action == Keys.MENU || action == Keys.TRASH) showLive();
        else if (action == Keys.ENTER) {
            switch (panel.selected()) {
                case MENU_LENS: openLenses(); break;
                case MENU_LANGUAGE: openLanguages(); break;
                case MENU_PROBE: runProbe(); break;
                case MENU_SAVE_LOG: saveLog(); break;
                case MENU_RELOAD:
                    loadProfiles();
                    camera = Matcher.camera(profiles, device.model);
                    detectLens();
                    if (preset != null) preset = profiles.preset(preset.id);
                    showLive();
                    overlay.setStatus(i18n.t("ui.reloaded"));
                    break;
                case MENU_PROBLEMS: openText(i18n.t("menu.problems_title"), problemLines()); break;
                case MENU_ABOUT: openText(i18n.t("menu.about"), aboutLines()); break;
                case MENU_EXIT: finish(); break;
                default: break;
            }
        }
    }

    private void openLenses() {
        overlay.setUnderPanel(true);
        screen = Screen.LENSES;
        List<ListPanel.Row> rows = new ArrayList<ListPanel.Row>();
        String reported = lensReading == null || lensReading.name == null ? i18n.t("lens.not_reported") : lensReading.name;
        rows.add(new ListPanel.Row(i18n.t("lens.auto"), null, reported, Prefs.AUTO.equals(prefs.lens())));
        List<LensProfile> lenses = profiles.interchangeableLenses();
        int selected = 0;
        for (int i = 0; i < lenses.size(); i++) {
            LensProfile l = lenses.get(i);
            boolean chosen = l.id.equals(prefs.lens());
            if (chosen) selected = i + 1;
            rows.add(new ListPanel.Row(l.name, null, l.electronic ? null : i18n.t("lens.manual"), chosen));
        }
        panel.show(i18n.t("lens.choose"), rows, selected, i18n.t("ui.legend.list"));
    }

    private void handleLenses(int action) {
        if (action == Keys.UP || action == Keys.PREV) panel.move(-1);
        else if (action == Keys.DOWN || action == Keys.NEXT) panel.move(1);
        else if (action == Keys.MENU || action == Keys.TRASH) openMenu();
        else if (action == Keys.ENTER) {
            int i = panel.selected();
            prefs.setLens(i == 0 ? Prefs.AUTO : profiles.interchangeableLenses().get(i - 1).id);
            detectLens();
            showLive();
            apply();
        }
    }

    private void openLanguages() {
        overlay.setUnderPanel(true);
        screen = Screen.LANGUAGES;
        List<ListPanel.Row> rows = new ArrayList<ListPanel.Row>();
        rows.add(new ListPanel.Row(i18n.t("menu.auto"), null, null, Prefs.AUTO.equals(prefs.language())));
        List<String> langs = i18n.languages();
        int selected = 0;
        for (int i = 0; i < langs.size(); i++) {
            boolean chosen = langs.get(i).equals(prefs.language());
            if (chosen) selected = i + 1;
            rows.add(new ListPanel.Row(i18n.languageName(langs.get(i)), null, null, chosen));
        }
        panel.show(i18n.t("menu.language"), rows, selected, i18n.t("ui.legend.list"));
    }

    private void handleLanguages(int action) {
        if (action == Keys.UP || action == Keys.PREV) panel.move(-1);
        else if (action == Keys.DOWN || action == Keys.NEXT) panel.move(1);
        else if (action == Keys.MENU || action == Keys.TRASH) openMenu();
        else if (action == Keys.ENTER) {
            int i = panel.selected();
            prefs.setLanguage(i == 0 ? Prefs.AUTO : i18n.languages().get(i - 1));
            setLanguage();
            openMenu();
        }
    }

    private void openText(String title, List<String> lines) {
        overlay.setUnderPanel(true);
        screen = Screen.TEXT;
        List<ListPanel.Row> rows = new ArrayList<ListPanel.Row>();
        for (String l : lines) rows.add(new ListPanel.Row(l, null, null, false));
        panel.show(title, rows, 0, i18n.t("ui.legend.text"));
    }

    private List<String> problemLines() {
        List<String> out = new ArrayList<String>();
        if (!card.available()) out.add(i18n.t("status.no_card"));
        out.addAll(profiles.problems());
        if (out.isEmpty()) out.add(i18n.t("menu.no_problems"));
        return out;
    }

    private List<String> aboutLines() {
        List<String> out = new ArrayList<String>();
        Map<String, String> v = new HashMap<String, String>();
        v.put("version", appVersion());
        out.add(i18n.t("ui.about.version", v));
        out.add(i18n.t("ui.about.unofficial"));
        out.add(i18n.t("ui.about.license"));
        Map<String, String> d = new HashMap<String, String>();
        d.put("model", String.valueOf(device.model));
        d.put("firmware", String.valueOf(device.firmware));
        out.add(i18n.t("ui.about.device", d));
        if (camera != null) {
            Map<String, String> s = new HashMap<String, String>();
            s.put("name", camera.name);
            s.put("status", i18n.t("status." + camera.status.name().toLowerCase(Locale.US)));
            out.add(i18n.t("ui.about.profile", s));
        }
        return out;
    }

    private String appVersion() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    // ---- probe and logs ----

    private void runProbe() {
        if (!card.available()) {
            overlay.setStatus(i18n.t("status.no_card"), true);
            showLive();
            return;
        }
        ProbeReport report = new ProbeReport();
        report.section("device")
                .put("app", appVersion())
                .put("model", device.model)
                .put("firmware", device.firmware)
                .put("platform", device.platform)
                .put("android", Build.VERSION.RELEASE)
                .put("cameraProfile", camera == null ? null : camera.id)
                .put("lensProfile", lens == null ? null : lens.id)
                .put("screen", overlay.getWidth() + "x" + overlay.getHeight())
                .put("liveViewRect", liveViewRect());
        if (sony != null) sony.probe(report);
        else report.section("camera").put("open", "failed");
        try {
            Map<String, String> args = new HashMap<String, String>();
            args.put("path", card.writeProbe(report.text()));
            overlay.setStatus(i18n.t("probe.saved", args));
        } catch (IOException e) {
            Map<String, String> args = new HashMap<String, String>();
            args.put("reason", ApplyReport.describe(e));
            overlay.setStatus(i18n.t("probe.failed", args), true);
        }
        showLive();
    }

    private void saveLog() {
        if (!card.available()) {
            overlay.setStatus(i18n.t("status.no_card"), true);
            showLive();
            return;
        }
        try {
            Map<String, String> args = new HashMap<String, String>();
            args.put("path", card.saveLog());
            overlay.setStatus(i18n.t("log.saved", args));
        } catch (IOException e) {
            Map<String, String> args = new HashMap<String, String>();
            args.put("reason", ApplyReport.describe(e));
            overlay.setStatus(i18n.t("log.failed", args), true);
        }
        showLive();
    }

    /** Crashes go to the app log; MENU > Save log to card copies it where a camera owner can share it. */
    private void installCrashLog() {
        Thread.UncaughtExceptionHandler current = Thread.getDefaultUncaughtExceptionHandler();
        if (current instanceof CrashLog) return;
        Thread.setDefaultUncaughtExceptionHandler(new CrashLog(card, current));
    }

    private static final class CrashLog implements Thread.UncaughtExceptionHandler {
        private final CardStore card;
        private final Thread.UncaughtExceptionHandler previous;

        CrashLog(CardStore card, Thread.UncaughtExceptionHandler previous) {
            this.card = card;
            this.previous = previous;
        }

        @Override
        public void uncaughtException(Thread t, Throwable e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            List<String> lines = new ArrayList<String>();
            lines.add("crash in " + t.getName());
            for (String l : sw.toString().split("\n")) lines.add("  " + l.trim());
            card.log(lines);
            if (previous != null) previous.uncaughtException(t, e);
        }
    }
}
