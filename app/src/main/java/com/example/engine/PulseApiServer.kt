package com.example.engine

import android.util.Base64
import com.example.data.firebase.PulseRepository
import com.example.data.model.AccountSettings
import com.example.data.model.ApiKeyRecord
import com.example.data.model.ApiRequestLog
import com.example.data.model.MessageDirection
import com.example.data.model.OtpChannel
import com.example.data.model.OtpVerificationRecord
import com.example.data.model.PhoneNumberRecord
import com.example.data.model.SmsMessageRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets

class PulseApiServer(
    private val sendingEngine: SendingEngine,
    private val scope: CoroutineScope
) {
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _port = MutableStateFlow(8765)
    val port: StateFlow<Int> = _port.asStateFlow()

    private val _requestLogs = MutableStateFlow<List<ApiRequestLog>>(emptyList())
    val requestLogs: StateFlow<List<ApiRequestLog>> = _requestLogs.asStateFlow()

    @Volatile
    private var currentSettings: AccountSettings? = null
    @Volatile
    private var currentKeys: List<ApiKeyRecord> = emptyList()
    @Volatile
    private var currentNumbers: List<PhoneNumberRecord> = emptyList()
    @Volatile
    private var currentMessages: List<SmsMessageRecord> = emptyList()
    @Volatile
    private var currentOtps: List<OtpVerificationRecord> = emptyList()

    fun syncSnapshot(
        settings: AccountSettings?,
        keys: List<ApiKeyRecord>,
        numbers: List<PhoneNumberRecord>,
        messages: List<SmsMessageRecord>,
        otps: List<OtpVerificationRecord>
    ) {
        currentSettings = settings
        currentKeys = keys
        currentNumbers = numbers
        currentMessages = messages
        currentOtps = otps
    }

    fun start(desiredPort: Int = 8765) {
        if (_isRunning.value) return
        acceptJob = scope.launch(Dispatchers.IO) {
            try {
                val socket = ServerSocket(desiredPort, 32, InetAddress.getByName("127.0.0.1"))
                serverSocket = socket
                _port.value = desiredPort
                _isRunning.value = true

                while (isActive && !socket.isClosed) {
                    val client = try {
                        socket.accept()
                    } catch (_: Exception) {
                        break
                    }
                    launch(Dispatchers.IO) {
                        handleClient(client)
                    }
                }
            } catch (_: Exception) {
                _isRunning.value = false
            }
        }
    }

    fun stop() {
        _isRunning.value = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
        acceptJob?.cancel()
        acceptJob = null
    }

    private suspend fun handleClient(client: Socket) {
        val startNs = System.nanoTime()
        client.use { sock ->
            sock.soTimeout = 5000
            val reader = BufferedReader(InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8))
            val out = sock.getOutputStream()

            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) {
                writeResponse(out, 400, """{"error":"Malformed HTTP request line"}""")
                return
            }
            val method = parts[0].uppercase()
            val path = parts[1].substringBefore("?")

            val headers = mutableMapOf<String, String>()
            var line = reader.readLine()
            while (!line.isNullOrEmpty()) {
                val idx = line.indexOf(':')
                if (idx > 0) {
                    headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
                }
                line = reader.readLine()
            }

            val contentLength = headers["content-length"]?.toIntOrNull()?.coerceIn(0, 65536) ?: 0
            val requestBody = if (contentLength > 0) {
                val buf = CharArray(contentLength)
                var readTotal = 0
                while (readTotal < contentLength) {
                    val count = reader.read(buf, readTotal, contentLength - readTotal)
                    if (count <= 0) break
                    readTotal += count
                }
                String(buf, 0, readTotal)
            } else {
                ""
            }

            if (method == "GET" && path == "/v1/health") {
                val json = JSONObject().apply {
                    put("status", "operational")
                    put("engine", "PulseRelay-Core/1.0")
                    put("port", _port.value)
                    put("active_numbers", currentNumbers.count { it.status == "ACTIVE" })
                    put("unlimited_texts", currentSettings?.unlimitedTexts ?: true)
                }.toString(2)
                writeAndLog(out, method, path, 200, "public", requestBody, json, startNs)
                return
            }

            val matchedKey = authenticate(headers["authorization"])
            if (matchedKey == null) {
                val err = JSONObject().apply {
                    put("error", "unauthorized")
                    put("message", "Provide valid Bearer pr_live_... or pr_test_... API key")
                    put("code", 40101)
                }.toString(2)
                writeAndLog(out, method, path, 401, "unauthorized", requestBody, err, startNs)
                return
            }

            when {
                method == "GET" && path == "/v1/numbers" -> {
                    val arr = JSONArray()
                    currentNumbers.forEach { num ->
                        arr.put(
                            JSONObject().apply {
                                put("sid", num.id)
                                put("phone_number", num.e164Number)
                                put("country", num.countryCode)
                                put("type", num.numberType)
                                put("status", num.status)
                                put("default_sender", num.defaultSender)
                            }
                        )
                    }
                    val res = JSONObject().put("numbers", arr).put("count", arr.length()).toString(2)
                    writeAndLog(out, method, path, 200, matchedKey.label, requestBody, res, startNs)
                }

                method == "GET" && path == "/v1/messages" -> {
                    val arr = JSONArray()
                    currentMessages.take(25).forEach { msg ->
                        arr.put(
                            JSONObject().apply {
                                put("sid", msg.sid)
                                put("to", msg.toNumber)
                                put("from", msg.fromNumber)
                                put("body", msg.body)
                                put("status", msg.status.lowercase())
                                put("segments", msg.segments)
                                put("price_usd", msg.priceUsd)
                            }
                        )
                    }
                    val res = JSONObject().put("messages", arr).put("count", arr.length()).toString(2)
                    writeAndLog(out, method, path, 200, matchedKey.label, requestBody, res, startNs)
                }

                method == "POST" && path == "/v1/messages" -> {
                    val json = runCatching { JSONObject(requestBody) }.getOrNull()
                    if (json == null) {
                        val err = JSONObject().put("error", "invalid_json").toString(2)
                        writeAndLog(out, method, path, 400, matchedKey.label, requestBody, err, startNs)
                        return
                    }
                    val to = json.optString("to", "")
                    val from = json.optString("from", "")
                    val body = json.optString("body", "")

                    val sendRes = sendingEngine.sendSms(
                        toNumber = to,
                        fromNumber = from,
                        body = body,
                        direction = MessageDirection.OUTBOUND_API,
                        apiKeyLabel = matchedKey.label,
                        settings = currentSettings,
                        ownedNumbers = currentNumbers,
                        apiKeyId = matchedKey.id
                    )

                    if (sendRes.isSuccess) {
                        val rec = sendRes.getOrThrow().record
                        val res = JSONObject().apply {
                            put("sid", rec.sid)
                            put("status", rec.status.lowercase())
                            put("to", rec.toNumber)
                            put("from", rec.fromNumber)
                            put("country", rec.countryCode)
                            put("carrier", rec.carrierName)
                            put("segments", rec.segments)
                            put("price_usd", rec.priceUsd)
                            put("unlimited_plan", currentSettings?.unlimitedTexts ?: true)
                        }.toString(2)
                        writeAndLog(out, method, path, 201, matchedKey.label, requestBody, res, startNs)
                    } else {
                        val err = JSONObject()
                            .put("error", "validation_error")
                            .put("message", sendRes.exceptionOrNull()?.message ?: "Send failed")
                            .toString(2)
                        writeAndLog(out, method, path, 400, matchedKey.label, requestBody, err, startNs)
                    }
                }

                method == "POST" && path == "/v1/verify/send" -> {
                    val json = runCatching { JSONObject(requestBody) }.getOrNull()
                    if (json == null) {
                        val err = JSONObject().put("error", "invalid_json").toString(2)
                        writeAndLog(out, method, path, 400, matchedKey.label, requestBody, err, startNs)
                        return
                    }
                    val to = json.optString("to", "")
                    val from = json.optString("from", "")
                    val service = json.optString("service", "PulseAuth Global")
                    val length = json.optInt("length", 6)
                    val ttl = json.optInt("ttl_seconds", 300)

                    val otpRes = sendingEngine.sendOtpVerification(
                        toNumber = to,
                        fromNumber = from,
                        serviceName = service,
                        codeLength = length,
                        channel = OtpChannel.SMS,
                        ttlSeconds = ttl,
                        direction = MessageDirection.OUTBOUND_API,
                        apiKeyLabel = matchedKey.label,
                        settings = currentSettings,
                        ownedNumbers = currentNumbers,
                        apiKeyId = matchedKey.id
                    )

                    if (otpRes.isSuccess) {
                        val otp = otpRes.getOrThrow()
                        val res = JSONObject().apply {
                            put("sid", otp.sid)
                            put("to", otp.toNumber)
                            put("from", otp.fromNumber)
                            put("service", otp.serviceName)
                            put("channel", otp.channel.lowercase())
                            put("status", otp.status.lowercase())
                            put("expires_at_ms", otp.expiresAtMillis)
                        }.toString(2)
                        writeAndLog(out, method, path, 201, matchedKey.label, requestBody, res, startNs)
                    } else {
                        val err = JSONObject()
                            .put("error", "validation_error")
                            .put("message", otpRes.exceptionOrNull()?.message ?: "OTP dispatch failed")
                            .toString(2)
                        writeAndLog(out, method, path, 400, matchedKey.label, requestBody, err, startNs)
                    }
                }

                method == "POST" && path == "/v1/verify/check" -> {
                    val json = runCatching { JSONObject(requestBody) }.getOrNull()
                    if (json == null) {
                        val err = JSONObject().put("error", "invalid_json").toString(2)
                        writeAndLog(out, method, path, 400, matchedKey.label, requestBody, err, startNs)
                        return
                    }
                    val to = json.optString("to", "")
                    val code = json.optString("code", "")
                    val checkRes = sendingEngine.verifyOtpCode(to, code, currentOtps)
                    if (checkRes.isSuccess) {
                        val chk = checkRes.getOrThrow()
                        val res = JSONObject().apply {
                            put("sid", chk.otpSid)
                            put("to", to)
                            put("valid", chk.valid)
                            put("status", chk.status.name.lowercase())
                            put("message", chk.message)
                        }.toString(2)
                        writeAndLog(out, method, path, 200, matchedKey.label, requestBody, res, startNs)
                    } else {
                        val err = JSONObject().put("error", "verify_failed").toString(2)
                        writeAndLog(out, method, path, 400, matchedKey.label, requestBody, err, startNs)
                    }
                }

                else -> {
                    val err = JSONObject()
                        .put("error", "not_found")
                        .put("message", "Unknown route $method $path")
                        .toString(2)
                    writeAndLog(out, method, path, 404, matchedKey.label, requestBody, err, startNs)
                }
            }
        }
    }

    private fun authenticate(authHeader: String?): ApiKeyRecord? {
        if (authHeader.isNullOrBlank()) return null
        val token = when {
            authHeader.startsWith("Bearer ", ignoreCase = true) ->
                authHeader.substring(7).trim()
            authHeader.startsWith("Basic ", ignoreCase = true) -> {
                val encoded = authHeader.substring(6).trim()
                val decoded = runCatching {
                    String(Base64.decode(encoded, Base64.DEFAULT), StandardCharsets.UTF_8)
                }.getOrNull() ?: return null
                decoded.substringAfter(":", decoded).trim()
            }
            else -> return null
        }
        return currentKeys.firstOrNull { it.active && it.secretKey == token }
    }

    private fun writeAndLog(
        out: OutputStream,
        method: String,
        path: String,
        status: Int,
        keyLabel: String,
        reqBody: String,
        resBody: String,
        startNs: Long
    ) {
        writeResponse(out, status, resBody)
        val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
        val log = ApiRequestLog(
            id = PulseRepository.randomHex(8),
            method = method,
            path = path,
            statusCode = status,
            latencyMs = elapsedMs,
            apiKeyUsed = keyLabel,
            requestBody = reqBody,
            responseBody = resBody
        )
        _requestLogs.update { existing -> (listOf(log) + existing).take(40) }
    }

    private fun writeResponse(out: OutputStream, status: Int, body: String) {
        val statusText = when (status) {
            200 -> "OK"
            201 -> "Created"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            404 -> "Not Found"
            else -> "Internal Server Error"
        }
        val bodyBytes = body.toByteArray(StandardCharsets.UTF_8)
        val headers = buildString {
            append("HTTP/1.1 $status $statusText\r\n")
            append("Content-Type: application/json; charset=utf-8\r\n")
            append("Content-Length: ${bodyBytes.size}\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(headers.toByteArray(StandardCharsets.UTF_8))
        out.write(bodyBytes)
        out.flush()
    }
}
