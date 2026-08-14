# Dora Release Status

**Milestone:** Native text foundation / pre-alpha  
**Date:** 2026-08-14  
**Repository:** [unictoai/Dora](https://github.com/unictoai/Dora)

## Verified in the sandbox

| Check | Result |
|---|---|
| Gradle wrapper | Gradle 8.9 wrapper generated and committed |
| Android build | `:app:assembleDebug` passes with Android API 35, NDK 27.2.12479018, ARM64 native target |
| Native text binary | APK contains `lib/arm64-v8a/libdora_native.so` built from pinned llama.cpp |
| Unit tests | `:app:testDebugUnitTest` passes |
| Lint | `:app:lintDebug` passes with no lint errors |
| APK artifact | `app/build/outputs/apk/debug/app-debug.apk`, approximately 21 MB in the sandbox build |
| Repository hygiene | `git diff --check` passes; model weights and native build outputs are ignored |

## Shipped engineering capabilities

Dora now has a reproducible Android project, ARM64 llama.cpp JNI library, GGUF validation/import with SHA-256 provenance, Room schema for durable model/job records, resumable checksum-verified HTTPS model downloader, WorkManager download worker, app-private backup exclusion, device RAM/storage/ABI profile, lifecycle-safe chat error/cancellation behavior, GitHub Actions CI, pinned native submodules, and a unit-tested Compose application shell.

## Gates before calling Dora beta-ready

Dora still needs execution on real ARM64 Android devices with at least one known-good GGUF model in airplane mode. The support matrix must record model load time, first-token latency, tokens/second, peak memory, thermal behavior, cancellation, process death, and low-storage behavior.

Image generation is not shipped in this milestone. stable-diffusion.cpp is pinned and a standalone ARM64 build script exists, but full compilation exceeded the sandbox’s resource ceiling before producing the final image library. The app intentionally keeps the image capability behind an explicit not-ready state until the runtime is isolated, built, and validated on a real device.

The next release gate is therefore a **physical-device native inference beta**, not a larger UI feature set.
