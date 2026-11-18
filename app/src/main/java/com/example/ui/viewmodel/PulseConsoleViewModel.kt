package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.PulseRepository
import com.example.data.model.AccountSettings
import com.example.data.model.ApiKeyRecord
import com.example.data.model.ApiRequestLog
import com.example.data.model.CountryCarrierInfo
import com.example.data.model.GitHubIntegrationRecord
import com.example.data.model.GitHubTriggerMode
import com.example.data.model.KeyEnvironment
import com.example.data.model.MessageDirection
import com.example.data.model.NumberStatus
import com.example.data.model.NumberType
import com.example.data.model.OtpChannel
import com.example.data.model.OtpVerificationRecord
import com.example.data.model.PhoneNumberRecord
import com.example.data.model.PlanTier
import com.example.data.model.SmsMessageRecord
import com.example.engine.GitHubApiClient
import com.example.engine.GitHubRepoSummary
import com.example.engine.OtpCheckResult
import com.example.engine.PulseApiServer
import com.example.engine.SendingEngine
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class ConsoleDashboardData(
    val settings: AccountSettings?,
    val apiKeys: List<ApiKeyRecord>,
    val phoneNumbers: List<PhoneNumberRecord>,
    val smsMessages: List<SmsMessageRecord>,
    val otpVerifications: List<OtpVerificationRecord>,
    val githubIntegrations: List<GitHubIntegrationRecord> = emptyList()
)

data class ApiExecutionResult(
    val statusCode: Int,
    val latencyMs: Long,
    val responseBody: String,
    val isSuccess: Boolean
)

