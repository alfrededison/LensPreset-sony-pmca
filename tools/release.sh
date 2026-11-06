#!/usr/bin/env bash
# Cuts release X.Y.Z and starts the next version in one go:
#
#   tools/release.sh                    no argument: X.Y.Z is the current versionName, unchanged
#   tools/release.sh patch|minor|major  bump versionName first; with versionName 0.1.1:
#                                       patch 0.1.2 (0.1.1 is never released), minor 0.2.0, major 1.0.0
#   tools/release.sh -h                 print this usage
#
#   1. versionName becomes X.Y.Z and CHANGELOG.md "## <versionName> — unreleased" becomes
#      "## X.Y.Z — YYYY-MM-DD"; commit "Release X.Y.Z".
#   2. Tag vX.Y.Z on that commit.
#   3. Bump versionName to the next patch X.Y.(Z+1), open "## X.Y.(Z+1) — unreleased" in
#      CHANGELOG.md; commit "Start X.Y.(Z+1) development".
#
# Every check runs before the first write, and a failure after that (a commit hook, GPG) resets the
# branch to where it started and deletes the tag, so the script can simply be run again.
#
# Nothing is pushed. Push both refs atomically afterwards, so the tag never lands without main:
#
#   git push --atomic origin main vX.Y.Z
#
# The tag builds the signed release as a draft. The bump commit on main builds nothing and deletes
# the old "dev" prerelease (its X.Y.Z-dev.N is below the release); the next code commit publishes
# <next>-dev.N, whose versionCode is above the release, so dev builds keep installing over it.
# Without step 3 every dev build would stay at X.Y.Z-dev.N, below X.Y.Z, and Android would refuse
# it as a downgrade.
set -euo pipefail
cd "$(dirname "$0")/.."

usage() {
  cat <<'USAGE'
usage: tools/release.sh [patch|minor|major]

  (no argument)  release the current versionName as it is
  patch          bump versionName first, then release: 0.1.1 -> 0.1.2 (0.1.1 is never released)
  minor          bump versionName first, then release: 0.1.1 -> 0.2.0
  major          bump versionName first, then release: 0.1.1 -> 1.0.0

Then versionName moves on to the next patch (0.2.0 -> 0.2.1). Nothing is pushed.
USAGE
}
KIND="${1:-}"
case "$KIND" in
  ""|patch|minor|major) ;;
  -h|--help) usage; exit 0 ;;
  *) usage >&2; exit 2 ;;
esac

MANIFEST=app/AndroidManifest.xml
CHANGELOG=CHANGELOG.md
BRANCH="${RELEASE_BRANCH:-main}"
REMOTE="${RELEASE_REMOTE:-origin}"

fail() { echo "$*" >&2; exit 1; }

# ---- Checks (nothing is written before all of them pass) ----

[ "$(git rev-parse --abbrev-ref HEAD)" = "$BRANCH" ] || fail "release from $BRANCH"
[ -z "$(git status --porcelain)" ] || fail "working tree is not clean"

RELEASE=1 . tools/version.sh
CURRENT="$VERSION_NAME"
IFS=. read -r MAJOR MINOR PATCH <<< "$CURRENT"
case "$KIND" in
  "") TARGET="$CURRENT" ;;
  patch) TARGET="$MAJOR.$MINOR.$((PATCH + 1))" ;;
  minor) TARGET="$MAJOR.$((MINOR + 1)).0" ;;
  major) TARGET="$((MAJOR + 1)).0.0" ;;
esac
TAG="v$TARGET"
IFS=. read -r T_MAJOR T_MINOR T_PATCH <<< "$TARGET"
NEXT="$T_MAJOR.$T_MINOR.$((T_PATCH + 1))"

git remote get-url "$REMOTE" >/dev/null 2>&1 || fail "no remote '$REMOTE' (set RELEASE_REMOTE)"
# Only the branch: tags are checked with ls-remote below. Fetching all tags would fail whenever the
# "dev" tag, which dev-build recreates on every code push (and deletes after a release), has moved
# since the last fetch.
git fetch -q "$REMOTE" "$BRANCH" || fail "cannot fetch $BRANCH from $REMOTE"
if git rev-parse -q --verify "refs/remotes/$REMOTE/$BRANCH" >/dev/null; then
  git merge-base --is-ancestor "$REMOTE/$BRANCH" HEAD ||
    fail "$BRANCH is behind or has diverged from $REMOTE/$BRANCH: pull first"
