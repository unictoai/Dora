# Dora Production Capability Architecture and 1000+ Feature Roadmap

## Product standard

Dora should be treated as a local-AI operating environment for Android, not as a collection of screens. Every roadmap item must belong to a capability group, have an observable user outcome, preserve the offline-first promise where applicable, and include a test or verification path. A feature is not considered shipped merely because a button exists; the associated runtime, persistence, failure path, accessibility behavior, and documentation must work.

The roadmap below contains **1,080 feature slots** across 27 capability groups. The groups are intentionally broad enough to support long-term product planning while keeping the first production milestones narrow. The first priority remains repairing and proving model installation, then proving real local chat on physical ARM64 devices. Image generation, multimodal inference, synchronization, and advanced automation remain gated behind runtime evidence.

## Priority model

| Priority | Meaning | Release rule |
|---|---|---|
| P0 | Release blocker or trust boundary | Required before beta claims |
| P1 | Core product value | Required for the first serious public release |
| P2 | Strong differentiator | Add after P0/P1 stability |
| P3 | Expansion | Add only after device and support costs are understood |

## Capability budget

| Group | Feature slots | Primary priority | Product outcome |
|---|---:|---|---|
| Model discovery and Hugging Face | 60 | P0/P1 | Find and acquire compatible public models reliably |
| Model download reliability | 50 | P0 | Complete, resume, verify, and recover large downloads |
| Model catalog and provenance | 40 | P0/P1 | Know exactly what is installed and under which license |
| Device profiling and recommendations | 40 | P0/P1 | Recommend models based on measured constraints |
| Native text runtime | 60 | P0/P1 | Generate locally with real GGUF models |
| Chat fundamentals | 60 | P0/P1 | Make local chat useful and predictable |
| Chat quality and controls | 40 | P1/P2 | Improve generation quality without hiding trade-offs |
| Conversations and persistence | 50 | P0/P1 | Preserve user work safely on device |
| Privacy and security | 50 | P0 | Make the privacy contract auditable |
| Storage and lifecycle | 40 | P0/P1 | Keep models and app state recoverable and manageable |
| Background work and notifications | 40 | P0/P1 | Handle long operations transparently |
| Image runtime | 60 | P2 | Add real local image generation only after backend proof |
| Multimodal input and output | 40 | P2 | Support images, audio, and documents locally |
| Voice input and output | 40 | P2 | Add private on-device speech interaction |
| Documents and retrieval | 50 | P1/P2 | Ask questions over user-selected local files |
| Prompt and workflow tools | 40 | P1/P2 | Turn chat into reusable local workflows |
| Export and interoperability | 30 | P1 | Let users own and move their data |
| Accessibility and internationalization | 40 | P0/P1 | Make Dora usable across abilities and languages |
| UI system and design quality | 40 | P0/P1 | Keep the product simple, calm, and coherent |
| Performance and thermals | 40 | P0/P1 | Prevent memory, heat, and battery surprises |
| Reliability and diagnostics | 40 | P0 | Explain and recover from failures |
| Testing and release engineering | 50 | P0 | Make builds and releases reproducible |
| Account-free collaboration options | 20 | P3 | Optional local sharing without cloud dependency |
| Optional cloud connectors | 20 | P3 | Explicit opt-in integrations without changing local defaults |
| Developer and extension SDK | 30 | P3 | Let trusted developers add runtimes safely |
| Product analytics without surveillance | 20 | P3 | Local diagnostics and consent-based telemetry only |
| Governance and community operations | 20 | P1/P3 | Maintain models, licenses, support, and trust |
| **Total** | **1,080** |  |  |

## P0 implementation sequence

### Model installation trust boundary

The next release must make model installation a first-class state machine: `discovered`, `metadata verified`, `queued`, `downloading`, `paused`, `resuming`, `checksum verified`, `GGUF verified`, `native-load verified`, `installed`, `failed`, and `deleted`. Each state requires a visible user explanation and a recoverable action. Downloads must never silently turn a partial or unvalidated file into an installed model.

The Hugging Face path must support revision-pinned URLs, redirect-safe HTTPS, range requests, CDN/Xet behavior, expected byte counts, SHA-256 when available, GGUF magic validation, native llama.cpp validation, storage preflight, foreground notifications, Room job persistence, cancellation, retry, and restart recovery. Public and gated repositories must be visually distinct, and Dora must never ask the user for a token inside an untrusted text field.

### First physical-device gate

Before adding broad features, the team must run a small public GGUF through the entire flow on representative ARM64 devices. The test record must include model size, exact revision, download duration, resume behavior, peak memory, load duration, first-token latency, sustained token rate, battery drain, temperature trend, cancellation, process death recovery, and airplane-mode chat behavior. A successful desktop build does not satisfy this gate.

### Core local chat

The chat runtime must bind to the validated installed artifact rather than a demo fallback. Dora must expose model load progress, context limits, generation controls, stop behavior, error messages, and a safe recovery path. A demo adapter may remain in developer builds, but the public pre-alpha must clearly distinguish demo output from native output and should not silently substitute one for the other.

## P1 capability groups

The first serious product should add persistent conversations, message editing, regeneration, export, model switching, prompt templates, document selection, basic local retrieval, reliable delete-and-reclaim storage, accessibility labels, large-font layouts, dark mode validation, instrumentation tests, crash-safe migrations, and a public support matrix. The UI should remain centered on three primary destinations: Chat, Models, and Settings.

A useful model catalog should present a small number of high-confidence recommendations rather than an uncontrolled marketplace. Recommendations should be derived from the device profile, model bytes, quantization, context requirements, license visibility, and native-runtime support. Popularity may be a secondary signal, never the primary compatibility decision.

## P2 and P3 expansion

Image generation should use a separately isolated native runtime until its dependency graph and memory footprint are proven compatible with llama.cpp on Android. Multimodal features should be added only when a model family, tokenizer, preprocessing path, and device benchmark are available. Voice, document retrieval, extensions, synchronization, and optional connectors must all preserve explicit local defaults and clear user consent.

The 1,080-slot budget is deliberately not a promise to ship 1,080 controls in one release. It is a controlled product inventory. Dora should ship in narrow, reliable slices, with each slice tied to measurable runtime evidence and a release gate.

## Immediate backlog

| Order | Deliverable | Acceptance test |
|---:|---|---|
| 1 | Ship the repaired foreground Hugging Face downloader | Public small GGUF downloads, resumes, verifies, and survives process recreation on ARM64 hardware |
| 2 | Add a download detail view | User can see source repository, revision, file, size, license, checksum, progress, and error reason |
| 3 | Add a model-fit explanation screen | Recommendation shows the RAM/storage/ABI calculations behind its label |
| 4 | Add native model-load benchmark | User can run a local benchmark and save results without network access |
| 5 | Add persistent conversation storage | Force-stop and relaunch preserve conversations and active model metadata |
| 6 | Add instrumentation coverage | Model onboarding, download failure, cancellation, resume, and delete flows are tested |
| 7 | Complete the physical-device release gate | Two or more representative ARM64 devices pass the beta checklist |
| 8 | Revisit image generation | Choose one runtime only after isolated build and device memory evidence |

## Definition of done

Dora is ready to leave pre-alpha only when a fresh user can install the APK, discover a public compatible model, understand its license and resource requirements, download it over an unreliable connection, resume it after interruption, pass checksum and native validation, start a real offline conversation, stop generation safely, relaunch without losing the model, delete the model and reclaim storage, and understand every limitation that remains.
