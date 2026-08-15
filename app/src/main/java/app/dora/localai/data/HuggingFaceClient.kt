package app.dora.localai.data

import app.dora.localai.domain.DeviceFit
import app.dora.localai.domain.HuggingFaceCandidate
import app.dora.localai.domain.HuggingFaceFileCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class HuggingFaceClient(private val deviceProfile: DeviceProfile) {
    suspend fun search(query: String): Result<List<HuggingFaceCandidate>> = withContext(Dispatchers.IO) {
        runCatching {
            val encoded = URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
            val searchUrl = "$BASE_URL/api/models?search=$encoded&filter=gguf&limit=12&sort=downloads&direction=-1"
            val results = readJsonArray(searchUrl)
            val candidates = buildList {
                for (index in 0 until results.length()) {
                    val item = results.optJSONObject(index) ?: continue
                    val repoId = item.optString("id").takeIf { it.isNotBlank() } ?: continue
                    runCatching { fetchCandidate(repoId) }.getOrNull()?.let(::add)
                }
            }
            candidates
                .filter { it.files.isNotEmpty() }
                .sortedWith(compareByDescending<HuggingFaceCandidate> { it.recommendedFile?.deviceFit?.level?.rank ?: -1 }.thenByDescending { it.downloads })
        }
    }

    suspend fun fetch(repoId: String): Result<HuggingFaceCandidate> = withContext(Dispatchers.IO) {
        runCatching { fetchCandidate(repoId) }
    }

    private fun fetchCandidate(repoId: String): HuggingFaceCandidate {
        require(repoId.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))) { "Enter a valid Hugging Face repository, for example org/model." }
        val root = readJsonObject("$BASE_URL/api/models/${encodePath(repoId)}?blobs=true&files_metadata=true")
        val siblings = root.optJSONArray("siblings") ?: JSONArray()
        val revision = root.optString("sha").ifBlank { "main" }
        val license = root.optString("license").ifBlank {
            root.optJSONObject("cardData")?.optString("license").orEmpty()
        }.ifBlank { "License not declared" }
        val files = buildList {
            for (index in 0 until siblings.length()) {
                val sibling = siblings.optJSONObject(index) ?: continue
                val filename = sibling.optString("rfilename")
                if (!filename.lowercase().endsWith(".gguf")) continue
                if (Regex("-\\d{5}-of-\\d{5}\\.gguf$", RegexOption.IGNORE_CASE).containsMatchIn(filename)) continue
                val lfs = sibling.optJSONObject("lfs")
                val size = sibling.optLong("size", lfs?.optLong("size", -1L) ?: -1L)
                if (size <= 0L) continue
                val sha = lfs?.optString("sha256")?.takeIf { !it.isNullOrBlank() }
                    ?: sibling.optString("sha256").takeIf { it.isNotBlank() }
                val fit = deviceProfile.fitForModel(size)
                add(
                    HuggingFaceFileCandidate(
                        id = "$repoId/$filename",
                        repoId = repoId,
                        filename = filename,
                        revision = revision,
                        sizeBytes = size,
                        sha256 = sha,
                        license = license,
                        downloadUrl = "$BASE_URL/$repoId/resolve/$revision/${encodePath(filename)}?download=true",
                        deviceFit = fit,
                    ),
                )
            }
        }.sortedWith(compareBy<HuggingFaceFileCandidate> { it.deviceFit.level.rank }.thenBy { it.sizeBytes })
        return HuggingFaceCandidate(
            repoId = repoId,
            displayName = root.optString("modelId").ifBlank { repoId.substringAfter('/') },
            author = root.optString("author").ifBlank { repoId.substringBefore('/') },
            description = root.optString("description").ifBlank { "Public Hugging Face GGUF repository" },
            license = license,
            revision = revision,
            downloads = root.optLong("downloads", 0L),
            gated = root.optBoolean("gated", false),
            files = files,
        )
    }

    private fun readJsonArray(url: String): JSONArray = JSONArray(readText(url))

    private fun readJsonObject(url: String): JSONObject = JSONObject(readText(url))

    private fun readText(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Dora-Android/0.3")
        }
        connection.connect()
        return try {
            val response = connection.responseCode
            if (response !in 200..299) error("Hugging Face returned HTTP $response")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun encodePath(path: String): String = path.split('/').joinToString("/") {
        URLEncoder.encode(it, Charsets.UTF_8.name()).replace("+", "%20")
    }

    private companion object {
        const val BASE_URL = "https://huggingface.co"
    }
}

private val app.dora.localai.domain.DeviceFitLevel.rank: Int
    get() = when (this) {
        app.dora.localai.domain.DeviceFitLevel.RECOMMENDED -> 3
        app.dora.localai.domain.DeviceFitLevel.POSSIBLE -> 2
        app.dora.localai.domain.DeviceFitLevel.TOO_HEAVY -> 1
        app.dora.localai.domain.DeviceFitLevel.UNSUPPORTED -> 0
    }
