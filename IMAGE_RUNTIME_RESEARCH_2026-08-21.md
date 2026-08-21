# Image Runtime Research — 2026-08-21

## Source reviewed

[stable-diffusion.cpp Android build documentation](https://github.com/leejet/stable-diffusion.cpp/blob/master/docs/build.md)

## Findings

The project documents an Android ARM64 build path using the Android NDK and an OpenCL configuration. The documented build uses `ANDROID_ABI=arm64-v8a`, `ANDROID_PLATFORM=android-28`, `GGML_OPENMP=OFF`, and `SD_OPENCL=ON`. It also requires OpenCL headers and an ICD loader library to be supplied to the NDK sysroot, and notes a runtime `LD_LIBRARY_PATH=/vendor/lib64` requirement for the command-line configuration.

The documentation establishes that an Android ARM64 compilation path exists, but it does not by itself prove that a self-contained Dora APK can ship the required OpenCL dependencies across supported devices, nor that a selected model family will fit Dora’s memory, storage, thermal, cancellation, and output-persistence requirements. The source page lists many model/runtime variants but does not provide a single stable Android embedding contract sufficient for immediate production integration.

## Product decision

Do not add a fake image-generation success path or bundle an unverified diffusion runtime. Keep Dora’s image adapter explicitly unavailable until one narrow backend and model family can be built into the existing Gradle/CMake project, validated on representative ARM64 devices, and covered by model validation, progress, cancellation, output persistence, and memory/thermal tests.


## Additional source reviewed

[MNN official repository](https://github.com/alibaba/MNN)

## Additional findings

MNN presents a broader mobile inference stack with Android support, CPU/GPU/NPU backends, quantization options, MNN-LLM, and an MNN-Diffusion component. The repository’s breadth makes it a technically plausible future backend, but the public overview does not establish a minimal stable Dora-specific diffusion adapter, a single model packaging contract, or cross-device performance and thermal guarantees.

## Updated product decision

Do not add MNN or stable-diffusion.cpp as a production Dora feature merely because an Android build is documented. A real integration would require selecting one model family, vendoring and licensing the native dependencies, defining model validation and output storage, implementing cancellation/progress/error states, and validating on physical ARM64 devices. Until those gates are met, Dora should keep image generation visibly unavailable rather than expose a dead or simulated button.


## Third source reviewed

[ExecuTorch official repository](https://github.com/pytorch/executorch)

## Third-source findings

ExecuTorch documents Android deployment and several mobile backends, including XNNPACK, Vulkan, Qualcomm, MediaTek, and Samsung Exynos. Its repository is a general PyTorch edge deployment platform rather than a ready-made Dora diffusion adapter. The Android support list does not establish a turnkey image-generation model format, a stable Kotlin/JNI contract matching Dora, or the output/progress/cancellation/device acceptance behavior required for a production feature.

## Runtime integration conclusion

The research supports keeping image generation as a gated roadmap item rather than claiming it is shipped. Dora can revisit a narrow backend after selecting a model family and completing a source-vendored ARM64 build, licensing review, model validation, durable output storage, progress/cancellation behavior, and physical-device tests. No simulated image button or fake completion state will be added.
