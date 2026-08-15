# Dora v0.2.0-prealpha

> **A functional chat-first pre-alpha build for trusted Android device testing.** This release replaces the previous dashboard-like interface with a simpler Apple-inspired flow centered on importing and using a real local GGUF model.

## Artifact

| Field | Value |
|---|---|
| Application ID | `app.dora.localai` |
| Version | `0.2.0` |
| Version code | `2` |
| Minimum Android | API 26 / Android 8.0 |
| Target SDK | 35 |
| Native ABI | `arm64-v8a` |
| Source commit | `9ca5109c3d56aa2f7d4a05b79d4f2e76f6be1086` plus the v0.2.0 UI/flow changes in this release source tree |
| APK size | 21,293,601 bytes |
| SHA-256 | `6bbd5820c94e5ed327ac877068e9277a947a69c04805acce68c92d57ac8a7cec` |

## What changed

Dora now opens with a focused model setup screen instead of a multi-tab dashboard. A user must import a `.gguf` file before entering chat. The file is copied into Dora’s private storage, checked for GGUF structure, hashed with SHA-256, and then loaded through the native llama.cpp validator. Unsupported or unreadable models are removed instead of being shown as installed.

The main product is now a quiet chat screen with an active model header, an explicit `On device` status, a minimal composer, streaming response state, and safe stop behavior. Models and Settings are secondary destinations. The visual system uses a restrained indigo accent, off-white canvas, white surfaces, simple typography, generous spacing, and fewer cards. Image generation is no longer presented as a primary action because its local runtime is not yet shipped.

## Installation

Download `dora-v0.2.0-prealpha-arm64-v8a.apk`, verify its SHA-256 digest, and install it on an ARM64 Android device. Android may require enabling installation from the download source because this is a debug/pre-alpha APK and is not signed with a production Play App Signing key.

After opening Dora, choose **Import a GGUF model**. Dora will only open the chat screen after native validation succeeds. Model weights are not included in the APK.

## Important limitations

This is still a pre-alpha build. It has not yet been benchmarked across a physical device matrix, and it does not include image generation. The first model import may take time and memory because the native loader checks the selected file. The app intentionally supports the ARM64 native target only in this release.

There is no cloud inference fallback, telemetry service, production signing key, or Play Store packaging. Explicit model acquisition is the only intended network-dependent operation; inference is designed to stay local after the model is installed.

## Tester focus

Please test model import rejection, successful native load, first-token latency, tokens per second, stop generation, airplane-mode chat, process restart, low storage, and thermal behavior. Report the Android device, RAM, model filename, model SHA-256, and exact reproduction steps for failures.
