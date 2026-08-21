# Image Runtime Research — 2026-08-21

## Source reviewed

[stable-diffusion.cpp Android build documentation](https://github.com/leejet/stable-diffusion.cpp/blob/master/docs/build.md)

## Findings

The project documents an Android ARM64 build path using the Android NDK and an OpenCL configuration. The documented build uses `ANDROID_ABI=arm64-v8a`, `ANDROID_PLATFORM=android-28`, `GGML_OPENMP=OFF`, and `SD_OPENCL=ON`. It also requires OpenCL headers and an ICD loader library to be supplied to the NDK sysroot, and notes a runtime `LD_LIBRARY_PATH=/vendor/lib64` requirement for the command-line configuration.

The documentation establishes that an Android ARM64 compilation path exists, but it does not by itself prove that a self-contained Dora APK can ship the required OpenCL dependencies across supported devices, nor that a selected model family will fit Dora’s memory, storage, thermal, cancellation, and output-persistence requirements. The source page lists many model/runtime variants but does not provide a single stable Android embedding contract sufficient for immediate production integration.

## Product decision

Do not add a fake image-generation success path or bundle an unverified diffusion runtime. Keep Dora’s image adapter explicitly unavailable until one narrow backend and model family can be built into the existing Gradle/CMake project, validated on representative ARM64 devices, and covered by model validation, progress, cancellation, output persistence, and memory/thermal tests.
