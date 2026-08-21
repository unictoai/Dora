# Dora Engineering Audit

**Repository:** `unictoai/Dora`  
**Audit basis:** Dora Master Engineering & Production Hardening Prompt  
**Audit date:** 2026-08-16  
**Current branch:** `main`  
**Current implementation milestone:** ARM64 pre-alpha with local llama.cpp text foundation and Hugging Face model acquisition

## Executive summary

Dora has moved beyond a static UI prototype. The repository now contains a reproducible Android build, Jetpack Compose UI, Room persistence, WorkManager-backed model jobs, a Hugging Face catalog, validated GGUF import, a pinned ARM64 llama.cpp JNI library, device-fit guidance, CI configuration, and a release APK. The strongest current product path is public GGUF acquisition followed by local text inference.

The principal production risk is not the absence of screens. It is verification depth. The development environment can compile and statically inspect Dora, but it cannot substitute for a physical ARM64 Android device running a real model through search, download, native load, offline generation, cancellation, process death, thermal, and low-storage scenarios. Dora must keep these limits visible.

The most important implementation decision is to preserve the existing UI → ViewModel → persistence/filesystem/network/native-engine separation. The next changes should harden correctness and testability inside that architecture rather than replace it.

## Architecture assessment

| Layer | Current implementation | Assessment |
|---|---|---|
| UI | Compose chat-first flow with Chat, Models, and Settings destinations | Good direction; needs broader loading/error/accessibility instrumentation |
| ViewModel | `MainViewModel` owns UI state, chat lifecycle, HF search, and WorkManager observation | Functional but dense; should gradually move download/search use cases into repositories |
| Persistence | Room model/job records plus `LocalRegistry` artifact metadata | Correct foundation; reconciliation and migration tests remain incomplete |
| Download | `ModelDownloadManager` plus `DoraModelDownloadWorker` | Hardened for HTTPS redirects, ranges, checksums, GGUF magic, foreground work, and retry; physical-device proof remains |
| Native runtime | `NativeLlamaEngine` and pinned llama.cpp CMake/JNI bridge | ARM64 build is verified; runtime behavior, unload, concurrent calls, and memory limits require device tests |
| Image runtime | Explicitly isolated/not-ready | Correctly not advertised as shipped; requires a separate validated milestone |
| CI | GitHub Actions workflow for build/test/lint/native submodules | Good baseline; release build and instrumentation coverage should be added |

## Findings register

