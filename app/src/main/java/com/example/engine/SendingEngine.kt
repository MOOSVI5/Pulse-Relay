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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val scope: CoroutineScope
) {
    private val secureRandom = SecureRandom()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    fun isValidE164(phone: String): Boolean {
        val trimmed = phone.trim()
        return trimmed.matches(Regex("^\\+[1-9]\\d{6,14}$"))
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
        apiKeyId: String? = null
    ): Result<SmsDispatchResult> {
        val cleanTo = toNumber.trim()
        val cleanFrom = fromNumber.trim().ifBlank {
            ownedNumbers.firstOrNull { it.defaultSender }?.e164Number
                ?: ownedNumbers.firstOrNull()?.e164Number
                ?: "+14158904321"
        }
        val cleanBody = body.trim()

        if (!isValidE164(cleanTo)) {
            return Result.failure(IllegalArgumentException("Recipient must be valid E.164 format (e.g., +14155550199)"))
        }
        if (cleanBody.isEmpty() || cleanBody.length > 1600) {
            return Result.failure(IllegalArgumentException("Message body must be between 1 and 1600 characters"))
        }

        val carrier = GlobalCountryCatalog.resolveDestination(cleanTo)
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
            carrierName = carrier.carrierName,
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
            return Result.failure(createRes.exceptionOrNull() ?: IllegalStateException("Failed to enqueue SMS"))
        }
        val messageId = createRes.getOrThrow()

        if (!apiKeyId.isNullOrBlank()) {
            repository.incrementApiKeyUsage(apiKeyId)
        }

        // Resolve webhook URL (number-specific overrides global account webhook)
        val senderObj = ownedNumbers.firstOrNull { it.e164Number == cleanFrom }
        val webhookUrl = senderObj?.webhookUrl?.takeIf { it.isNotBlank() }
            ?: settings?.defaultWebhookUrl?.takeIf { it.isNotBlank() }

        // Execute async carrier routing & webhook callback pipeline
        scope.launch(Dispatchers.IO) {
            delay(180L)
            repository.updateSmsDelivery(messageId, MessageStatus.SENDING, 0L, "NONE")

            val jitter = secureRandom.nextInt(38) - 14
            val deliveryLatency = (carrier.avgLatencyMs + jitter).coerceIn(55, 450).toLong()
            delay(deliveryLatency + 240L)

            val webhookResult = if (!webhookUrl.isNullOrBlank()) {
                dispatchWebhook(webhookUrl, sid, cleanFrom, cleanTo, cleanBody, "DELIVERED", deliveryLatency)
            } else {
                "NONE"
            }

            repository.updateSmsDelivery(
                messageId = messageId,
                status = MessageStatus.DELIVERED,
                latencyMs = deliveryLatency,
                webhookStatus = webhookResult
            )
        }

        return Result.success(SmsDispatchResult(messageId, initialRecord.copy(id = messageId)))
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
        apiKeyId: String? = null
    ): Result<OtpVerificationRecord> {
        val cleanTo = toNumber.trim()
        if (!isValidE164(cleanTo)) {
            return Result.failure(IllegalArgumentException("Recipient must be valid E.164 format (e.g., +447700900412)"))
        }
        val safeLen = codeLength.coerceIn(4, 8)
        val code = buildString(safeLen) {
            repeat(safeLen) { append(secureRandom.nextInt(10)) }
        }
        val cleanService = serviceName.trim().ifBlank { "PulseAuth Global" }.take(80)
        val cleanFrom = fromNumber.trim().ifBlank {
            ownedNumbers.firstOrNull { it.defaultSender }?.e164Number
                ?: ownedNumbers.firstOrNull()?.e164Number
                ?: "+14158904321"
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
            return Result.failure(createOtpRes.exceptionOrNull() ?: IllegalStateException("Failed to create OTP challenge"))
        }
        val otpId = createOtpRes.getOrThrow()

        val ttlMinutes = (ttlSeconds / 60).coerceAtLeast(1)
        val smsBody = "$cleanService verification code: $code. Valid for $ttlMinutes min. Do not share."
        sendSms(
            toNumber = cleanTo,
            fromNumber = cleanFrom,
            body = smsBody,
            direction = direction,
            apiKeyLabel = apiKeyLabel,
            settings = settings,
            ownedNumbers = ownedNumbers,
            apiKeyId = apiKeyId
        )

        return Result.success(otpRecord.copy(id = otpId))
    }

    suspend fun verifyOtpCode(
        toNumber: String,
        submittedCode: String,
        activeOtps: List<OtpVerificationRecord>
    ): Result<OtpCheckResult> {
        val cleanTo = toNumber.trim()
        val cleanCode = submittedCode.trim()
        val target = activeOtps.firstOrNull {
            it.toNumber == cleanTo && OtpStatus.fromRaw(it.status) == OtpStatus.PENDING
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