fi
# Exit 2 is "no such tag"; anything else but 0 (network, auth) must not pass as "absent".
remote_tag=0
git ls-remote --exit-code --tags "$REMOTE" "refs/tags/$TAG" >/dev/null 2>&1 || remote_tag=$?
case "$remote_tag" in
  0) fail "$TAG already exists on $REMOTE: release with patch, minor or major" ;;
  2) ;;
  *) fail "cannot list tags on $REMOTE" ;;
esac
if git rev-parse -q --verify "refs/tags/$TAG" >/dev/null; then
  fail "$TAG already exists locally: release with patch, minor or major"
fi

TODAY="${RELEASE_DATE:-$(date +%Y-%m-%d)}"

# Both file edits, checked up front: the changelog header, exactly one unreleased section that lists
# changes, no section already named after the release or the next version, the versionName attribute.
# "check" validates only; "release" and "bump" write.
edit() {
  python3 - "$1" "$MANIFEST" "$CHANGELOG" "$CURRENT" "$TARGET" "$NEXT" "$TODAY" <<'PY'
import re, sys
mode, manifest, changelog, current, target, nxt, today = sys.argv[1:]
head = "# Changelog\n\n"
section = "## %s — unreleased" % current
def attr(version):
    return re.compile(r'android:versionName="%s"' % re.escape(version))
log = open(changelog, encoding="utf-8").read()
xml = open(manifest, encoding="utf-8").read()
if mode == "check":
    problems = []
    if not log.startswith(head):
        problems.append("%s must start with '# Changelog' and a blank line" % changelog)
    if log.count(section) != 1:
        problems.append("%s needs exactly one '%s' section" % (changelog, section))
    else:
        # Running the script again right after a release would otherwise tag an empty release.
        body = log[log.index(section) + len(section):].split("\n## ", 1)[0]
        # Subheadings such as "### Added" alone are not changes.
        if not any(l.strip() and not l.lstrip().startswith("#") for l in body.splitlines()):
            problems.append("%s: '%s' lists no changes: nothing to release" % (changelog, section))
    def headings(version):
        return len(re.findall(r'^## %s(?= |$)' % re.escape(version), log, re.M))
    if headings(target) > (1 if target == current else 0):
        problems.append("%s already has a '## %s' section" % (changelog, target))
    if headings(nxt):
        problems.append("%s already has a '## %s' section" % (changelog, nxt))
    if len(attr(current).findall(xml)) != 1:
        problems.append("%s needs exactly one 'android:versionName=\"%s\"'" % (manifest, current))
    if problems:
        sys.exit("\n".join(problems))
elif mode == "release":
    open(changelog, "w", encoding="utf-8").write(log.replace(section, "## %s — %s" % (target, today)))
    open(manifest, "w", encoding="utf-8").write(attr(current).sub('android:versionName="%s"' % target, xml, count=1))
elif mode == "bump":
    open(manifest, "w", encoding="utf-8").write(attr(target).sub('android:versionName="%s"' % nxt, xml, count=1))
    open(changelog, "w", encoding="utf-8").write(head + "## %s — unreleased\n\n" % nxt + log[len(head):])
PY
}
edit check

# ---- Writes (rolled back on any failure) ----

START=$(git rev-parse HEAD)
TAGGED=0
DONE=0
rollback() {
  local status=$?
  if [ "$DONE" = 1 ] || [ "$status" = 0 ]; then return; fi
  echo "release failed: resetting $BRANCH to $START" >&2
  if [ "$TAGGED" = 1 ]; then git tag -d "$TAG" >/dev/null 2>&1 || true; fi
  git reset -q --hard "$START"
}
trap rollback EXIT

# 1. Release commit
edit release
git add "$MANIFEST" "$CHANGELOG"
git commit -q -m "Release $TARGET"

# 2. Tag
git tag -a "$TAG" -m "LensPreset $TARGET"
TAGGED=1

# 3. Start the next version
edit bump
git add "$MANIFEST" "$CHANGELOG"
git commit -q -m "Start $NEXT development"
DONE=1

RELEASE=0 . tools/version.sh
echo "Tagged $TAG; $BRANCH is now $VERSION_NAME (versionCode $VERSION_CODE)."
echo "Review, then push both atomically:  git push --atomic $REMOTE $BRANCH $TAG"
