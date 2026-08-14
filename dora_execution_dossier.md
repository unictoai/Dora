# Dora Local AI Android App — Execution Dossier

**Prepared by:** Manus AI  
**Research date:** 2026-08-14  
**Status:** Competitive analysis and implementation plan complete; Android device feasibility remains the first engineering gate

## Executive conclusion

Dora should not enter the market as another generic offline chat wrapper. PocketPal already has meaningful adoption and a strong privacy-first message, including on-device language models, Hugging Face downloads, benchmarking, configurable model parameters, and an explicit no-server/no-internet position.[1] [2] The competitive opportunity is to make local AI **understandable and dependable**: Dora should explain whether a model fits the phone, validate downloads, expose memory and thermal trade-offs, make cancellation reliable, and unify text and image generation inside one resource-aware local workspace.

The recommended product is an **Android-native Kotlin/Jetpack Compose application** with no account and no required backend. The first text engine should be a llama.cpp adapter for GGUF models, while MLC LLM should remain a benchmarked alternative rather than a mandatory dependency.[3] [4] The first image engine should be one validated local diffusion pipeline selected through real-device testing; broad support for SDXL, FLUX, ControlNet, LoRA, video, music, or agents should be deferred even though adjacent projects advertise such breadth.[5] [6]

> **Dora’s positioning:** “The simplest trustworthy local AI studio for Android: know what your phone can run, install it safely, and keep generation on-device.”

## What the competitor research shows

The competitive landscape has three distinct layers. Complete apps such as PocketPal, SmolChat, OfflineLLM, Maid, ChatterUI, and LLM-Hub compete on user experience and model workflows. Image-focused projects such as SDAI and Local-Diffusion demonstrate the additional complexity of local diffusion generation. Runtime projects such as llama.cpp and MLC LLM are not direct consumer competitors, but they determine Dora’s technical constraints and the model ecosystem it can access.

