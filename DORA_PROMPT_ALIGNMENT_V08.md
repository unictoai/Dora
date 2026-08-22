# Dora Prompt Alignment Audit — v0.8

**Author:** Manus AI  
**Source of truth:** `pasted_content.txt` supplied by the project owner  
**Repository baseline:** Dora `main`, v0.8.0-prealpha architecture

## Executive decision

The supplied prompt is a strong product direction, but it contains a few requirements that conflict with Dora’s established release constraints or require evidence that is not available in the sandbox. Dora will preserve the working ARM64-first Android 8+ baseline, existing llama.cpp/GGUF/download architecture, explicit unsupported-runtime boundaries, and fail-loudly behavior. The prompt will be implemented incrementally rather than by adding decorative screens or making unverified performance claims.

## Conflict and feasibility table

| Prompt area | Current Dora reality | v0.8 decision | Rationale |
|---|---|---|---|
| Minimum SDK | Dora currently supports API 26 and the product requirement is Android 8+ | Preserve API 26; target/compile API 35 | Changing minSdk to 35 would remove Android 8–14 devices and contradict the established product scope. |
| Dependency injection | Dora uses explicit constructor wiring and a small application graph | Do not add Hilt/Koin solely for parity | A DI migration is cross-cutting and does not improve the user-visible v0.8 product. It belongs in an isolated architecture milestone with its own migration tests. |
| Native llama.cpp | ARM64 JNI runtime already exists with serialized session ownership and cancellation | Deepen session lifecycle, metrics, and memory safeguards | The native foundation is real; the next work should improve observability and lifecycle safety. |
| GGUF validation | Dora validates magic, supported version, counts, checksum, and native load | Add metadata exposure only when parsed safely | “Architecture/parameters/quantization” must come from actual GGUF metadata, not inferred labels. |
| Performance claims | No physical-device benchmark matrix exists in the sandbox | Add instrumentation/benchmark hooks; make no “20% faster” claim | Performance superiority requires repeatable same-device competitor measurements. |
| Rich chat | Durable multi-chat, export, system prompt, native sampling settings, and local lexical document context exist | Add Markdown/code rendering and metrics without unsafe HTML or fake LaTeX | Rendering and metrics improve the real chat experience while remaining runtime-independent. |
| Document/RAG | Dora has a bounded private text importer and lexical retrieval | Keep lexical retrieval explicit; defer embeddings/vector RAG | A semantic RAG claim requires an embedding model, index, citations, deletion semantics, and device tests. |
| Voice/vision/image | Interfaces or isolated placeholders exist, but production engines are not shipped | Keep unavailable states visible; implement only with real model/runtime adapters | The prompt’s multimodal direction is valid but cannot be represented by fake buttons. |
| Model switching | Persistent active-model selection exists | Add readiness diagnostics and safe switch behavior | “Hot swapping without context loss” requires native unload/load state tests. |
| Download manager | Resumable HTTPS, WorkManager queue, checksum, atomic finalization, pause/resume/retry, and recovery exist | Add network-policy and queue-priority controls where safe | Peer-to-peer, delta updates, and mirrors require a trust and provenance design first. |
| Privacy | Offline-only default and private storage exist | Add retention/incognito controls and document them clearly | Encryption/biometrics require a separate Keystore and migration design. |
| Deliverables | APK, source, roadmap, audit, competitive report, schemas, and tests exist | Add API/user guide and v0.8 verification reports | Physical-device acceptance and user-acceptance scores remain external gates. |

## v0.8 implementation slice

The prompt’s highest-value feasible requirements for this milestone are a polished and honest chat surface; bounded Markdown and code rendering; response timing and token-throughput instrumentation; context-window visibility based on known prompt/token estimates; model readiness diagnostics; persisted performance events; retention/incognito controls that never silently discard ordinary history; richer model metadata; and operational documentation.

The following requirements remain explicitly future milestones: production semantic RAG, PDF parsing, encrypted database storage, biometric lock, voice input, image/audio/vision inference, model distillation, NPU/DSP acceleration, multi-model orchestration, semantic response caching, peer-to-peer model sharing, delta updates, MCP/plugins, local HTTP serving, cross-device sync, federated learning, and a community marketplace.

## Acceptance gates

A v0.8 release may claim implementation completion only for features covered by source-level tests, JVM tests, migration checks, lint, debug/release builds, APK signature verification, and ARM64 native packaging. A beta claim additionally requires a physical ARM64 device run covering model discovery, download/recovery, native load, generation cancellation, document import/retrieval, chat persistence, storage reconciliation, thermal behavior, notifications, and offline mode. A performance-superiority claim requires a reproducible benchmark matrix; it must not be inferred from a successful build.

