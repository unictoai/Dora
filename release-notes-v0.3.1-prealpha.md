# Dora v0.3.1-prealpha — Hugging Face download reliability fix

## What changed

- Replaced implicit redirect handling with explicit HTTPS-only redirect handling for Hugging Face and CDN/Xet destinations.
- Preserved byte-range requests across redirects so interrupted model downloads can resume.
- Added `Content-Range` total-size parsing, identity transfer encoding, bounded redirects, GGUF magic validation, expected-byte validation, and SHA-256 validation.
- Moved Hugging Face downloads to WorkManager with network constraints, exponential retry for transient network failures, foreground progress notifications, Room job records, and registry restoration after process death.
- Added native llama.cpp validation before a downloaded model becomes available for chat.
- Added visible queued/downloading/retry status to the Models screen.
- Added Android foreground-service declarations and notification support for large private model downloads.
- Added the 1,080-slot production capability roadmap in `DORA_1000_FEATURE_ROADMAP.md`.

## Install

1. Download `dora-v0.3.1-prealpha-arm64-v8a.apk`.
2. Verify it with `dora-v0.3.1-prealpha-arm64-v8a.apk.sha256`.
3. Install it on an ARM64 Android device running Android 8.0/API 26 or newer.
4. Tap **Find a model on Hugging Face**, choose a file marked **Recommended** or **Possible**, and start the download.
5. Keep the device connected to power for large models. Dora will show progress and retry transient network failures.

## Important limitations

This remains an ARM64 pre-alpha/debug build. Model weights are not bundled, image generation is not included, and physical-device verification is still required. The development environment verified Hugging Face API metadata, CDN redirects, range behavior, Android compilation, unit tests, and lint; it cannot substitute for a real-device download and native-load test.
