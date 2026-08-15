# Dora

Dora is an Android-first local AI studio focused on transparent model compatibility, offline-first text chat, and a capability-aware image-generation workflow.

## Current implementation

This first repository build provides a native Kotlin/Jetpack Compose application shell with:

- Chat-first onboarding that asks for a real GGUF model before opening the main product.
- Calm local chat screen with an active model header, on-device status, streaming output, stop generation, and a minimal composer.
- Models screen for importing and removing real local GGUF files, with validated readiness state.
- Compact Settings screen for offline mode, device fit, storage, runtime status, and deletion.
- Replaceable `TextInferenceEngine` and `ImageInferenceEngine` interfaces in `engine/`.
- A deterministic offline demo text engine for development fallback.
- A real ARM64 Android `dora_native` library built from pinned llama.cpp sources, with GGUF validation and native generation APIs.
- App-private GGUF import with atomic finalization, GGUF magic validation, SHA-256 verification, and persistent artifact metadata.

The first-run experience does not pretend a placeholder model is installed. Dora opens with GGUF onboarding, validates the selected file, and only then opens chat. When a validated GGUF file is imported, Dora selects the real llama.cpp JNI path. Image generation is intentionally not exposed as a primary destination until a real image backend is shipped.

## Build requirements

The project uses Android Gradle Plugin 8.7.3, Kotlin 2.0.21, Jetpack Compose, Material 3, Android API 35, NDK 27.2.12479018, and a pinned Gradle 8.9 wrapper. The app builds ARM64-first native inference binaries and never bundles model weights.

Open the repository in Android Studio, allow Gradle to resolve dependencies, and run the `app` configuration on an Android device or emulator. The first build requires the Android SDK platform and build tools for API 35.

## Product direction

Dora is intentionally chat-first. The product surface uses a quiet off-white canvas, restrained indigo accent, simple typography, minimal cards, and three destinations: Chat, Models, and Settings. The app avoids marketing dashboards and keeps the user’s next action visible.

## Runtime roadmap

1. Run the native llama.cpp path on representative physical Android devices with a real GGUF file, then lock the support matrix.
2. Expand the current validated import path with curated HTTPS manifests, resumable downloads, device-fit checks, and external-file references.
3. Persist conversations and jobs through Room and add lifecycle recovery, cancellation, and foreground-work policy.
4. Isolate stable-diffusion.cpp or select a compatible image backend, then validate one real model family on ARM64 Android.
5. Add instrumentation tests for lifecycle recovery, cancellation, corrupted models, low storage, and airplane-mode inference.

## Privacy position

Dora’s planned production inference path has no required account, no cloud fallback, and no telemetry. Model downloads must be explicit and visible to the user. Model weights are not stored in Git and must retain their own licenses and provenance records.

## Repository documents

- `dora_execution_dossier.md` — competitive analysis, strategy, and complete implementation backlog.
- `dora_requirements.md` — product requirements, support matrix, user journeys, and acceptance criteria.
- `dora_feasibility_spec.md` — runtime comparison and real-device benchmark protocol.
- `dora_plan.md` — approved product and engineering plan.
