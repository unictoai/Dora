package app.dora.localai.data

import app.dora.localai.domain.DeviceFit
import app.dora.localai.domain.HuggingFaceCandidate
import app.dora.localai.domain.HuggingFaceFileCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

class HuggingFaceClient(private val deviceProfile: DeviceProfile) {
    private data class CacheEntry(val createdAt: Long, val value: HuggingFaceCandidate)

    private val candidateCache = ConcurrentHashMap<String, CacheEntry>()
    private val searchCache = ConcurrentHashMap<String, Pair<Long, List<HuggingFaceCandidate>>>()

    suspend fun search(query: String): Result<List<HuggingFaceCandidate>> = withContext(Dispatchers.IO) {
        runCatching {
            val normalized = query.trim().lowercase()
            val cached = searchCache[normalized]
            if (cached != null && !expired(cached.first)) return@runCatching cached.second

            val encoded = URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
            val searchUrl = "$BASE_URL/api/models?search=$encoded&filter=gguf&limit=12&sort=downloads&direction=-1"
            val results = readJsonArray(searchUrl)
            val candidates = buildList {
                val failures = mutableListOf<Exception>()
                for (index in 0 until results.length()) {
                    val item = results.optJSONObject(index) ?: continue
                    val repoId = item.optString("id").takeIf { it.isNotBlank() } ?: continue
                    try {
                        add(getCachedCandidate(repoId))
                    } catch (error: Exception) {
                        failures += error
                    }
                }
                if (isEmpty() && failures.isNotEmpty()) throw failures.first()
            }.filter { it.files.isNotEmpty() }
                .sortedWith(compareByDescending<HuggingFaceCandidate> { it.recommendedFile?.deviceFit?.level?.rank ?: -1 }.thenByDescending { it.downloads })
            searchCache[normalized] = System.currentTimeMillis() to candidates
            candidates
        }
    }

    suspend fun fetch(repoId: String): Result<HuggingFaceCandidate> = withContext(Dispatchers.IO) {
        runCatching { getCachedCandidate(repoId) }
    }

    private fun getCachedCandidate(repoId: String): HuggingFaceCandidate {
        val now = System.currentTimeMillis()
        val cached = candidateCache[repoId]
        if (cached != null && !expired(cached.createdAt)) return cached.value
        return fetchCandidate(repoId).also { candidateCache[repoId] = CacheEntry(now, it) }
    }

    private fun fetchCandidate(repoId: String): HuggingFaceCandidate {
        require(repoId.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))) {
            "Enter a valid Hugging Face repository, for example org/model."
        }
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
                        deviceFit = deviceProfile.fitForModel(size),
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

    private fun readJsonArray(url: String): JSONArray = try {
        JSONArray(readText(url))
    } catch (error: JSONException) {
        throw HuggingFaceMalformedResponseException(url, error)
    }

    private fun readJsonObject(url: String): JSONObject = try {
        JSONObject(readText(url))
    } catch (error: JSONException) {
        throw HuggingFaceMalformedResponseException(url, error)
    }

    private fun readText(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Dora-Android/0.4")
        }
        return try {
            val response = connection.responseCode
            val stream = if (response in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (response !in 200..299) throw HuggingFaceHttpException(response, body)
            body
        } catch (error: HuggingFaceHttpException) {
            throw error
        } catch (error: IOException) {
            throw HuggingFaceNetworkException(url, error)
        } finally {
            connection.disconnect()
        }
    }

    private fun expired(createdAt: Long): Boolean = System.currentTimeMillis() - createdAt > CACHE_TTL_MS

    private fun encodePath(path: String): String = path.split('/').joinToString("/") {
        URLEncoder.encode(it, Charsets.UTF_8.name()).replace("+", "%20")
    }

    private companion object {
        const val BASE_URL = "https://huggingface.co"
        const val CACHE_TTL_MS = 5 * 60 * 1000L
    }
}

class HuggingFaceHttpException(val statusCode: Int, responseBody: String) : IOException(
    when (statusCode) {
        403 -> "Hugging Face denied this request (HTTP 403). The repository may be gated or require access."
        404 -> "Hugging Face repository or metadata was not found (HTTP 404)."
        429 -> "Hugging Face rate limited Dora (HTTP 429). Please wait and try again."
        in 500..599 -> "Hugging Face is temporarily unavailable (HTTP $statusCode). Please try again later."
        else -> "Hugging Face request failed (HTTP $statusCode)."
    } + responseBody.takeIf { it.isNotBlank() }?.let { " Details: ${it.take(180)}" }.orEmpty(),
)

class HuggingFaceMalformedResponseException(url: String, cause: JSONException) : IOException(
    "Hugging Face returned malformed metadata for $url.", cause,
)

class HuggingFaceNetworkException(url: String, cause: IOException) : IOException(
    "Could not reach Hugging Face for $url. Check the network connection and try again.", cause,
)

private val app.dora.localai.domain.DeviceFitLevel.rank: Int
    get() = when (this) {
        app.dora.localai.domain.DeviceFitLevel.RECOMMENDED -> 3
        app.dora.localai.domain.DeviceFitLevel.POSSIBLE -> 2
        app.dora.localai.domain.DeviceFitLevel.TOO_HEAVY -> 1
        app.dora.localai.domain.DeviceFitLevel.UNSUPPORTED -> 0
    }
