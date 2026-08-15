# Dora v0.3.0-prealpha — Hugging Face models and Dora branding

## What changed

- Browse public Hugging Face repositories from the Models screen.
- Discover GGUF files with exact revision, filename, quantization, size, license, and model-card provenance.
- Rank candidate files using measured ARM64 ABI, available storage, and RAM headroom.
- Download public GGUF files with resumable HTTPS transfer, byte-count checks, SHA-256 verification when Hub metadata provides it, atomic finalization, and native llama.cpp validation before the model is enabled.
- Restore downloaded Hugging Face models and provenance after app restart through the local registry and Room migration.
- Add the Dora logo to the launcher and first-run onboarding.
- Keep gated/private repositories explicit: Dora does not collect Hugging Face tokens; users must open Hugging Face and request access themselves.

## Install

1. Download `dora-v0.3.0-prealpha-arm64-v8a.apk`.
2. Verify it with `dora-v0.3.0-prealpha-arm64-v8a.apk.sha256`.
3. Install it on an ARM64 Android device running Android 8.0/API 26 or newer.
4. Tap **Find a model on Hugging Face** or import a local `.gguf` file.
5. Select a file marked **Recommended** or **Possible**, review its repository and license details, and download it.
6. Dora validates the completed file with the native llama.cpp loader before opening chat.

## Important limitations

This is still an ARM64 pre-alpha/debug build. Public Hugging Face model downloads are supported; gated and private repositories require browser access and are not silently downloaded. Model weights are not bundled. Image generation is not included in this release. Device-fit labels are conservative guidance, not a guarantee of speed, thermals, or context length. A physical-device test with a small public GGUF remains required before a beta release.
