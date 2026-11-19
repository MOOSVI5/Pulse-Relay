package com.example.engine

import com.example.data.firebase.PulseRepository
import com.example.data.model.AccountSettings
import com.example.data.model.GlobalCountryCatalog
import com.example.data.model.MessageDirection
import com.example.data.model.MessageStatus
import com.example.data.model.OtpChannel
import com.example.data.model.OtpStatus
import com.example.data.model.OtpVerificationRecord
import com.example.data.model.PhoneNumberRecord
import com.example.data.model.PlanTier
import com.example.data.model.SmsMessageRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

data class SmsDispatchResult(
    val messageId: String,
    val record: SmsMessageRecord
)

data class OtpCheckResult(
    val valid: Boolean,
    val status: OtpStatus,
    val message: String,
    val otpSid: String
)

class SendingEngine(
    private val repository: PulseRepository,
    private val scope: CoroutineScope,
    private val gsmSimSmsSender: GsmSimSmsSender = GsmSimSmsSender(null)
) {
    private val secureRandom = SecureRandom()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    fun simDiagnostics(): SimDiagnostics = gsmSimSmsSender.diagnostics()

    fun normalizePhoneNumber(raw: String): String {
        val stripped = raw.trim().replace(Regex("[\\s()\\-]"), "")
        return if (stripped.startsWith("00") && stripped.length > 4) {
            "+" + stripped.substring(2)
        } else {
            stripped
        }
    }

    fun isValidE164(phone: String): Boolean {
        val normalized = normalizePhoneNumber(phone)
        return normalized.matches(Regex("^\\+[1-9]\\d{6,14}$"))
    }

    fun calculateSegments(body: String): Int {
        if (body.isEmpty()) return 1
        val isGsm7 = body.all { it.code <= 127 }
        return if (isGsm7) {
            if (body.length <= 160) 1 else ceil(body.length / 153.0).toInt().coerceAtMost(20)
        } else {
            if (body.length <= 70) 1 else ceil(body.length / 67.0).toInt().coerceAtMost(20)
        }
    }

    fun estimatePriceUsd(segments: Int, planTier: PlanTier, toNumber: String): Double {
        if (planTier == PlanTier.UNLIMITED_DIRECT) return 0.0
        val carrier = GlobalCountryCatalog.resolveDestination(toNumber)
        return ((carrier.meteredRateUsd * segments) * 10000.0).toInt() / 10000.0
    }

    suspend fun sendSms(
        toNumber: String,
        fromNumber: String,
        body: String,
        direction: MessageDirection,
        apiKeyLabel: String,
        settings: AccountSettings?,
        ownedNumbers: List<PhoneNumberRecord>,
        apiKeyId: String? = null,
        transportMode: SmsTransportMode = SmsTransportMode.GSM_SIM,
        twilioConfig: TwilioGatewayConfig = TwilioGatewayConfig()
    ): Result<SmsDispatchResult> {
        val cleanTo = normalizePhoneNumber(toNumber)
        val cleanFrom = if (transportMode == SmsTransportMode.TWILIO_CLOUD && twilioConfig.fromNumber.isNotBlank()) {
            normalizePhoneNumber(twilioConfig.fromNumber)
        } else {
            fromNumber.trim().ifBlank {
                ownedNumbers.firstOrNull { it.defaultSender }?.e164Number
                    ?: ownedNumbers.firstOrNull()?.e164Number
                    ?: "SIM-GSM"
            }
        }
        val cleanBody = body.trim()

        if (!isValidE164(cleanTo)) {
            return Result.failure(
                IllegalArgumentException("Recipient must include country code in E.164 format (e.g., +923001234567 or +14155550199)")
            )
        }
        if (cleanBody.isEmpty() || cleanBody.length > 1600) {
            return Result.failure(IllegalArgumentException("Message body must be between 1 and 1600 characters"))
        }

        if (transportMode == SmsTransportMode.TWILIO_CLOUD && !twilioConfig.isConfigured()) {
            return Result.failure(
                IllegalArgumentException("Twilio Cloud selected: configure Account SID, Auth Token & From Number in API tab, or switch to Phone GSM SIM.")
            )
        }

        val simDiag = gsmSimSmsSender.diagnostics()
        val carrier = GlobalCountryCatalog.resolveDestination(cleanTo)
        val carrierLabel = if (transportMode == SmsTransportMode.GSM_SIM) {
            "SIM (${simDiag.carrierName}) -> ${carrier.countryCode}"
        } else {
            "Twilio Cloud -> ${carrier.carrierName}"
        }.take(60)
        val segments = calculateSegments(cleanBody).toLong()
        val planTier = PlanTier.fromRaw(settings?.planTier)
        val priceUsd = estimatePriceUsd(segments.toInt(), planTier, cleanTo)
        val sid = "SM${PulseRepository.randomHex(16)}"

        val initialRecord = SmsMessageRecord(
            sid = sid,
            direction = direction.name,
            fromNumber = cleanFrom,
            toNumber = cleanTo,
            countryCode = carrier.countryCode,
            carrierName = carrierLabel,
            body = cleanBody,
            segments = segments,
            status = MessageStatus.QUEUED.name,
            latencyMs = 0L,
            priceUsd = priceUsd,
            apiKeyLabel = apiKeyLabel.ifBlank { "Console" },
            webhookStatus = "NONE"
        )

        val createRes = repository.createSmsMessage(initialRecord)
        if (createRes.isFailure) {
            return Result.failure(createRes.exceptionOrNull() ?: IllegalStateException("Failed to log SMS in workspace"))
        }
        val messageId = createRes.getOrThrow()

        if (!apiKeyId.isNullOrBlank()) {
            repository.incrementApiKeyUsage(apiKeyId)
        }

        val senderObj = ownedNumbers.firstOrNull { it.e164Number == cleanFrom }
        val webhookUrl = senderObj?.webhookUrl?.takeIf { it.isNotBlank() }
            ?: settings?.defaultWebhookUrl?.takeIf { it.isNotBlank() }

        repository.updateSmsDelivery(messageId, MessageStatus.SENDING, 0L, "NONE")

        return when (transportMode) {
            SmsTransportMode.GSM_SIM -> {
                val radioOutcome = gsmSimSmsSender.dispatchRealSimSms(
                    toNumber = cleanTo,
                    body = cleanBody,
                    scope = scope,
                    onDelivered = { totalLatencyMs ->
                        val dlrWebhook = if (!webhookUrl.isNullOrBlank()) {
                            dispatchWebhook(webhookUrl, sid, cleanFrom, cleanTo, cleanBody, "DELIVERED", totalLatencyMs)
                        } else {
                            "SIM_DLR_OK"
                        }
                        repository.updateSmsDelivery(
                            messageId = messageId,
                            status = MessageStatus.DELIVERED,
                            latencyMs = totalLatencyMs,
                            webhookStatus = dlrWebhook
                        )
                    },
                    onDeliveryTimeout = {
                        // Carrier accepted via SMS_SENT; no explicit SMS_DELIVERED report returned within 30s
                    }
                )

                when (radioOutcome) {
                    is SimRadioOutcome.SentToCarrier -> {
                        val sentWebhook = if (!webhookUrl.isNullOrBlank()) {
                            dispatchWebhook(webhookUrl, sid, cleanFrom, cleanTo, cleanBody, "SENT", radioOutcome.latencyMs)
                        } else {
                            "SIM_RADIO_SENT"
                        }
                        repository.updateSmsDelivery(
                            messageId = messageId,
                            status = MessageStatus.SENT,
                            latencyMs = radioOutcome.latencyMs,
                            webhookStatus = sentWebhook
                        )
                        Result.success(
                            SmsDispatchResult(
                                messageId = messageId,
                                record = initialRecord.copy(
                                    id = messageId,
                                    status = MessageStatus.SENT.name,
                                    latencyMs = radioOutcome.latencyMs,
                                    webhookStatus = sentWebhook
                                )
                            )
                        )
                    }

                    is SimRadioOutcome.Failed -> {
                        val failWebhook = if (!webhookUrl.isNullOrBlank()) {
                            dispatchWebhook(webhookUrl, sid, cleanFrom, cleanTo, cleanBody, "FAILED", 0L)
                        } else {
                            "SIM_FAILED"
                        }
                        repository.updateSmsDelivery(
                            messageId = messageId,
                            status = MessageStatus.FAILED,
                            latencyMs = 0L,
                            webhookStatus = failWebhook,
                            errorMessage = radioOutcome.errorMessage
                        )
                        Result.failure(IllegalStateException(radioOutcome.errorMessage))
                    }
                }
            }

            SmsTransportMode.TWILIO_CLOUD -> {
                val cloudRes = dispatchViaTwilioCloud(
                    config = twilioConfig,
                    to = cleanTo,
                    body = cleanBody
                )
                if (cloudRes.isSuccess) {
                    val latencyMs = cloudRes.getOrThrow()
                    val webhookResult = if (!webhookUrl.isNullOrBlank()) {
                        dispatchWebhook(webhookUrl, sid, cleanFrom, cleanTo, cleanBody, "SENT", latencyMs)
                    } else {
                        "TWILIO_201"
                    }
                    repository.updateSmsDelivery(
                        messageId = messageId,
                        status = MessageStatus.SENT,
                        latencyMs = latencyMs,
                        webhookStatus = webhookResult
                    )
                    Result.success(
                        SmsDispatchResult(
                            messageId = messageId,
                            record = initialRecord.copy(
                                id = messageId,
                                status = MessageStatus.SENT.name,
                                latencyMs = latencyMs,
                                webhookStatus = webhookResult
                            )
                        )
                    )
                } else {
                    val err = cloudRes.exceptionOrNull()?.message ?: "Twilio API request failed"
                    val webhookResult = if (!webhookUrl.isNullOrBlank()) {
                        dispatchWebhook(webhookUrl, sid, cleanFrom, cleanTo, cleanBody, "FAILED", 0L)
                    } else {
                        "TWILIO_FAILED"
                    }
                    repository.updateSmsDelivery(
                        messageId = messageId,
                        status = MessageStatus.FAILED,
                        latencyMs = 0L,
                        webhookStatus = webhookResult,
                        errorMessage = err
                    )
                    Result.failure(IllegalStateException(err))
                }
            }
        }
    }

    suspend fun sendOtpVerification(
        toNumber: String,
        fromNumber: String,
        serviceName: String,
        codeLength: Int,
        channel: OtpChannel,
        ttlSeconds: Int,
        direction: MessageDirection,
        apiKeyLabel: String,
        settings: AccountSettings?,
        ownedNumbers: List<PhoneNumberRecord>,
        apiKeyId: String? = null,
        transportMode: SmsTransportMode = SmsTransportMode.GSM_SIM,
        twilioConfig: TwilioGatewayConfig = TwilioGatewayConfig()
    ): Result<OtpVerificationRecord> {
        val cleanTo = normalizePhoneNumber(toNumber)
        if (!isValidE164(cleanTo)) {
            return Result.failure(
                IllegalArgumentException("Recipient must be valid E.164 format (e.g., +923001234567 or +447700900412)")
            )
        }
        val safeLen = codeLength.coerceIn(4, 8)
        val code = buildString(safeLen) {
            repeat(safeLen) { append(secureRandom.nextInt(10)) }
        }
        val cleanService = serviceName.trim().ifBlank { "PulseAuth" }.take(80)
        val cleanFrom = fromNumber.trim().ifBlank {
            ownedNumbers.firstOrNull { it.defaultSender }?.e164Number
                ?: ownedNumbers.firstOrNull()?.e164Number
                ?: "SIM-GSM"
        }
        val ttlMinutes = (ttlSeconds / 60).coerceAtLeast(1)
        val smsBody = "$cleanService verification code: $code. Valid for $ttlMinutes min. Do not share."

        // Dispatch real SMS first so OTP challenge is only activated when carrier radio accepts it
        val smsResult = sendSms(
            toNumber = cleanTo,
            fromNumber = cleanFrom,
            body = smsBody,
            direction = direction,
            apiKeyLabel = apiKeyLabel,
            settings = settings,
            ownedNumbers = ownedNumbers,
            apiKeyId = apiKeyId,
            transportMode = transportMode,
            twilioConfig = twilioConfig
        )
        if (smsResult.isFailure) {
            return Result.failure(
                smsResult.exceptionOrNull() ?: IllegalStateException("Failed to transmit OTP SMS")
            )
        }

        val expiresAt = System.currentTimeMillis() + (ttlSeconds.coerceIn(60, 1800) * 1000L)
        val sid = "VE${PulseRepository.randomHex(16)}"

        val otpRecord = OtpVerificationRecord(
            sid = sid,
            serviceName = cleanService,
            toNumber = cleanTo,
            fromNumber = cleanFrom,
            code = code,
            channel = channel.name,
            status = OtpStatus.PENDING.name,
            attempts = 0L,
            maxAttempts = 5L,
            expiresAtMillis = expiresAt
        )

        val createOtpRes = repository.createOtpVerification(otpRecord)
        if (createOtpRes.isFailure) {
            return Result.failure(createOtpRes.exceptionOrNull() ?: IllegalStateException("SMS sent, but failed to store OTP challenge"))
        }
        return Result.success(otpRecord.copy(id = createOtpRes.getOrThrow()))
    }

    suspend fun verifyOtpCode(
        toNumber: String,
        submittedCode: String,
        activeOtps: List<OtpVerificationRecord>
    ): Result<OtpCheckResult> {
        val cleanTo = normalizePhoneNumber(toNumber)
        val cleanCode = submittedCode.trim()
        val target = activeOtps.firstOrNull {
            normalizePhoneNumber(it.toNumber) == cleanTo && OtpStatus.fromRaw(it.status) == OtpStatus.PENDING
        } ?: return Result.success(
            OtpCheckResult(
                valid = false,
                status = OtpStatus.EXPIRED,
                message = "No active pending OTP found for $cleanTo",
                otpSid = ""
            )
        )

        val now = System.currentTimeMillis()
        if (now > target.expiresAtMillis) {
            repository.updateOtpStatus(target.id, OtpStatus.EXPIRED, target.attempts)
            return Result.success(
                OtpCheckResult(
                    valid = false,
                    status = OtpStatus.EXPIRED,
                    message = "Verification code expired",
                    otpSid = target.sid
                )
            )
        }

        val nextAttempts = target.attempts + 1L
        return if (cleanCode == target.code) {
            repository.updateOtpStatus(target.id, OtpStatus.APPROVED, nextAttempts, verifiedAtMillis = now)
            Result.success(
                OtpCheckResult(
                    valid = true,
                    status = OtpStatus.APPROVED,
                    message = "OTP approved for ${target.serviceName}",
                    otpSid = target.sid
                )
            )
        } else if (nextAttempts >= target.maxAttempts) {
            repository.updateOtpStatus(target.id, OtpStatus.MAX_ATTEMPTS, nextAttempts)
            Result.success(
                OtpCheckResult(
                    valid = false,
                    status = OtpStatus.MAX_ATTEMPTS,
                    message = "Max verification attempts (${target.maxAttempts}) exceeded",
                    otpSid = target.sid
                )
            )
        } else {
            repository.updateOtpStatus(target.id, OtpStatus.PENDING, nextAttempts)
            Result.success(
                OtpCheckResult(
                    valid = false,
                    status = OtpStatus.PENDING,
                    message = "Invalid code. ${target.maxAttempts - nextAttempts} attempts remaining.",
                    otpSid = target.sid
                )
            )
        }
    }

    private suspend fun dispatchViaTwilioCloud(
        config: TwilioGatewayConfig,
        to: String,
        body: String
    ): Result<Long> = withContext(Dispatchers.IO) {
        val startNs = System.nanoTime()
        try {
            val sid = config.accountSid.trim()
            val token = config.authToken.trim()
            val from = normalizePhoneNumber(config.fromNumber)
            val formBody = FormBody.Builder()
                .add("To", to)
                .add("From", from)
                .add("Body", body)
                .build()

            val request = Request.Builder()
                .url("https://api.twilio.com/2010-04-01/Accounts/$sid/Messages.json")
                .header("Authorization", Credentials.basic(sid, token))
                .post(formBody)
                .build()

            httpClient.newCall(request).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                val elapsed = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
                if (resp.isSuccessful) {
                    Result.success(elapsed)
                } else {
                    val msg = runCatching { JSONObject(raw).optString("message") }
                        .getOrNull()
                        ?.takeIf { it.isNotBlank() }
                        ?: "Twilio HTTP ${resp.code}"
                    Result.failure(IllegalStateException(msg))
                }
            }
        } catch (e: Exception) {
            Result.failure(IllegalStateException(e.localizedMessage ?: "Twilio network error"))
        }
    }

    private suspend fun dispatchWebhook(
        url: String,
        sid: String,
        from: String,
        to: String,
        body: String,
        status: String,
        latencyMs: Long
    ): String = withContext(Dispatchers.IO) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return@withContext "INVALID_URL"
        }
        return@withContext try {
            val payload = JSONObject().apply {
                put("MessageSid", sid)
                put("From", from)
                put("To", to)
                put("Body", body)
                put("MessageStatus", status)
                put("LatencyMs", latencyMs)
                put("Timestamp", System.currentTimeMillis())
            }.toString()

            val request = Request.Builder()
                .url(url)
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .header("User-Agent", "PulseRelay-Webhook/1.0")
                .build()

            httpClient.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) "DISPATCHED_${resp.code}" else "HTTP_${resp.code}"
            }
        } catch (e: Exception) {
            "FAILED_${e.javaClass.simpleName}"
        }
    }
}
