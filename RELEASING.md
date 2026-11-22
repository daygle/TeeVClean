# 🚀 Releasing TeeVClean

Everything needed to sign, release and publish TeeVClean: signing credentials, the tag-driven
GitHub Release workflow, and the first Google Play upload for an Android TV app.

---

## 🔑 Release signing

Release builds are signed from a keystore that must **never** be committed. The four signing
values are read first from environment variables, then (for local builds) from an optional
`keystore.properties` file in the project root, which is gitignored:

```properties
RELEASE_KEYSTORE_FILE=keystore/release.jks   # absolute path, or relative to the project root
RELEASE_KEYSTORE_PASSWORD=...
RELEASE_KEY_ALIAS=teevclean
RELEASE_KEY_PASSWORD=...
```

If there is no keystore yet, create one and keep it (and its passwords) backed up:

```bash
mkdir -p keystore
keytool -genkeypair -v -keystore keystore/release.jks -alias teevclean \
  -keyalg RSA -keysize 2048 -validity 10000
```

`bundleRelease` then writes `app/build/outputs/bundle/release/app-release.aab`. Without a
configured keystore the build still succeeds, but the bundle is unsigned and Google Play will
not accept it.

To let CI build signed release bundles, add these repository secrets under
*Settings → Secrets and variables → Actions*:

| Secret | Value |
| --- | --- |
| `RELEASE_KEYSTORE_B64` | the keystore file, base64-encoded: `base64 -w0 keystore/release.jks` |
| `RELEASE_KEYSTORE_PASSWORD` | keystore password |
| `RELEASE_KEY_ALIAS` | key alias |
| `RELEASE_KEY_PASSWORD` | key password |

> [!WARNING]
> Back up the keystore and its passwords somewhere safe. Android only installs an update over an
> existing app when both are signed with the same key. If the key is lost, users must uninstall and
> reinstall. Keep Play App Signing enabled as well: that is what lets a lost *upload* key be reset.

---

## 🏷 Releases

Pushing a `vMAJOR.MINOR.PATCH` tag (e.g. `v1.0.0`, or `v1.1.0-beta.1` for a pre-release) runs lint
and the unit tests, then publishes a signed **App Bundle** as `TeeVClean-<version>-release.aab`
to GitHub Releases. The `versionCode` is derived from the tag (`v1.0.0` → `1000000`) so it always
increases; the workflow fails rather than publishing an unsigned bundle or reusing a version.

```bash
git tag v1.0.0
git push origin v1.0.0
```

The `.aab` embeds the R8 deobfuscation mapping, so Google Play picks that up automatically on
upload; a copy of `mapping.txt` is kept as a workflow artifact for retracing stack traces locally.
Release builds are minified (`isMinifyEnabled`), so keep rules for anything reached only through
reflection belong in [`app/proguard-rules.pro`](app/proguard-rules.pro).

---

## 📤 Publishing to Google Play (first upload)

Nothing in this repo uploads to Play automatically: the workflow ends at a signed `.aab`, and the
first upload is done by hand in Play Console (exactly as for PackaTrack and the AI Camera app).

In Play Console order:

1. **Create the app**: name `TeeVClean`, default language English, type *App*, *Free*.
2. **Package name** `com.teevclean` (the `applicationId`; the Kotlin namespace is the same).
   This is permanent and cannot be changed after the first upload.
3. **Play App Signing**: opt in on the first upload. Play then holds the app signing key and the
   keystore configured above becomes the *upload* key. Never upload with a debug keystore.
4. **Upload a bundle**: push a `v*` tag and take `TeeVClean-<version>-release.aab` from the
   release (or its workflow artifact), then upload it to an *Internal testing* track first.
   Every later upload needs a higher `versionCode`, which the tag-derived scheme gives you for free.
5. **Android TV requirements**: TeeVClean declares `android.software.leanback` (required) and a
   `LEANBACK_LAUNCHER` entry point, so it installs as a TV app. Play asks for TV-specific assets
   that are **not** in this repo and must be supplied in the listing:
   - TV banner, 1280 × 720 PNG (the launcher banner in `app/src/main/res/drawable-*/`
     is a different asset and does not satisfy this).
   - App icon, 512 × 512, 32-bit PNG.
   - Feature graphic, 1024 × 500.
   - At least two TV screenshots at 1920 × 1080, captured from a TV device or emulator.
6. **Sensitive permissions**: the manifest requests two permissions that each need a Play
   declaration form, or the upload/review is rejected:
   - `QUERY_ALL_PACKAGES` - used to list installed apps with their sizes for the Applications
     screen and Guided App Cache. Declare it as core functionality; no data leaves the device.
   - `MANAGE_EXTERNAL_STORAGE` (All files access) - used for the Large Files, Duplicates and Junk
     Sweep tools. Play requires a justification and only allows it for core file-management
     functionality, which is why the internal-testing track is the right first step.
   - `PACKAGE_USAGE_STATS` is a user-granted special access (not a Play declaration) and is
     requested from the Settings screen; the app works without it.
7. **Privacy policy**: a public URL is mandatory. TeeVClean stores everything on-device, has no
   servers, no analytics, no ads and no crash-reporting SDK, so [`SECURITY.md`](SECURITY.md) is a
   good basis for the policy text.
8. **Data safety**: declare that no user data is collected or shared. Scans, folder grants and
   cleanup history stay on the device.
9. **Content rating questionnaire**: no ads, no user-generated content, no purchases, no data
   sharing, so the result is the lowest rating band (Everyone / PEGI 3).
10. **Target audience**: not designed for children, so exclude the Families policies.
11. **Rollout**: promote from internal testing to production once the bundle installs and runs on
    a TV. The first production release always goes through Play review, which can take a few days.

> [!NOTE]
> TeeVClean targets `targetSdk` 37 and compiles against API 37.2, which satisfies Play's
> current target API requirement at the time of writing.
