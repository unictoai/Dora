# Dora Product Requirements and UX Specification

**Document status:** Execution-ready v0.1  
**Observation basis:** Public repository and store evidence reviewed on 2026-08-14  
**Product:** Dora, Android-first local AI studio

## Product thesis

Dora should be the **simplest trustworthy local AI studio for Android**. The product is not differentiated by claiming that it is merely offline: PocketPal already communicates local inference, Hugging Face model downloads, benchmarking, adjustable parameters, and zero-telemetry positioning at significant scale.[1] [2] Dora must instead make the difficult parts of local AI understandable and dependable: model selection, compatibility, storage, memory, thermal behavior, cancellation, deletion, and the separation between local inference and optional model downloads.

Dora v1 will support two local capabilities: **text generation** and **image generation**. It will not include cloud fallback, accounts, ads, telemetry, agents, MCP, video, music, or a server dependency. Broader all-in-one projects such as LLM-Hub show the appeal of multiple modalities, but they also demonstrate why Dora needs a narrow first release.[3]

> **Core promise:** “Dora tells you what your phone can run, helps you install it safely, and keeps generation on your device.”

## Target users

| Persona | Need | Main anxiety | Dora’s response |
|---|---|---|---|
| Privacy-conscious beginner | Chat privately without learning model formats or native runtimes | “Will my prompts leave my phone, and will this model work?” | Offline-first status, a visible Privacy Center, guided model-fit explanations, and safe defaults |
| Technical local-AI user | Import GGUF or image models, tune parameters, keep storage under control | “Will the app duplicate multi-gigabyte files or hide runtime limitations?” | Managed import and external-file reference modes, full manifest metadata, checksums, advanced settings, and exportable diagnostics |
| Local creator | Generate images without cloud limits or subscription fees | “Will image generation exhaust memory, heat the phone, or fail after a long wait?” | Conservative presets, measured memory estimates, progress/cancel, thermal warnings, and a local gallery |

## Product principles

Dora’s behavior should follow five principles. **Truth before convenience** means a downloaded file is not treated as a valid model until it is parsed, hashed, and checked against the device capability profile. **Offline means observable** means the app exposes what it stores and whether the current action needs network access. **Progressive disclosure** means beginners see safe presets while technical controls remain available. **One job at a time** means text and image generation share resource scheduling and cannot silently compete for memory. **Deletion is a feature** means removing a chat, model, image, or temporary artifact has a clear and testable effect.

These principles are informed by existing projects. SmolChat uses llama.cpp with Android NDK and JNI bindings, while ChatterUI demonstrates the value of both copying models into app storage and referencing external model files to avoid duplication.[4] [5] OfflineLLM shows that privacy can be implemented through zero network permissions, encrypted settings, biometric lock, secure deletion, and explicit import verification.[6]

## v1 capability and support matrix

The matrix intentionally distinguishes **supported**, **experimental**, and **not supported**. Dora should never advertise “any model” until a model family has passed conformance and device tests.

| Capability | v1 decision | Support status | Acceptance condition |
|---|---|---|---|
| Android platform | Native Kotlin/Jetpack Compose; ARM64-first | Supported | Reproducible build and lifecycle tests pass on selected Android versions |
| Minimum Android version | Set only after feasibility spike; do not copy a competitor’s minimum | Pending | Model load, file access, notifications, and background behavior pass on the chosen minimum |
| CPU text inference | llama.cpp through a JNI/NDK adapter | Supported first | GGUF model loads, streams, cancels, unloads, and survives lifecycle tests |
| Vulkan text acceleration | Optional backend if reliable on the support matrix | Experimental initially | Benchmark demonstrates an improvement or acceptable trade-off with CPU fallback |
| MLC text backend | Adapter spike only | Experimental / deferred | It materially improves supported-device performance without unacceptable packaging friction |
| GGUF model import | File picker, checksum, manifest parse, compatibility validation | Supported | Invalid/truncated/unsupported models are rejected with actionable errors |
| External text model reference | Read from user-selected external storage without copying | Advanced supported | Permissions and missing-file behavior are reliable across restart and upgrade |
| Hugging Face catalog download | Curated metadata and explicit user-triggered download | Supported with network | Source, license, size, hash, and model compatibility are displayed before download |
| Text chat | Streaming, stop, regenerate, edit/resubmit, persistence, export | Supported | All core actions work after restart and with airplane mode enabled |
| Text context controls | Context length, temperature, max tokens, system prompt, seed where supported | Supported progressively | Settings are validated against model metadata and memory budget |
| Local text-to-image | One validated pipeline only | Supported after image spike | A selected model generates and cancels reliably on benchmark devices |
| Image model formats | Runtime-specific; do not promise safetensors/ckpt/ONNX universally | Limited / explicit | Each supported format has a parser, hash check, and tested model manifest |
| Image controls | Prompt, negative prompt where supported, size presets, steps, seed, guidance/quality | Supported progressively | Controls map to the chosen runtime without silent parameter loss |
| Image gallery | Local history, save/share, delete, intermediate cleanup | Supported | Generated files and thumbnails follow deletion policy and never leave the device implicitly |
| Image acceleration | Runtime/device-specific CPU, Vulkan, OpenCL, or other backend | Experimental | Only advertise paths verified by device benchmark; always show fallback state |
| Cloud providers/accounts | None in v1 | Not supported | UI and binary contain no required remote inference path |
| Agents/MCP/video/music/audio | None in v1 | Not supported | Deferred proposals only; no hidden scope expansion |

