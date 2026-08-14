# Dora Runtime Feasibility and Benchmark Specification

**Document status:** Execution-ready v0.1  
**Purpose:** Decide the first Android text and image runtimes using repeatable real-device evidence

## Decision to be made

Dora should begin with a native Kotlin/Compose shell and isolate inference behind two interfaces: `TextInferenceEngine` and `ImageInferenceEngine`. The first text candidate is llama.cpp with GGUF because the official project documents Android NDK builds, Android Studio bindings, runtime CPU-feature detection, and Vulkan support.[1] MLC LLM remains a comparison candidate because it provides compiled deployment and Android OpenCL paths for Adreno and Mali GPUs, but it may require more specialized packaging.[2]

For image generation, Dora should compare a small, validated diffusion pipeline using ONNX Runtime Mobile against a second viable native backend such as stable-diffusion.cpp or the current Android-native diffusion work used by an adjacent project. ONNX Runtime’s official Android material confirms that mobile deployment depends on model export/conversion, Android NDK packaging, device installation, and potentially custom reduced-operator builds.[3] Local-Diffusion provides practical evidence that stable-diffusion.cpp can support broad model families, quantization, memory optimizations, and multiple accelerator paths, but also shows large memory differences and cases where Vulkan is slower than CPU.[4]

No device benchmark has been executed in the sandbox. The following protocol is the required feasibility work before Dora makes public support claims.

## Benchmark device matrix

The exact physical devices must be recorded by model, Android build, SoC, RAM, storage free space, GPU, thermal state, and battery percentage. The matrix should contain at least one device in each tier.

| Tier | Required profile | Purpose | Minimum test condition |
|---|---|---|---|
| Low-memory | ARM64 Android phone with the lowest supported RAM target | Determine whether Dora can provide a useful small text experience and safely reject image jobs | At least 8 GB free storage and a cool device before each run |
| Mid-range | Recent ARM64 phone with mainstream Snapdragon or Exynos class SoC | Define the likely target experience and default model/image presets | Same OS build and battery protocol across repeated runs |
| Flagship | Current high-end ARM64 phone with capable GPU/NPU path | Measure best-case acceleration and thermal sustain | Test CPU and accelerator paths separately |
| Optional x86/ChromeOS | Android/ChromeOS x86-64 device if available | Validate whether the packaging/runtime abstraction accidentally assumes ARM-only | Separate artifact/ABI and CPU-feature results |

A benchmark run must start from a recorded baseline. The device should be rebooted or allowed to cool between thermal sessions, background applications should be minimized, battery percentage and charging state must be recorded, and the same model file and prompt/image parameters must be reused for comparable runs.

## Text-runtime experiments

### Candidates

| Candidate | Model/input contract | Native integration | Primary question |
|---|---|---|---|
| llama.cpp | GGUF text model | Android NDK/CMake plus JNI or official Android binding pattern | Can Dora provide flexible model import, stable streaming, and acceptable performance across the support matrix? |
| MLC LLM | Compiled/packaged model artifacts | MLC Android engine and its compiler/model packaging path | Does compiled acceleration materially improve sustained performance enough to justify reduced model flexibility and build complexity? |

### Required text test set

Use one small instruct model that is likely to fit on the low-memory device, one mid-size instruct model for mid-range devices, and one model that intentionally exceeds the low-memory budget. The exact model IDs must be chosen after checking current model licenses and verified GGUF/MLC availability. Do not commit model weights to the Dora repository.

Use three fixed prompts: a short factual answer, a 512-token structured answer, and a multi-turn conversation with a long context. Record the exact prompt text, system prompt, context length, temperature, max output tokens, quantization, model hash, runtime revision, compiler flags, and device metadata.

### Text measurements