class PulseConsoleViewModel(
    private val repository: PulseRepository,
    val currentUserId: String
) : ViewModel() {

    private val sendingEngine = SendingEngine(repository, viewModelScope)
    private val apiServer = PulseApiServer(sendingEngine, viewModelScope)
    private val githubClient = GitHubApiClient()
    private val secureRandom = SecureRandom()
    private val playgroundHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    val isServerRunning: StateFlow<Boolean> = apiServer.isRunning
    val serverPort: StateFlow<Int> = apiServer.port
    val apiRequestLogs: StateFlow<List<ApiRequestLog>> = apiServer.requestLogs

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _lastOtpCheck = MutableStateFlow<OtpCheckResult?>(null)
    val lastOtpCheck: StateFlow<OtpCheckResult?> = _lastOtpCheck.asStateFlow()

    private val _lastApiResult = MutableStateFlow<ApiExecutionResult?>(null)
    val lastApiResult: StateFlow<ApiExecutionResult?> = _lastApiResult.asStateFlow()

    private val _firebaseLatencyMs = MutableStateFlow<Long?>(null)
    val firebaseLatencyMs: StateFlow<Long?> = _firebaseLatencyMs.asStateFlow()

    private val _fetchedGitHubRepos = MutableStateFlow<List<GitHubRepoSummary>>(emptyList())
    val fetchedGitHubRepos: StateFlow<List<GitHubRepoSummary>> = _fetchedGitHubRepos.asStateFlow()

    val dashboardState: StateFlow<UiState<ConsoleDashboardData>> = combine(
        combine(
            repository.observeAccountSettings(currentUserId),
            repository.observeApiKeys(currentUserId),
            repository.observePhoneNumbers(currentUserId)
        ) { settings, keys, numbers -> Triple(settings, keys, numbers) },
        combine(
            repository.observeSmsMessages(currentUserId),
            repository.observeOtpVerifications(currentUserId),
            repository.observeGitHubIntegrations(currentUserId)
        ) { messages, otps, gh -> Triple(messages, otps, gh) }
    ) { left, right ->
        ConsoleDashboardData(
            settings = left.first,
            apiKeys = left.second,
            phoneNumbers = left.third,
            smsMessages = right.first,
            otpVerifications = right.second,
            githubIntegrations = right.third
        )
    }
        .onEach { data ->
            apiServer.syncSnapshot(
                settings = data.settings,
                keys = data.apiKeys,
                numbers = data.phoneNumbers,
                messages = data.smsMessages,
                otps = data.otpVerifications
            )
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing console data", rootError)
            emitErrorState(rootError.message ?: "Failed to load PulseRelay console")
        }
        .let { flow ->
            flow
        }.let { combined ->
            combine(
                combined,
                MutableStateFlow(Unit)
            ) { data, _ ->
                UiState.Success(data) as UiState<ConsoleDashboardData>
            }.catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Console stream failure", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to sync PulseRelay workspace"))
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    private fun emitErrorState(msg: String) {
        _toastMessage.value = msg
    }

    init {
        viewModelScope.launch {
            val seedResult = repository.seedWorkspaceIfNeeded()
            if (seedResult.isFailure) {
                _toastMessage.value = seedResult.exceptionOrNull()?.message ?: "Could not initialize workspace"
            }
            apiServer.start(8765)
        }
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun clearOtpCheck() {
        _lastOtpCheck.value = null
    }

    fun toggleApiServer(start: Boolean) {
        if (start) {
            apiServer.start(8765)
            _toastMessage.value = "Local REST API Server listening on http://127.0.0.1:8765"
        } else {
            apiServer.stop()
            _toastMessage.value = "Local REST API Server stopped"
        }
    }

    fun sendSmsFromConsole(
        toNumber: String,
        fromNumber: String,
        body: String,
        data: ConsoleDashboardData
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = sendingEngine.sendSms(
                toNumber = toNumber,
                fromNumber = fromNumber,
                body = body,
                direction = MessageDirection.OUTBOUND_CONSOLE,
                apiKeyLabel = "Developer Console",
                settings = data.settings,
                ownedNumbers = data.phoneNumbers
            )
            _isBusy.value = false
            _toastMessage.value = if (res.isSuccess) {
                "SMS queued to $toNumber (${res.getOrThrow().record.carrierName})"
            } else {
                res.exceptionOrNull()?.message ?: "Failed to send SMS"
            }
        }
    }

    fun sendOtpFromConsole(
        toNumber: String,
        fromNumber: String,
        serviceName: String,
        codeLength: Int,
        channel: OtpChannel,
        data: ConsoleDashboardData
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = sendingEngine.sendOtpVerification(
                toNumber = toNumber,
                fromNumber = fromNumber,
                serviceName = serviceName,
                codeLength = codeLength,
                channel = channel,
                ttlSeconds = 300,
                direction = MessageDirection.OUTBOUND_CONSOLE,
                apiKeyLabel = "Verify Console",
                settings = data.settings,
                ownedNumbers = data.phoneNumbers
            )
            _isBusy.value = false
            _toastMessage.value = if (res.isSuccess) {
                val otp = res.getOrThrow()
                "OTP ${otp.code} dispatched to ${otp.toNumber}"
            } else {
                res.exceptionOrNull()?.message ?: "Failed to dispatch OTP"
            }
        }
    }

    fun verifyOtpFromConsole(
        toNumber: String,
        code: String,
        data: ConsoleDashboardData
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = sendingEngine.verifyOtpCode(toNumber, code, data.otpVerifications)
            _isBusy.value = false
            if (res.isSuccess) {
                val check = res.getOrThrow()
                _lastOtpCheck.value = check
                _toastMessage.value = check.message
            } else {
                _toastMessage.value = res.exceptionOrNull()?.message ?: "OTP verification error"
            }
        }
    }

    fun createApiKey(label: String, environment: KeyEnvironment, scopes: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.createApiKey(label, environment, scopes)
            _isBusy.value = false
            _toastMessage.value = if (res.isSuccess) {
                "Created API key '${res.getOrThrow().label}'"
            } else {
                res.exceptionOrNull()?.message ?: "Failed to create API key"
            }
        }
    }

    fun toggleApiKey(keyId: String, active: Boolean) {
        viewModelScope.launch {
            val res = repository.toggleApiKeyActive(keyId, active)
            if (res.isFailure) {
                _toastMessage.value = res.exceptionOrNull()?.message ?: "Failed to update key"
            }
        }
    }

    fun deleteApiKey(keyId: String) {
        viewModelScope.launch {
            val res = repository.deleteApiKey(keyId)
            _toastMessage.value = if (res.isSuccess) "API key deleted" else "Failed to delete API key"
        }
    }

    fun provisionGlobalNumber(
        country: CountryCarrierInfo,
        numberType: NumberType,
        customAlphaId: String,
        planTier: PlanTier
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val generatedNumber = when (numberType) {
                NumberType.ALPHANUMERIC -> customAlphaId.trim().ifBlank { "PulseRelay" }.take(11)
                NumberType.SHORT_CODE -> (40000 + secureRandom.nextInt(55000)).toString()
                NumberType.TOLL_FREE -> "${country.dialPrefix}800${1000000 + secureRandom.nextInt(8999999)}"
                NumberType.LOCAL -> "${country.dialPrefix}${country.sampleAreaCode}${100000 + secureRandom.nextInt(899999)}"
            }
            val monthlyPrice = if (planTier == PlanTier.UNLIMITED_DIRECT) 0.0 else when (numberType) {
                NumberType.SHORT_CODE -> 25.0
                NumberType.TOLL_FREE -> 2.15
                else -> 1.15
            }
            val res = repository.provisionPhoneNumber(
                e164Number = generatedNumber,
                countryCode = country.countryCode,
                countryName = country.countryName,
                regionLabel = country.regionLabel,
                numberType = numberType,
                capabilities = if (numberType == NumberType.ALPHANUMERIC) "SMS,OTP" else "SMS,OTP,MMS",
                monthlyPriceUsd = monthlyPrice
            )
            _isBusy.value = false
            _toastMessage.value = if (res.isSuccess) {
                "Provisioned $generatedNumber (${country.countryCode})"
            } else {
                res.exceptionOrNull()?.message ?: "Failed to provision number"
            }
        }
    }

    fun updateNumberConfig(
        number: PhoneNumberRecord,
        webhookUrl: String,
        status: NumberStatus,
        setAsDefault: Boolean,
        allNumbers: List<PhoneNumberRecord>
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.updatePhoneNumber(
                numberId = number.id,
                webhookUrl = webhookUrl,
                status = status,
                setAsDefault = setAsDefault,
                allNumberIds = allNumbers.map { it.id }
            )
            _isBusy.value = false
            _toastMessage.value = if (res.isSuccess) {
                "Updated ${number.e164Number}"
            } else {
                res.exceptionOrNull()?.message ?: "Failed to update number"
            }
        }
    }

    fun releaseNumber(numberId: String) {
        viewModelScope.launch {
            val res = repository.releasePhoneNumber(numberId)
            _toastMessage.value = if (res.isSuccess) "Number released" else "Failed to release number"
        }
    }

    fun updateAccountSettings(planTier: PlanTier, directRouting: Boolean, webhookUrl: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.updateAccountPlan(planTier, directRouting, webhookUrl)
            _isBusy.value = false
            _toastMessage.value = if (res.isSuccess) {
                "Plan updated to ${planTier.displayName}"
            } else {
                res.exceptionOrNull()?.message ?: "Failed to update account settings"
            }
        }
    }

    fun executeLiveHttpApiCall(
        method: String,
        path: String,
        bearerSecret: String,
        jsonBody: String
    ) {
        viewModelScope.launch {
            if (!apiServer.isRunning.value) {
                apiServer.start(8765)
            }
            _isBusy.value = true
            val result = withContext(Dispatchers.IO) {
                val startNs = System.nanoTime()
                try {
                    val url = "http://127.0.0.1:${apiServer.port.value}$path"
                    val builder = Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer $bearerSecret")
                        .header("Accept", "application/json")
                    if (method == "POST") {
                        builder.post(jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    } else {
                        builder.get()
                    }
                    playgroundHttpClient.newCall(builder.build()).execute().use { resp ->
                        val bodyStr = resp.body?.string().orEmpty()
                        val elapsed = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
                        ApiExecutionResult(
                            statusCode = resp.code,
                            latencyMs = elapsed,
                            responseBody = bodyStr,
                            isSuccess = resp.isSuccessful
                        )
                    }
                } catch (e: Exception) {
                    val elapsed = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
                    ApiExecutionResult(
                        statusCode = 0,
                        latencyMs = elapsed,
                        responseBody = """{"error":"connection_failed","message":"${e.localizedMessage}"}""",
                        isSuccess = false
                    )
                }
            }
            _isBusy.value = false
            _lastApiResult.value = result
        }
    }

    fun verifyFirebaseSync() {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.verifyFirebaseLatencyMs()
            _isBusy.value = false
            if (res.isSuccess) {
                val ms = res.getOrThrow()
                _firebaseLatencyMs.value = ms
                _toastMessage.value = "Firebase Firestore verified (${ms}ms round-trip)"
            } else {
                _toastMessage.value = res.exceptionOrNull()?.message ?: "Firebase sync check failed"
            }
        }
    }

    fun fetchGitHubRepos(username: String, personalAccessToken: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = githubClient.fetchRepositories(username, personalAccessToken)
            _isBusy.value = false
            if (res.isSuccess) {
                val repos = res.getOrThrow()
                _fetchedGitHubRepos.value = repos
                _toastMessage.value = "Loaded ${repos.size} GitHub repositories"
            } else {
                _toastMessage.value = res.exceptionOrNull()?.message ?: "Failed to fetch GitHub repos"
            }
        }
    }

    fun linkGitHubRepo(
        username: String,
        repoFullName: String,
        toNumber: String,
        mode: GitHubTriggerMode,
        personalAccessToken: String
    ) {
        viewModelScope.launch {
            if (!sendingEngine.isValidE164(toNumber)) {
                _toastMessage.value = "Recipient must be valid E.164 format (e.g., +14155550199)"
                return@launch
            }
            _isBusy.value = true
            val commitRes = githubClient.fetchLatestCommit(repoFullName, personalAccessToken)
            val commit = commitRes.getOrNull()
            val ownerName = username.trim().ifBlank { repoFullName.substringBefore("/") }
            val saveRes = repository.createGitHubIntegration(
                githubUsername = ownerName,
                repoFullName = repoFullName,
                notifyToNumber = toNumber,
                triggerMode = mode,
                lastCommitSha = commit?.sha.orEmpty(),
                lastCommitMessage = commit?.message.orEmpty()
            )
            _isBusy.value = false
            _toastMessage.value = if (saveRes.isSuccess) {
                "Linked $repoFullName to Firebase & PulseRelay"
            } else {
                saveRes.exceptionOrNull()?.message ?: "Failed to link repository"
            }
        }
    }

    fun triggerGitHubAlert(
        integration: GitHubIntegrationRecord,
        personalAccessToken: String,
        data: ConsoleDashboardData
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val commitRes = githubClient.fetchLatestCommit(integration.repoFullName, personalAccessToken)
            val commit = commitRes.getOrNull()
            val shaShort = commit?.shortSha ?: integration.lastCommitSha.take(7).ifBlank { "HEAD" }
            val msg = commit?.message ?: integration.lastCommitMessage.ifBlank { "New repository event" }
            val author = commit?.authorName ?: integration.githubUsername

            val mode = GitHubTriggerMode.fromRaw(integration.triggerMode)
            val defaultFrom = data.phoneNumbers.firstOrNull { it.defaultSender }?.e164Number
                ?: data.phoneNumbers.firstOrNull()?.e164Number
                ?: "+14158904321"

            if (commit != null) {
                repository.updateGitHubIntegration(
                    integrationId = integration.id,
                    notifyToNumber = integration.notifyToNumber,
                    triggerMode = mode,
                    lastCommitSha = commit.sha,
                    lastCommitMessage = commit.message,
                    active = integration.active
                )
            }

            if (mode == GitHubTriggerMode.DEPLOY_OTP) {
                val otpRes = sendingEngine.sendOtpVerification(
                    toNumber = integration.notifyToNumber,
                    fromNumber = defaultFrom,
                    serviceName = "GitHub Deploy (${integration.repoFullName.substringAfter("/")})",
                    codeLength = 6,
                    channel = OtpChannel.SMS,
                    ttlSeconds = 300,
                    direction = MessageDirection.OUTBOUND_API,
                    apiKeyLabel = "GitHub Integration",
                    settings = data.settings,
                    ownedNumbers = data.phoneNumbers
                )
                _isBusy.value = false
                _toastMessage.value = if (otpRes.isSuccess) {
                    "Deploy OTP ${otpRes.getOrThrow().code} sent for ${integration.repoFullName} ($shaShort)"
                } else {
                    otpRes.exceptionOrNull()?.message ?: "Failed to send deploy OTP"
                }
            } else {
                val body = "[GitHub] ${integration.repoFullName} @ $shaShort by $author: $msg"
                val smsRes = sendingEngine.sendSms(
                    toNumber = integration.notifyToNumber,
                    fromNumber = defaultFrom,
                    body = body,
                    direction = MessageDirection.OUTBOUND_API,
                    apiKeyLabel = "GitHub Integration",
                    settings = data.settings,
                    ownedNumbers = data.phoneNumbers
                )
                _isBusy.value = false
                _toastMessage.value = if (smsRes.isSuccess) {
                    "GitHub alert SMS dispatched for $shaShort"
                } else {
                    smsRes.exceptionOrNull()?.message ?: "Failed to send GitHub SMS alert"
                }
            }
        }
    }

    fun installGitHubWebhook(
        integration: GitHubIntegrationRecord,
        personalAccessToken: String,
        webhookUrl: String
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = githubClient.installWebhook(integration.repoFullName, personalAccessToken, webhookUrl)
            _isBusy.value = false
            _toastMessage.value = res.getOrElse { it.message ?: "Webhook creation failed" }
        }
    }

    fun toggleGitHubIntegration(integration: GitHubIntegrationRecord, active: Boolean) {
        viewModelScope.launch {
            repository.updateGitHubIntegration(
                integrationId = integration.id,
                notifyToNumber = integration.notifyToNumber,
                triggerMode = GitHubTriggerMode.fromRaw(integration.triggerMode),
                lastCommitSha = integration.lastCommitSha,
                lastCommitMessage = integration.lastCommitMessage,
                active = active
            )
        }
    }

    fun deleteGitHubIntegration(integrationId: String) {
        viewModelScope.launch {
            val res = repository.deleteGitHubIntegration(integrationId)
            _toastMessage.value = if (res.isSuccess) "GitHub integration removed" else "Failed to remove integration"
        }
    }

    fun calculateSegments(body: String): Int = sendingEngine.calculateSegments(body)
    fun estimatePrice(segments: Int, planTier: PlanTier, toNumber: String): Double =
        sendingEngine.estimatePriceUsd(segments, planTier, toNumber)

    fun stopServerForSignOut() {
        apiServer.stop()
    }

    override fun onCleared() {
        super.onCleared()
        apiServer.stop()
    }

    private companion object {
        const val TAG = "PulseConsoleVM"
    }
}
