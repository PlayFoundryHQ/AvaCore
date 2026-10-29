# Releasing AvaCore

Releases are automated with [release-please](https://github.com/googleapis/release-please)
and conventional-commit messages. You never tag or version by hand.

## The flow

1. Land work on `main` (direct push or merged PR) using conventional commits:
   - `feat: …` → minor bump (x.Y.0)
   - `fix: …` → patch bump (x.y.Z)
   - `feat!:` / `BREAKING CHANGE:` → major bump
   - `docs:`, `chore:`, `refactor:`, `test:` … → **no release**
2. `release.yml` runs and keeps an open **“chore: release x.y.z”** PR with the
   computed version + generated `CHANGELOG.md`. It updates as more commits land.
3. **Merge that release PR** when you want to cut the release. release-please then:
   - bumps `versionName` in `app/build.gradle.kts` (via the `x-release-please-version`
     annotation) — `versionCode` is derived from it automatically,
   - creates the git tag `vX.Y.Z` and a GitHub Release (marked *Latest*).
4. The `build-and-attach` job provisions the offline models, builds a **signed
   release APK** and uploads it to that release as `avacore-latest.apk`.
5. The landing page (`docs/index.html`) already links to
   `https://github.com/PlayFoundryHQ/AvaCore/releases/latest/download/avacore-latest.apk`,
   which always resolves to the newest release — no page redeploy needed for a new APK.

`android.yml` still runs a debug build on every PR/push and redeploys the landing
page HTML to GitHub Pages.

> Note: the old mutable `latest` tag/release is superseded by semver releases. Once
> the first `vX.Y.Z` release exists it becomes *Latest*; you can delete the old
> `latest` release and tag if you like.

## One-time setup

### 1. Allow Actions to open the release PR
Repo **Settings → Actions → General → Workflow permissions**:
- “Read and write permissions”
- ✅ “Allow GitHub Actions to create and approve pull requests”

### 2. Create a release keystore (once, keep it safe & backed up)
```bash
keytool -genkeypair -v \
  -keystore release.keystore \
  -alias avacore \
  -keyalg RSA -keysize 2048 -validity 10000
# answer the prompts; remember the store & key passwords
base64 -w0 release.keystore > release.keystore.b64   # macOS: base64 -i release.keystore
```

### 3. Add repo secrets
**Settings → Secrets and variables → Actions → New repository secret**:

| Secret | Value |
| --- | --- |
| `KEYSTORE_B64` | contents of `release.keystore.b64` |
| `KEYSTORE_PASSWORD` | the store password |
| `KEY_ALIAS` | `avacore` (the alias above) |
| `KEY_PASSWORD` | the key password |

> Keep the same keystore forever — Android rejects updates signed with a different
> key. Do **not** commit the keystore.

## Local builds
`./gradlew assembleRelease` works locally without the keystore (produces an
**unsigned** release APK). Note the release build runs R8/proguard
(`isMinifyEnabled = true`) — if you hit native/JNI stripping issues with
Sherpa-ONNX, add keep rules in `app/proguard-rules.pro`. To sign locally, pass the
keystore values as Gradle properties (`-PKEYSTORE_PATH=… -PKEYSTORE_PASSWORD=… …`).