| Metric | Method | Required interpretation |
|---|---|---|
| Install size | Measure APK/AAB native libraries and model storage separately | Prevent model weights from being confused with app size |
| Model load time | Time from request to first successful inference-ready state | Include cold file access and warm reload separately |
| First-token latency | Time from submit to first emitted token | Directly affects perceived responsiveness |
| Decode throughput | Sustained tokens/second after warm-up | Report median and p95 across repeated runs, not one best run |
| Prompt throughput | Time to process short and long contexts | Detect context-scaling and KV-cache problems |
| Peak memory | Android memory/profiling measurement during load and generation | Used for fit classification and concurrency policy |
| Cancellation latency | Time from Stop to native generation actually stopping | Must be bounded and leak-free |
| Lifecycle recovery | Background, rotate, process kill, reopen | Detect native resource and job-state bugs |
| Thermal sustain | Throughput and temperature trend over a long repeated session | Identify throttling and unsafe defaults |
| Battery impact | Battery percentage/current proxy over a fixed workload | Compare runtime modes under equivalent output |
| Failure rate | Repeat identical jobs and categorize errors | Establish reliability before optimizing speed |

Run each supported candidate at least five times per device after one warm-up run. Report median, p95, and failure count. If the device is not instrumentable with sufficient precision, document the measurement limitation rather than inventing a value.

### Text decision gates

Select llama.cpp as the v1 text backend if it loads the chosen starter models reliably, supports streaming and cancellation, and meets the minimum responsiveness/resource thresholds on the low and mid-range tiers. Add Vulkan only when it has a reliable fallback and does not increase crashes or memory failures beyond the agreed threshold. Add MLC to v1 only if it provides a substantial, repeatable advantage on at least the mid-range and flagship tiers while preserving an acceptable model-distribution story.

Dora must reject or classify a text model as **Not recommended** when it exceeds the tested memory budget, requires an unsupported architecture/template, fails checksum or metadata validation, or produces repeated native failures. “GGUF compatible” must mean “validated by Dora’s parser and conformance suite,” not merely “file extension ends in `.gguf`.”

## Image-runtime experiments

### Candidates

| Candidate | Model/input contract | Primary question |
|---|---|---|
| ONNX Runtime Mobile | Exported ONNX/ORT graph and runtime-specific operator set | Can Dora package a predictable Android image pipeline with acceptable size and memory? |
| stable-diffusion.cpp or validated native diffusion backend | Runtime-specific diffusion model files and quantization | Does a native diffusion runtime provide better model coverage or performance at manageable packaging/maintenance cost? |

The first image test should use a small, legally distributable, documented model family. Run the same 256×256 and 512×512 text-to-image cases on the low, mid-range, and flagship devices. If 512×512 is unsafe on the low tier, that is a product result: the UI must recommend a smaller preset or reject the job.

### Image measurements

| Metric | Method | Required interpretation |
|---|---|---|
| Model footprint | Measure each model file, converted artifact, and extracted runtime assets | Drives download/storage warnings |
| Validation time | Time manifest/checksum/runtime compatibility checks | Must be acceptable before generation begins |
| First-image latency | Time from submit to saved output | Report separately from model-load time |
| Step time | Measure seconds per diffusion step after warm-up | Helps create user-facing duration categories |
| Peak memory | Measure peak RAM during text encoder, denoiser, and VAE stages | Must be stage-aware; the maximum stage governs fit |
| Resolution scaling | Compare 256×256, 512×512, and supported larger sizes | Detect nonlinear memory/time growth |
| Quantization effect | Compare only supported quantization variants | Do not assume lower precision always improves quality or speed |
| Accelerator effect | Compare CPU, Vulkan/OpenCL/other path when available | Record fallbacks and failed loads separately |
| Cancellation | Cancel at early, middle, and late generation points | Must remove or retain intermediates deterministically |
| Thermal sustain | Repeat several image jobs with cool-down protocol | Establish safe queue/concurrency policy |
| Output integrity | Verify image can be decoded and metadata/history is consistent | Prevent partial/corrupt gallery artifacts |

### Image decision gates

