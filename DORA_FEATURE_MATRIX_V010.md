# Dora Comprehensive Feature Matrix — v0.10.0-prealpha

**Author:** Manus AI  
**Repository:** `unictoai/Dora`  
**Purpose:** Convert the long-term 1,080-slot capability inventory into a truthful implementation sequence. A roadmap slot is not considered shipped because a screen or button exists; it requires a working runtime or persistence path, a visible failure state, verification coverage, and documentation.

## Product interpretation

Dora is being developed as a privacy-first Android local-AI application rather than a checklist of simulated integrations. The current product promise is deliberately narrow: **verified GGUF text models can run locally on ARM64 Android devices through llama.cpp**, with explicit Hugging Face discovery/download, durable local chat, private document context, diagnostics, and user-owned exports. Image generation, speech, vision, cloud inference, plugins, MCP, tool execution, and synchronization are not claimed until real Android-compatible runtimes and acceptance tests exist.

The 1,080-slot inventory remains a planning system, not a promise to deliver 1,080 controls in one APK. The order below prioritizes trust boundaries and user value: model installation, real local inference, recoverability, privacy, chat productivity, device verification, and only then additional runtimes or extension ecosystems.

## Capability status matrix

| Capability group | Current v0.10 status | Evidence already in the repository | Next acceptance gate |
|---|---|---|---|
| Model discovery and Hugging Face | **Implemented, needs expansion** | Public GGUF search, cached catalog, curated suggestions, gating/error states, device-fit ranking | Fixture-driven API failures, offline cache behavior, and physical-device download flow |
| Model download reliability | **Implemented, device gate open** | WorkManager state machine, HTTPS redirects, ranges, resume, checksum, GGUF/native validation, pause/resume/retry/recovery | Android integration tests for interruption, disk exhaustion, process death, and notifications |
| Model catalog and provenance | **Implemented, needs richer lifecycle** | Repository, revision, filename, license, checksum, source metadata, model details | Provenance persistence migration tests and safe duplicate/deduplication behavior |
| Device profiling and recommendations | **Partial** | ARM64 ABI, RAM, storage, headroom, conservative file-fit labels | Context/KV-cache estimates, thermal profile, and measured support matrix on real devices |
| Native text runtime | **Implemented foundation, physical verification pending** | Pinned llama.cpp C++17 JNI library, serialized inference, cancellation, sampler controls, validation | Real GGUF load and offline generation on representative ARM64 phones |
| Chat fundamentals | **Implemented** | Compose chat, streaming surface, model readiness, stop behavior, multiple conversations, automatic titles | Instrumentation coverage for rotation, process recreation, error recovery, and large text |
| Chat quality and controls | **Implemented baseline** | System prompt, max tokens, threads, temperature, top-k/top-p, profiles, prompt templates, Markdown/code | Tokenizer-aware context accounting and device benchmark feedback |
| Conversations and persistence | **Implemented baseline** | Room-backed conversations/messages, rename/delete/switch, retention, JSON/Markdown export | Edit/regenerate/message-level actions and exhaustive migration tests |
| Privacy and security | **Implemented baseline, hardening pending** | Offline-only default, app-private files, incognito mode, retention, explicit delete, no cloud fallback | Keystore-backed encryption decision, adversarial path/metadata fixtures, backup audit |
| Storage and lifecycle | **Implemented baseline** | Atomic model finalization, `.part` handling, orphan reporting, delete controls, storage preflight | Reclaim verification, concurrent delete/import tests, and user-confirmed cleanup tooling |
| Background work and notifications | **Implemented baseline** | Durable Room jobs, WorkManager, foreground download flow, progress/ETA/error states | Notification permission rationale and Android lifecycle instrumentation |
| Image runtime | **Not shipped** | Explicit failing/isolated adapter; no false success path | Select one real backend, validate model format, memory footprint, output persistence, and ARM64 device support |
| Multimodal input and output | **Not shipped** | Text/document boundary is explicit | Real model family, preprocessing, memory budget, and offline acceptance tests |
| Voice input and output | **Not shipped** | No simulated voice buttons or cloud fallback | On-device speech runtime, permissions, latency, privacy, and language support matrix |
| Documents and retrieval | **Implemented lexical baseline** | Text/Markdown/CSV/JSON/XML import, SHA-256, bounded chunking, lexical retrieval, delete/enable controls | Optional semantic retrieval only after embedding runtime, citations, and deletion tests |
| Prompt and workflow tools | **Implemented baseline** | Prompt templates and generation profiles persisted per conversation | Reusable user-authored templates, import/export, and workflow execution only with a trusted local runtime |
| Export and interoperability | **Implemented baseline** | Explicit Markdown/JSON export with metrics and model label | Import format, attachment policy, schema versioning, and round-trip tests |
| Accessibility and internationalization | **Partial** | Content descriptions, semantics for progress, simple Compose layout, text overflow handling | TalkBack traversal, large-font/narrow-width tests, pluralization, localization, contrast review |
| UI system and design quality | **Implemented baseline** | Calm three-destination Compose UI, explore-first onboarding, explicit empty/loading/error states | Screenshot/instrumentation review across API levels, dark theme, landscape, and large text |
| Performance and thermals | **Partial** | Bounded downloads, serialized inference, real per-response telemetry, storage preflight | Device benchmark protocol with peak memory, token rate, temperature, battery, and cancellation |
| Reliability and diagnostics | **Implemented baseline, deeper testing pending** | Startup registry/filesystem/Room reconciliation, GGUF validation, runtime diagnostics, error classification | Failure-injection suite, crash/lifecycle tests, and actionable recovery verification |
| Testing and release engineering | **Implemented sandbox baseline** | Gradle wrapper, debug/release builds, JVM tests, lint, ARM64 artifact checks, schema guard, signed releases | Physical-device acceptance and Android instrumentation pipeline |
| Account-free collaboration | **Not shipped** | No remote sharing or account requirement | Local export/import or nearby transfer design with explicit consent and no cloud dependency |
| Optional cloud connectors | **Not shipped by design** | Offline-only default and no embedded credentials | Separate opt-in connector architecture with threat model and user-controlled secrets |
| Developer and extension SDK | **Not shipped** | No untrusted plugin execution or dynamic code loading | Sandboxed, signed extension contract and resource/security review |
| Product analytics without surveillance | **Local diagnostics only** | Runtime/device/model status shown locally; no telemetry dependency | Opt-in, local-only diagnostics export and clear redaction policy |
| Governance and community operations | **Documentation baseline** | Roadmaps, audits, licenses, release notes, support boundaries | Public support matrix, model compatibility policy, security response process, and release checklist |

