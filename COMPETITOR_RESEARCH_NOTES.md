# Dora Competitive Research Notes

## Research scope

This working file records evidence collected from public official sources for the competitive local-AI review. The goal is to separate observed capabilities from assumptions and translate them into Dora features only when they fit Dora’s privacy-first Android architecture.

## PocketPal AI

Source: [PocketPal AI GitHub repository](https://github.com/a-ghorbani/pocketpal-ai), accessed 2026-08-21.

The repository describes PocketPal as a private on-device assistant that runs without an account, cloud service, or internet connection after model acquisition. The README explicitly positions local chatting, voice, and tools as product capabilities. The project is open-source under MIT, publishes Android and iOS apps, integrates with Hugging Face model acquisition, and maintains a public model/assistant ecosystem through PalsHub. The repository also shows mature product signals Dora should study: a large automated test suite, end-to-end tests, release automation, design-token work, localization, model capability probing, speculative-decoding work, neural TTS with multiple engines, streaming speech, voice-led onboarding, and explicit disk-space/memory gates.

Important Dora implications are not to copy the breadth blindly. The highest-value capabilities to adapt are: a model catalog with capability metadata; multiple conversations; configurable system prompts and generation settings; voice input/output only after a real Android implementation; tool use only with explicit offline boundaries; localization-ready design tokens; deterministic model/runtime capability probes; disk/memory preflight; and stronger release/e2e coverage.

## Google AI Edge Gallery

Sources: [Google AI Edge Gallery](https://developers.google.com/edge/gallery) and [Google AI Edge Gallery GitHub repository](https://github.com/google-ai-edge/gallery), accessed 2026-08-21.

The official developer page is a dynamic app shell, but the official search result describes a gallery for high-performance, private, offline local-AI workflows. The current product direction includes local Gemma models and agent skills that can augment model capabilities with tools such as Wikipedia grounding, interactive maps, and rich visual summary cards. This is a strong benchmark for capability discovery and guided demos rather than a reason to add remote services to Dora.

Important Dora implications are: model capability cards should explain text, vision, tool, or multimodal support; setup should present tested local workflows; agent/tool features must be clearly labeled as optional and privacy-preserving; and unsupported modalities must remain explicitly marked as not shipped.

## Evidence limits

This file is an intermediate research record, not the final competitive report. Dynamic pages can expose incomplete text through static extraction. Further review is required for MLC Chat, Layla, Maid/SmolChat, ChatterUI, LM Studio, Jan, Ollama/Termux Android workflows, and relevant model/runtime repositories. Final recommendations should cite official pages or repositories wherever possible and should not infer feature parity from marketing language alone.

## MLC Chat / MLC LLM

Source: [MLC LLM Android SDK documentation](https://llm.mlc.ai/docs/deploy/android.html), accessed 2026-08-21.

MLC’s Android architecture is materially different from Dora’s GGUF/llama.cpp path. Its Android package configuration identifies model repositories, unique model IDs, estimated runtime VRAM, bundled-weight policy, and overrides. Packaging compiles model libraries and a runtime/tokenizer into Android-native artifacts, and the app downloads pre-converted model weights from Hugging Face. The docs expose an important production lesson: model compatibility is runtime-specific and should be represented explicitly rather than inferred from a filename.

Dora should adapt the product lesson, not copy MLC’s compiler stack: model records should store runtime, architecture, quantization, estimated memory, supported context, and tested ABI; model download UI should distinguish “downloadable” from “validated on this device”; and a future runtime adapter should allow additional engines without weakening the current llama.cpp boundary.

## Layla

Source: [Layla official product site](https://www.layla-network.ai/), accessed 2026-08-21.

Layla positions itself as an offline AI companion with chat, roleplay, characters, and creation workflows. Its official page states that it can run local models in GGUF through llama.cpp as well as LiteRT-LM and ExecuTorch formats, load user-provided GGUF models, expose temperature/sampler controls, encrypt private chats on-device, remember user traits/preferences, support deletion, and request consent before internet-sharing. It also clearly distinguishes local modes from optional cloud mode.

Important Dora implications are: persona/system-prompt profiles should be real persisted conversations rather than decorative character buttons; advanced generation controls need validation and safe defaults; encrypted local conversation storage is a major privacy milestone; capability/runtime provenance should be visible; and any future online provider must be explicit opt-in, never a silent fallback. Dora should not advertise memory, encryption, or multi-runtime support until implemented and tested.

## Maid

Source: [Maid official repository](https://github.com/Mobile-Artificial-Intelligence/maid), accessed 2026-08-21.

Maid is an Android-capable, open-source app that supports local GGUF inference through llama.cpp, one-tap curated Hugging Face downloads, local model import, conversation create/rename/delete, JSON chat export/import, customizable temperature/top-p/top-k/context settings, a global system prompt and assistant persona, voice output through a companion project, and multiple optional remote providers. Its repository also includes privacy policy/terms documentation, tests, and a database layer.

Important Dora implications are: chats should become durable entities with export/import; generation settings should be explicit per session; model acquisition should be curated and explainable; and Dora’s local-only boundary should be preserved even if remote-provider adapters are considered later. Provider flexibility is valuable, but it must not turn Dora’s offline mode into a misleading toggle.

## ChatterUI

Source: [ChatterUI official repository](https://github.com/Vali-98/ChatterUI), accessed 2026-08-21.

ChatterUI offers a mobile frontend for on-device GGUF through llama.cpp plus optional remote APIs. Its local mode supports both copying a model into app storage and using an external model in place, explicitly trading startup speed against storage use. Its README documents backend/provider configuration, custom API templates, multiple open-source and commercial endpoints, and a device-oriented quantization recommendation.

Important Dora implications are: Dora should make the import-versus-external-storage decision explicit; model details should explain the storage/startup tradeoff; provider and runtime settings should be isolated from local-only mode; and custom connectors should never be presented as part of the offline product path. A future advanced settings screen should include validated quantization/context guidance rather than raw expert controls without explanation.

## LM Studio

Source: [LM Studio official documentation](https://lmstudio.ai/docs/app), accessed 2026-08-21.

LM Studio’s official docs cover offline operation, model download/search through Hugging Face, a simple flexible chat interface, local model management, prompt/config management, document chat/RAG, MCP server integration, per-model defaults, prompt templates, speculative decoding, parallel requests, local/OpenAI-compatible APIs, and remote device linking. It supports llama.cpp/GGUF and MLX on supported desktop platforms.

Important Dora implications are: a clean default chat must coexist with an advanced settings path; document chat should be considered only with an actual local retrieval/indexing implementation; model defaults and prompt templates should be persisted per model or conversation; MCP/tools must be opt-in and visibly outside the strict offline core; and a future desktop companion or LAN API should not be added to Android until its security model is explicit.

## Jan

Source: [Jan official repository](https://github.com/janhq/jan), accessed 2026-08-21.

Jan presents local models from Hugging Face, custom assistants, an offline-first experience, OpenAI-compatible local serving, MCP integration, and a broad extension architecture. Its repository also shows mature engineering concerns around guided onboarding, model/backend readiness probes, atomic backend updates with rollback, checksum verification, router adoption, crash logs, backend version retention, update history, and extensive CI/testing.

Important Dora implications are: onboarding should verify readiness rather than assume it; update/download operations should be download → verify → commit with rollback; diagnostics and logs should survive crashes; model/runtime versions need provenance and safe rollback; and optional integrations must remain separate from the privacy-first local path. Dora should implement the reliability patterns before considering a broad extension ecosystem.

## Competitive synthesis so far

The strongest shared expectations across serious products are not decorative screens. They are a trustworthy model lifecycle, clear runtime/device compatibility, durable conversations, configurable but safe generation settings, explicit privacy boundaries, explainable errors, document/voice/tool features only when genuinely implemented, and a mature test/release loop. Dora’s immediate differentiation should be a simpler, more honest Android-native experience: verified GGUF acquisition, device-fit guidance, private storage, transparent progress/recovery, real local chat, and no silent cloud fallback.

## SmolChat

Source: [SmolChat Android repository](https://github.com/shubham0204/SmolChat-Android), accessed 2026-08-21.

SmolChat is a focused Android GGUF app built around local llama.cpp inference. Repository history shows attention to HF connectivity failure handling, unsupported chat-template fallback, auto-scroll during streaming, additional chat settings such as chat template, memory mapping, locking, thread count, and minimal vector-database RAG/document Q&A. It also maintains release checklists, privacy policy, and CI.

Important Dora implications are: the default experience should stay focused; streaming chat behavior and auto-scroll matter; advanced controls need safe capability-aware defaults; chat-template incompatibilities should be visible and recoverable; and a future document mode can be built incrementally on a real local index rather than a placeholder.

## ToolNeuron

Source: [ToolNeuron official repository](https://github.com/Siddhesh2377/ToolNeuron), accessed 2026-08-21.

ToolNeuron is an Android privacy-focused competitor with encrypted local AI workflows. Its repository describes a storage inspector with category-level sizes and deletion, onboarding that includes RAG setup, an install-progress tracker with stable speed, an HF explorer with filters, a multi-engine catalog spanning chat/VLM/embedding/TTS/STT/image, an HTTP server with OpenAI-shaped endpoints and bearer-token/rate-limit/audit controls, and a plugin store with sandboxed Android modules and capability gates. It also emphasizes keeping models, chats, RAG documents, and key material on the phone.

Important Dora implications are: storage visibility must be actionable; model/download/extraction progress should be aggregate and stable; filtering is a major catalog usability feature; future multimodal work needs typed engine boundaries; and plugins/server APIs need a capability/security model before they can be considered production features. Dora’s image generation remains intentionally not shipped until an Android runtime is physically validated.

## Expanded synthesis

The competitive bar now includes: reliable model acquisition and recovery; model/runtime compatibility metadata; multiple conversations and durable export/import; safe advanced generation settings; private encrypted persistence; document/RAG workflows; optional voice; capability-aware multimodal engines; excellent streaming/auto-scroll; catalog filters; storage inspection; onboarding readiness probes; diagnostics and rollback; and a carefully gated extension/API ecosystem. Dora’s best path is to implement the local Android fundamentals deeply first, then add these capabilities in measured, testable milestones rather than advertise a broad but unverified surface.

## LLM Hub

Source: [LLM Hub official repository](https://github.com/timmyy123/LLM-Hub), accessed 2026-08-21.

LLM Hub is an Android/iOS local-AI project whose repository describes on-device text chat, image/video/music generation, Whisper transcription, Kokoro TTS, RAG/global memory, Hugging Face downloads, import of multiple model formats, GPU/NPU acceleration, and many language interfaces. The recent history also shows model-specific thinking configuration, MCP work, and explicit model loading/unloading changes.

Important Dora implications are: multimodal scope requires a typed model/runtime registry; hardware acceleration should be measured and capability-gated; loading/unloading must be explicit; and image/audio/document features should ship only behind real model, memory, and device tests. Dora can borrow the capability taxonomy and model-specific settings approach without pretending its current image runtime is ready.

## Box

Source: [Box official repository](https://github.com/jegly/Box), accessed 2026-08-21.

Box is an Android all-in-one local-AI suite based on Google AI Edge Gallery plus llama.cpp, stable-diffusion.cpp, and whisper.cpp. Its repository describes local chat with GGUF import, LiteRT downloads, thinking mode, markdown/LaTeX rendering, persisted/resumable conversations, vision, document analysis, voice mode, STT/TTS, image generation, image upscaling, encrypted chats with SQLCipher, biometric lock, hard offline mode, security audit log, prompt sanitization, tapjacking guard, model allowlists, and NPU paths.

Important Dora implications are: this is the clearest benchmark for a future multimodal Android suite; its security features are first-class product features rather than settings copy; conversations must be resumable; prompt and input sanitization deserve explicit boundaries; and model allowlists/capability probes improve safety. Dora should implement encrypted conversation storage and biometric lock only with Android/security tests, and should keep stable-diffusion.cpp isolated until its native dependency conflicts, memory behavior, and device support are proven.

## Multimodal synthesis

The competitive multimodal bar includes chat, vision, documents/RAG, STT, TTS, image generation/editing/upscaling, and sometimes video/music. The common engineering requirement is a typed engine catalog with per-model capability and resource metadata, lazy loading/unloading, cancel-safe sessions, storage/memory preflight, and honest unsupported-state UI. Dora’s current text/GGUF release should be positioned as a hardened foundation; the next credible feature is durable secure chat, not an untested image-generation button.

## Stable Diffusion AI / SDAI

Source: [Stable-Diffusion-KMP official repository](https://github.com/ShiftHackZ/Stable-Diffusion-KMP), accessed 2026-08-21.

SDAI is a cross-platform image-generation client with self-hosted and hosted providers plus local Android/iOS runtimes where supported. Its repository documents Android ONNX, MediaPipe, stable-diffusion.cpp SDXL, and experimental PrismML Bonsai paths; text-to-image, image-to-image, inpainting controls, negative prompts, batches, samplers, model selection, safety controls, generation interruption, and local benchmarks. It explicitly marks some local runtimes as platform-specific or experimental and documents model/feature differences per provider.

Important Dora implications are: image generation needs a provider/runtime matrix rather than a generic button; generation controls must be capability-aware; interruption and benchmark behavior are first-class; model assets require explicit import/download workflows; and experimental runtimes must be visibly labeled. Dora’s existing isolated stable-diffusion.cpp subtree should remain unshipped until dependency conflicts and physical-device performance are proven.

## Google AI Edge LLM Inference

Source: [Google AI Edge LLM Inference for Android](https://developers.google.com/edge/mediapipe/solutions/genai/llm_inference/android), accessed 2026-08-21.

Google’s official Android documentation states that its MediaPipe LLM Inference API runs models fully on-device, supports streaming responses, and is optimized for high-end devices such as Pixel 8 and Samsung S23 or later. The page also states that the API is in maintenance-only mode and recommends migration to LiteRT-LM. Its sample/gallery description covers image question answering, prompt-lab tasks, multi-turn chat, model discovery/download, custom `.litertlm`/`.task` import, and real-time performance metrics such as time to first token and decode speed.

Important Dora implications are: runtime compatibility must be explicit and versioned; streaming and performance metrics improve trust; multimodal flows need separate capability paths; and Dora should not add a second Android runtime until an adapter, package size, model format, memory profile, and physical-device test plan exist.
