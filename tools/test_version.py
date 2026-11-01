"""Checks version numbering against temporary Git histories; needs Python, Bash, and Git."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


SCRIPT = Path(__file__).resolve().with_name("version.sh")


class VersionTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix="lenspreset-version-")
        self.addCleanup(self.tmp.cleanup)
        self.repo = Path(self.tmp.name)
        (self.repo / "app").mkdir()
        self.manifest("0.1.0")

    def git(self, *args):
        return subprocess.run(
            ["git", "-c", "user.name=Version Test", "-c", "user.email=version@example.invalid",
             "-c", "commit.gpgsign=false", *args],
            cwd=self.repo, text=True, capture_output=True, check=True,
        ).stdout.strip()

    def init(self):
        self.git("init", "-q", "-b", "main")
        self.commit()

    def manifest(self, version, extra=""):
        (self.repo / "app/AndroidManifest.xml").write_text(
            '<manifest xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:versionName="%s" %s />\n' % (version, extra)
        )

    def commit(self):
        self.git("add", ".")
        self.git("commit", "-q", "--allow-empty", "-m", "Test history")

    def version(self, release=False):
        env = dict(os.environ, RELEASE="1" if release else "0")
        result = subprocess.run(
            ["bash", "-eu", "-o", "pipefail", "-c",
             '. "$1"; printf "%s\\n%s\\n" "$VERSION_NAME" "$VERSION_CODE"',
             "version-test", str(SCRIPT)],
            cwd=self.repo, env=env, text=True, capture_output=True, check=True,
        )
        name, code = result.stdout.splitlines()
        return name, int(code)

    def test_without_git_starts_at_one(self):
        self.assertEqual(("0.1.0-dev.1", 100001), self.version())

    def test_initial_commit_and_following_commits(self):
        self.init()
        self.assertEqual(("0.1.0-dev.1", 100001), self.version())
        self.commit()
        self.commit()
        self.assertEqual(("0.1.0-dev.3", 100003), self.version())

    def test_version_bump_resets_before_and_after_commit(self):
        self.init()
        self.commit()
        self.manifest("1.0.0")
        self.assertEqual(("1.0.0-dev.1", 10000001), self.version())
        self.commit()
        self.assertEqual(("1.0.0-dev.1", 10000001), self.version())
        self.commit()
        self.assertEqual(("1.0.0-dev.2", 10000002), self.version())

    def test_other_manifest_edits_do_not_reset(self):
        self.init()
        self.manifest("0.1.0", 'android:versionCode="1"')
        self.commit()
        self.assertEqual(("0.1.0-dev.2", 100002), self.version())

    def test_tags_do_not_reset_or_change_the_counter(self):
        self.init()
        self.git("tag", "v0.1.0")
        self.manifest("1.0.0")
        self.commit()
        self.commit()
        self.git("tag", "dev")
        self.git("tag", "v1.0.0")
        self.assertEqual(("1.0.0-dev.2", 10000002), self.version())
        self.commit()
        self.assertEqual(("1.0.0-dev.3", 10000003), self.version())

    def test_release_has_no_suffix_and_outranks_its_dev_build(self):
        self.init()
        self.manifest("1.0.0")
        self.commit()
        self.assertEqual(("1.0.0", 10000999), self.version(release=True))
        self.assertGreater(self.version(release=True)[1], self.version()[1])

    def test_adding_manifest_to_existing_repo_starts_at_one(self):
        self.git("init", "-q", "-b", "main")
        (self.repo / "README.md").write_text("Existing repository\n")
        self.git("add", "README.md")
        self.git("commit", "-q", "-m", "Before app")
        self.assertEqual(("0.1.0-dev.1", 100001), self.version())
        self.commit()
        self.assertEqual(("0.1.0-dev.1", 100001), self.version())

    def test_merging_old_version_branch_does_not_choose_its_manifest_edit(self):
        self.init()
        old_base = self.git("rev-parse", "HEAD")
        self.manifest("1.0.0")
        self.commit()
        self.commit()
        self.git("checkout", "-q", "-b", "old-version", old_base)
        self.commit()
        self.commit()
        self.manifest("0.1.0", 'android:versionCode="1"')
        self.commit()
        self.git("checkout", "-q", "main")
        try:
            self.git("merge", "--no-ff", "--no-commit", "old-version")
        except subprocess.CalledProcessError as error:
            self.assertEqual(1, error.returncode)  # Resolve the manifest conflict below.
        self.manifest("1.0.0", 'android:versionCode="1"')
        self.commit()
        self.assertEqual(("1.0.0-dev.3", 10000003), self.version())

    def test_version_first_introduced_by_merge_starts_at_one(self):
        self.init()
        self.git("checkout", "-q", "-b", "new-version")
        self.manifest("1.0.0")
        self.commit()
        self.commit()
        self.git("checkout", "-q", "main")
        self.commit()
        self.git("merge", "-q", "--no-ff", "new-version", "-m", "Merge new version")
        self.assertEqual(("1.0.0-dev.1", 10000001), self.version())


if __name__ == "__main__":
    unittest.main()
