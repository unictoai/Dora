# Dora release signing

Dora’s pre-alpha APKs must be signed before distribution. Android rejects an unsigned `app-release-unsigned.apk` with an invalid-package or parsing error because it has no APK signature block.

The GitHub Actions workflow creates an ephemeral RSA test key for pre-alpha validation, signs the assembled release APK with APK Signature Scheme v2/v3, and verifies the result before uploading it as an artifact. This is suitable for development and device testing only.

A production or Play Store release must use a long-lived private release keystore controlled by the Dora project owner. That keystore must never be committed to the repository or embedded in CI logs. The production signing configuration should be supplied through protected CI secrets when Dora is ready for a beta or public release.

When installing a pre-alpha build, use the asset explicitly labeled `test-signed`. The APK is ARM64-only (`arm64-v8a`) and requires Android 8.0/API 26 or later. If a previous Dora build with a different signing key is installed, uninstall that earlier build first or install over it only when both builds use the same signing key.
