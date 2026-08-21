# Dora Product Roadmap

## Product principle

Dora optimizes for reliability, security, performance, user experience, and maintainability rather than feature count. Existing working functionality remains the foundation; unsupported runtime behavior is documented rather than simulated.

## Current milestone: Download Center foundation

The current engineering pass implements the core professional download flow: persistent Room job metadata, explicit download lifecycle states, WorkManager-backed background work, real progress events, moving-average speed and ETA, storage preflight, duplicate protection, pause/resume/cancel/retry actions, bounded network retry, atomic finalization, GGUF validation, llama.cpp validation, startup recovery, Download Center sections, expandable details, and storage visibility.

| Capability | Status | Evidence |
|---|---|---|
| Persistent download card | Implemented | `DoraUiState.jobs` and `DownloadProgress` are populated before WorkManager starts. |
| Explicit state machine | Implemented | `DownloadState` and tested `DownloadStateMachine`. |
| Real speed and ETA | Implemented | `DownloadTelemetry` uses rolling time samples and leaves values unavailable until measurable. |
| Room checkpoints | Implemented | JobRecord v3 stores meaningful telemetry and provenance. |
| Pause/resume/cancel | Implemented | Persistent control markers distinguish pause from cancel; Range resume remains in the downloader. |
| Retry and recovery | Implemented | WorkManager backoff plus startup requeue of interrupted states. |
| Download Center sections | Implemented | Compose sections for active, queued, paused, failed, and completed jobs. |
| Storage manager summary | Implemented | Total, available, model, temporary, and orphan counts are shown. |
| Physical-device workflow | Pending | Requires ARM64 Android device validation of network, notification, pause/resume, native loading, and offline chat. |

## Next milestone: model library depth

The next milestone should add model detail navigation, favorites, search history, installed-only and downloaded-only filters, sorting, metadata viewer fields when actually present, and compatibility explanations that account for available RAM, context, quantization, CPU/ABI, storage, and runtime overhead. These changes should remain read-only until their metadata and persistence behavior are tested.

## Following milestone: runtime session manager

Dora should move the native facade toward a `ModelSessionManager` responsible for one active model, load, unload, context lifetime, generation, cancellation, cleanup, and robust error propagation. The current mutex and single-generation gate are safety foundations, not a substitute for a full persistent session lifecycle.

## Release criteria

A beta candidate requires successful debug and release builds, JVM tests, lint, Room migration checks, a signed release artifact, ARM64 package verification, and a physical-device acceptance run covering discovery, download, background notification, pause, resume, completion stages, chat, stop generation, process restart, offline inference, deletion, and storage reconciliation. No beta claim should be made until the physical-device run is recorded.

## Current UX milestone: Explore before install

Dora now opens into the full three-tab product shell even when no verified model is installed. New users can explore Chat, Models, and Settings before downloading weights, while the empty chat state clearly explains the local model requirement and routes directly to model recommendations or local GGUF import. The chat composer and send control are disabled until a verified local text model is active, preventing a misleading demo interaction.

The Download Center now includes a curated “Start here” section with real Hugging Face repository IDs for a compact Qwen starter, SmolLM2 lightweight chat, Qwen Coder, and Phi Mini balanced chat. Each card explains its intended use and routes to a repository-specific search that displays the actual compatible files, quantizations, device-fit status, license, and provenance before download. This is inspired by mobile local-AI browsing patterns while preserving Dora’s own integrity checks and ARM64 constraints.

The curated list is deliberately not treated as a substitute for live compatibility. Repository metadata can change, and Dora always re-fetches the repository and evaluates the exact GGUF file against the current device before enabling download. Image generation, voice, vision, RAG, plugins, and remote providers remain explicit future milestones until their runtimes and acceptance tests exist.

## v0.8 local-context foundation

Dora now includes a real first local-document foundation rather than a decorative RAG switch. Users can import text, Markdown, CSV, JSON, or XML files up to 20 MB into Dora’s private app storage. Each file is hashed, chunked into bounded readable sections, indexed with normalized lexical text, persisted in Room v5, and manageable from the Chat local-context panel. Users can enable or disable document search per session, inspect size and section count, and delete a document and its chunks.

During local generation, Dora selects up to four relevant enabled sections using token overlap and passes them as an explicit context block to the existing local engine. The prompt instructs the model to say when excerpts do not answer the question and not to invent citations. This is intentionally a lexical retrieval foundation, not an embedding/vector database or a claim of production-grade semantic RAG. PDF parsing, embeddings, citations with source spans, encrypted document storage, and document-specific permissions remain follow-up milestones.
