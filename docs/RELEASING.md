# Releasing LensPreset

This document covers release **1.2.0**: playback inside the app, for checking the pictures just
taken, with zoom up to 3x and a minimap. The release tag is `v1.2.0`.

Existing compatibility evidence is for the MVP on ILCE-5100 firmware 3.10 with a Sigma 30mm
F1.4 DC DN, tested by [@alfrededison](https://github.com/alfrededison). The trip presets and
preset-owned hints from 1.0.1 and the ISO/EV controls from 1.1.0 still need real-camera testing.
For 1.2.0, playback browsing, opening on the picture just taken, the live view on return and zoom
up to 3x with the minimap were checked on that body before publishing.
Do not describe the release as verified on all PMCA bodies.
The [README](../README.md) and [changelog](../CHANGELOG.md) state the tested scope and current limits.

## Keep one signing identity

Public dev builds and releases should use the same project keystore and key alias. Keep a secure
backup of the keystore and its credentials outside the repository. Configure all four repository
Actions secrets consumed by the build:

| Secret | Value |
|---|---|
| `ANDROID_KEYSTORE_B64` | Base64-encoded project keystore |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Signing key alias |
| `ANDROID_KEY_PASSWORD` | Signing key password |

`dev-build` and tagged releases require all four secrets. `RELEASE=1` and
`REQUIRE_PROJECT_SIGNING=1` fail before building if any secret is absent. After signing, the build
verifies that the APK certificate matches `signing/release-cert.sha256`; the corresponding public
certificate is `signing/release-cert.pem`. A wrong key cannot silently become a published build.

Ordinary local builds and the `ci` workflow still use `out/debug.keystore` when no project key is
provided. CI's debug APK is for testing, not an upgrade-compatible replacement for a project-signed
APK. A fresh CI runner can generate a different debug key.

Local maintainer credentials are in the ignored `.signing/` directory: the encrypted PKCS12
keystore and `credentials.json` are owner-readable only. Back up both outside the checkout;
GitHub secrets cannot be downloaded later as a substitute for that backup. To build locally with
these credentials without putting passwords on the command line:

```bash
ANDROID_SDK=~/Library/Android/sdk python3 tools/build_signed.py          # signed dev APK
ANDROID_SDK=~/Library/Android/sdk python3 tools/build_signed.py --release # signed stable APK
```

The official key is different from the local debug key used during initial camera testing.
Those debug installations need one uninstall/reinstall to move to the project-signed builds;
preferences will be lost in that transition. Future project-signed dev and release builds share
the same certificate.
The local APK checksum and Actions artifacts use the APK's basename, so verify them from their
containing directory: `cd out && shasum -a 256 -c LensPreset.apk.sha256` for a local build.

Before publication, inspect the certificate SHA-256 digest of the actual APK using the Android
SDK's `apksigner` and compare it with the established signing identity. With the SDK build-tools
on `PATH`:

```bash
apksigner verify --min-sdk-version 10 --print-certs LensPreset-v1.2.0.apk
```

Different signing certificates prevent an in-place update. A transition from an earlier local
debug key requires an uninstall/reinstall, which removes the app's preferences; tell testers if
such a transition is necessary. Do not commit a keystore, encoded keystore, or credentials.

## Prepare the release

1. `CHANGELOG.md` has exactly one `## <versionName> — unreleased` section, right under
   `# Changelog`, listing the changes (subheadings alone do not count). `versionName` in
   `app/AndroidManifest.xml` is the only app version; `tools/release.sh` edits it, and `build.sh`
   derives `versionCode`, so leave that unchanged.
2. Commit the intended changes and confirm the latest `dev-build` run on `main` passed.
   Documentation-only commits start no run, so this is the run for the last commit that changed
   code. It runs core/adapter tests, the version, signing and release-script tests, profile
   schemas, committed firmware-report coverage, firmware-tool tests, and the signed APK build;
   `release.yml` runs the same checks again on the tag. For local commands, see
   [Development](DEVELOPMENT.md#tests).
3. Check that README compatibility claims, the camera profile's test provenance, and the
   changelog match the evidence, and update README and this guide to the version being released.
4. Verify the signing secrets and certificate identity. Never treat a green build alone as
   evidence that the expected project key was used.

## Build the draft

With a clean `main`, cut the release and start the next version in one step, then push both refs
together:

```bash
tools/release.sh            # no argument: release the current versionName as it is
tools/release.sh minor      # patch | minor | major: bump versionName first, then release
git push --atomic origin main vX.Y.Z
```

With an argument the script bumps `versionName` before tagging; from `1.2.1`, `patch` releases
`1.2.2` (1.2.1 is never released), `minor` `1.3.0` and `major` `2.0.0`, and the unreleased
changelog section takes the new number. `tools/release.sh -h` prints this. It dates the changelog
section and commits "Release X.Y.Z", tags it `vX.Y.Z`, then commits "Start X.Y.(Z+1)
development", which bumps `versionName` to the next patch and opens a new unreleased section.

Before writing anything it fetches `main` from `origin` and stops if the local branch is behind,
if the tag already exists locally or on GitHub, if the unreleased section lists no changes (what a
second run right after a release would find), or if the changelog already has a section for the
release or the next version. A failure halfway (a commit hook, GPG) resets the branch and deletes
the tag, so it can simply be run again. It never pushes; `--atomic` makes the tag and `main` land
together or not at all.

The bump commit on `main` builds nothing: `dev-build` deletes the old `dev` prerelease instead,
because its `X.Y.Z-dev.N` ranks below the release and the camera would refuse it as a downgrade.
The next commit that changes code publishes `X.Y.(Z+1)-dev.N`, which installs over the release.
Until the draft is published there is no public download of the new code, so publish it soon.
`dev-build` also fails if `versionName` is still a released version, rather than publishing a
downgrade.

Pushing the `vX.Y.Z` tag triggers `.github/workflows/release.yml`. The workflow checks the tag
against the manifest, runs the full checks, builds a v1-signed APK with `RELEASE=1`, and creates
a **draft** release with these assets (for `v1.2.0`):

- `LensPreset-v1.2.0.apk`
- `LensPreset-v1.2.0.apk.sha256`

The workflow's draft is intentional; a successful job does not mean users can download it from a
public release. Download the draft assets as a maintainer, verify the checksum from the asset's
directory, and inspect the signing certificate:

```bash
shasum -a 256 -c LensPreset-v1.2.0.apk.sha256
apksigner verify --min-sdk-version 10 --print-certs LensPreset-v1.2.0.apk
```

Use the **actual draft APK** for a short test on the A5100, rather than assuming the local dev
APK is identical. Check live view, one immediate shot, a timed shot with half-press AF, preset
restoration, and exit/relaunch. If reopening is enabled, check both power-on reopening and an
explicit exit without an immediate reopen. Confirm Keep current look is available and report
its state when checking Creative Style / Soft Skin.

## Publish and verify access

Replace the generated commit summary with release notes based on the changelog. Include:

- The tested body, firmware, lens, and tester account.
- What works, the two settings and their defaults, and the known limits.
- Installation/update instructions, including any signing-key transition.
- Links to the camera test report form and profile/preset contribution instructions.

Publish the draft from GitHub Releases after its assets and body test have been checked. Verify
the release page and both download links without maintainer authentication. Confirm that the
version/date in the changelog matches publication; do not imply that an unpublished draft is a
public download.

## Where development APKs appear

- `ci` runs on pull requests and uploads a debug-signed Actions artifact named `LensPreset-apk`
  after its APK build succeeds.
- `dev-build` runs on pushes to `main` that change more than documentation. It uploads the
  project-signed `LensPreset-dev-apk` Actions artifact and attaches a versioned APK and checksum
  to a `dev` prerelease. Check the actual release state; a draft cannot serve as a public download.
- The tag-triggered release workflow uploads the project-signed `LensPreset-release-apk` Actions
  artifact and creates a draft for a maintainer to publish.

For example, maintainer metadata can be inspected without exposing any signing credentials:

```bash
gh release view dev --json tagName,isDraft,isPrerelease,assets,url
gh release view v1.2.0 --json tagName,isDraft,isPrerelease,assets,url
```