The image decision is deliberately conservative. Local-Diffusion demonstrates that broad image-model coverage is possible but also documents meaningful peak-memory differences by model, quantization, and resolution, as well as GPU paths that may be slower than CPU on some devices.[7] Dora should begin with a small, verified pipeline and measured presets rather than a large compatibility claim.

## Functional requirements

### Model management

Dora shall present every recommended model as a **model card** containing name, publisher, model family, task, runtime, file format, quantization, parameter count when available, context length when available, approximate download size, estimated peak memory, license, source URL, checksum, and verification date. The card shall state whether the model is suitable for the current device using one of three states: **Recommended**, **Possible with trade-offs**, or **Not recommended**.

Dora shall support a user-initiated download flow with pause, resume, cancellation, retry, checksum verification, temporary-file isolation, and final atomic move. A failed or canceled download must not appear as installed. A user-imported model shall be copied or referenced only after explicit choice. Deleting a managed model shall remove its files and metadata; deleting an external reference shall remove only Dora’s reference.

### Text chat

Dora shall allow a user to choose an installed text model, create a conversation, enter a prompt, see streaming output, stop generation, regenerate the last answer, edit a prior user message, copy/share a response, and reopen the conversation after app restart. The user shall be able to change system prompt, temperature, context length, and maximum output within the model/device budget.

A chat must show the active model and whether the response is being generated locally. If generation fails, Dora shall report a reason category such as insufficient memory, unsupported template, unloaded model, canceled job, or native runtime error, and provide a next action.

### Image Studio

Dora shall allow a user to select an installed image model, enter a prompt, optionally enter a negative prompt if supported, choose from safe size presets, set quality/steps within validated bounds, submit a generation job, view progress, cancel the job, save the result, share it through Android’s share sheet, and delete it. The screen shall display an estimated storage/memory cost before starting a large job.

The UI must make image generation’s uncertainty visible. If a runtime does not support a control, Dora shall disable or hide it rather than pretending it has an effect. If a generation is CPU-only or has entered fallback mode, the job surface shall state that fact.

### Jobs and resource control

All downloads and generation requests shall be represented as jobs with state, progress, start time, cancel action, error state, retry action where safe, and artifact references. Text and image generation shall be mutually aware of model residency and available memory. Dora shall prevent concurrent jobs when the runtime/device profile indicates that doing so is unsafe.

A job may continue when the app is backgrounded only if the Android lifecycle and resource policy support it. Otherwise Dora shall pause or cancel deterministically and preserve a user-readable state. No job may be left “running” after process death without a recoverable record.

### Privacy and deletion

The Privacy Center shall explain that inference is local, list the local data categories stored by Dora, show the last network operation and its purpose when applicable, and provide separate actions to delete conversations, model files, generated images, temporary files, and all local data. The application shall not include analytics or required cloud services in v1.

Dora shall separate **model acquisition network access** from **inference** in its UX. Before a download starts, the user must see that network access is being used and why. Once a model is installed, airplane-mode inference must work. The privacy claim should be backed by manifest/dependency inspection and repeatable tests, not only by copy.

