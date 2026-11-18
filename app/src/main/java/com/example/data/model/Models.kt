package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties

enum class PlanTier(val displayName: String, val tagline: String) {
    UNLIMITED_DIRECT("Unlimited Direct", "Flat-rate unlimited global texts & OTPs"),
    PAY_AS_YOU_GO("Pay-As-You-Go", "Metered $0.0075/segment global carrier routing");

    companion object {
        fun fromRaw(raw: String?): PlanTier =
            entries.firstOrNull { it.name == raw } ?: UNLIMITED_DIRECT
    }
}

enum class KeyEnvironment {
    LIVE, TEST;

    companion object {
        fun fromRaw(raw: String?): KeyEnvironment =
            entries.firstOrNull { it.name == raw } ?: LIVE
    }
}

enum class NumberType(val label: String) {
    LOCAL("Local"),
    TOLL_FREE("Toll-Free"),
    SHORT_CODE("Short Code"),
    ALPHANUMERIC("Alpha ID");

    companion object {
        fun fromRaw(raw: String?): NumberType =
            entries.firstOrNull { it.name == raw } ?: LOCAL
    }
}

enum class NumberStatus {
    ACTIVE, PAUSED;

    companion object {
        fun fromRaw(raw: String?): NumberStatus =
            entries.firstOrNull { it.name == raw } ?: ACTIVE
    }
}

enum class MessageDirection(val label: String) {
    OUTBOUND_API("API"),
    OUTBOUND_CONSOLE("Console"),
    INBOUND("Inbound");

    companion object {
        fun fromRaw(raw: String?): MessageDirection =
            entries.firstOrNull { it.name == raw } ?: OUTBOUND_CONSOLE
    }
}

enum class MessageStatus {
    QUEUED, SENDING, SENT, DELIVERED, FAILED;

    companion object {
        fun fromRaw(raw: String?): MessageStatus =
            entries.firstOrNull { it.name == raw } ?: QUEUED
    }
}

enum class OtpChannel {
    SMS, FLASH_SMS;

    companion object {
        fun fromRaw(raw: String?): OtpChannel =
            entries.firstOrNull { it.name == raw } ?: SMS
    }
}

enum class OtpStatus {
    PENDING, APPROVED, EXPIRED, MAX_ATTEMPTS;

    companion object {
        fun fromRaw(raw: String?): OtpStatus =
            entries.firstOrNull { it.name == raw } ?: PENDING
    }
}