## Staged implementation sequence

| Stage | Focus | Exit criteria |
|---:|---|---|
| 1 | Core hardening | Room migrations, privacy behavior, file/path security, download failure injection, recovery tests, and no stale state |
| 2 | Model lifecycle | Rich model manifests, duplicate detection, compatibility explanations, context/KV estimates, and safe cleanup |
| 3 | Chat productivity | Edit/regenerate, message actions, user-authored templates, importable conversations, and tokenizer-aware context display |
| 4 | Accessibility and operational quality | TalkBack/large-font coverage, dark theme, narrow layouts, notification rationale, diagnostics export, and support matrix |
| 5 | Physical-device gate | Real GGUF download, native load, airplane-mode chat, stop/cancel, process death, thermal/memory, battery, and storage tests |
| 6 | Runtime expansion | Only one additional runtime at a time, with real model validation, adapter isolation, memory evidence, and honest device support |
| 7 | Ecosystem features | Local sharing, connectors, SDK, or automation only after the local text product is stable and security-reviewed |

## Definition of “added”

A feature will be reported as **added** only when its implementation path is real, the user can observe loading/success/error/recovery states, data lifecycle behavior is defined, accessibility is considered, automated verification exists where feasible, and the corresponding limitation is documented. This definition intentionally prevents fake image generation, fake voice, fake vision, fake cloud providers, dead buttons, and unsupported performance claims from entering Dora.

## Current release decision

Dora v0.10.0-prealpha is a meaningful local-text product milestone, but it remains **pre-alpha**. The next highest-value work is not to add superficial breadth; it is to complete the physical-device gate and harden the core workflows above. Additional features will continue to be delivered in verified slices and published as separate releases.
