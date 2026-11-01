# Third-party notices

LensPreset is licensed under the GPL-3.0. It builds on the following work.

- **ma1co/OpenMemories-Framework** (MIT, © 2016 ma1co). The compile-time stubs in
  `camera-adapter/stubs/` reproduce the signatures and constant values of its Sony API stubs. They
  are not packaged into the APK.
- **obs1dium/BetterManual** (MIT, © 2016 Martin Tofall). The table of
  `setAutoShutterSpeedLowLimit` codes in `SonyValues.MIN_SHUTTER_CODES`, the live view activity's
  structure (layout, the order of camera open, surface callback, direct shutter and preview, 16-bit
  framebuffer) and `app/.../BaseActivity.java` come from it.
- **ma1co/PMCADemo** (MIT, © 2015 ma1co). `BaseActivity` is adapted from its BaseActivity, through
  BetterManual.
- **voxivoid/recipe-lab-sony-pmca** (GPL-3.0). The Gradle-free build, the 8.3 memory-card naming
  rule, key scan codes per body, and the project and release workflow follow its documentation.
- **ma1co/fwtool.py** (MIT). Used, not included, by `tools/firmware/extract.py`.

Sony, Alpha and PlayMemories are trademarks of Sony Group Corporation. Firmware read by the test
tools stays Sony's property and is never stored in or distributed from this repository.
