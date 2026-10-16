# Releasing to Google Play

Google Play takes an Android App Bundle (`.aab`) signed with your **upload key**. Google then
re-signs it for users with the app signing key it holds (Play App Signing). CI builds and signs
the bundle; the upload key itself lives only in GitHub Actions secrets.

## One-time setup: create the upload key

Do this on a computer you trust, or on the phone in Termux (`pkg install openjdk-17`), **not** in
a cloud agent session. Keep the `.jks` file and both passwords somewhere safe (a password
manager). If you lose them, you can ask Google for an upload key reset, but it takes days.

```sh
keytool -genkeypair -v \
  -keystore unzip-upload.jks \
  -alias upload \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=Unzip upload key"
```

It asks for a keystore password and a key password. You can use the same one for both.

Then produce the base64 text for the secret:

```sh
base64 -w0 unzip-upload.jks > unzip-upload.jks.b64   # macOS: base64 -i unzip-upload.jks -o unzip-upload.jks.b64
```

## One-time setup: add the GitHub secrets

In the repo: **Settings → Secrets and variables → Actions → New repository secret**.

| Secret | Value |
|---|---|
| `UNZIP_UPLOAD_KEYSTORE_BASE64` | contents of `unzip-upload.jks.b64` |
| `UNZIP_UPLOAD_KEYSTORE_PASSWORD` | the keystore password |
| `UNZIP_UPLOAD_KEY_ALIAS` | `upload` |
| `UNZIP_UPLOAD_KEY_PASSWORD` | the key password |

Delete `unzip-upload.jks.b64` afterwards; keep the `.jks` backed up.

## Every release

1. Merge the PRs you want released. Each one has already bumped the version (agents/CLAUDE.md §6),
   and Play needs a `versionCode` higher than any bundle uploaded before.
2. Open the CI run for the merge commit on `main`, job **Release bundle (Play)**, and download the
   `Unzip-<version>-play` artifact. It's a zip; the `.aab` is inside.
3. In Play Console: **Testing → Internal testing → Create new release → Upload**, pick the `.aab`,
   name the release after the version, then **Next → Save and publish**.

Without the secrets, the job still builds the release bundle on every push (so R8/shrinking
problems in the release build show up early), but it doesn't sign or upload it.
