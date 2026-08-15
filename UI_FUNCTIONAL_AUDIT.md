# Dora UI and Functional Audit

## Current problems

The current app presents five equally weighted tabs—Home, Chat, Create, Models, and Settings—before the user has completed the essential task of installing or importing a model. This makes Dora feel like a showcase rather than a tool. The home screen contains several explanatory cards, repeated privacy language, and a large starter panel, while Chat, Models, and Settings repeat the same runtime caveats. The visual hierarchy is card-heavy, saturated, and vertically dense; it does not establish one calm primary action.

The current model install button only changes the registry state. A real model enters through the document picker, while the starter catalog item is explicitly a placeholder. A user can therefore see an “Installed” state without a real GGUF artifact. The first-run flow also does not force model onboarding before chat, and there is no compact model switcher in the chat surface.

The native llama.cpp bridge is present and the ViewModel selects it when an imported validated GGUF has a private file path. However, the app still falls back to the demo engine when no file is imported, so the UI must make that distinction unmistakable. Conversations remain in memory in the current ViewModel, while Room currently stores model/job records without being the source of truth for the visible conversation history.

Image generation is not functional in this milestone. The current Create tab lets users start a job even though the engine returns an unsupported-runtime failure. That is misleading and should be replaced by a disabled or clearly gated capability state until a real image backend is shipped.

## Redesign direction

Dora should have one primary destination: **Chat**. The first-run empty state should be an onboarding screen that asks the user to import a GGUF model. After a model is installed, the app should open directly to a quiet conversation view with a compact model selector, a minimal composer, and a small local-status line. Models and Settings should be secondary destinations reached from the chat header or a simple More sheet. Image generation should not occupy primary navigation until it is functional.

The visual language should use an off-white canvas, white surfaces, near-black typography, one restrained indigo accent, 16–20dp corner radii, hairline dividers, and generous vertical spacing. Cards should be used for bounded actions only, not as the default container for every section. The app should avoid marketing copy such as “Your local AI studio” and instead use direct task language.

## Target first-run flow

1. Launch into `ModelSetupScreen` when no validated local text model exists.
2. Explain, in one screen, that Dora runs inference locally and does not bundle model weights.
3. Let the user import a `.gguf` file. Validate magic bytes, size, and SHA-256, then show the artifact name and digest.
4. Offer `Start chatting` only after validation succeeds.
5. Open `ChatScreen` with the imported model shown in the header and a clear offline status.

## Target steady-state flow

Chat is the home screen. The header contains Dora, the active model name, and a More button. The body contains a calm empty state or messages. The composer supports text, send, and stop. A compact footer reports `On device` or `Demo fallback` and never hides which engine is active. Model management is a secondary sheet/screen. Settings contains privacy and storage controls. Image generation is shown as “Coming later” only if included at all.