| ID | Severity | File / symbol | Problem and root cause | Impact | Reproduction | Recommended fix | Status | Verification |
|---|---|---|---|---|---|---|---|---|
| DORA-P0-001 | P0 | `app/src/main/java/app/dora/localai/data/DoraJobWorker.kt` | Native validation is performed after download, but the physical device path is not automated in CI | A model can appear correct in sandbox builds while failing on-device | Install APK on ARM64 device and download a small GGUF | Add device/instrumentation test and capture native-load diagnostics | Open | Static compile passed; device pending |
| DORA-P0-002 | P0 | `app/src/main/java/app/dora/localai/data/ModelDownloadManager.kt` | Resume correctness and CDN behavior are covered by code and network probes, not Android integration tests | A regression could append an invalid response or lose a partial file | Inject 200, 206, 302, 403, 404, 429, 500, timeout, reset, and malformed range responses | Add a fake HTTP server test matrix and failure-injection tests | In progress | Public CDN range probe passed |
| DORA-P0-003 | P0 | `app/src/main/java/app/dora/localai/data/LocalRegistry.kt`, `DoraDatabase.kt` | Registry/database/filesystem reconciliation is not yet a single transactional recovery operation | Crash, external deletion, or upgrade can leave metadata and files inconsistent | Delete model file without deleting Room/registry row, then relaunch | Add reconciliation use case with repair states and tests | Open | Manual design reviewed |
| DORA-P0-004 | P0 | `app/src/main/java/app/dora/localai/engine/NativeLlamaEngine.kt`, `app/src/main/cpp/dora_jni.cpp` | JNI lifecycle and concurrent-operation behavior have not been exercised under cancellation, unload, or process pressure | Native crash or leaked memory is possible even though compilation passes | Start generation, cancel, delete model, reload, and repeat under stress | Add a native session owner, mutex/state machine, invalid-input tests, and ASAN-compatible host checks | Open | ARM64 native build passed |
| DORA-P0-005 | P0 | `MainViewModel.kt` | Core state and use cases are concentrated in one ViewModel | Race conditions and lifecycle complexity will grow as features expand | Start search, download, delete, and chat operations concurrently | Extract `ModelRepository`, `DownloadRepository`, and `InferenceUseCase` incrementally | Open | Architecture boundary identified |
| DORA-P0-006 | P0 | `MainActivity.kt` | Physical-device error states and accessibility behavior are not covered by instrumentation tests | Users may receive poor recovery guidance or inaccessible controls | Rotate/recreate during download, error, and chat generation | Add Compose UI tests and Android instrumentation coverage | Open | Lint passed |
| DORA-P1-001 | P1 | `DeviceProfile.kt` | RAM/storage/ABI fit is conservative but does not yet model context length, KV cache, CPU/GPU capability, or thermal state | Recommendations can be too optimistic or too pessimistic | Compare the same model at multiple context sizes | Add runtime-specific memory estimate and benchmark-informed profiles | Open | Static profile compiles |
| DORA-P1-002 | P1 | `HuggingFaceClient.kt` | Search results are public-API dependent and need caching, rate-limit handling, malformed-response fixtures, and deterministic deduplication tests | Catalog can be slow or unavailable on poor networks | Airplane mode, 429, malformed JSON, deleted repository | Add repository cache, explicit HTTP classification, fixtures, and offline states | Open | Public endpoints smoke-tested |
| DORA-P1-003 | P1 | `DoraJobWorker.kt` | Foreground notifications are implemented, but notification permission UX and worker cancellation tests are incomplete | Downloads may be less visible or not cancel cleanly on newer Android versions | Deny notifications, cancel work, kill/restart app | Add permission rationale and cancellation instrumentation | Open | Lint/build passed |
| DORA-P1-004 | P1 | `DoraDatabase.kt` | Room migration path exists but supported upgrade paths are not exhaustively tested | An upgrade could lose provenance or jobs | Install v0.3.0 database, upgrade to current, inspect records | Add migration fixtures and schema export in CI | Open | KSP/build passed |
| DORA-P1-005 | P1 | `LocalModelStore.kt` | GGUF validation is intentionally lightweight before native load | Corruption or incompatible metadata may be discovered late | Import malformed but magic-valid GGUF | Add streaming header/version/metadata checks before native validation | Open | Magic/checksum path verified |
| DORA-P2-001 | P2 | `MainActivity.kt` | Models screen can become dense as catalog, jobs, installed models, and provenance expand | Discoverability and accessibility may regress | Test large font, TalkBack, narrow width | Add compact detail route and semantics tests | Open | UI build passed |
| DORA-P2-002 | P2 | CI workflow | CI should add release assembly, dependency verification, formatting, and artifact checks | Regressions may land after debug-only validation | Push a dependency/native change | Expand workflow with release and static gates | Open | Debug workflow exists |
| DORA-P3-001 | P3 | repository docs | Some earlier release/audit documents describe older milestones | Contributors can misunderstand what is actually shipped | Compare docs against current `main` | Add document index and update stale milestone references | In progress | Current audit is this document |

## Security findings

Dora uses explicit HTTPS model sources, validates redirects, avoids embedded Hugging Face credentials, keeps model files in app-private storage, excludes private data from backup, and does not require cloud inference. The remaining security work is to test malformed metadata and filenames, verify every URL/file path boundary with adversarial fixtures, inspect release artifacts for secrets, and document the threat model for native inputs and downloaded models.

No production secret should exist in the Android repository. Hugging Face gated access must remain browser-mediated or user-supplied through a secure, explicit future connector; it must never be silently inferred from logs or stored in plaintext preferences.

## Performance and memory findings

The downloader streams to disk and uses bounded buffers. It does not load the entire GGUF into a Kotlin byte array. The native engine still needs physical-device measurement for model load memory, KV cache growth, token throughput, and thermal throttling. The image runtime remains isolated because stable-diffusion.cpp’s dependency graph and Android resource footprint are not yet proven compatible with the text runtime.

## Native/JNI findings

The ARM64 `dora_native` library compiles from pinned llama.cpp sources and is packaged into the APK. JNI boundaries need tests for null paths, invalid model files, concurrent load/generate/cancel, native exception propagation, repeated cleanup, and process death. The current code should be treated as a compiled runtime foundation, not as proof of production stability.

## Download-system findings

The current download path now handles explicit HTTPS redirects, CDN/Xet destinations, range requests, 200-after-resume restart behavior, `Content-Range`, identity transfer encoding, timeouts, retryable failures, SHA-256, expected size, GGUF magic, atomic rename, WorkManager foreground execution, Room job records, and visible UI status. The remaining P0 gate is Android integration/failure-injection coverage and a real-device run.

## Database and WorkManager findings

Room and WorkManager provide the right foundation. The missing production layer is reconciliation: on startup, Dora should compare Room model records, registry artifacts, final files, and `.part` files, then classify each discrepancy without silently deleting user data. Unique work names should be accompanied by explicit cancel/replace policy tests.

