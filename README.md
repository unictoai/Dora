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
- A deterministic offline demo text engine so the UI can be exercised without a cloud service or bundled model weights.

The demo engine is intentionally **not presented as a production AI model**. The production text path will replace it with a pinned llama.cpp/GGUF JNI adapter after real Android-device feasibility testing. The image path remains a seam until one local diffusion backend is validated on representative devices.

## Build requirements

The project uses Android Gradle Plugin 8.7.3, Kotlin 2.0.21, Jetpack Compose, Material 3, and Android API 35. The current module targets ARM64-first local inference architecture but does not yet bundle native inference binaries or model weights.

Open the repository in Android Studio, allow Gradle to resolve dependencies, and run the `app` configuration on an Android device or emulator. The first build requires the Android SDK platform and build tools for API 35.

## Runtime roadmap

1. Add the disposable native feasibility harness and benchmark llama.cpp/GGUF against MLC on low-, mid-, and flagship-tier Android devices.
2. Replace `DoraDemoTextEngine` with the selected production adapter behind the existing `TextInferenceEngine` interface. `LlamaCppTextEngine` is included as an explicit fail-loudly seam until the native bridge is validated.
3. Expand the current SharedPreferences-backed local registry into persistent model metadata, file checksums, managed imports, external references, and download jobs.
4. Validate one image backend and model family, then replace `DoraDemoImageEngine` behind `ImageInferenceEngine`.
5. Add instrumentation tests for lifecycle recovery, cancellation, corrupted models, low storage, and airplane-mode inference.

## Privacy position

Dora’s planned production inference path has no required account, no cloud fallback, and no telemetry. Model downloads must be explicit and visible to the user. Model weights are not stored in Git and must retain their own licenses and provenance records.

## Repository documents

- `dora_execution_dossier.md` — competitive analysis, strategy, and complete implementation backlog.
- `dora_requirements.md` — product requirements, support matrix, user journeys, and acceptance criteria.
- `dora_feasibility_spec.md` — runtime comparison and real-device benchmark protocol.
- `dora_plan.md` — approved product and engineering plan.
