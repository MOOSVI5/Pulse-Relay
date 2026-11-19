package com.example.ui.viewmodel

import android.content.Context
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
import com.example.data.model.OtpStatus
import com.example.data.model.OtpVerificationRecord
import com.example.data.model.PhoneNumberRecord
import com.example.data.model.PlanTier
import com.example.data.model.SmsMessageRecord
import com.example.data.model.TeamProfileRecord
import com.example.data.model.TeamRole
import com.example.engine.GitHubApiClient
import com.example.engine.GitHubReleaseInfo
import com.example.engine.GitHubRepoSummary
import com.example.engine.GsmSimSmsSender
import com.example.engine.OtpCheckResult
import com.example.engine.PulseApiServer
import com.example.engine.SendingEngine
import com.example.engine.SimDiagnostics
import com.example.engine.SmsTransportMode
import com.example.engine.TwilioGatewayConfig
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
    val githubIntegrations: List<GitHubIntegrationRecord> = emptyList(),
    val teamProfiles: List<TeamProfileRecord> = emptyList()
)

data class ApiExecutionResult(
    val statusCode: Int,
    val latencyMs: Long,
    val responseBody: String,
    val isSuccess: Boolean
)

class PulseConsoleViewModel(
    private val repository: PulseRepository,
    val currentUserId: String,
    appContext: Context? = null
) : ViewModel() {

    private val gsmSimSmsSender = GsmSimSmsSender(appContext?.applicationContext)
    private val sendingEngine = SendingEngine(repository, viewModelScope, gsmSimSmsSender)
    private val apiServer = PulseApiServer(sendingEngine, viewModelScope)
    private val githubClient = GitHubApiClient()
    private val secureRandom = SecureRandom()
    private val playgroundHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val isServerRunning: StateFlow<Boolean> = apiServer.isRunning
    val serverPort: StateFlow<Int> = apiServer.port
    val lanBindEnabled: StateFlow<Boolean> = apiServer.lanBindEnabled
    val lanIpAddresses: StateFlow<List<String>> = apiServer.lanIpAddresses
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

    private val _latestRelease = MutableStateFlow<GitHubReleaseInfo?>(null)
    val latestRelease: StateFlow<GitHubReleaseInfo?> = _latestRelease.asStateFlow()

    private val _activeProfileId = MutableStateFlow("profile_admin")
    val activeProfileId: StateFlow<String> = _activeProfileId.asStateFlow()

    private val _otpServiceEnabled = MutableStateFlow(true)
    val otpServiceEnabled: StateFlow<Boolean> = _otpServiceEnabled.asStateFlow()

    private val _transportMode = MutableStateFlow(SmsTransportMode.GSM_SIM)
    val transportMode: StateFlow<SmsTransportMode> = _transportMode.asStateFlow()

    private val _twilioConfig = MutableStateFlow(TwilioGatewayConfig())
    val twilioConfig: StateFlow<TwilioGatewayConfig> = _twilioConfig.asStateFlow()

    private val _simDiagnostics = MutableStateFlow(gsmSimSmsSender.diagnostics())
    val simDiagnostics: StateFlow<SimDiagnostics> = _simDiagnostics.asStateFlow()

    private val _latestProfilesSnapshot = MutableStateFlow<List<TeamProfileRecord>>(emptyList())

    val dashboardState: StateFlow<UiState<ConsoleDashboardData>> = combine(
        combine(
            repository.observeAccountSettings(currentUserId),
            repository.observeApiKeys(currentUserId),
            repository.observePhoneNumbers(currentUserId)
        ) { settings, keys, numbers -> Triple(settings, keys, numbers) },
        combine(
            repository.observeSmsMessages(currentUserId),
            repository.observeOtpVerifications(currentUserId),
            repository.observeGitHubIntegrations(currentUserId),
            repository.observeTeamProfiles(currentUserId)
        ) { messages, otps, gh, profiles ->
            listOf(messages, otps, gh, profiles)
        }
    ) { left, right ->
        @Suppress("UNCHECKED_CAST")
        val messages = right[0] as List<SmsMessageRecord>
        @Suppress("UNCHECKED_CAST")
        val otps = right[1] as List<OtpVerificationRecord>
        @Suppress("UNCHECKED_CAST")
        val gh = right[2] as List<GitHubIntegrationRecord>
        @Suppress("UNCHECKED_CAST")
        val profiles = right[3] as List<TeamProfileRecord>
        ConsoleDashboardData(
            settings = left.first,
            apiKeys = left.second,
            phoneNumbers = left.third,
            smsMessages = messages,
            otpVerifications = otps,
            githubIntegrations = gh,
            teamProfiles = profiles
        )
    }
        .onEach { data ->
            _latestProfilesSnapshot.value = data.teamProfiles
            if (data.teamProfiles.isNotEmpty() && data.teamProfiles.none { it.id == _activeProfileId.value }) {
                _activeProfileId.value = data.teamProfiles.firstOrNull { it.ownerAdmin }?.id
                    ?: data.teamProfiles.first().id
            }
            apiServer.syncSnapshot(
                settings = data.settings,
                keys = data.apiKeys,
                numbers = data.phoneNumbers,
                messages = data.smsMessages,
                otps = data.otpVerifications
            )
            syncServerTransport()
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
        .let { combined ->
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
            apiServer.start(8765, bindLan = true)
            refreshSimDiagnostics()
        }
    }

    private fun syncServerTransport() {
        apiServer.syncTransport(
            transportMode = _transportMode.value,
            twilioConfig = _twilioConfig.value,
            otpServiceEnabled = _otpServiceEnabled.value
        )
    }

    fun refreshSimDiagnostics() {
        _simDiagnostics.value = gsmSimSmsSender.diagnostics()
        apiServer.refreshNetworkAddresses()
    }

    fun setTransportMode(mode: SmsTransportMode) {
        _transportMode.value = mode
        syncServerTransport()
        _toastMessage.value = "SMS Transport set to ${mode.label}"
    }

    fun updateTwilioConfig(accountSid: String, authToken: String, fromNumber: String) {
        _twilioConfig.value = TwilioGatewayConfig(
            accountSid = accountSid.trim(),
            authToken = authToken.trim(),
            fromNumber = fromNumber.trim()
        )
        syncServerTransport()
        _toastMessage.value = if (_twilioConfig.value.isConfigured()) {
            "Twilio Cloud Gateway credentials active"
        } else {
            "Twilio Cloud credentials incomplete (check SID, Token & +From)"
        }
    }

    fun toggleLanBind(enabled: Boolean) {
        if (!requireActivePermission("configure REST API LAN access") { it.canControlService }) return
        apiServer.setLanBindEnabled(enabled)
        _toastMessage.value = if (enabled) {
            "REST API exposed on Wi-Fi LAN & Localhost (:8765)"
        } else {
            "REST API restricted to Localhost (127.0.0.1:8765)"
        }
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun clearOtpCheck() {
        _lastOtpCheck.value = null
    }

    private fun activeProfile(): TeamProfileRecord? {
        val list = _latestProfilesSnapshot.value
        return list.firstOrNull { it.id == _activeProfileId.value }
            ?: list.firstOrNull { it.ownerAdmin }
            ?: list.firstOrNull()
    }

    private fun requireActivePermission(
        actionLabel: String,
        predicate: (TeamRole) -> Boolean
    ): Boolean {
        val profile = activeProfile() ?: return true
        if (!profile.active) {
            _toastMessage.value = "Profile '${profile.displayName}' is paused. Switch back to Admin for full control."
            return false
        }
        val role = TeamRole.fromRaw(profile.role)
        if (!predicate(role)) {
            _toastMessage.value = "${role.label} profile cannot $actionLabel. Switch to Admin."
            return false
        }
        return true
    }

    fun switchActiveProfile(profile: TeamProfileRecord) {
        _activeProfileId.value = profile.id
        val role = TeamRole.fromRaw(profile.role)
        _toastMessage.value = if (profile.active) {
            "Switched profile to ${profile.displayName} (${role.label})"
        } else {
            "Switched to ${profile.displayName} (PAUSED · read-only)"
        }
    }

    fun toggleOtpService(enabled: Boolean) {
        if (!requireActivePermission("pause or resume the OTP service") { it.canControlService }) return
        _otpServiceEnabled.value = enabled
        syncServerTransport()
        _toastMessage.value = if (enabled) {
            "OTP SMS Sending Service is LIVE"
        } else {
            "OTP SMS Sending Service PAUSED by Admin"
        }
    }

    fun createTeamMember(
        displayName: String,
        contactHandle: String,
        role: TeamRole,
        dailyOtpLimit: Long
    ) {
        if (!requireActivePermission("add team members") { it.canManageTeam }) return
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.createTeamProfile(displayName, contactHandle, role, dailyOtpLimit)
            _isBusy.value = false
            _toastMessage.value = if (res.isSuccess) {
                "Added ${res.getOrThrow().displayName} as ${role.label}"
            } else {
                res.exceptionOrNull()?.message ?: "Failed to add team member"
            }
        }
    }

    fun updateTeamMember(
        profile: TeamProfileRecord,
        displayName: String,
        contactHandle: String,
        role: TeamRole,
        active: Boolean,
        dailyOtpLimit: Long
    ) {
        if (!requireActivePermission("update team roles") { it.canManageTeam }) return
        if (profile.ownerAdmin && (role != TeamRole.ADMIN || !active)) {
            _toastMessage.value = "Owner Admin must stay ADMIN and active"
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.updateTeamProfile(
                profileId = profile.id,
                displayName = displayName,
                contactHandle = contactHandle,
                role = role,
                active = active,
                dailyOtpLimit = dailyOtpLimit
            )
            _isBusy.value = false
            _toastMessage.value = if (res.isSuccess) {
                "Updated $displayName (${role.label})"
            } else {
                res.exceptionOrNull()?.message ?: "Failed to update team member"
            }
        }
    }

    fun deleteTeamMember(profile: TeamProfileRecord) {
        if (!requireActivePermission("remove team members") { it.canManageTeam }) return
        if (profile.ownerAdmin) {
            _toastMessage.value = "Cannot remove Owner Admin profile"
            return
        }
        viewModelScope.launch {
            val res = repository.deleteTeamProfile(profile.id)
            if (res.isSuccess) {
                if (_activeProfileId.value == profile.id) {
                    _activeProfileId.value = "profile_admin"
                }
                _toastMessage.value = "Removed ${profile.displayName}"
            } else {
                _toastMessage.value = res.exceptionOrNull()?.message ?: "Failed to remove team member"
            }
        }
    }

    fun toggleApiServer(start: Boolean) {
        if (!requireActivePermission("control the REST API engine") { it.canControlService }) return
        if (start) {
            apiServer.start(8765, lanBindEnabled.value)
            val lanHint = apiServer.lanIpAddresses.value.firstOrNull()?.let { " & http://$it:8765" }.orEmpty()
            _toastMessage.value = "REST API Server live on http://127.0.0.1:8765$lanHint"
        } else {
            apiServer.stop()
            _toastMessage.value = "REST API Server stopped"
        }
    }

    fun sendSmsFromConsole(
        toNumber: String,
        fromNumber: String,
        body: String,
        data: ConsoleDashboardData
    ) {
        if (!requireActivePermission("send SMS messages") { it.canSendSms }) return
        refreshSimDiagnostics()
        viewModelScope.launch {
            _isBusy.value = true
            val res = sendingEngine.sendSms(
                toNumber = toNumber,
                fromNumber = fromNumber,
                body = body,
                direction = MessageDirection.OUTBOUND_CONSOLE,
                apiKeyLabel = "Developer Console",
                settings = data.settings,
                ownedNumbers = data.phoneNumbers,
                transportMode = _transportMode.value,
                twilioConfig = _twilioConfig.value
            )
            _isBusy.value = false
            _toastMessage.value = if (res.isSuccess) {
                val rec = res.getOrThrow().record
                "SMS sent to ${rec.toNumber} via ${_transportMode.value.shortTag} (${rec.latencyMs}ms)"
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
        data: ConsoleDashboardData,
        ttlSeconds: Int = 300
    ) {
        if (!requireActivePermission("dispatch OTP codes") { it.canSendOtp }) return
        if (!_otpServiceEnabled.value) {
            _toastMessage.value = "OTP Service is PAUSED. Turn it LIVE in Admin control first."
            return
        }
        refreshSimDiagnostics()
        viewModelScope.launch {
            _isBusy.value = true
            val res = sendingEngine.sendOtpVerification(
                toNumber = toNumber,
                fromNumber = fromNumber,
                serviceName = serviceName,
                codeLength = codeLength,
                channel = channel,
                ttlSeconds = ttlSeconds,
                direction = MessageDirection.OUTBOUND_CONSOLE,
                apiKeyLabel = activeProfile()?.displayName ?: "Verify Console",
                settings = data.settings,
                ownedNumbers = data.phoneNumbers,
                transportMode = _transportMode.value,
                twilioConfig = _twilioConfig.value
            )
            _isBusy.value = false
            _toastMessage.value = if (res.isSuccess) {
                val otp = res.getOrThrow()
                "OTP ${otp.code} transmitted to ${otp.toNumber} via ${_transportMode.value.shortTag}"
            } else {
                res.exceptionOrNull()?.message ?: "Failed to dispatch OTP"
            }
        }
    }

    fun revokeOtpChallenge(otp: OtpVerificationRecord) {
        if (!requireActivePermission("revoke pending OTP codes") { it.canSendOtp }) return
        viewModelScope.launch {
            val res = repository.updateOtpStatus(otp.id, OtpStatus.EXPIRED, otp.attempts)
            _toastMessage.value = if (res.isSuccess) {
                "Expired OTP ${otp.sid}"
            } else {
                res.exceptionOrNull()?.message ?: "Failed to expire OTP"
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
        if (!requireActivePermission("create API keys") { it.canManageNumbersAndKeys }) return
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
        if (!requireActivePermission("modify API keys") { it.canManageNumbersAndKeys }) return
        viewModelScope.launch {
            val res = repository.toggleApiKeyActive(keyId, active)
            if (res.isFailure) {
                _toastMessage.value = res.exceptionOrNull()?.message ?: "Failed to update key"
            }
        }
    }

    fun deleteApiKey(keyId: String) {
        if (!requireActivePermission("delete API keys") { it.canManageNumbersAndKeys }) return
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
        if (!requireActivePermission("provision phone numbers") { it.canManageNumbersAndKeys }) return
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
        if (!requireActivePermission("configure phone numbers") { it.canManageNumbersAndKeys }) return
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
        if (!requireActivePermission("release phone numbers") { it.canManageNumbersAndKeys }) return
        viewModelScope.launch {
            val res = repository.releasePhoneNumber(numberId)
            _toastMessage.value = if (res.isSuccess) "Number released" else "Failed to release number"
        }
    }

    fun updateAccountSettings(planTier: PlanTier, directRouting: Boolean, webhookUrl: String) {
        if (!requireActivePermission("update plan & routing") { it.canControlService }) return
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
        refreshSimDiagnostics()
        viewModelScope.launch {
            if (!apiServer.isRunning.value) {
                apiServer.start(8765, lanBindEnabled.value)
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
                _toastMessage.value = "Recipient must be valid E.164 format (e.g., +923001234567)"
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
                ?: "+923008904321"

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
                    ownedNumbers = data.phoneNumbers,
                    transportMode = _transportMode.value,
                    twilioConfig = _twilioConfig.value
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
                    ownedNumbers = data.phoneNumbers,
                    transportMode = _transportMode.value,
                    twilioConfig = _twilioConfig.value
                )
                _isBusy.value = false
                _toastMessage.value = if (smsRes.isSuccess) {
                    "GitHub alert SMS sent via ${_transportMode.value.shortTag} for $shaShort"
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

    fun checkAppUpdate(repoFullName: String, personalAccessToken: String = "") {
        viewModelScope.launch {
            _isBusy.value = true
            val res = githubClient.fetchLatestRelease(repoFullName, personalAccessToken)
            _isBusy.value = false
            if (res.isSuccess) {
                val release = res.getOrThrow()
                _latestRelease.value = release
                _toastMessage.value = "Latest release found: ${release.releaseName}"
            } else {
                _toastMessage.value = res.exceptionOrNull()?.message ?: "Could not check GitHub releases"
            }
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
