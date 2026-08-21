package app.dora.localai.domain

data class CuratedModelSuggestion(
    val title: String,
    val repoId: String,
    val category: String,
    val description: String,
    val fitHint: String,
)

val defaultCuratedModelSuggestions = listOf(
    CuratedModelSuggestion(
        title = "Qwen 2.5 · Starter",
        repoId = "Qwen/Qwen2.5-1.5B-Instruct-GGUF",
        category = "Everyday chat",
        description = "A compact instruction model with several quantizations. Start with Q4_K_M when your device is comfortable with roughly 1.1 GB of weights.",
        fitHint = "Best first download",
    ),
    CuratedModelSuggestion(
        title = "SmolLM2 · Lightweight",
        repoId = "HuggingFaceTB/SmolLM2-1.7B-Instruct-GGUF",
        category = "Low-memory chat",
        description = "A small open model for trying local chat on more constrained phones. Dora still checks the exact quantization against your device.",
        fitHint = "Good for limited storage",
    ),
    CuratedModelSuggestion(
        title = "Qwen 2.5 Coder · Starter",
        repoId = "Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF",
        category = "Coding",
        description = "An instruction-tuned coding model in GGUF format for code explanations, snippets, and local developer notes.",
        fitHint = "For coding prompts",
    ),
    CuratedModelSuggestion(
        title = "Phi 3.5 Mini · Balanced",
        repoId = "bartowski/Phi-3.5-mini-instruct-GGUF",
        category = "Longer answers",
        description = "A larger 3.8B-class GGUF option for devices with more memory. Download only after Dora marks a quantization as possible or recommended.",
        fitHint = "For higher-memory phones",
    ),
)