## UI findings

The current interface is intentionally simple and chat-first. The next quality work is not adding more navigation. It is making every asynchronous state explicit, adding retry/cancel actions, improving accessibility semantics, handling large text and narrow screens, and showing provenance/diagnostics without making the core flow feel like a dashboard.

## Testing and CI/CD findings

The repository currently verifies Android debug assembly, unit tests, lint, and native compilation in the sandbox/CI path. Instrumentation tests, release assembly, migration fixtures, a network failure matrix, native lifecycle stress tests, and physical-device performance benchmarks remain open. These are release gates, not optional polish.

## Production-readiness score

**Current score: 6.5 / 10 for a pre-alpha engineering build; not beta-ready.** The score reflects strong build/repository progress and an honest capability boundary, discounted for missing physical-device proof, instrumentation coverage, native stress validation, and image-runtime completion.

## Recommended roadmap

1. Complete the download failure-injection matrix and WorkManager instrumentation tests.
2. Add startup reconciliation for Room, registry, final files, and partial files.
3. Add real ARM64 device validation with a small public GGUF in airplane mode.
4. Harden JNI session ownership, cancellation, unload, and memory limits.
5. Add persistent conversations and migration fixtures.
6. Add release-build CI and artifact/security checks.
7. Reassess image runtime only after the text runtime passes the device gate.

## Specification alignment addendum — 2026-08-16

The attached **Dora Master Product Engineering Specification** is now the active product reference. This pass implements the highest-priority Download Center foundation without replacing the existing architecture. The download domain now has an explicit state machine and structured `DownloadProgress`; Room version 3 persists source provenance, lifecycle state, bytes, speed, ETA, elapsed time, retry count, error, temporary path, and final path; WorkManager remains responsible for background execution; and Compose exposes active, queued, paused, failed, and completed sections with actionable controls and expandable details.

Startup recovery now re-enqueues interrupted download, verification, validation, and installation records when no active unique WorkManager job remains. Pause retains a partial file, cancel removes the private partial file after cancellation is observed, retry reconstructs the original request from Room, and storage preflight reports required versus available bytes. The UI no longer treats every model with a path as ready: invalid or unverified artifacts are surfaced explicitly.

The remaining release-blocking gaps are honest limitations rather than hidden claims. The repository still needs Android integration/failure-injection coverage for HTTP 200/206, redirects, timeouts, network loss, disk exhaustion, pause/resume, process death, duplicate taps, and concurrent delete/import. A physical ARM64 device run is required for notification behavior, native model loading, cancellation, thermal/memory behavior, offline chat, and the complete acceptance workflow. Queue reordering, configurable concurrency, richer metadata fields, persistent conversations, and a native persistent session manager remain follow-up milestones.


## v0.9.0-prealpha implementation evidence — 2026-08-21

Dora 0.9.0-prealpha extends the existing architecture rather than replacing it. The milestone adds normalized inference telemetry, Markdown and fenced-code chat rendering, configurable incognito and retention controls, portable Markdown/JSON conversation export, title-and-message conversation search, bounded GGUF scalar metadata parsing, model-detail dialogs, context-token estimates, runtime diagnostics, generation profiles, and prompt templates for coding, writing, and Q&A. Room version 6 persists the five inference-metric columns, and the committed schema fixture plus CI guard now target version 6.

The GGUF metadata reader is deliberately bounded: it validates the container prefix first, caps metadata strings and arrays, reads scalar values only, and never loads tensor data. The model-detail surface shows architecture, quantization, context length, parameter count, layer count, embedding length, vocabulary size, provenance, and verification state only when those values are actually available. Unknown or unsupported metadata is reported as unavailable rather than invented.

The export path now provides an explicit format choice. Markdown includes model provenance and measured inference metrics; JSON includes structured role/text records and metrics when present, with dedicated escaping for control characters and quotes. Incognito mode skips Room writes for new turns, while retention cleanup removes old conversation/message rows without touching models or documents. These behaviors remain subject to device-level and migration-level integration coverage.

Verification completed for this milestone includes `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, `:app:assembleDebug`, `:app:assembleRelease`, and `:app:lintDebug` with the ARM64 native build. New unit coverage validates metric normalization and representative GGUF metadata parsing. The published APK is signed with an ephemeral pre-alpha test key, so it must not be treated as a production-signed artifact.

The release remains **not beta-ready**. A physical ARM64 device is still required to validate model download and native loading, offline inference, cancellation, process death recovery, notification behavior, memory pressure, thermal throttling, large-text accessibility, and real throughput. Dora still does not ship image generation, voice, vision, cloud inference, plugins, MCP, or tool execution.
