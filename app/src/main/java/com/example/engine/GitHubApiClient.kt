package com.example.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class GitHubRepoSummary(
    val fullName: String,
    val name: String,
    val ownerLogin: String,
    val defaultBranch: String,
    val isPrivate: Boolean,
    val stargazersCount: Int,
    val language: String,
    val htmlUrl: String
)

data class GitHubCommitInfo(
    val sha: String,
    val shortSha: String,
    val authorName: String,
    val message: String,
    val htmlUrl: String
)

class GitHubApiClient {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun fetchRepositories(
        username: String,
        personalAccessToken: String
    ): Result<List<GitHubRepoSummary>> = withContext(Dispatchers.IO) {
        try {
            val token = personalAccessToken.trim()
            val cleanUser = username.trim()
            val url = if (token.isNotBlank()) {
                "https://api.github.com/user/repos?sort=updated&per_page=20&affiliation=owner,collaborator"
            } else {
                require(cleanUser.isNotBlank()) { "Enter a GitHub username or Personal Access Token" }
                "https://api.github.com/users/$cleanUser/repos?sort=updated&per_page=20"
            }

            val reqBuilder = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "PulseRelay-Android/1.0")
            if (token.isNotBlank()) {
                reqBuilder.header("Authorization", "Bearer $token")
            }

            httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val msg = runCatching { JSONObject(body).optString("message") }.getOrNull()
                        ?.takeIf { it.isNotBlank() } ?: "GitHub API HTTP ${resp.code}"
                    return@withContext Result.failure(IllegalStateException(msg))
                }
                val arr = JSONArray(body)
                val list = buildList {
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val owner = obj.optJSONObject("owner")?.optString("login").orEmpty()
                        add(
                            GitHubRepoSummary(
                                fullName = obj.optString("full_name"),
                                name = obj.optString("name"),
                                ownerLogin = owner,
                                defaultBranch = obj.optString("default_branch", "main"),
                                isPrivate = obj.optBoolean("private", false),
                                stargazersCount = obj.optInt("stargazers_count", 0),
                                language = obj.optString("language").takeIf { it.isNotBlank() && it != "null" } ?: "Code",
                                htmlUrl = obj.optString("html_url")
                            )
                        )
                    }
                }
                Result.success(list)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchLatestCommit(
        repoFullName: String,
        personalAccessToken: String
    ): Result<GitHubCommitInfo> = withContext(Dispatchers.IO) {
        try {
            val cleanRepo = repoFullName.trim()
            require(cleanRepo.contains("/")) { "Repository must be owner/repo" }
            val url = "https://api.github.com/repos/$cleanRepo/commits?per_page=1"
            val reqBuilder = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "PulseRelay-Android/1.0")
            if (personalAccessToken.isNotBlank()) {
                reqBuilder.header("Authorization", "Bearer ${personalAccessToken.trim()}")
            }

            httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val msg = runCatching { JSONObject(body).optString("message") }.getOrNull()
                        ?.takeIf { it.isNotBlank() } ?: "GitHub API HTTP ${resp.code}"
                    return@withContext Result.failure(IllegalStateException(msg))
                }
                val arr = JSONArray(body)
                if (arr.length() == 0) {
                    return@withContext Result.failure(IllegalStateException("No commits found in $cleanRepo"))
                }
                val first = arr.getJSONObject(0)
                val sha = first.optString("sha")
                val commitObj = first.optJSONObject("commit")
                val authorName = commitObj?.optJSONObject("author")?.optString("name").orEmpty()
                    .ifBlank { first.optJSONObject("author")?.optString("login").orEmpty() }
                    .ifBlank { "developer" }
                val message = commitObj?.optString("message").orEmpty()
                    .lineSequence().firstOrNull().orEmpty().take(200)
                Result.success(
                    GitHubCommitInfo(
                        sha = sha,
                        shortSha = sha.take(7),
                        authorName = authorName,
                        message = message,
                        htmlUrl = first.optString("html_url")
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun installWebhook(
        repoFullName: String,
        personalAccessToken: String,
        webhookUrl: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val token = personalAccessToken.trim()
            require(token.isNotBlank()) { "GitHub Personal Access Token required to create repo webhooks" }
            require(webhookUrl.startsWith("https://") || webhookUrl.startsWith("http://")) {
                "Valid webhook URL required"
            }
            val payload = JSONObject().apply {
                put("name", "web")
                put("active", true)
                put("events", JSONArray(listOf("push", "release")))
                put(
                    "config",
                    JSONObject().apply {
                        put("url", webhookUrl.trim())
                        put("content_type", "json")
                        put("insecure_ssl", "0")
                    }
                )
            }.toString()

            val request = Request.Builder()
                .url("https://api.github.com/repos/${repoFullName.trim()}/hooks")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("Authorization", "Bearer $token")
                .header("User-Agent", "PulseRelay-Android/1.0")
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val msg = runCatching { JSONObject(body).optString("message") }.getOrNull()
                        ?.takeIf { it.isNotBlank() } ?: "GitHub Hook HTTP ${resp.code}"
                    return@withContext Result.failure(IllegalStateException(msg))
                }
                val hookId = runCatching { JSONObject(body).optLong("id") }.getOrDefault(0L)
                Result.success("Webhook #$hookId installed on $repoFullName")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