## Screen and navigation specification

### First-run flow

1. **Welcome:** Dora explains local text and image generation in one paragraph and states that models are downloaded separately from the app.
2. **Privacy choice:** The user sees offline-only behavior, local storage, and the optional nature of model downloads. No account is requested.
3. **Device check:** Dora reports RAM, storage, ABI, Android version, and detected acceleration paths without pretending these guarantee performance.
4. **Choose a starting path:** “Try a small text model,” “Explore image generation,” or “Import a model I already have.”
5. **Model-fit education:** Dora recommends one small verified model and explains size, memory, expected latency category, and license before installation.
6. **First success:** After the first successful text response or image, Dora explains where the result is stored and how to delete it.

### Primary screens

| Screen | Primary purpose | Required states |
|---|---|---|
| Home | Resume chats, see installed models, start text or image generation | Empty, ready, model missing, job running, low storage |
| Chat | Local text conversation | No model, loading, streaming, stopped, completed, failed, offline |
| Image Studio | Local image generation | No model, ready, validating, queued, generating, canceled, completed, failed |
| Models | Discover, import, validate, install, unload, delete | Catalog unavailable, download, checksum failure, incompatible, installed, external reference |
| Jobs | Show downloads and generation work | Queued, active, paused, canceled, failed, completed, orphan recovered |
| Gallery | Browse generated images and metadata | Empty, loading, image detail, share, delete, missing artifact |
| Settings / Privacy | Configure behavior and audit local data | Offline mode, storage, performance, security, licenses, delete all |

### Chat interaction rules

The composer shall show the current model and a compact device-fit status. During generation, the send action changes to **Stop** and must be responsive. Streaming output must remain readable and should not auto-scroll if the user has intentionally scrolled upward. A partial response must either be saved as partial with an explicit label or discarded by an explicit user action; it must not silently vanish.

### Image interaction rules

The Image Studio shall start with safe presets such as “Fast draft” and “Balanced.” Advanced controls are behind an expandable section. Before generation, Dora shall show a concise estimate such as “Large job: may use significant memory and take several minutes on this device,” using measured categories rather than invented precision. The result page shall expose prompt, seed, model, and generation settings in metadata where available.

## Accessibility and content requirements

All core actions must have visible labels or content descriptions, support TalkBack, respect scalable text, maintain sufficient contrast, and use text or icons in addition to color for job states. Error messages must explain what happened, why it matters, and what the user can do next. Technical details such as quantization and context length should have plain-language explanations.

Dora must avoid absolute claims such as “any model,” “instant,” “works on every Android phone,” or “completely safe.” The approved product language should say **verified**, **estimated**, **device-dependent**, and **offline after installation** where those are the truthful conditions.

## Definition of done for v1

| Requirement | Pass condition |
|---|---|
| Onboarding | A new user can identify a compatible starter model and understand why it is recommended |
| Model lifecycle | Download/import, validation, pause/resume, deletion, and external-reference behavior pass restart and failure tests |
| Text generation | A verified GGUF model streams, stops, persists, exports, and works in airplane mode |
| Image generation | One verified image pipeline completes, reports progress, cancels, saves, and deletes locally |
| Privacy | No prompt, response, image, or model path is sent to a server during offline inference; required permissions and dependencies are documented |
| Resource safety | Dora warns or blocks unsafe jobs based on the device profile and does not leave orphaned native jobs/files |
| Licenses | App, native runtime, dependency, and recommended-model licenses are separately documented |
| Quality | CI, unit tests, instrumentation tests, and a reproducible debug APK pass on the locked support matrix |

## References

[1]: https://play.google.com/store/apps/details?id=com.pocketpalai "PocketPal AI on Google Play"
[2]: https://github.com/a-ghorbani/pocketpal-ai "PocketPal AI GitHub repository"
[3]: https://github.com/timmyy123/LLM-Hub "LLM-Hub GitHub repository"
[4]: https://github.com/shubham0204/SmolChat-Android "SmolChat Android GitHub repository"
[5]: https://github.com/Vali-98/ChatterUI "ChatterUI GitHub repository"
[6]: https://github.com/jegly/OfflineLLM "OfflineLLM GitHub repository"
[7]: https://github.com/rmatif/Local-Diffusion "Local-Diffusion GitHub repository"
