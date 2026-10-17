# Android release signing

This repository uses the same encrypted signing bundle and `SIGNING_PASSPHRASE`
repository secret as the MRadio Android project.

- `keys.tar.enc` contains the signing material encrypted with AES-256-CBC and PBKDF2 (600,000 iterations).
- The passphrase is stored only in GitHub Actions secrets: **Settings → Secrets and variables → Actions → `SIGNING_PASSPHRASE`**.
- The release workflow decrypts the bundle in the temporary GitHub Actions runner, signs and verifies the APK, and removes the extracted key material when the signing step exits.
- Never commit the passphrase, extracted keystores, or signing passwords.

The release workflow intentionally uses the app's hard-coded `versionName` and `versionCode`
in `app/build.gradle.kts` to preserve compatibility with F-Droid metadata parsing. Update
both values before publishing a new release; the workflow refuses to reuse an existing
`v<versionName>` tag.