| Project | Verified strengths | Strategic lesson for Dora | License / observation |
|---|---|---|---|
| [PocketPal AI](https://github.com/a-ghorbani/pocketpal-ai) and [Google Play](https://play.google.com/store/apps/details?id=com.pocketpalai) | Local language models, Hugging Face downloads, benchmarking, adjustable parameters, personas, offline/no-telemetry positioning; public repository with Android/iOS code, tests, and active releases | Privacy alone is not a sufficient wedge. Dora needs superior compatibility education, reliability, resource transparency, and image workflow | MIT; Play listing showed 1M+ downloads and 3.4 stars at observation time; metrics are time-sensitive |
| [SmolChat Android](https://github.com/shubham0204/SmolChat-Android) | GGUF on-device chat using llama.cpp, Android NDK, JNI, model-hub integration, Room, vector DB/RAG work | Native Kotlin/JNI/llama.cpp is a proven reference pattern; network failure and model lifecycle need first-class handling | Apache-2.0 |
| [OfflineLLM](https://github.com/jegly/OfflineLLM) | Zero network permissions, llama.cpp GGUF, Vulkan option, CPU dispatch, encrypted settings, biometric lock, secure deletion, import verification | Privacy can be made technically visible. Advanced performance controls should be progressive, not forced on beginners | Apache-2.0; current README describes Android 14+ / minSdk 34 |
| [ChatterUI](https://github.com/Vali-98/ChatterUI) | llama.cpp through a React Native adapter; local mode; both copied and external model references; configurable mobile chat | External references are important for multi-gigabyte files. Dora should keep remote providers out of v1 to reduce confusion | MIT |
| [Maid](https://github.com/Maid-GPT/Maid) | Cross-platform local GGUF/llama.cpp workflow plus remote backends | Local mode should be Dora’s product center, not one option among many in v1 | Verify current repository terms before reuse |
| [SDAI / Stable-Diffusion-KMP](https://github.com/ShiftHackZ/Stable-Diffusion-KMP) | Android/iOS image client spanning local, hosted, and server workflows; Kotlin Multiplatform/Compose architecture; ongoing Android runtime work | Image generation involves runtime/provider/model evolution, not just a prompt screen | AGPL-3.0; do not copy architecture/code without deliberate legal review |
| [Local-Diffusion](https://github.com/rmatif/Local-Diffusion) | Flutter + stable-diffusion.cpp; multiple diffusion families, quantization, memory optimizations, ControlNet, img2img, inpainting/outpainting, LoRA, multiple samplers | Image scope expands rapidly. Memory tables and accelerator caveats should drive Dora’s presets and support claims | Apache-2.0 |
| [LLM-Hub](https://github.com/timmyy123/LLM-Hub) | Broad local text/image/video/music toolbox with agents, RAG, TTS, transcription, translation, upscaling, and device tools | Broad modality scope is attractive but dangerous for a first release; capability interfaces should permit later expansion | PolyForm Noncommercial 1.0.0; unsuitable as a code source for a commercial/app-store product without permission |
| [llama.cpp](https://github.com/ggml-org/llama.cpp) | Active C/C++ runtime, Android build path, GGUF ecosystem, CPU and accelerator backends including Vulkan | Best first text-runtime candidate, but pin a revision and isolate it behind a narrow adapter | MIT |
| [MLC LLM](https://github.com/mlc-ai/mlc-llm) | Compiled deployment engine, Android OpenCL paths for Adreno/Mali, unified engine interfaces | Useful optimized alternative, but model packaging/compiler friction must be measured | Apache-2.0 |

The primary strategic insight is that **local text chat is becoming a product category, while local image generation remains a resource-management problem disguised as a creative feature**. Local-Diffusion reports large peak-memory differences by model, resolution, and quantization, and documents accelerator cases where Vulkan may be slower than CPU.[6] Dora should therefore win by being candid and operationally excellent, not by promising the largest catalog.

## Dora v1 product definition

| Product area | v1 decision |
|---|---|
| Platform | Android first, native Kotlin/Jetpack Compose, ARM64-first; minimum Android version locked after device testing |
| Privacy | No account, no required server, no telemetry, no remote inference, and explicit user-triggered network only for model acquisition |
| Text | GGUF models through llama.cpp adapter; local import plus curated downloads; managed copy and external-reference modes |
| Image | One validated local text-to-image pipeline; conservative presets first; no model weights bundled into the APK |
| Screens | Home, Chat, Image Studio, Models, Jobs, Gallery, Settings/Privacy |
| Model metadata | Format, quantization, size, hash, license, source, context, estimated memory, capability, verification date |
| Job control | Shared queue, progress, cancellation, retry policy, resource/thermal checks, Android lifecycle recovery |
| Out of scope | Cloud fallback, accounts, ads, agents, MCP, video, music, audio, and broad remote-provider support |

The most important product rule is that Dora must distinguish **downloaded**, **validated**, **compatible**, and **recommended**. A file extension is not a compatibility guarantee. The app must reject invalid hashes, unsupported architectures/templates, incomplete image bundles, and jobs that exceed the verified device budget.

## Recommended architecture

Dora should use stable domain and UI interfaces around replaceable native engines. The Compose layer must never call C/C++ directly. A domain-level `TextInferenceEngine` should expose load, stream, stop, unload, and capability methods. A domain-level `ImageInferenceEngine` should expose model validation, generation progress, cancellation, output metadata, and cleanup. Both should emit typed job events to a shared scheduler.

| Layer | Responsibility |
|---|---|
| Compose presentation | Navigation, accessibility, theme, empty/loading/error states, chat and image interactions |
| Domain use cases | Install model, validate model, run text, run image, cancel job, persist history, export, delete, inspect privacy |
| Model registry | Room metadata for models, hashes, licenses, capabilities, files, external references, and validation state |
| Job system | Downloads and inference jobs with progress, cancellation, recovery, single-job/resource policy, and notifications |
| Native bridge | JNI/NDK wrappers around pinned llama.cpp and selected image runtime |
| Storage | App-private weights/artifacts, Room metadata/history, Storage Access Framework for import/export |
| Privacy | No analytics SDK, explicit network policy, local audit screen, checksum verification, safe deletion |
| Test harness | Runtime conformance tests, device benchmark runner, lifecycle tests, corrupted-input tests |

The official llama.cpp Android documentation describes Android Studio bindings and Android NDK/CMake builds, including an ARM64 configuration and runtime CPU-feature detection.[3] ONNX Runtime’s Android deployment guidance reinforces that mobile model deployment requires explicit model conversion/export, Android NDK packaging, device installation, and potentially model-specific reduced runtime builds.[7] These are reasons to keep engine code isolated and model artifacts external to the repository.

## Required user journeys

### Beginner text journey

The first-run experience introduces local inference, explains that models are downloaded separately from the app, reports device RAM/storage/ABI/acceleration, recommends one starter model, shows its size/memory/license/source, and lets the user install it. After installation, the user enters a prompt and receives a streamed response in airplane mode. The chat screen always shows the active model and local status. The user can stop, regenerate, edit, reopen, export, and delete the conversation.

### Technical import journey

An advanced user opens Models, chooses “Import from device” or “Use external file,” sees the storage and lifecycle consequences, waits for checksum and metadata validation, and receives an explicit result. Unsupported models remain visible as rejected with a reason, not silently discarded. External references are not deleted when Dora’s metadata is deleted.

### Local image journey

The user opens Image Studio, selects a verified image model, enters a prompt, sees a safe resolution preset and a memory/latency warning category, starts the job, observes progress, cancels if necessary, and saves the output to the local gallery. The result stores prompt/model/seed/settings metadata where supported. Image jobs share the scheduler with text jobs so memory-heavy work is not silently run concurrently.

## Implementation backlog

### Milestone 0 — Competitive and legal foundation

| ID | Work item | Output | Gate |
|---|---|---|---|
| M0-1 | Freeze dated competitor matrix | Markdown dossier with verified claims and URLs | Every claim is labeled as documented, measured, or inferred |
| M0-2 | Complete dependency/license inventory | `THIRD_PARTY_LICENSES.md`, model-license policy | No AGPL/PolyForm code is copied unintentionally |
| M0-3 | Define benchmark devices | Device recording sheet and run protocol | At least low, mid, flagship tiers are available or explicitly marked unavailable |
| M0-4 | Select candidate starter models | Model manifest with source/hash/license/size | No model weight is committed to Git history |

### Milestone 1 — Runtime feasibility spike

| ID | Work item | Output | Gate |
|---|---|---|---|
| M1-1 | Build disposable Android test harness | Debug APK and reproducible build instructions | Installs on all available benchmark devices |
| M1-2 | Integrate llama.cpp text path | Load/stream/stop/unload demo | GGUF conformance tests pass |
| M1-3 | Integrate MLC comparison path | Build notes and performance comparison | Include only if packaging is reproducible |
| M1-4 | Prototype two image backends | Per-backend build and model instructions | One backend can produce a valid image on at least one device |
| M1-5 | Run repeated benchmarks | JSON/CSV and report | Median/p95 metrics plus failure categories are recorded |
| M1-6 | Lock v1 support matrix | Architecture Decision Record | Every supported model family and preset has evidence |

### Milestone 2 — Dora foundation

| ID | Work item | Output | Gate |
|---|---|---|---|
| M2-1 | Create GitHub repository and CI | Gradle build, formatting, lint, tests, debug APK artifact | Reproducible build on clean runner |
| M2-2 | Implement Compose shell/navigation | Home, Chat, Image Studio, Models, Jobs, Gallery, Settings routes | No dead-end navigation |
| M2-3 | Implement Room model registry | Schemas, migrations, validation state | Restart and migration tests pass |
| M2-4 | Implement storage/job abstractions | Typed jobs, progress, cancellation, recovery | No orphan job state after process death |
| M2-5 | Implement privacy center | Data inventory, network explanation, deletion actions | Behavior matches manifest/dependency audit |

### Milestone 3 — Text MVP

| ID | Work item | Output | Gate |
|---|---|---|---|
| M3-1 | Managed model download | Pause/resume, checksum, atomic finalize | Corrupt and interrupted downloads are rejected |
| M3-2 | File import/external reference | SAF picker and lifecycle policy | External references never delete user files |
| M3-3 | llama.cpp production adapter | Streaming, stop, unload, errors | Native lifecycle tests pass |
| M3-4 | Chat persistence and rendering | Markdown/code, conversation history, export | Reopen/export/delete works offline |
| M3-5 | Device-fit UX | Recommended/possible/not recommended states | No unsupported “works on any model” claim |

### Milestone 4 — Image MVP

| ID | Work item | Output | Gate |
|---|---|---|---|
| M4-1 | Install/validate selected image model | Model card, hash, compatibility status | Invalid bundles fail clearly |
| M4-2 | Image generation flow | Prompt, supported controls, progress, cancel | Valid image and metadata saved |
| M4-3 | Local gallery | History, share, delete, cleanup | No orphan intermediate files |
| M4-4 | Resource/thermal guardrails | Presets, warnings, job exclusivity | Unsafe jobs are blocked or explained |

### Milestone 5 — Hardening and beta

| ID | Work item | Output | Gate |
|---|---|---|---|
| M5-1 | Offline verification | Airplane-mode test suite | Installed models run without network |
| M5-2 | Reliability/soak testing | Crash and lifecycle report | No native leaks or unrecoverable jobs |
| M5-3 | Accessibility audit | TalkBack/scalable text/contrast results | Core journeys are accessible |
| M5-4 | Documentation | README, support matrix, troubleshooting, privacy, licenses | New contributor can build and understand limits |
| M5-5 | Closed beta | APK and issue triage process | Installation and compatibility failures prioritized before scope expansion |

## Acceptance criteria

| Area | Dora passes when |
|---|---|
| Offline operation | After model installation, airplane mode still allows existing chat, text generation, image generation, saving, and local history |
| Model safety | Invalid checksum, unsupported format, insufficient memory, and incomplete model bundles produce actionable failures |
| Text UX | Streaming, stop, regenerate, edit/resubmit, persistence, export, and deletion work after restart |
| Image UX | A validated model generates, reports progress, cancels, saves, shares, and deletes locally |
| Resource control | The scheduler prevents unsafe concurrent jobs and communicates storage/memory/thermal risk |
| Privacy | No prompt, response, image, path, or identifier is sent to a server during offline inference |
| Licensing | App code, native runtimes, dependencies, and recommended models have separate license records |
| Build quality | Clean CI passes formatting, static analysis, unit/instrumentation tests, and produces a debug APK |

## Risks that must remain visible

Android device fragmentation, thermal throttling, native memory leaks, runtime packaging complexity, model-license ambiguity, and image-generation latency are not polish issues; they are core product risks. Dora should measure them before using marketing language. Competitor projects demonstrate that model import failure, unsupported templates, GPU fallback, and memory pressure occur in active applications, so Dora’s reliability work is part of its product differentiation rather than a later cleanup task.[4] [6]

## Current status and next engineering action

Completed in this dossier phase: source-backed competitor review, current repository/store verification for the selected projects, license audit additions, product requirements, UX specification, architecture direction, runtime comparison protocol, support-matrix template, and implementation backlog. Not yet completed: physical Android-device benchmarks, the disposable native runtime prototype, the GitHub repository scaffold, and the first APK.

The next engineering action should be **Milestone 1: build the disposable Android feasibility harness and run it on real low-, mid-, and flagship-tier devices**. Until those results exist, Dora should not lock its minimum Android version, promise Vulkan/MLC acceleration, publish exact model recommendations, or claim a universal image-model catalog.

## References

[1]: https://play.google.com/store/apps/details?id=com.pocketpalai "PocketPal AI on Google Play"
[2]: https://github.com/a-ghorbani/pocketpal-ai "PocketPal AI GitHub repository"
[3]: https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md "llama.cpp Android documentation"
[4]: https://github.com/shubham0204/SmolChat-Android "SmolChat Android GitHub repository"
[5]: https://github.com/timmyy123/LLM-Hub "LLM-Hub GitHub repository"
[6]: https://github.com/rmatif/Local-Diffusion "Local-Diffusion GitHub repository"
[7]: https://onnxruntime.ai/docs/tutorials/mobile/deploy-android.html "ONNX Runtime Android deployment tutorial"
