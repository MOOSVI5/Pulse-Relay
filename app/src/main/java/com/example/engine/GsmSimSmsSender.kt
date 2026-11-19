package com.example.engine

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

enum class SmsTransportMode(val label: String, val shortTag: String) {
    GSM_SIM("Phone GSM SIM (Real SMS)", "GSM SIM"),
    TWILIO_CLOUD("Twilio Cloud API", "TWILIO CLOUD")
}

data class TwilioGatewayConfig(
    val accountSid: String = "",
    val authToken: String = "",
    val fromNumber: String = ""
) {
    fun isConfigured(): Boolean =
        accountSid.trim().startsWith("AC") &&
            authToken.trim().length >= 16 &&
            fromNumber.trim().startsWith("+")
}

data class SimDiagnostics(
    val hasTelephony: Boolean,
    val hasSendSmsPermission: Boolean,
    val simReady: Boolean,
    val simAbsent: Boolean,
    val carrierName: String,
    val detailLabel: String
)

sealed interface SimRadioOutcome {
    data class SentToCarrier(
        val latencyMs: Long,
        val waitForDeliveryReport: suspend () -> Boolean
    ) : SimRadioOutcome

    data class Failed(val errorMessage: String) : SimRadioOutcome
}

class GsmSimSmsSender(
    private val appContext: Context?
) {
    private val random = SecureRandom()

    fun diagnostics(): SimDiagnostics {
        val ctx = appContext ?: return SimDiagnostics(
            hasTelephony = false,
            hasSendSmsPermission = false,
            simReady = false,
            simAbsent = true,
            carrierName = "No Device Context",
            detailLabel = "Headless test mode (no Android telephony context)"
        )

        val hasTelephony = runCatching {
            ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
        }.getOrDefault(false)

        val hasPermission = ContextCompat.checkSelfPermission(
            ctx,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        val tm = runCatching {
            ctx.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        }.getOrNull()

        val simState = runCatching { tm?.simState ?: TelephonyManager.SIM_STATE_UNKNOWN }
            .getOrDefault(TelephonyManager.SIM_STATE_UNKNOWN)
        val simAbsent = simState == TelephonyManager.SIM_STATE_ABSENT
        val simLocked = simState == TelephonyManager.SIM_STATE_PIN_REQUIRED ||
            simState == TelephonyManager.SIM_STATE_PUK_REQUIRED ||
            simState == TelephonyManager.SIM_STATE_NETWORK_LOCKED
        val simReady = hasTelephony && !simAbsent && !simLocked &&
            (simState == TelephonyManager.SIM_STATE_READY || simState == TelephonyManager.SIM_STATE_UNKNOWN)

        val rawCarrier = runCatching {
            tm?.networkOperatorName?.takeIf { it.isNotBlank() }
                ?: tm?.simOperatorName?.takeIf { it.isNotBlank() }
        }.getOrNull().orEmpty()
        val carrierName = rawCarrier.ifBlank { if (simAbsent) "No SIM" else "GSM Carrier" }

        val detail = when {
            !hasTelephony -> "No cellular radio on this device. Use a SIM phone or switch to Twilio Cloud API."
            !hasPermission -> "Permission needed: tap 'Allow SIM SMS' so Android can send real texts."
            simAbsent -> "No SIM card detected. Insert an active SIM card with SMS balance."
            simState == TelephonyManager.SIM_STATE_PIN_REQUIRED -> "SIM PIN locked. Unlock SIM card in phone settings."
            simState == TelephonyManager.SIM_STATE_PUK_REQUIRED -> "SIM PUK locked. Unlock SIM card before sending."
            simState == TelephonyManager.SIM_STATE_READY -> "GSM SIM Ready · $carrierName"
            else -> "GSM Modem Standby · $carrierName"
        }

        return SimDiagnostics(
            hasTelephony = hasTelephony,
            hasSendSmsPermission = hasPermission,
            simReady = simReady,
            simAbsent = simAbsent,
            carrierName = carrierName,
            detailLabel = detail
        )
    }

    suspend fun dispatchRealSimSms(
        toNumber: String,
        body: String,
        scope: CoroutineScope,
        onDelivered: suspend (Long) -> Unit,
        onDeliveryTimeout: suspend () -> Unit
    ): SimRadioOutcome {
        val ctx = appContext
            ?: return SimRadioOutcome.Failed("Android application context missing")
        val diag = diagnostics()
        if (!diag.hasTelephony) {
            return SimRadioOutcome.Failed("Device has no cellular SIM modem. Test on a SIM phone or use Twilio Cloud API.")
        }
        if (!diag.hasSendSmsPermission) {
            return SimRadioOutcome.Failed("SMS permission not granted. Tap 'Allow SIM SMS' and try again.")
        }
        if (diag.simAbsent) {
            return SimRadioOutcome.Failed("No active SIM card detected in this phone.")
        }
        if (!diag.simReady) {
            return SimRadioOutcome.Failed(diag.detailLabel)
        }

        val smsManager = resolveSmsManager(ctx)
            ?: return SimRadioOutcome.Failed("Android SmsManager unavailable on this device")

        val parts = runCatching { smsManager.divideMessage(body) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?: arrayListOf(body)

        val nonce = abs(random.nextLong()).toString(36)
        val sentAction = "${ctx.packageName}.PULSERELAY_SMS_SENT.$nonce"
        val deliveredAction = "${ctx.packageName}.PULSERELAY_SMS_DELIVERED.$nonce"

        val sentDeferred = CompletableDeferred<Result<Long>>()
        val deliveredDeferred = CompletableDeferred<Boolean>()
        val remainingSent = AtomicInteger(parts.size)
        val remainingDelivered = AtomicInteger(parts.size)
        val startNs = System.nanoTime()

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val elapsed = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
                when (intent?.action) {
                    sentAction -> {
                        if (resultCode == Activity.RESULT_OK) {
                            if (remainingSent.decrementAndGet() <= 0) {
                                sentDeferred.complete(Result.success(elapsed))
                            }
                        } else {
                            sentDeferred.complete(Result.failure(IllegalStateException(mapSmsError(resultCode))))
                        }
                    }

                    deliveredAction -> {
                        if (resultCode == Activity.RESULT_OK) {
                            if (remainingDelivered.decrementAndGet() <= 0) {
                                deliveredDeferred.complete(true)
                            }
                        } else {
                            deliveredDeferred.complete(false)
                        }
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(sentAction)
            addAction(deliveredAction)
        }

        try {
            ContextCompat.registerReceiver(ctx, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        } catch (t: Throwable) {
            return SimRadioOutcome.Failed("Could not register SMS carrier listener: ${t.localizedMessage}")
        }

        val baseReqCode = abs(nonce.hashCode())
        val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        val sentIntents = ArrayList<PendingIntent>(parts.size)
        val deliveredIntents = ArrayList<PendingIntent>(parts.size)
        for (i in parts.indices) {
            val sentIntent = Intent(sentAction).setPackage(ctx.packageName)
            val deliveredIntent = Intent(deliveredAction).setPackage(ctx.packageName)
            sentIntents.add(PendingIntent.getBroadcast(ctx, baseReqCode + i * 2, sentIntent, piFlags))
            deliveredIntents.add(PendingIntent.getBroadcast(ctx, baseReqCode + i * 2 + 1, deliveredIntent, piFlags))
        }

        val invokeError = withContext(Dispatchers.Main.immediate) {
            runCatching {
                if (parts.size == 1) {
                    smsManager.sendTextMessage(
                        toNumber,
                        null,
                        parts[0],
                        sentIntents[0],
                        deliveredIntents[0]
                    )
                } else {
                    smsManager.sendMultipartTextMessage(
                        toNumber,
                        null,
                        parts,
                        sentIntents,
                        deliveredIntents
                    )
                }
            }.exceptionOrNull()
        }

        if (invokeError != null) {
            runCatching { ctx.unregisterReceiver(receiver) }
            return SimRadioOutcome.Failed(
                invokeError.localizedMessage ?: "SIM radio rejected SMS request"
            )
        }

        val sentResult = withTimeoutOrNull(25_000L) { sentDeferred.await() }
        if (sentResult == null) {
            runCatching { ctx.unregisterReceiver(receiver) }
            return SimRadioOutcome.Failed("Timed out waiting for SIM carrier transmission (check cellular signal/SMS balance)")
        }

        if (sentResult.isFailure) {
            runCatching { ctx.unregisterReceiver(receiver) }
            return SimRadioOutcome.Failed(
                sentResult.exceptionOrNull()?.message ?: "Carrier refused SMS transmission"
            )
        }

        val sentLatencyMs = sentResult.getOrDefault(1L)

        // Track asynchronous carrier SMSC delivery confirmation in background without leaking receiver
        scope.launch(Dispatchers.IO) {
            try {
                val deliveredOk = withTimeoutOrNull(30_000L) { deliveredDeferred.await() }
                if (deliveredOk == true) {
                    val totalLatency = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(sentLatencyMs)
                    onDelivered(totalLatency)
                } else {
                    onDeliveryTimeout()
                }
            } finally {
                runCatching { ctx.unregisterReceiver(receiver) }
            }
        }

        return SimRadioOutcome.SentToCarrier(
            latencyMs = sentLatencyMs,
            waitForDeliveryReport = {
                withTimeoutOrNull(30_000L) { deliveredDeferred.await() } == true
            }
        )
    }

    private fun resolveSmsManager(ctx: Context): SmsManager? {
        val base = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ctx.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
        val defaultSubId = runCatching { SmsManager.getDefaultSmsSubscriptionId() }
            .getOrDefault(SubscriptionManager.INVALID_SUBSCRIPTION_ID)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            base != null &&
            defaultSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID
        ) {
            runCatching { base.createForSubscriptionId(defaultSubId) }.getOrNull() ?: base
        } else {
            base
        }
    }

    private fun mapSmsError(resultCode: Int): String = when (resultCode) {
        SmsManager.RESULT_ERROR_NO_SERVICE -> "No cellular network service on SIM"
        SmsManager.RESULT_ERROR_RADIO_OFF -> "Cellular radio is off (turn off Airplane mode)"
        SmsManager.RESULT_ERROR_NULL_PDU -> "Carrier PDU encoding error"
        SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> "Android SMS rate limit reached"
        SmsManager.RESULT_ERROR_SHORT_CODE_NOT_ALLOWED -> "Short code SMS blocked by carrier"
        SmsManager.RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED -> "Short code SMS permanently denied"
        SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "Carrier rejected SMS (check SIM balance, SMSC & signal)"
        else -> "GSM carrier error code $resultCode"
    }
}
