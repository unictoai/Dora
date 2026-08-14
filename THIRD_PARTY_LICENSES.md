# Dora Third-Party Runtime Inventory

**Updated:** 2026-08-14

| Component | Revision | License | Build status |
|---|---|---|---|
| [llama.cpp](https://github.com/ggml-org/llama.cpp) | `9b05354ec6fb58b4e665e9a39ebc40285c015638` | MIT | Built into `dora_native` for ARM64 Android |
| [stable-diffusion.cpp](https://github.com/leejet/stable-diffusion.cpp) | `de298c225bed97c3f9026b73cd7b71e7879bd41b` | MIT | Vendored for evaluation; not linked into the current app target because its bundled ggml extensions conflict with llama.cpp’s shared ggml target |
| AndroidX / Jetpack | Maven artifacts declared in `app/build.gradle.kts` | Individual upstream licenses | Resolved by Gradle |

Dora does not include model weights in Git. Every model must carry a source URL, SHA-256 digest, format, compatibility result, and model-specific license before distribution.

## Runtime isolation note

The first attempt to link stable-diffusion.cpp into the same CMake target as llama.cpp failed because both projects assume different ggml extension surfaces, producing missing `ggml_mul_mat_i8_tensorwise` and `ggml_quantize_i8_convrot` symbols during Android compilation. This is recorded as an engineering finding, not hidden as a successful image integration. The production image milestone must use a separately isolated native target or a backend with a compatible shared ggml dependency, then repeat ARM64 device validation.

## Required notices

The final release artifact must ship the complete MIT notices for both runtimes if their code is distributed, plus all AndroidX notices and model licenses for user-selectable weights.