@Keep
@IgnoreExtraProperties
data class AccountSettings(
    @DocumentId val id: String = "",
    val userId: String = "",
    val accountSid: String = "",
    val planTier: String = PlanTier.UNLIMITED_DIRECT.name,
    val unlimitedTexts: Boolean = true,
    val directRouting: Boolean = true,
    val apiServerPort: Long = 8765L,
    val defaultWebhookUrl: String = "",
    val totalMessagesSent: Long = 0L,
    val totalOtpVerified: Long = 0L,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ApiKeyRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val label: String = "",
    val secretKey: String = "",
    val environment: String = KeyEnvironment.LIVE.name,
    val scopes: String = "SMS,VERIFY,NUMBERS",
    val active: Boolean = true,
    val requestsCount: Long = 0L,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PhoneNumberRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val e164Number: String = "",
    val countryCode: String = "",
    val countryName: String = "",
    val regionLabel: String = "",
    val numberType: String = NumberType.LOCAL.name,
    val capabilities: String = "SMS,OTP,MMS",
    val monthlyPriceUsd: Double = 0.0,
    val webhookUrl: String = "",
    val defaultSender: Boolean = false,
    val status: String = NumberStatus.ACTIVE.name,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SmsMessageRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val sid: String = "",
    val direction: String = MessageDirection.OUTBOUND_CONSOLE.name,
    val fromNumber: String = "",
    val toNumber: String = "",
    val countryCode: String = "US",
    val carrierName: String = "",
    val body: String = "",
    val segments: Long = 1L,
    val status: String = MessageStatus.QUEUED.name,
    val latencyMs: Long = 0L,
    val priceUsd: Double = 0.0,
    val apiKeyLabel: String = "Console",
    val webhookStatus: String = "NONE",
    val errorMessage: String? = null,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class OtpVerificationRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val sid: String = "",
    val serviceName: String = "PulseAuth Global",
    val toNumber: String = "",
    val fromNumber: String = "",
    val code: String = "",
    val channel: String = OtpChannel.SMS.name,
    val status: String = OtpStatus.PENDING.name,
    val attempts: Long = 0L,
    val maxAttempts: Long = 5L,
    val expiresAtMillis: Long = 0L,
    val verifiedAtMillis: Long? = null,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

enum class GitHubTriggerMode(val label: String) {
    COMMIT_SMS("Commit SMS Alert"),
    DEPLOY_OTP("Deploy 2FA OTP Gate"),
    RELEASE_ALERT("Release Broadcast");

    companion object {
        fun fromRaw(raw: String?): GitHubTriggerMode =
            entries.firstOrNull { it.name == raw } ?: COMMIT_SMS
    }
}

@Keep
@IgnoreExtraProperties
data class GitHubIntegrationRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val githubUsername: String = "",
    val repoFullName: String = "",
    val notifyToNumber: String = "",
    val triggerMode: String = GitHubTriggerMode.COMMIT_SMS.name,
    val lastCommitSha: String = "",
    val lastCommitMessage: String = "",
    val active: Boolean = true,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

enum class TeamRole(
    val label: String,
    val summary: String,
    val canSendOtp: Boolean,
    val canSendSms: Boolean,
    val canManageNumbersAndKeys: Boolean,
    val canManageTeam: Boolean,
    val canControlService: Boolean
) {
    ADMIN(
        label = "Admin",
        summary = "Full control: service toggle, roles, keys, numbers & OTP",
        canSendOtp = true,
        canSendSms = true,
        canManageNumbersAndKeys = true,
        canManageTeam = true,
        canControlService = true
    ),
    OTP_OPERATOR(
        label = "OTP Operator",
        summary = "Send & verify OTP codes and view SMS logs",
        canSendOtp = true,
        canSendSms = true,
        canManageNumbersAndKeys = false,
        canManageTeam = false,
        canControlService = false
    ),
    DEVELOPER(
        label = "Developer",
        summary = "Manage API keys, numbers, REST API & webhooks",
        canSendOtp = true,
        canSendSms = true,
        canManageNumbersAndKeys = true,
        canManageTeam = false,
        canControlService = false
    ),
    VIEWER(
        label = "Viewer",
        summary = "Read-only access to metrics, OTP codes & logs",
        canSendOtp = false,
        canSendSms = false,
        canManageNumbersAndKeys = false,
        canManageTeam = false,
        canControlService = false
    );

    companion object {
        fun fromRaw(raw: String?): TeamRole =
            entries.firstOrNull { it.name == raw } ?: ADMIN
    }
}

@Keep
@IgnoreExtraProperties
data class TeamProfileRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Admin",
    val contactHandle: String = "",
    val role: String = TeamRole.ADMIN.name,
    val ownerAdmin: Boolean = false,
    val active: Boolean = true,
    val dailyOtpLimit: Long = 1000L,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

data class CountryCarrierInfo(
    val countryCode: String,
    val countryName: String,
    val dialPrefix: String,
    val sampleAreaCode: String,
    val regionLabel: String,
    val carrierName: String,
    val avgLatencyMs: Int,
    val meteredRateUsd: Double
)

data class ApiRequestLog(
    val id: String,
    val method: String,
    val path: String,
    val statusCode: Int,
    val latencyMs: Long,
    val apiKeyUsed: String,
    val requestBody: String,
    val responseBody: String,
    val timestampMillis: Long = System.currentTimeMillis()
)

object GlobalCountryCatalog {
    val countries = listOf(
        CountryCarrierInfo("PK", "Pakistan", "+92", "300", "Karachi / Lahore, PK", "Jazz / Zong PK Direct", 128, 0.0110),
        CountryCarrierInfo("US", "United States", "+1", "415", "San Francisco, CA", "T-Mobile US Direct", 92, 0.0075),
        CountryCarrierInfo("GB", "United Kingdom", "+44", "7700", "London, UK", "Vodafone UK Tier-1", 108, 0.0090),
        CountryCarrierInfo("CA", "Canada", "+1", "647", "Toronto, ON", "Rogers Wireless", 98, 0.0075),
        CountryCarrierInfo("AE", "United Arab Emirates", "+971", "50", "Dubai, AE", "Etisalat Carrier", 148, 0.0150),
        CountryCarrierInfo("IN", "India", "+91", "9820", "Mumbai, IN", "Jio DLT Direct", 142, 0.0065),
        CountryCarrierInfo("DE", "Germany", "+49", "30", "Berlin, DE", "Deutsche Telekom", 116, 0.0110),
        CountryCarrierInfo("FR", "France", "+33", "6", "Paris, FR", "Orange Telecom", 122, 0.0115),
        CountryCarrierInfo("JP", "Japan", "+81", "90", "Tokyo, JP", "NTT Docomo Direct", 134, 0.0140),
        CountryCarrierInfo("SG", "Singapore", "+65", "8123", "Singapore, SG", "Singtel Mobile", 112, 0.0105),
        CountryCarrierInfo("AU", "Australia", "+61", "412", "Sydney, NSW", "Telstra Direct", 129, 0.0120),
        CountryCarrierInfo("BR", "Brazil", "+55", "11", "Sao Paulo, BR", "Vivo Direct", 156, 0.0130),
        CountryCarrierInfo("KR", "South Korea", "+82", "10", "Seoul, KR", "SK Telecom", 126, 0.0125),
        CountryCarrierInfo("NL", "Netherlands", "+31", "6", "Amsterdam, NL", "KPN Mobile", 114, 0.0105),
        CountryCarrierInfo("NG", "Nigeria", "+234", "803", "Lagos, NG", "MTN Nigeria Direct", 168, 0.0160)
    )

    fun resolveDestination(e164: String): CountryCarrierInfo {
        val normalized = e164.trim()
        // Sort by longest dial prefix first so +1 doesn't shadow specific prefixes
        return countries
            .sortedByDescending { it.dialPrefix.length }
            .firstOrNull { normalized.startsWith(it.dialPrefix) }
            ?: CountryCarrierInfo("GL", "Global Carrier", "+", "000", "International Direct", "PulseRelay Global Tier-1", 138, 0.0120)
    }
}