Choose the first image backend only if it can validate one model family, generate at least the conservative preset reliably on the low and mid-range tiers, cancel without native leaks, and expose progress that users can understand. The flagship tier may receive a higher-resolution preset, but the low tier must never be silently pushed into an unsafe workload.

The image feature should be marked **Experimental** if it passes the functional tests but exhibits unstable accelerator behavior, high variance, or thermal throttling. Dora should not advertise broad SDXL/FLUX/ControlNet/LoRA coverage in v1 merely because an adjacent project lists those features; each additional capability changes model assets, memory, UI, licensing, and test burden.[4]

## Prototype structure

Build the feasibility prototype as a disposable native Android project with no final Dora UI dependency. It should contain:

| Component | Responsibility |
|---|---|
| `benchmark-core` | Shared test definitions, prompt/image parameters, run IDs, and result schema |
| `text-llama` | llama.cpp loading, streaming, cancellation, unload, and metrics events |
| `text-mlc` | MLC model loading and inference adapter, if the toolchain can be built reproducibly |
| `image-onnx` | ONNX/ORT model loading and image pipeline adapter |
| `image-native` | stable-diffusion.cpp or other selected native diffusion candidate |
| `device-profiler` | RAM, ABI, CPU features, GPU/accelerator identifiers, storage, battery, thermal state |
| `result-export` | JSON/CSV export with no prompt or generated-image content unless explicitly enabled for local analysis |

The result schema must include a run ID, timestamp, device fingerprint hash or anonymized device label, Android version, ABI, SoC/GPU label, runtime name and revision, model ID and SHA-256, model format/quantization, parameters, cold/warm state, metric values, error category, and notes. Raw prompts and generated images should be excluded by default; if content is needed for quality review, store it locally and mark it as an explicit opt-in artifact.

## Compatibility and conformance tests

Every model candidate must pass parser and lifecycle tests before performance benchmarking. For text, these tests include file readability, GGUF metadata parsing, supported architecture/template detection, token streaming, stop sequence behavior, context limit enforcement, model unload, and reload after process recreation. For image, tests include model-file completeness, graph/operator compatibility, tokenizer/text-encoder loading, VAE decode, output image validation, cancellation, and cleanup of intermediate artifacts.

The conformance suite must include intentionally broken inputs: truncated files, invalid hashes, unsupported quantizations, missing companion files, wrong model family, insufficient storage, and insufficient memory. A failure must return a stable Dora error category so the product UI can explain it consistently.

## Initial support-matrix template

The following table is a blank result template; it is not a claim that these devices or runtimes have passed.

| Device label | Android/API | RAM | SoC/GPU | ABI | Text CPU | Text Vulkan/MLC | Image backend | Max verified image preset | Notes |
|---|---:|---:|---|---|---|---|---|---|---|
| Low-tier device A | To measure | To measure | To measure | arm64-v8a | Pending | Pending | Pending | Pending | Pending |
| Mid-tier device B | To measure | To measure | To measure | arm64-v8a | Pending | Pending | Pending | Pending | Pending |
| Flagship device C | To measure | To measure | To measure | arm64-v8a | Pending | Pending | Pending | Pending | Pending |

## Required outputs from the feasibility spike

The spike is complete only when it produces a versioned benchmark report, raw JSON/CSV results, build instructions, runtime revisions, model provenance/license records, a locked v1 support matrix, a list of rejected configurations, and an architecture decision record explaining why each first backend was selected or deferred. The report must distinguish **measured results**, **vendor/project documentation**, and **product assumptions**.

## References

[1]: https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md "llama.cpp Android documentation"
[2]: https://github.com/mlc-ai/mlc-llm "MLC LLM GitHub repository"
[3]: https://onnxruntime.ai/docs/tutorials/mobile/deploy-android.html "ONNX Runtime Android deployment tutorial"
[4]: https://github.com/rmatif/Local-Diffusion "Local-Diffusion GitHub repository"