## v0.10 implementation evidence — 2026-08-21

The following prompt-aligned capabilities are now implemented on top of the preserved Dora architecture:

| Prompt-aligned area | Evidence in Dora | Boundary or caveat |
|---|---|---|
| Transparent local inference | `InferenceMetrics`, Room v6 columns, completed-message telemetry, runtime diagnostics | Throughput and latency still require physical ARM64 measurements; sandbox builds are not benchmarks |
| Trustworthy chat UX | Markdown headings, emphasis, inline code, fenced code blocks, profiles, and system-prompt templates | Rendering is intentionally lightweight and not a full CommonMark engine |
| Privacy controls | Incognito mode skips Room writes for new turns; Keep/7/30/90-day retention cleanup is scoped to conversations | Existing history remains until explicitly deleted; models/documents are unaffected by retention |
| Export and portability | Explicit Markdown/JSON choice; Markdown includes metrics; JSON preserves role/text/metrics structure | Export is user-initiated through Android’s document picker |
| Model transparency | Bounded GGUF metadata reader and model-detail dialog | Missing metadata is shown as unavailable; no metadata is guessed |
| Context awareness | Header shows an estimated token count and parsed model context limit when available | Estimate is whitespace-based and is not a tokenizer measurement |
| Discovery and recovery | Existing Hugging Face catalog and download state machine remain intact | Gated repositories still require user-mediated access; no hidden credentials |

Verification for this evidence pass completed with the debug Kotlin compile, unit tests, debug APK assembly, release APK assembly, lint, and ARM64 native CMake build. Added unit coverage checks metric normalization and representative GGUF scalar metadata parsing. The CI Room schema guard checks the committed v6 fixture.

The current milestone remains a pre-alpha release. Dora intentionally does not claim image generation, voice, vision, cloud providers, plugins, MCP, or tool execution. The Comprehensive Development Prompt’s `minSdk 35` recommendation is not adopted: Dora retains `minSdk 26` to support Android 8+ as an explicit product constraint, and Hilt/Koin migration remains out of scope for this milestone.


## v0.11 hardening evidence — 2026-08-21

The hardening slice now refuses silent demo substitution in the user-facing native chat path, adds explicit regenerate and edit-and-regenerate actions, bounded JSON conversation import, persisted theme selection, notification permission UX, compatibility calculation details, device-local benchmark output, orphan cleanup, multi-model-safe import identities, model-operation concurrency guards, and confirmation dialogs for destructive actions. These changes improve capability honesty and recovery behavior without claiming image generation, voice, vision, cloud inference, plugins, MCP, or tool execution.


## v0.12 candidate evidence — 2026-08-21

Additional verified work includes copy/share actions for completed messages, named per-model generation profiles, Room model-manifest persistence, registry repair during reconciliation, destructive model/data confirmations, and minSdk-26-safe bounded import reading. The image-runtime research note documents an Android ARM64 build path for stable-diffusion.cpp but also records the missing cross-device embedding and acceptance evidence; Dora therefore keeps image generation gated instead of simulating it.


## v0.13 candidate evidence — 2026-08-21

Dora now supports durable pinned conversations through a Room v7 migration and pinned-first ordering, plus an installed-model filter independent of Hugging Face discovery. The v7 schema fixture is generated and CI now enforces it. Pinning is unavailable in incognito mode by design, and the local model filter reports an explicit empty state rather than hiding the absence of matches.


## v0.14 PocketPal-parity evidence — 2026-08-22

Dora adds a real Android Storage Access Framework folder importer with bounded recursive scanning, aggregate limits, private-copy storage, GGUF validation, and per-file skip reporting. It also adds first-class locally persisted Assistant Profiles with model binding, prompt and settings application, optional starter greetings, and a local profile picker. Completed assistant messages can be read aloud through the device’s Android TextToSpeech engine, with explicit device-engine wording. Finally, an opt-in deterministic local-tool engine provides `/calc`, `/count`, `/now`, and `/help` without arbitrary code execution or network access.

PocketPal parity research is stored in `DORA_POCKETPAL_PARITY_2026.md`. Neural TTS engines, arbitrary agent tools, PalsHub, external endpoints, GPU/NPU controls, and image generation remain gated because a button or general upstream runtime is not sufficient evidence of a production Dora feature.
