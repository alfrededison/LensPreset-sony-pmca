package com.lenspreset.app;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

/**
 * voxivoid/recipe-lab-sony-pmca (GPL-3.0), the colour-recipe app for PMCA bodies. LensPreset does
 * not write recipes itself: MENU → Color recipe opens Recipe Lab on top of LensPreset, and Recipe
 * Lab's own exit lands back here. Recipe Lab exposes no intent API beyond its launcher activity, so
 * this is a plain explicit launch; the recipe it stores stays in the camera's settings, which
 * LensPreset leaves alone while Keep current look is on.
 */
final class RecipeLab {
    static final String PACKAGE = "com.voxivoid.recipelab";
    static final String ACTIVITY = PACKAGE + ".MainActivity";
    static final String URL = "github.com/voxivoid/recipe-lab-sony-pmca";

    private RecipeLab() {
    }

    /** The launch intent, or null when Recipe Lab is not installed. */
    static Intent launchIntent(Context context) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setClassName(PACKAGE, ACTIVITY);
        try {
            PackageManager pm = context.getPackageManager();
            if (pm.resolveActivity(intent, 0) != null) return intent;
            // A later Recipe Lab may rename its activity: fall back to whatever it launches with.
            Intent fallback = pm.getLaunchIntentForPackage(PACKAGE);
            if (fallback == null) return null;
            // getLaunchIntentForPackage asks for a new task; stay in ours so Recipe Lab's exit returns here.
            fallback.setFlags(0);
            return fallback;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
