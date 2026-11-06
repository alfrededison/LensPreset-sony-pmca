"""Runs tools/release.sh in temporary Git repositories; needs Python, Bash, and Git."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


TOOLS = Path(__file__).resolve().parent


class ReleaseTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix="lenspreset-release-")
        self.addCleanup(self.tmp.cleanup)
        self.repo = Path(self.tmp.name)
        (self.repo / "app").mkdir()
        (self.repo / "tools").mkdir()
        for name in ("release.sh", "version.sh"):
            shutil.copy(TOOLS / name, self.repo / "tools" / name)
        (self.repo / "app/AndroidManifest.xml").write_text(
            '<manifest xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:versionCode="1"\n'
            '    android:versionName="0.1.0">\n</manifest>\n')
        (self.repo / "CHANGELOG.md").write_text("# Changelog\n\n## 0.1.0 — unreleased\n\n- First.\n")
        self.git("init", "-q", "-b", "main")
        self.git("add", ".")
        self.git("commit", "-q", "-m", "Initial")
        self.origin = Path(self.tmp.name + "-origin.git")
        self.addCleanup(shutil.rmtree, self.origin, True)
        subprocess.run(["git", "init", "-q", "--bare", "-b", "main", str(self.origin)], check=True)
        self.git("remote", "add", "origin", str(self.origin))
        self.git("push", "-q", "origin", "main")
        self.git("fetch", "-q", "origin")

    def git(self, *args, cwd=None):
        return subprocess.run(
            ["git", "-c", "user.name=Release Test", "-c", "user.email=release@example.invalid",
             "-c", "commit.gpgsign=false", "-c", "tag.gpgsign=false", *args],
            cwd=cwd or self.repo, text=True, capture_output=True, check=True,
        ).stdout.strip()

    def other_clone(self):
        """A second clone that pushes to origin behind this repo's back."""
        other = Path(self.tmp.name + "-other")
        self.addCleanup(shutil.rmtree, other, True)
        subprocess.run(["git", "clone", "-q", str(self.origin), str(other)], check=True)
        return other

    def assert_untouched(self, start, tag="v0.1.0"):
        self.assertEqual(start, self.git("rev-parse", "HEAD"))
        self.assertEqual("", self.git("tag", "-l", tag))
        self.assertEqual("", self.git("status", "--porcelain"))

    def release(self, *args):
        env = dict(os.environ, RELEASE_DATE="2026-11-05",
                   GIT_AUTHOR_NAME="Release Test", GIT_AUTHOR_EMAIL="release@example.invalid",
                   GIT_COMMITTER_NAME="Release Test", GIT_COMMITTER_EMAIL="release@example.invalid",
                   GIT_CONFIG_COUNT="2", GIT_CONFIG_KEY_0="commit.gpgsign", GIT_CONFIG_VALUE_0="false",
                   GIT_CONFIG_KEY_1="tag.gpgsign", GIT_CONFIG_VALUE_1="false")
        return subprocess.run(["bash", "tools/release.sh", *args], cwd=self.repo, env=env,
                              text=True, capture_output=True)

    def version(self, release):
        result = subprocess.run(
            ["bash", "-eu", "-c", '. tools/version.sh; printf "%s\\n%s\\n" "$VERSION_NAME" "$VERSION_CODE"'],
            cwd=self.repo, env=dict(os.environ, RELEASE="1" if release else "0"),
            text=True, capture_output=True, check=True)
        name, code = result.stdout.splitlines()
        return name, int(code)

    def show(self, rev, path):
        return self.git("show", "%s:%s" % (rev, path))

    def test_tags_release_and_bumps_to_next_dev_version(self):
        result = self.release()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(["Start 0.1.1 development", "Release 0.1.0", "Initial"],
                         self.git("log", "--format=%s").splitlines())
        self.assertIn('android:versionName="0.1.0"', self.show("v0.1.0", "app/AndroidManifest.xml"))
        self.assertIn("## 0.1.0 — 2026-11-05", self.show("v0.1.0", "CHANGELOG.md"))
        self.assertEqual("LensPreset 0.1.0", self.git("tag", "-l", "--format=%(contents:subject)", "v0.1.0"))
        changelog = (self.repo / "CHANGELOG.md").read_text()
        self.assertTrue(changelog.startswith("# Changelog\n\n## 0.1.1 — unreleased\n\n## 0.1.0 — 2026-11-05"))
        self.assertEqual(("0.1.1-dev.1", 101001), self.version(release=False))
        self.assertIn('    android:versionCode="1"\n    android:versionName="0.1.1">\n',
                      (self.repo / "app/AndroidManifest.xml").read_text())

    def test_next_dev_build_outranks_the_release(self):
        released = self.version(release=True)  # 0.1.0 before releasing
        self.assertEqual(0, self.release().returncode)
        self.assertGreater(self.version(release=False)[1], released[1])

    def test_bumps_version_name_before_tagging(self):
        result = self.release("minor")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(["Start 0.2.1 development", "Release 0.2.0", "Initial"],
                         self.git("log", "--format=%s").splitlines())
        self.assertEqual("", self.git("tag", "-l", "v0.1.0"))
        self.assertIn('android:versionName="0.2.0"', self.show("v0.2.0", "app/AndroidManifest.xml"))
        self.assertIn("## 0.2.0 — 2026-11-05\n\n- First.", self.show("v0.2.0", "CHANGELOG.md"))
        self.assertNotIn("0.1.0", self.show("v0.2.0", "CHANGELOG.md"))
        changelog = (self.repo / "CHANGELOG.md").read_text()
        self.assertTrue(changelog.startswith("# Changelog\n\n## 0.2.1 — unreleased\n\n## 0.2.0 — 2026-11-05"))
        self.assertEqual(("0.2.1-dev.1", 201001), self.version(release=False))
        self.assertIn("git push --atomic origin main v0.2.0", result.stdout)

    def add_change(self):
        log = self.repo / "CHANGELOG.md"
        log.write_text(log.read_text().replace(" — unreleased\n", " — unreleased\n\n- More.\n", 1))
        self.git("commit", "-q", "-am", "More")

    def test_patch_and_major(self):
        self.assertEqual(0, self.release("patch").returncode)  # 0.1.0 -> v0.1.1
        self.assertNotEqual("", self.git("tag", "-l", "v0.1.1"))
        self.assertEqual(("0.1.2-dev.1", 102001), self.version(release=False))
        self.add_change()  # into "## 0.1.2 — unreleased", opened above
        self.assertEqual(0, self.release("major").returncode)
        self.assertNotEqual("", self.git("tag", "-l", "v1.0.0"))
        self.assertEqual(("1.0.1-dev.1", 10001001), self.version(release=False))

    def test_help_explains_no_argument_and_bumps_without_writing(self):
        start = self.git("rev-parse", "HEAD")
        result = self.release("-h")
        self.assertEqual(0, result.returncode)
        self.assertIn("release the current versionName", result.stdout)
        self.assertIn("bump versionName first", result.stdout)
        self.assert_untouched(start)

    def test_refuses_an_unknown_bump(self):
        start = self.git("rev-parse", "HEAD")
        result = self.release("0.2.0")
        self.assertEqual(2, result.returncode)
        self.assertIn("usage", result.stderr)
        self.assert_untouched(start)

    def test_refuses_dirty_tree_missing_changelog_section_and_existing_tag(self):
        (self.repo / "dirty.txt").write_text("x")
        self.assertNotEqual(0, self.release().returncode)
        (self.repo / "dirty.txt").unlink()
        (self.repo / "CHANGELOG.md").write_text("# Changelog\n\n## 0.0.9 — 2026-01-01\n")
        self.git("commit", "-q", "-am", "Drop section")
        result = self.release()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("unreleased", result.stderr)
        (self.repo / "CHANGELOG.md").write_text("# Changelog\n\n## 0.1.0 — unreleased\n\n- First.\n")
        self.git("commit", "-q", "-am", "Restore section")
        self.git("tag", "v0.1.0")
        result = self.release()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("already exists", result.stderr)
        self.assertEqual("", self.git("status", "--porcelain"))


    def test_prints_an_atomic_push_that_lands_both_refs(self):
        result = self.release()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("git push --atomic origin main v0.1.0", result.stdout)
        self.git("push", "-q", "--atomic", "origin", "main", "v0.1.0")
        self.assertEqual(self.git("rev-parse", "HEAD"), self.git("rev-parse", "main", cwd=self.origin))
        self.assertEqual(self.git("rev-parse", "v0.1.0^{commit}"),
                         self.git("rev-parse", "v0.1.0^{commit}", cwd=self.origin))

    def test_refuses_when_main_is_behind_origin(self):
        start = self.git("rev-parse", "HEAD")
        other = self.other_clone()
        (other / "README.md").write_text("pushed elsewhere\n")
        self.git("add", "README.md", cwd=other)
        self.git("commit", "-q", "-m", "Elsewhere", cwd=other)
        self.git("push", "-q", "origin", "main", cwd=other)
        result = self.release()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("pull first", result.stderr)
        self.assert_untouched(start)

    def test_refuses_when_the_tag_exists_only_on_origin(self):
        start = self.git("rev-parse", "HEAD")
        other = self.other_clone()
        self.git("tag", "v0.1.0", cwd=other)
        self.git("push", "-q", "origin", "v0.1.0", cwd=other)
        result = self.release()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("already exists", result.stderr)
        self.assertEqual(start, self.git("rev-parse", "HEAD"))
        self.assertEqual("", self.git("status", "--porcelain"))

    def test_a_moved_dev_tag_does_not_block_the_release(self):
        other = self.other_clone()
        self.git("tag", "dev", cwd=other)
        self.git("push", "-q", "origin", "dev", cwd=other)
        self.git("fetch", "-q", "--tags", "origin")  # local "dev" now points at the old commit
        self.git("commit", "-q", "--allow-empty", "-m", "Moves on", cwd=other)
        self.git("push", "-q", "origin", "main", cwd=other)
        self.git("tag", "-f", "dev", cwd=other)
        self.git("push", "-q", "-f", "origin", "dev", cwd=other)  # as dev-build does on every push
        self.git("pull", "-q", "--no-tags", "origin", "main")
        result = self.release()
        self.assertEqual(0, result.returncode, result.stderr)

    def test_bad_changelog_header_is_caught_before_any_write(self):
        (self.repo / "CHANGELOG.md").write_text("# LensPreset changelog\n\n## 0.1.0 — unreleased\n\n- First.\n")
        self.git("commit", "-q", "-am", "Rename header")
        self.git("push", "-q", "origin", "main")
        start = self.git("rev-parse", "HEAD")
        result = self.release()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("# Changelog", result.stderr)
        self.assert_untouched(start)

    def rolls_back_and_can_be_rerun(self, args, tag, after):
        start = self.git("rev-parse", "HEAD")
        hook = self.repo / ".git/hooks/commit-msg"
        hook.write_text('#!/bin/sh\ngrep -q "^Start " "$1" && exit 1\nexit 0\n')
        hook.chmod(0o755)
        result = self.release(*args)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("resetting", result.stderr)
        self.assert_untouched(start, tag)
        self.assertIn('android:versionName="0.1.0"', (self.repo / "app/AndroidManifest.xml").read_text())
        hook.unlink()
        result = self.release(*args)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertNotEqual("", self.git("tag", "-l", tag))
        self.assertEqual(after, self.version(release=False))

    def test_failure_after_tagging_rolls_back_and_can_be_rerun(self):
        self.rolls_back_and_can_be_rerun((), "v0.1.0", ("0.1.1-dev.1", 101001))

    def test_failure_after_a_bumped_release_rolls_back_the_bump_too(self):
        self.rolls_back_and_can_be_rerun(("minor",), "v0.2.0", ("0.2.1-dev.1", 201001))

    def test_running_twice_does_not_tag_an_empty_release(self):
        self.assertEqual(0, self.release().returncode)
        start = self.git("rev-parse", "HEAD")
        result = self.release()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("nothing to release", result.stderr)
        self.assert_untouched(start, "v0.1.1")
        self.add_change()
        self.assertEqual(0, self.release().returncode)  # once a change is listed, it releases

    def test_refuses_a_section_already_named_after_the_release_or_next_version(self):
        # (arguments, stale section, tag that must not appear)
        for args, old, tag in ((("minor",), "0.2.0", "v0.2.0"), (("minor",), "0.2.1", "v0.2.0"),
                               ((), "0.1.0", "v0.1.0"), ((), "0.1.1", "v0.1.0")):
            (self.repo / "CHANGELOG.md").write_text(
                "# Changelog\n\n## 0.1.0 — unreleased\n\n- First.\n\n## %s — 2026-01-01\n\n- Old.\n" % old)
            self.git("commit", "-q", "-am", "Old " + old)
            start = self.git("rev-parse", "HEAD")
            result = self.release(*args)
            self.assertNotEqual(0, result.returncode, (args, old))
            self.assertIn("already has a '## %s' section" % old, result.stderr)
            self.assert_untouched(start, tag)

    def test_subheadings_alone_are_nothing_to_release(self):
        (self.repo / "CHANGELOG.md").write_text("# Changelog\n\n## 0.1.0 — unreleased\n\n### Added\n\n### Fixed\n")
        self.git("commit", "-q", "-am", "Only subheadings")
        start = self.git("rev-parse", "HEAD")
        result = self.release()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("nothing to release", result.stderr)
        self.assert_untouched(start)
        (self.repo / "CHANGELOG.md").write_text("# Changelog\n\n## 0.1.0 — unreleased\n\n### Added\n\n- Thing.\n")
        self.git("commit", "-q", "-am", "A change under a subheading")
        result = self.release()
        self.assertEqual(0, result.returncode, result.stderr)


if __name__ == "__main__":
    unittest.main()
