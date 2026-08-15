# Hugging Face Integration Research

## Verified platform facts

Hugging Face has built-in Hub support for GGUF files, which package tensors and standardized metadata for llama.cpp-style executors. The Hub exposes GGUF discovery through its GGUF library filter and provides metadata/tensor viewers on model and file pages. Source: <https://huggingface.co/docs/hub/en/gguf>.

The Hub’s file-download model is revision-aware. A client can identify a repository and exact filename, optionally pin a full commit revision, and download through a resolved file URL. Dora should store the repository ID, filename, revision, remote size, and SHA-256 in its local provenance record. Source: <https://huggingface.co/docs/huggingface_hub/en/guides/download>.

Gated repositories require the user to authenticate and request access through Hugging Face. A native Android browser/login flow is therefore required for gated downloads; Dora must not attempt to collect or hardcode a user token in the app. The first release should support public repositories only and explain why a gated model cannot be downloaded in-app. Source: <https://huggingface.co/docs/hub/en/models-gated>.

Hugging Face downloads may redirect from `huggingface.co` to storage/CDN hosts. Dora’s downloader should follow redirects, support resumable HTTP ranges, validate the final byte count and SHA-256, and report network failures without leaving a partial model marked as installed. Source: <https://huggingface.co/docs/hub/en/models-downloading>.

## Product decisions

Dora’s first Hugging Face integration should be a public GGUF browser rather than an unrestricted search engine. Search results should be filtered to repositories with GGUF files, then ranked by device fit, file size, quantization, license visibility, and repository metadata quality. The app should never recommend a model solely because it is popular.

Each downloadable file must be presented with its exact repo ID, revision, filename, size, quantization, license information when available, and a link to the model card. If license metadata is missing or ambiguous, the download action should require an explicit acknowledgement rather than imply endorsement.

The in-app catalog can use the public Hub API without an authentication token. Public models are the default. Gated/private models should have a `Open in Hugging Face` action and must not be silently downloaded by Dora.

Device recommendations should be conservative. Dora should reserve part of RAM for Android and the runtime, compare the remaining budget with the file size plus a safety overhead, require ARM64 for the native build, and warn when free storage is below the remote file size plus a staging margin. Recommended quantization should generally be Q4_K_M or comparable 4-bit variants for the first public catalog, with Q5/Q6 marked as higher quality but heavier.
