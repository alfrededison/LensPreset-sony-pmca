"""Tests for the firmware tools, with no firmware: the app's own stubs, compiled to DEX, stand in
for a camera framework.

    python3 -m unittest discover -s tools/firmware -p 'test_*.py'

Needs a JDK; the DEX cases also need the Android SDK's d8 (ANDROID_SDK / ANDROID_HOME, build-tools
35.0.0 or BUILD_TOOLS) and are skipped without it.
"""
import glob
import json
import os
import shutil
import struct
import subprocess
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import classfile  # noqa: E402
import contract  # noqa: E402
import dex  # noqa: E402
import fetch  # noqa: E402


def find_d8():
    sdk = os.environ.get("ANDROID_SDK") or os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME")
    if not sdk:
        return None
    jar = os.path.join(sdk, "build-tools", os.environ.get("BUILD_TOOLS", "35.0.0"), "lib", "d8.jar")
    return jar if os.path.exists(jar) else None


def to_dex(classes_dir, out_dir):
    os.makedirs(out_dir, exist_ok=True)
    files = glob.glob(os.path.join(classes_dir, "**", "*.class"), recursive=True)
    subprocess.check_call(["java", "-cp", find_d8(), "com.android.tools.r8.D8", "--min-api", "10",
                           "--output", out_dir] + files, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    return os.path.join(out_dir, "classes.dex")


class StubContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.mkdtemp()
        cls.classes = contract.compile_stubs(cls.tmp)
        cls.contract = contract.load_contract(cls.classes)

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(cls.tmp)

    def test_contract_lists_what_the_adapter_calls(self):
        cam = self.contract["com.sony.scalar.hardware.CameraEx"]
        self.assertIn("getLensInfo()Lcom/sony/scalar/hardware/CameraEx$LensInfo;", cam.methods)
        self.assertIn("open(ILcom/sony/scalar/hardware/CameraEx$OpenOptions;)Lcom/sony/scalar/hardware/CameraEx;", cam.methods)
        mod = self.contract["com.sony.scalar.hardware.CameraEx$ParametersModifier"]
        self.assertEqual(mod.fields["SCENE_MODE_SHUTTER_PRIORITY"], ("Ljava/lang/String;", "shutter-speed"))
        keys = self.contract["com.sony.scalar.sysutil.ScalarInput"]
        self.assertEqual(keys.fields["ISV_KEY_ENTER"], ("I", 232))
        self.assertNotIn("android.util.Pair", self.contract, "android stand-ins are not part of the contract")

    def test_sources_json_is_complete_or_skipped(self):
        for s in fetch.sources():
            self.assertIn("model", s)
            self.assertIn("page", s)
            if fetch.ready(s):
                self.assertRegex(s["sha256"], "^[0-9a-f]{64}$")


@unittest.skipUnless(find_d8(), "Android SDK d8 not found")
class DexTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.mkdtemp()
        cls.classes = contract.compile_stubs(os.path.join(cls.tmp, "stubs"))
        cls.contract = contract.load_contract(cls.classes)
        cls.dex_path = to_dex(cls.classes, os.path.join(cls.tmp, "dex"))

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(cls.tmp)

    def test_reads_members_and_constants(self):
        found = dex.read_file(self.dex_path)
        mod = found["com.sony.scalar.hardware.CameraEx$ParametersModifier"]
        self.assertIn("setISOSensitivity(I)V", mod.methods)
        self.assertEqual(mod.fields["SOFT_SKIN_EFFECT_MIDDLE"], ("Ljava/lang/String;", "middle"))
        self.assertEqual(found["com.sony.scalar.sysutil.ScalarInput"].fields["ISV_DIAL_3_COUNTERCW"], ("I", 634))
        self.assertEqual(found["com.sony.scalar.hardware.CameraEx$ApertureInfo"].superclass, "java.lang.Object")

    def test_stubs_satisfy_their_own_contract(self):
        found = dex.read_file(self.dex_path)
        api, missing_classes, missing_members, mismatches = contract.check(self.contract, found)
        self.assertEqual([], missing_classes)
        self.assertEqual([], missing_members)
        self.assertEqual([], mismatches)
        self.assertTrue(all(api.values()))

    def test_odex_and_jar_wrappers(self):
        with open(self.dex_path, "rb") as f:
            data = f.read()
        odex = os.path.join(self.tmp, "framework.odex")
        header = b"dey\n036\x00" + struct.pack("<II", 40, len(data)) + bytes(24)
        with open(odex, "wb") as f:
            f.write(header + data)
        jar = os.path.join(self.tmp, "framework.jar")
        with zipfile.ZipFile(jar, "w") as z:
            z.write(self.dex_path, "classes.dex")
        for path in (odex, jar):
            self.assertIn("com.sony.scalar.hardware.CameraEx", dex.read_file(path), path)

    def test_reports_missing_methods_and_changed_constants(self):
        # A "firmware" whose CameraEx lacks getLensInfo and whose shutter-priority name differs.
        src = os.path.join(self.tmp, "broken-src")
        shutil.copytree(contract.STUBS_SRC, src)
        cam = os.path.join(src, "com", "sony", "scalar", "hardware", "CameraEx.java")
        with open(cam) as f:
            text = f.read()
        text = text.replace('    public LensInfo getLensInfo() { throw new RuntimeException("Stub!"); }\n', "")
        text = text.replace('"shutter-speed"', '"shutter-priority"')
        with open(cam, "w") as f:
            f.write(text)
        old = contract.STUBS_SRC
        contract.STUBS_SRC = src
        try:
            broken_classes = contract.compile_stubs(os.path.join(self.tmp, "broken"))
        finally:
            contract.STUBS_SRC = old
        firmware = os.path.join(self.tmp, "firmware", "system", "framework")
        os.makedirs(firmware)
        shutil.copy(to_dex(broken_classes, os.path.join(self.tmp, "broken-dex")), os.path.join(firmware, "scalar.dex"))
        reports = os.path.join(self.tmp, "reports")
        rc = contract.main(["--extracted", os.path.join(self.tmp, "firmware"), "--model", "TEST", "--version", "1.0",
                            "--stubs-classes", self.classes, "--out", reports])
        self.assertEqual(1, rc)
        with open(os.path.join(reports, "TEST-1.0.json")) as f:
            report = json.load(f)
        self.assertFalse(report["ok"])
        self.assertEqual(["com.sony.scalar.hardware.CameraEx#getLensInfo()Lcom/sony/scalar/hardware/CameraEx$LensInfo;"],
                         report["missing_members"])
        self.assertEqual(1, len(report["constant_mismatches"]))
        self.assertIn("SCENE_MODE_SHUTTER_PRIORITY", report["constant_mismatches"][0])
        self.assertEqual(["system/framework/scalar.dex"], report["framework_files"])
        self.assertIn("CameraEx#setFocalLengthChangeListener(Lcom/sony/scalar/hardware/CameraEx$FocalLengthChangeListener;)V",
                      report["observations"])


class ClassfileTest(unittest.TestCase):
    def test_skips_private_and_synthetic_members(self):
        tmp = tempfile.mkdtemp()
        try:
            src = os.path.join(tmp, "E.java")
            with open(src, "w") as f:
                f.write("public enum E { A, B; private int x; public static final int K = 7; public void m(String s) {} }")
            subprocess.check_call(["javac", "--release", "8", "-d", tmp, src])
            c = classfile.read_dir(tmp)["E"]
            self.assertIn("m(Ljava/lang/String;)V", c.methods)
            self.assertIn("values()[LE;", c.methods)
            self.assertNotIn("x", c.fields)
            self.assertNotIn("$VALUES", c.fields)
            self.assertEqual(("I", 7), c.fields["K"])
        finally:
            shutil.rmtree(tmp)


if __name__ == "__main__":
    unittest.main()
