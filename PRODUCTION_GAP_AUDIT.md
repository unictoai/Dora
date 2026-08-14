# Dora Production Gap Audit

**Audit date:** 2026-08-14  
**Repository:** `unictoai/Dora`  
**Current head at audit:** `47d47fc`

## Executive finding

Dora currently contains a polished Compose shell and a useful architecture seam, but it is not yet a local-AI product. The text path uses a deterministic demo engine, the llama.cpp adapter intentionally throws `UnsupportedOperationException`, the image path intentionally fails because no runtime is bundled, and model installation currently changes registry state without downloading or validating model artifacts.

## Gap matrix

| Area | Current state | Production requirement | Priority |
|---|---|---|---|
| Android build | Gradle project files exist, but the repository has no Gradle wrapper and the sandbox has no Android SDK/Gradle | Wrapper, pinned SDK/build tools, CI build, lint, unit and instrumentation jobs | P0 |
| Text inference | `DoraDemoTextEngine` streams canned text; `LlamaCppTextEngine` is a fail-loudly seam | Real pinned llama.cpp/GGUF JNI integration with streaming, stop, unload, error mapping, and ABI packaging | P0 |
| Image inference | `DoraDemoImageEngine` always fails; no native image backend or model format | One validated local diffusion backend with model validation, progress, cancel, output persistence, and device support matrix | P0 |
| Model management | Two hardcoded cards; SharedPreferences stores installed IDs only | Model manifests, source/license/hash, resumable downloads, temporary-file isolation, checksums, import/reference modes, deletion | P0 |
| Persistence | Offline preference and installed IDs persist; chats/jobs do not | Room/DataStore schema, migrations, durable conversations, jobs, artifacts, and recovery after process death | P0 |
| Privacy | No network permission is currently declared; UI explains policy | Explicit privacy contract, no telemetry dependencies, local data inventory, deletion, network/download separation, tests | P0 |
| Job system | Image job is transient and text generation is only in ViewModel scope | Durable queue, lifecycle recovery, foreground policy, cancellation, retry categories, single-resource-heavy-job policy | P0 |
| Device fit | UI copy promises future measurement but performs no detection | RAM/storage/ABI/CPU/GPU/thermal profile and model fit classification | P1 |
| Verification | `git diff --check` only; no Android build was run | Reproducible build, unit tests, instrumentation, native conformance, offline/airplane-mode, low-memory/storage, lifecycle tests | P0 |
| Release | Source was pushed to `main`; no APK, GitHub Actions, signed release, or support matrix | CI artifact, versioning, release notes, privacy/license docs, benchmark report, and beta channel | P1 |

## Architecture decision

The existing separation between UI, domain models, ViewModel, and engine interfaces is worth preserving. Production work should replace the demo adapters rather than letting JNI/native pointers leak into Compose. The next implementation order is therefore: reproducible build first, real text runtime second, durable model/job system third, then image runtime.

## Non-negotiable quality bar

Dora must not claim to run AI models locally until a real model file has been loaded and generated output on an Android device with the network disabled. It must not claim image generation until a validated image model has produced and saved an image on the locked device support matrix. The demo adapter may remain available only in a clearly labeled developer/demo build flavor.
