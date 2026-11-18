package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.data.model.AccountSettings
import com.example.data.model.ApiKeyRecord
import com.example.data.model.GitHubIntegrationRecord
import com.example.data.model.GitHubTriggerMode
import com.example.data.model.KeyEnvironment
import com.example.data.model.MessageDirection
import com.example.data.model.MessageStatus
import com.example.data.model.NumberStatus
import com.example.data.model.NumberType
import com.example.data.model.OtpChannel
import com.example.data.model.OtpStatus
import com.example.data.model.OtpVerificationRecord
import com.example.data.model.PhoneNumberRecord
import com.example.data.model.PlanTier
import com.example.data.model.SmsMessageRecord
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom

class PulseRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun <T> wrapSnapshotFlow(
        path: String,
        uid: String,
        opType: OperationType,
        upstream: Flow<T>
    ): Flow<T> = upstream.catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, opType, path)
        }
        throw error
    }

    fun observeAccountSettings(uid: String): Flow<AccountSettings?> = flow {
        require(uid.isNotBlank()) { "Authenticated userId required" }
        val docRef = db.collection("users").document(uid)
        emitAll(
            wrapSnapshotFlow(docRef.path, uid, OperationType.GET, docRef.snapshots()).map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(AccountSettings::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else {
                    null
                }
            }
        )
    }

    fun observeApiKeys(uid: String): Flow<List<ApiKeyRecord>> = flow {
        require(uid.isNotBlank()) { "Authenticated userId required" }
        val colRef = db.collection("users").document(uid).collection("api_keys")
            .orderBy("createdAt", Query.Direction.DESCENDING)
        emitAll(
            wrapSnapshotFlow("users/$uid/api_keys", uid, OperationType.LIST, colRef.snapshots()).map { snapshot ->
                snapshot.toObjects(ApiKeyRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
        )
    }

    fun observePhoneNumbers(uid: String): Flow<List<PhoneNumberRecord>> = flow {
        require(uid.isNotBlank()) { "Authenticated userId required" }
        val colRef = db.collection("users").document(uid).collection("phone_numbers")
            .orderBy("createdAt", Query.Direction.DESCENDING)
        emitAll(
            wrapSnapshotFlow("users/$uid/phone_numbers", uid, OperationType.LIST, colRef.snapshots()).map { snapshot ->
                snapshot.toObjects(PhoneNumberRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
        )
    }

    fun observeSmsMessages(uid: String): Flow<List<SmsMessageRecord>> = flow {
        require(uid.isNotBlank()) { "Authenticated userId required" }
        val colRef = db.collection("users").document(uid).collection("sms_messages")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(100)
        emitAll(
            wrapSnapshotFlow("users/$uid/sms_messages", uid, OperationType.LIST, colRef.snapshots()).map { snapshot ->
                snapshot.toObjects(SmsMessageRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
        )
    }

    fun observeOtpVerifications(uid: String): Flow<List<OtpVerificationRecord>> = flow {
        require(uid.isNotBlank()) { "Authenticated userId required" }
        val colRef = db.collection("users").document(uid).collection("otp_verifications")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
        emitAll(
            wrapSnapshotFlow("users/$uid/otp_verifications", uid, OperationType.LIST, colRef.snapshots()).map { snapshot ->
                snapshot.toObjects(OtpVerificationRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
        )
    }

    fun observeGitHubIntegrations(uid: String): Flow<List<GitHubIntegrationRecord>> = flow {
        require(uid.isNotBlank()) { "Authenticated userId required" }
        val colRef = db.collection("users").document(uid).collection("github_integrations")
            .orderBy("createdAt", Query.Direction.DESCENDING)
        emitAll(
            wrapSnapshotFlow("users/$uid/github_integrations", uid, OperationType.LIST, colRef.snapshots()).map { snapshot ->
                snapshot.toObjects(GitHubIntegrationRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
        )
    }

    suspend fun seedWorkspaceIfNeeded(): Result<Unit> {
        var docPath = "users"
        return try {
            val uid = requireUserId()
            val userDocRef = db.collection("users").document(uid)
            docPath = userDocRef.path
            val existing = userDocRef.get().await()
            if (existing.exists()) {
                return Result.success(Unit)
            }

            val accountSid = "AC${randomHex(22)}"
            val batch = db.batch()
            batch.set(
                userDocRef,
                mapOf(
                    "userId" to uid,
                    "accountSid" to accountSid,
                    "planTier" to PlanTier.UNLIMITED_DIRECT.name,
                    "unlimitedTexts" to true,
                    "directRouting" to true,
                    "apiServerPort" to 8765L,
                    "defaultWebhookUrl" to "",
                    "totalMessagesSent" to 3L,
                    "totalOtpVerified" to 1L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            val keysCol = userDocRef.collection("api_keys")
            batch.set(
                keysCol.document("key_prod_primary"),
                mapOf(
                    "userId" to uid,
                    "label" to "Production Web & App",
                    "secretKey" to "pr_live_${randomHex(24)}",
                    "environment" to KeyEnvironment.LIVE.name,
                    "scopes" to "SMS,VERIFY,NUMBERS",
                    "active" to true,
                    "requestsCount" to 12L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                keysCol.document("key_sandbox_dev"),
                mapOf(
                    "userId" to uid,
                    "label" to "Staging Sandbox",
                    "secretKey" to "pr_test_${randomHex(24)}",
                    "environment" to KeyEnvironment.TEST.name,
                    "scopes" to "SMS,VERIFY",
                    "active" to true,
                    "requestsCount" to 4L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            val numbersCol = userDocRef.collection("phone_numbers")
            batch.set(
                numbersCol.document("num_us_sf"),
                mapOf(
                    "userId" to uid,
                    "e164Number" to "+14158904321",
                    "countryCode" to "US",
                    "countryName" to "United States",
                    "regionLabel" to "San Francisco, CA",
                    "numberType" to NumberType.LOCAL.name,
                    "capabilities" to "SMS,OTP,MMS",
                    "monthlyPriceUsd" to 0.0,
                    "webhookUrl" to "",
                    "defaultSender" to true,
                    "status" to NumberStatus.ACTIVE.name,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                numbersCol.document("num_gb_lon"),
                mapOf(
                    "userId" to uid,
                    "e164Number" to "+447700900412",
                    "countryCode" to "GB",
                    "countryName" to "United Kingdom",
                    "regionLabel" to "London, UK",
                    "numberType" to NumberType.LOCAL.name,
                    "capabilities" to "SMS,OTP",
                    "monthlyPriceUsd" to 0.0,
                    "webhookUrl" to "",
                    "defaultSender" to false,
                    "status" to NumberStatus.ACTIVE.name,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                numbersCol.document("num_jp_tyo"),
                mapOf(
                    "userId" to uid,
                    "e164Number" to "+819044128890",
                    "countryCode" to "JP",
                    "countryName" to "Japan",
                    "regionLabel" to "Tokyo, JP",
                    "numberType" to NumberType.LOCAL.name,
                    "capabilities" to "SMS,OTP",
                    "monthlyPriceUsd" to 0.0,
                    "webhookUrl" to "",
                    "defaultSender" to false,
                    "status" to NumberStatus.ACTIVE.name,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            val msgCol = userDocRef.collection("sms_messages")
            batch.set(
                msgCol.document("msg_seed_1"),
                mapOf(
                    "userId" to uid,
                    "sid" to "SM${randomHex(16)}",
                    "direction" to MessageDirection.OUTBOUND_API.name,
                    "fromNumber" to "+14158904321",
                    "toNumber" to "+447911123456",
                    "countryCode" to "GB",
                    "carrierName" to "Vodafone UK Tier-1",
                    "body" to "Your PulseRelay login verification code is 849201. Expires in 5m.",
                    "segments" to 1L,
                    "status" to MessageStatus.DELIVERED.name,
                    "latencyMs" to 104L,
                    "priceUsd" to 0.0,
                    "apiKeyLabel" to "Production Web & App",
                    "webhookStatus" to "NONE",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                msgCol.document("msg_seed_2"),
                mapOf(
                    "userId" to uid,
                    "sid" to "SM${randomHex(16)}",
                    "direction" to MessageDirection.OUTBOUND_API.name,
                    "fromNumber" to "+447700900412",
                    "toNumber" to "+4915123456789",
                    "countryCode" to "DE",
                    "carrierName" to "Deutsche Telekom",
                    "body" to "Order #PR-4812 shipped via DHL Express. Track live in app.",
                    "segments" to 1L,
                    "status" to MessageStatus.DELIVERED.name,
                    "latencyMs" to 118L,
                    "priceUsd" to 0.0,
                    "apiKeyLabel" to "Production Web & App",
                    "webhookStatus" to "NONE",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            val otpCol = userDocRef.collection("otp_verifications")
            batch.set(
                otpCol.document("otp_seed_1"),
                mapOf(
                    "userId" to uid,
                    "sid" to "VE${randomHex(16)}",
                    "serviceName" to "PulseAuth Global",
                    "toNumber" to "+447911123456",
                    "fromNumber" to "+14158904321",
                    "code" to "849201",
                    "channel" to OtpChannel.SMS.name,
                    "status" to OtpStatus.APPROVED.name,
                    "attempts" to 1L,
                    "maxAttempts" to 5L,
                    "expiresAtMillis" to (System.currentTimeMillis() + 300_000L),
                    "verifiedAtMillis" to System.currentTimeMillis(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateAccountPlan(
        planTier: PlanTier,
        directRouting: Boolean,
        defaultWebhookUrl: String
    ): Result<Unit> {
        var docPath = "users"
        return try {
            val uid = requireUserId()
            val cleanWebhook = defaultWebhookUrl.trim().take(500)
            val docRef = db.collection("users").document(uid)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "planTier" to planTier.name,
                    "unlimitedTexts" to (planTier == PlanTier.UNLIMITED_DIRECT),
                    "directRouting" to directRouting,
                    "defaultWebhookUrl" to cleanWebhook,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun createApiKey(label: String, environment: KeyEnvironment, scopes: String): Result<ApiKeyRecord> {
        var docPath = "users"
        return try {
            val uid = requireUserId()
            val cleanLabel = label.trim().take(80)
            require(cleanLabel.isNotBlank()) { "Key label cannot be empty" }
            val cleanScopes = scopes.trim().ifBlank { "SMS,VERIFY" }.take(120)
            val prefix = if (environment == KeyEnvironment.LIVE) "pr_live_" else "pr_test_"
            val secret = "$prefix${randomHex(24)}"
            val docRef = db.collection("users").document(uid).collection("api_keys").document()
            docPath = docRef.path
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "label" to cleanLabel,
                    "secretKey" to secret,
                    "environment" to environment.name,
                    "scopes" to cleanScopes,
                    "active" to true,
                    "requestsCount" to 0L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(
                ApiKeyRecord(
                    id = docRef.id,
                    userId = uid,
                    label = cleanLabel,
                    secretKey = secret,
                    environment = environment.name,
                    scopes = cleanScopes,
                    active = true,
                    requestsCount = 0L
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleApiKeyActive(keyId: String, active: Boolean): Result<Unit> {
        var docPath = "users"
        return try {
            val uid = requireUserId()
            require(keyId.isNotBlank()) { "Key ID required" }
            val docRef = db.collection("users").document(uid).collection("api_keys").document(keyId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "active" to active,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun incrementApiKeyUsage(keyId: String): Result<Unit> {
        var docPath = "users"
        return try {
            val uid = requireUserId()
            if (keyId.isBlank()) return Result.success(Unit)
            val docRef = db.collection("users").document(uid).collection("api_keys").document(keyId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "requestsCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteApiKey(keyId: String): Result<Unit> {
        var docPath = "users"
        return try {
            val uid = requireUserId()
            require(keyId.isNotBlank()) { "Key ID required" }
            val docRef = db.collection("users").document(uid).collection("api_keys").document(keyId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun provisionPhoneNumber(
        e164Number: String,
        countryCode: String,
        countryName: String,
        regionLabel: String,
        numberType: NumberType,
        capabilities: String,
        monthlyPriceUsd: Double
    ): Result<PhoneNumberRecord> {
        var docPath = "users"
        return try {
            val uid = requireUserId()
            val cleanNumber = e164Number.trim().take(24)
            require(cleanNumber.length >= 2) { "Number must be at least 2 characters" }
            val docRef = db.collection("users").document(uid).collection("phone_numbers").document()
            docPath = docRef.path
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "e164Number" to cleanNumber,
                    "countryCode" to countryCode.take(4),
                    "countryName" to countryName.take(60),
                    "regionLabel" to regionLabel.take(80),
                    "numberType" to numberType.name,
                    "capabilities" to capabilities.take(60),
                    "monthlyPriceUsd" to monthlyPriceUsd.coerceAtLeast(0.0),
                    "webhookUrl" to "",
                    "defaultSender" to false,
                    "status" to NumberStatus.ACTIVE.name,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(
                PhoneNumberRecord(
                    id = docRef.id,
                    userId = uid,
                    e164Number = cleanNumber,
                    countryCode = countryCode,
                    countryName = countryName,
                    regionLabel = regionLabel,
                    numberType = numberType.name,
                    capabilities = capabilities,
                    monthlyPriceUsd = monthlyPriceUsd,
                    status = NumberStatus.ACTIVE.name
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updatePhoneNumber(
        numberId: String,
        webhookUrl: String,
        status: NumberStatus,
        setAsDefault: Boolean,
        allNumberIds: List<String>
    ): Result<Unit> {
        var docPath = "users"
        return try {
            val uid = requireUserId()
            require(numberId.isNotBlank()) { "Number ID required" }
            val colRef = db.collection("users").document(uid).collection("phone_numbers")
            val targetRef = colRef.document(numberId)
            docPath = targetRef.path
            val batch = db.batch()
            if (setAsDefault) {
                allNumberIds.filter { it.isNotBlank() && it != numberId }.forEach { otherId ->
                    batch.update(
                        colRef.document(otherId),
                        mapOf(
                            "defaultSender" to false,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }
            batch.update(
                targetRef,
                mapOf(
                    "webhookUrl" to webhookUrl.trim().take(500),
                    "status" to status.name,
                    "defaultSender" to setAsDefault,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun releasePhoneNumber(numberId: String): Result<Unit> {
        var docPath = "users"
        return try {
            val uid = requireUserId()
            require(numberId.isNotBlank()) { "Number ID required" }
            val docRef = db.collection("users").document(uid).collection("phone_numbers").document(numberId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun createSmsMessage(record: SmsMessageRecord): Result<String> {
        var docPath = "users"
        return try {
            val uid = requireUserId()
            val userRef = db.collection("users").document(uid)
            val docRef = userRef.collection("sms_messages").document()
            docPath = docRef.path

            val payload = mapOf<String, Any?>(
                "userId" to uid,
                "sid" to record.sid.take(48),
                "direction" to record.direction,
                "fromNumber" to record.fromNumber.take(24),
                "toNumber" to record.toNumber.take(24),
                "countryCode" to record.countryCode.take(4),
                "carrierName" to record.carrierName.take(60),
                "body" to record.body.take(1600),
                "segments" to record.segments.coerceIn(1L, 20L),
                "status" to record.status,
                "latencyMs" to record.latencyMs.coerceIn(0L, 60000L),
                "priceUsd" to record.priceUsd.coerceAtLeast(0.0),
                "apiKeyLabel" to record.apiKeyLabel.take(80),
                "webhookStatus" to record.webhookStatus.take(120),
                "errorMessage" to record.errorMessage?.take(300),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>

            val batch = db.batch()
            batch.set(docRef, payload)
            batch.update(
                userRef,
                mapOf(
                    "totalMessagesSent" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateSmsDelivery(
        messageId: String,
        status: MessageStatus,
        latencyMs: Long,
        webhookStatus: String,
        errorMessage: String? = null
    ): Result<Unit> {
        var docPath = "users"
        return try {
            val uid = requireUserId()
            require(messageId.isNotBlank()) { "Message ID required" }
            val docRef = db.collection("users").document(uid).collection("sms_messages").document(messageId)
            docPath = docRef.path
            val payload = mapOf<String, Any?>(
                "status" to status.name,
                "latencyMs" to latencyMs.coerceIn(0L, 60000L),
                "webhookStatus" to webhookStatus.take(120),
                "errorMessage" to errorMessage?.take(300),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>
            docRef.update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun createOtpVerification(record: OtpVerificationRecord): Result<String> {
        var docPath = "users"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("otp_verifications").document()
            docPath = docRef.path
            val payload = mapOf<String, Any?>(
                "userId" to uid,
                "sid" to record.sid.take(48),
                "serviceName" to record.serviceName.take(80),
                "toNumber" to record.toNumber.take(24),
                "fromNumber" to record.fromNumber.take(24),
                "code" to record.code.take(10),
                "channel" to record.channel,
                "status" to record.status,
                "attempts" to record.attempts.coerceIn(0L, 20L),
                "maxAttempts" to record.maxAttempts.coerceIn(1L, 10L),
                "expiresAtMillis" to record.expiresAtMillis,
                "verifiedAtMillis" to record.verifiedAtMillis,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateOtpStatus(
        otpId: String,
        newStatus: OtpStatus,
        attempts: Long,
        verifiedAtMillis: Long? = null
    ): Result<Unit> {
        var docPath = "users"
        return try {
            val uid = requireUserId()
            require(otpId.isNotBlank()) { "OTP ID required" }
            val userRef = db.collection("users").document(uid)
            val otpRef = userRef.collection("otp_verifications").document(otpId)
            docPath = otpRef.path
            val payload = mapOf<String, Any?>(
                "status" to newStatus.name,
                "attempts" to attempts.coerceIn(0L, 20L),
                "verifiedAtMillis" to verifiedAtMillis,
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>

            val batch = db.batch()
            batch.update(otpRef, payload)
            if (newStatus == OtpStatus.APPROVED) {
                batch.update(
                    userRef,
                    mapOf(
                        "totalOtpVerified" to FieldValue.increment(1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun verifyFirebaseLatencyMs(): Result<Long> {
        var docPath = "users"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
            docPath = docRef.path
            val start = System.nanoTime()
            docRef.get(com.google.firebase.firestore.Source.SERVER).await()
            val elapsedMs = ((System.nanoTime() - start) / 1_000_000L).coerceAtLeast(1L)
            Result.success(elapsedMs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docPath)
            Result.failure(e)
        }
    }

    suspend fun createGitHubIntegration(
        githubUsername: String,
        repoFullName: String,
        notifyToNumber: String,
        triggerMode: GitHubTriggerMode,
        lastCommitSha: String = "",
        lastCommitMessage: String = ""
    ): Result<GitHubIntegrationRecord> {
        var docPath = "users"
        return try {
            val uid = requireUserId()
            val cleanUser = githubUsername.trim().take(64)
            val cleanRepo = repoFullName.trim().take(140)
            val cleanNumber = notifyToNumber.trim().take(24)
            require(cleanUser.isNotBlank()) { "GitHub username required" }
            require(cleanRepo.length >= 3) { "Valid owner/repo required" }
            require(cleanNumber.length >= 4) { "Valid E.164 recipient number required" }

            val docRef = db.collection("users").document(uid).collection("github_integrations").document()
            docPath = docRef.path
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "githubUsername" to cleanUser,
                    "repoFullName" to cleanRepo,
                    "notifyToNumber" to cleanNumber,
                    "triggerMode" to triggerMode.name,
                    "lastCommitSha" to lastCommitSha.take(64),
                    "lastCommitMessage" to lastCommitMessage.take(300),
                    "active" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(
                GitHubIntegrationRecord(
                    id = docRef.id,
                    userId = uid,
                    githubUsername = cleanUser,
                    repoFullName = cleanRepo,
                    notifyToNumber = cleanNumber,
                    triggerMode = triggerMode.name,
                    lastCommitSha = lastCommitSha,
                    lastCommitMessage = lastCommitMessage,
                    active = true
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateGitHubIntegration(
        integrationId: String,
        notifyToNumber: String,
        triggerMode: GitHubTriggerMode,
        lastCommitSha: String,
        lastCommitMessage: String,
        active: Boolean
    ): Result<Unit> {
        var docPath = "users"
        return try {
            val uid = requireUserId()
            require(integrationId.isNotBlank()) { "Integration ID required" }
            val docRef = db.collection("users").document(uid).collection("github_integrations").document(integrationId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "notifyToNumber" to notifyToNumber.trim().take(24),
                    "triggerMode" to triggerMode.name,
                    "lastCommitSha" to lastCommitSha.take(64),
                    "lastCommitMessage" to lastCommitMessage.take(300),
                    "active" to active,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteGitHubIntegration(integrationId: String): Result<Unit> {
        var docPath = "users"
        return try {
            val uid = requireUserId()
            require(integrationId.isNotBlank()) { "Integration ID required" }
            val docRef = db.collection("users").document(uid).collection("github_integrations").document(integrationId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    companion object {
        private val random = SecureRandom()
        private const val HEX = "0123456789abcdef"

        fun randomHex(length: Int): String {
            return buildString(length) {
                repeat(length) { append(HEX[random.nextInt(HEX.length)]) }
            }
        }
    }
}
