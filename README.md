# Dora

Dora is an Android-first local AI studio focused on transparent model compatibility, offline-first text chat, and a capability-aware image-generation workflow.

## Current implementation

This first repository build provides a native Kotlin/Jetpack Compose application shell with:

- Home screen with offline-first positioning and runtime status.
- Local chat screen with streaming output, stop generation, conversation state, and a visible demo-runtime notice.
- Models screen with model cards, format/size/memory/license metadata, verification state, and registry actions.
- Image Studio screen with prompt entry, safe-preset UI, job progress surface, and a clear image-runtime-not-bundled state.
- Settings and Privacy screen describing local storage, offline inference, model acquisition, and deletion policy.
- Replaceable `TextInferenceEngine` and `ImageInferenceEngine` interfaces in `engine/`.
- A deterministic offline demo text engine for development fallback.
- A real ARM64 Android `dora_native` library built from pinned llama.cpp sources, with GGUF validation and native generation APIs.
- App-private GGUF import with atomic finalization, GGUF magic validation, SHA-256 verification, and persistent artifact metadata.

The demo engine is intentionally **not presented as a production AI model**. When a validated GGUF file is imported, Dora selects the real llama.cpp JNI path; without one, the demo adapter remains visible as a fallback. The image path remains isolated until a compatible local diffusion backend is validated on representative devices.

## Build requirements

The project uses Android Gradle Plugin 8.7.3, Kotlin 2.0.21, Jetpack Compose, Material 3, Android API 35, NDK 27.2.12479018, and a pinned Gradle 8.9 wrapper. The app builds ARM64-first native inference binaries and never bundles model weights.

Open the repository in Android Studio, allow Gradle to resolve dependencies, and run the `app` configuration on an Android device or emulator. The first build requires the Android SDK platform and build tools for API 35.

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
