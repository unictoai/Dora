# Dora Image Runtime Research

**Research date:** 2026-08-14

## Decision basis

The selected candidate for Dora’s first local image backend is `leejet/stable-diffusion.cpp`. Its repository describes a pure C/C++ implementation based on ggml, supports safetensors and GGUF weights, exposes a C API through `include/stable-diffusion.h`, and supports CPU, Vulkan, OpenCL, Metal, CUDA, and SYCL backends.[1] The repository’s current README lists Android support through Termux and points to Local Diffusion as the Android UI reference, so Dora must still validate its own NDK/JNI integration on physical Android devices rather than treating upstream platform support as proof of app compatibility.[1]

The C API exposes context creation (`new_sd_ctx`), image generation (`generate_image`), progress callbacks, cancellation (`sd_cancel_generation`), image cleanup (`free_sd_images`), and PNG/output handling through returned image buffers.[2] The repository is MIT licensed, subject to preserving the copyright and license notice in substantial copies.[3]

## Dora integration rule

Dora will integrate stable-diffusion.cpp behind `ImageInferenceEngine`, with no weights in Git. v1 should support one verified model bundle and one conservative preset only after native build and device tests pass. The image backend must expose model validation, progress, cancellation, cleanup, memory/thermal warnings, and output metadata. Current app code remains explicit about image runtime readiness until this bridge is implemented and benchmarked.

## References

[1]: https://github.com/leejet/stable-diffusion.cpp "stable-diffusion.cpp repository README"
[2]: https://github.com/leejet/stable-diffusion.cpp/blob/master/include/stable-diffusion.h "stable-diffusion.cpp C API"
[3]: https://github.com/leejet/stable-diffusion.cpp/blob/master/LICENSE "stable-diffusion.cpp MIT license"

## Current implementation result

Dora now vendors stable-diffusion.cpp as a pinned submodule and includes `scripts/build-image-runtime.sh`, which initializes the upstream ggml submodule and attempts a separate ARM64 Android shared-library build. The first same-target integration was rejected because the two runtimes require incompatible ggml extension surfaces. The isolated build configured successfully and compiled a substantial portion of the source, but the sandbox terminated the final compile under its resource ceiling before producing a release library. Therefore the current APK does **not** ship image generation, and the UI continues to report that limitation. This is an intentional release-quality boundary, not a hidden fallback.
