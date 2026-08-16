# Dora Production Roadmap

**Source of truth:** `Dora_Master_Engineering_Prompt.txt`  
**Repository:** `unictoai/Dora`  
**Rule:** Preserve existing architecture unless a demonstrable technical reason requires change.

## Phase 0 — Baseline and build stabilization

The baseline is the current ARM64 pre-alpha repository. Debug assembly, unit tests, lint, and native llama.cpp compilation are available. The remaining baseline tasks are clean-clone verification, release assembly, instrumentation test setup, dependency/build artifact checks, and a reproducible record of the current support matrix.

**Exit criteria:** a clean clone can run the pinned wrapper, compile the ARM64 native target, assemble debug and release variants, run unit tests, run lint, and publish a CI artifact without undocumented local state.

## Phase 1 — Critical correctness fixes

Implement a single startup reconciliation use case that compares Room records, LocalRegistry artifacts, final model files, and partial files. Classify missing, corrupt, interrupted, orphaned, and externally deleted states. Do not silently delete user data. Add deterministic duplicate-operation rules for download, delete, retry, model load, and registry refresh.

**Exit criteria:** repeated actions are idempotent; process death and restart restore a truthful state; every corrupted or missing model has an actionable user message.

## Phase 2 — Download and model reliability

Complete the fake-server failure matrix for HTTP 200, 206, 302/307, 403, 404, 429, 500+, timeout, reset, malformed `Content-Range`, missing length, incorrect checksum, incorrect byte count, disk-full, and cancellation. Preserve HTTPS-only behavior, bounded retries, resume correctness, atomic finalization, and GGUF validation. Add cache behavior for public Hugging Face metadata and explicit offline mode states.

**Exit criteria:** every failure class has deterministic behavior and tests; no partial file becomes an installed model; a restarted worker can resume or fail safely.

## Phase 3 — Native and JNI stability

Introduce an explicit native session state machine with serialized load/unload/generate/cancel operations, validated paths and parameters, clear ownership, safe repeated cleanup, and mapped native errors. Test invalid paths, invalid GGUF, concurrent calls, cancellation during generation, deletion during load, and process recreation.

**Exit criteria:** native errors never crash the UI process in tested scenarios; a model can be loaded, generated, cancelled, unloaded, and reloaded repeatedly on the supported ARM64 device matrix.

## Phase 4 — Memory and mobile performance

Add preflight model memory estimates using model size, context, KV cache, runtime overhead, and available memory. Measure load time, first-token latency, token rate, peak Java/native memory, battery, and thermal behavior. Add conservative refusal and alternative recommendations when a model is too heavy. Avoid unnecessary byte-array copies and aggressive polling.

**Exit criteria:** device profiles show measured support classes; predictable OOM and thermal failures are prevented or explained; benchmark data is persisted without private prompt content.

## Phase 5 — UI and UX hardening

Keep the three-destination Chat, Models, and Settings structure. Add explicit Idle, Loading, Success, Error, Retrying, Cancelling, and Cancelled states for every asynchronous operation. Add retry/cancel actions, compact download details, provenance, license visibility, low-storage guidance, large-font layouts, screen-reader semantics, non-color status communication, and recovery after navigation/recreation.

**Exit criteria:** no infinite spinner, no misleading success state, no inaccessible critical action, and all core paths are covered by Compose/UI tests.

## Phase 6 — Security hardening

Add adversarial tests for URLs, redirects, repository IDs, filenames, JSON fields, native paths, malformed model metadata, temporary files, logs, and release artifacts. Verify exported components, backup exclusion, foreground service declarations, network security, no secrets, and least-privilege permissions. Document the privacy/threat model and data inventory.

**Exit criteria:** security review has no unresolved P0 findings; downloaded models and metadata cannot escape the intended app-private boundaries; logs contain no tokens or private prompts.

## Phase 7 — Testing and CI

Expand GitHub Actions to run formatting/static checks, debug and release compilation, unit tests, Room migration tests, WorkManager tests, native compilation, artifact inspection, and instrumentation tests where a device runner is available. Add failure injection and deterministic local HTTP fixtures. Keep known warnings visible and classified.

**Exit criteria:** every P0/P1 acceptance test runs automatically or is explicitly listed as a physical-device gate with owner and evidence.

## Phase 8 — Release validation

Run the complete matrix on representative ARM64 devices: install, first-run onboarding, public Hugging Face search, download, resume, checksum, native validation, offline chat, cancellation, process death, rotation, low storage, delete, reinstall, upgrade migration, and notification permission behavior. Record model revision, quantization, device profile, timings, memory, thermal, and battery observations.

**Exit criteria:** the release checklist is complete, documentation matches the actual binary, the APK checksum is published, and the support matrix names what Dora supports and what it does not.

## Current milestone status

| Area | Status |
|---|---|
| Existing architecture preservation | In place |
| ARM64 llama.cpp build | Verified in sandbox/CI build |
| Hugging Face public metadata | Smoke-tested |
| Redirect/range/checksum downloader | Implemented and compiled |
| WorkManager foreground download | Implemented and compiled |
| Room job/provenance persistence | Implemented; reconciliation tests pending |
| Real-device local inference | Physical-device gate pending |
| Image generation | Not shipped; isolated milestone pending |
| Release readiness | Pre-alpha only |

## Change-management rule

For every major change, record the problem, root cause, implementation, compile result, relevant tests, behavior inspection, and remaining limitation. Do not hide failures or widen claims beyond observed evidence.
