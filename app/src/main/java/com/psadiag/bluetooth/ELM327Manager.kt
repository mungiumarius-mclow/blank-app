package com.psadiag.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import com.psadiag.protocol.PSAProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Core ELM327 communication manager.
 *
 * CRITICAL: Clone ELM327 v1.5 (PIC18F25K80) do NOT support physical CAN addressing.
 * Any ATSH with a physical address (7E0, 7E1, 765...) returns NO DATA.
 * Only ATSH7DF (broadcast) works.
 *
 * Solution: Intercept ALL ATSH commands at 3 levels and convert to:
 *   ATCRA{rxAddr}   - filter responses from target ECU only
 *   ATFCSH{txAddr}  - flow control header = physical address
 *   ATSH7DF         - transmit on broadcast (the only one that works on clones)
 */
class ELM327Manager(private val context: Context) {

    companion object {
        private const val TAG = "ELM327Manager"
        private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val READ_TIMEOUT_MS = 2000L
        private const val PROMPT_CHAR = '>'
    }

    // ── Connection state ──
    enum class ConnectionState {
        DISCONNECTED, CONNECTING, CONNECTED, INITIALIZING, READY, ERROR
    }

    data class ECUInfo(
        val code: String,
        val name: String,
        val txAddress: String,
        val rxAddress: String,
        var partNumber: String = "",
        var calibration: String = ""
    )

    // ── State ──
    var connectionState: ConnectionState = ConnectionState.DISCONNECTED
        private set
    var connectedDeviceName: String = ""
        private set
    var elmVersion: String = ""
        private set
    var detectedECUs: List<ECUInfo> = emptyList()
        private set
    var isCloneAdapter: Boolean = true  // Assume clone until proven otherwise
        private set
    var currentECU: ECUInfo? = null
        private set

    private var onStateChanged: ((ConnectionState) -> Unit)? = null
    private var onLog: ((String) -> Unit)? = null

    // ── Bluetooth ──
    private val bluetoothManager by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        bluetoothManager.adapter
    }
    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    // ── Mutex for serial access ──
    private val commandMutex = Mutex()

    // ── Current ECU filter state ──
    private var currentRxFilter: String = ""
    private var currentFCHeader: String = ""
    private var canBusActive: Boolean = false

    fun setOnStateChanged(listener: (ConnectionState) -> Unit) {
        onStateChanged = listener
    }

    fun setOnLog(listener: (String) -> Unit) {
        onLog = listener
    }

    private fun setState(state: ConnectionState) {
        connectionState = state
        onStateChanged?.invoke(state)
    }

    private fun log(msg: String) {
        Log.d(TAG, msg)
        onLog?.invoke(msg)
    }

    // ═══════════════════════════════════════════════════════
    // BLUETOOTH CONNECTION
    // ═══════════════════════════════════════════════════════

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDevice> {
        return bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
    }

    @SuppressLint("MissingPermission")
    suspend fun connect(device: BluetoothDevice): Boolean = withContext(Dispatchers.IO) {
        try {
            setState(ConnectionState.CONNECTING)
            log("Connecting to ${device.name} (${device.address})...")

            bluetoothAdapter?.cancelDiscovery()

            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket?.connect()

            inputStream = socket?.inputStream
            outputStream = socket?.outputStream

            connectedDeviceName = device.name ?: device.address
            setState(ConnectionState.CONNECTED)
            log("Bluetooth connected to $connectedDeviceName")

            // Initialize ELM327
            val initialized = initializeELM327()
            if (initialized) {
                setState(ConnectionState.READY)
                log("ELM327 ready")
                true
            } else {
                log("ELM327 initialization failed")
                setState(ConnectionState.ERROR)
                false
            }
        } catch (e: IOException) {
            log("Connection failed: ${e.message}")
            setState(ConnectionState.ERROR)
            disconnect()
            false
        }
    }

    fun disconnect() {
        try {
            canBusActive = false
            currentRxFilter = ""
            currentFCHeader = ""
            currentECU = null
            detectedECUs = emptyList()
            inputStream?.close()
            outputStream?.close()
            socket?.close()
        } catch (e: IOException) {
            log("Disconnect error: ${e.message}")
        } finally {
            inputStream = null
            outputStream = null
            socket = null
            connectedDeviceName = ""
            setState(ConnectionState.DISCONNECTED)
        }
    }

    val isConnected: Boolean
        get() = socket?.isConnected == true && connectionState == ConnectionState.READY

    // ═══════════════════════════════════════════════════════
    // ELM327 INITIALIZATION
    // ═══════════════════════════════════════════════════════

    private suspend fun initializeELM327(): Boolean {
        try {
            setState(ConnectionState.INITIALIZING)

            // Reset ELM327 (ONLY at startup, never after CAN is active)
            val resetResponse = sendRawDirect("ATZ")
            log("ATZ -> $resetResponse")
            delay(1000)

            // Parse version
            elmVersion = resetResponse.lines().find { it.contains("ELM327") } ?: "Unknown"
            log("ELM327 version: $elmVersion")

            // Core initialization — MUST keep ATH1/ATS1
            val initCommands = listOf(
                PSAProtocol.AT.ECHO_OFF,          // ATE0
                PSAProtocol.AT.LINEFEED_OFF,       // ATL0
                PSAProtocol.AT.HEADERS_ON,         // ATH1 — CRITICAL: never switch off
                PSAProtocol.AT.SPACES_ON,          // ATS1 — CRITICAL: never switch off
                PSAProtocol.AT.PROTOCOL_CAN_500K_11BIT,  // ATSP6
                PSAProtocol.AT.SET_TIMEOUT,        // ATST64
                PSAProtocol.AT.ADAPTIVE_TIMING_ON  // ATAT1
            )

            for (cmd in initCommands) {
                val response = sendRawDirect(cmd)
                log("$cmd -> $response")
                if (response.contains("ERROR") || response.contains("?")) {
                    log("WARNING: Command $cmd failed")
                }
                delay(100)
            }

            // Setup CAN protocol — detect ECUs with OBD2 broadcast
            val canSetup = setupCANProtocol()
            if (canSetup) {
                canBusActive = true
                // Switch to FAPlite mode for clone adapter compatibility
                switchToFAPliteMode()
            }

            return canSetup
        } catch (e: Exception) {
            log("Init error: ${e.message}")
            return false
        }
    }

    /**
     * Setup CAN protocol: send 0100 (supported PIDs) to detect ECUs on the bus.
     * Parses response headers to discover ECU addresses.
     */
    private suspend fun setupCANProtocol(): Boolean {
        log("Setting up CAN protocol...")

        val response = sendRawDirect(PSAProtocol.OBD2.SUPPORTED_PIDS)
        log("0100 -> $response")

        if (response.contains("NO DATA") || response.contains("UNABLE") || response.contains("ERROR")) {
            log("No CAN bus response — check ignition and adapter")
            return false
        }

        // Parse ECU addresses from response headers
        // Format with ATH1: "7E8 06 41 00 BE 3F A8 13"
        val ecuSet = mutableSetOf<ECUInfo>()
        for (line in response.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            val rxAddr = trimmed.take(3).uppercase()
            val matched = PSAProtocol.ECU.ALL.find { it.rxAddress == rxAddr }
            if (matched != null) {
                ecuSet.add(ECUInfo(matched.code, matched.name, matched.txAddress, matched.rxAddress))
                log("Detected ECU: ${matched.code} (${matched.name}) TX=${matched.txAddress} RX=${matched.rxAddress}")
            }
        }

        detectedECUs = ecuSet.toList()
        log("Found ${detectedECUs.size} ECU(s)")
        return detectedECUs.isNotEmpty()
    }

    /**
     * Switch to FAPlite-compatible mode for clone ELM327 adapters.
     * Sets up CAN filter and flow control using broadcast addressing.
     *
     * FAPlite reference: ATCRA7E8 → ATFCSH7E0 → ATFCSD300000 → ATFCSM1
     * Then ALL communication goes through ATSH7DF (broadcast).
     */
    private suspend fun switchToFAPliteMode() {
        log("Switching to FAPlite mode (clone-compatible broadcast)...")

        // Default to ECM (motor) if available
        val defaultECU = detectedECUs.find { it.code == "ECM" } ?: detectedECUs.firstOrNull()
        if (defaultECU == null) {
            log("No ECU to set FAPlite mode for")
            return
        }

        // Set RX filter — only receive from target ECU
        val craResp = sendRawDirect(PSAProtocol.AT.setReceiveAddress(defaultECU.rxAddress))
        log("ATCRA${defaultECU.rxAddress} -> $craResp")

        // Set flow control header — physical TX address
        val fcshResp = sendRawDirect(PSAProtocol.AT.setFlowControlHeader(defaultECU.txAddress))
        log("ATFCSH${defaultECU.txAddress} -> $fcshResp")

        // Set flow control data: ContinueToSend, no block limit
        val fcsdResp = sendRawDirect(PSAProtocol.AT.FC_DATA)
        log("ATFCSD300000 -> $fcsdResp")

        // Set flow control mode: User Defined
        val fcsmResp = sendRawDirect(PSAProtocol.AT.FC_MODE_USER)
        log("ATFCSM1 -> $fcsmResp")

        // Set broadcast header (the ONLY header that works on clones)
        val shResp = sendRawDirect(PSAProtocol.AT.SET_HEADER_BROADCAST)
        log("ATSH7DF -> $shResp")

        currentRxFilter = defaultECU.rxAddress
        currentFCHeader = defaultECU.txAddress
        currentECU = defaultECU

        log("FAPlite mode active: TX=broadcast(7DF) RX-filter=${defaultECU.rxAddress} FC=${defaultECU.txAddress}")
    }

    // ═══════════════════════════════════════════════════════
    // LEVEL 1: selectECU() — Explicit ECU selection
    // ═══════════════════════════════════════════════════════

    /**
     * Select an ECU for communication.
     * On clone adapters: uses ATSH7DF + ATCRA + ATFCSH instead of direct ATSH.
     */
    suspend fun selectECU(txAddress: String, rxAddress: String): Boolean = commandMutex.withLock {
        try {
            log("Selecting ECU TX=$txAddress RX=$rxAddress")

            // Skip if already targeting this ECU
            if (currentRxFilter == rxAddress && currentFCHeader == txAddress) {
                log("ECU already selected")
                return true
            }

            // CLONE MODE: Never send ATSH with physical address
            // Set RX filter to target ECU
            val craResp = sendRawDirect(PSAProtocol.AT.setReceiveAddress(rxAddress))
            if (craResp.contains("ERROR")) {
                log("ATCRA failed: $craResp")
                return false
            }

            // Set flow control header
            val fcshResp = sendRawDirect(PSAProtocol.AT.setFlowControlHeader(txAddress))
            if (fcshResp.contains("ERROR")) {
                log("ATFCSH failed: $fcshResp")
                return false
            }

            // Flow control data
            sendRawDirect(PSAProtocol.AT.FC_DATA)
            sendRawDirect(PSAProtocol.AT.FC_MODE_USER)

            // ALWAYS broadcast
            sendRawDirect(PSAProtocol.AT.SET_HEADER_BROADCAST)

            currentRxFilter = rxAddress
            currentFCHeader = txAddress
            currentECU = detectedECUs.find { it.txAddress == txAddress }
                ?: ECUInfo("UNK", "Unknown", txAddress, rxAddress)

            log("ECU selected: ${currentECU?.code} via broadcast")
            return true
        } catch (e: Exception) {
            log("selectECU error: ${e.message}")
            return false
        }
    }

    /**
     * Select ECU by code (ECM, TCM, BSI).
     */
    suspend fun selectECU(ecuCode: String): Boolean {
        val ecu = detectedECUs.find { it.code == ecuCode }
            ?: PSAProtocol.ECU.ALL.find { it.code == ecuCode }?.let {
                ECUInfo(it.code, it.name, it.txAddress, it.rxAddress)
            }

        if (ecu == null) {
            log("Unknown ECU code: $ecuCode")
            return false
        }
        return selectECU(ecu.txAddress, ecu.rxAddress)
    }

    // ═══════════════════════════════════════════════════════
    // LEVEL 2: sendCommand() — Intercepts ATSH from DiagnosticService
    // ═══════════════════════════════════════════════════════

    /**
     * Send a diagnostic command, intercepting any ATSH with physical address.
     * Used by DiagnosticService for all ECU communication.
     *
     * If the command starts with "ATSH" and is NOT "ATSH7DF",
     * it's intercepted and converted to ATCRA + ATFCSH + ATSH7DF.
     */
    suspend fun sendCommand(command: String): String = commandMutex.withLock {
        val cmd = command.trim().uppercase()

        // ── ATSH INTERCEPTION ──
        if (cmd.startsWith("ATSH") && cmd != "ATSH7DF") {
            val physicalAddr = cmd.removePrefix("ATSH")
            log("INTERCEPTED ATSH$physicalAddr → converting to broadcast mode")
            return@withLock interceptATSH(physicalAddr)
        }

        // Normal command — send directly
        return@withLock sendRawDirect(cmd)
    }

    /**
     * Convert a physical ATSH to broadcast mode with CRA + FCSH filters.
     */
    private suspend fun interceptATSH(physicalTxAddr: String): String {
        // Determine RX address from TX
        val rxAddr = txToRx(physicalTxAddr)

        log("  → ATCRA$rxAddr")
        sendRawDirect(PSAProtocol.AT.setReceiveAddress(rxAddr))

        log("  → ATFCSH$physicalTxAddr")
        sendRawDirect(PSAProtocol.AT.setFlowControlHeader(physicalTxAddr))

        sendRawDirect(PSAProtocol.AT.FC_DATA)
        sendRawDirect(PSAProtocol.AT.FC_MODE_USER)

        log("  → ATSH7DF")
        val result = sendRawDirect(PSAProtocol.AT.SET_HEADER_BROADCAST)

        currentRxFilter = rxAddr
        currentFCHeader = physicalTxAddr

        return result
    }

    // ═══════════════════════════════════════════════════════
    // LEVEL 3: sendRaw() — Intercepts ATSH from pre-commands
    // ═══════════════════════════════════════════════════════

    /**
     * Send raw data/command with optional pre-commands.
     * Used by DTCManager, TesterPresent, ECUIdentifier, etc.
     *
     * Pre-commands that contain ATSH are intercepted and converted.
     * Everything runs within the same mutex lock to prevent race conditions.
     */
    suspend fun sendRaw(
        data: String,
        preCommands: List<String> = emptyList()
    ): String = commandMutex.withLock {
        // Process pre-commands (intercept any ATSH)
        for (preCmd in preCommands) {
            val cmd = preCmd.trim().uppercase()
            if (cmd.startsWith("ATSH") && cmd != "ATSH7DF") {
                val physicalAddr = cmd.removePrefix("ATSH")
                log("sendRaw: INTERCEPTED pre-command ATSH$physicalAddr")
                interceptATSH(physicalAddr)
            } else {
                sendRawDirect(cmd)
            }
            delay(50)
        }

        // Send the actual data
        return@withLock sendRawDirect(data.trim().uppercase())
    }

    /**
     * Send raw data without pre-commands (convenience method).
     * Still uses mutex for thread safety.
     */
    suspend fun sendRawSimple(data: String): String = commandMutex.withLock {
        sendRawDirect(data.trim().uppercase())
    }

    // ═══════════════════════════════════════════════════════
    // LOW-LEVEL SERIAL COMMUNICATION
    // ═══════════════════════════════════════════════════════

    /**
     * Direct serial send/receive — NO mutex, NO interception.
     * Only called from within locked contexts.
     */
    private suspend fun sendRawDirect(command: String): String = withContext(Dispatchers.IO) {
        val os = outputStream ?: return@withContext "ERROR: Not connected"
        val ins = inputStream ?: return@withContext "ERROR: Not connected"

        try {
            // Send command with CR
            val cmdBytes = "$command\r".toByteArray()
            os.write(cmdBytes)
            os.flush()

            log("TX: $command")

            // Read response until prompt '>'
            val response = readResponse(ins)
            val cleaned = cleanResponse(response, command)
            log("RX: $cleaned")

            cleaned
        } catch (e: IOException) {
            log("IO Error: ${e.message}")
            "ERROR: ${e.message}"
        }
    }

    /**
     * Read from input stream until '>' prompt is received.
     */
    private suspend fun readResponse(inputStream: InputStream): String {
        val buffer = StringBuilder()
        val startTime = System.currentTimeMillis()

        while (System.currentTimeMillis() - startTime < READ_TIMEOUT_MS) {
            if (inputStream.available() > 0) {
                val byte = inputStream.read()
                if (byte == -1) break
                val char = byte.toChar()
                if (char == PROMPT_CHAR) break
                buffer.append(char)
            } else {
                delay(10)
            }
        }

        return buffer.toString()
    }

    /**
     * Clean response: remove echoes, CRs, LFs, extra spaces.
     */
    private fun cleanResponse(raw: String, sentCommand: String): String {
        return raw
            .replace("\r", "\n")
            .replace(sentCommand, "", ignoreCase = true)
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "OK" && !it.startsWith("SEARCHING") }
            .joinToString("\n")
    }

    // ═══════════════════════════════════════════════════════
    // RESPONSE PARSING UTILITIES
    // ═══════════════════════════════════════════════════════

    /**
     * Parse UDS response bytes from ELM327 output.
     * Input: "7E8 06 62 D4 1F 0C 5E" (with headers ON)
     * Output: [0x62, 0xD4, 0x1F, 0x0C, 0x5E]
     */
    fun parseResponseBytes(response: String): List<Int>? {
        val lines = response.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return null

        // Check for errors
        val firstLine = lines.first().trim()
        if (firstLine.contains("NO DATA") || firstLine.contains("ERROR") ||
            firstLine.contains("UNABLE") || firstLine.contains("?")) {
            return null
        }

        // Single-frame response: "7E8 06 62 D4 1F 0C 5E"
        // Multi-frame: first frame "7E8 10 0A 62 ..." then "7E8 21 ..."
        val allBytes = mutableListOf<Int>()
        var isMultiFrame = false

        for (line in lines) {
            val parts = line.trim().split("\\s+".toRegex())
            if (parts.size < 2) continue

            // Skip header (first element is CAN ID like 7E8)
            val dataStart = if (parts[0].length == 3 && parts[0].all { it.isLetterOrDigit() }) 1 else 0

            if (dataStart >= parts.size) continue

            // Check for multi-frame
            val firstByte = parts[dataStart].toIntOrNull(16) ?: continue

            if (!isMultiFrame && (firstByte and 0xF0) == 0x10) {
                // First Frame indicator (10 xx)
                isMultiFrame = true
                // Skip length bytes, start from actual data
                for (i in (dataStart + 2) until parts.size) {
                    parts[i].toIntOrNull(16)?.let { allBytes.add(it) }
                }
            } else if (isMultiFrame && (firstByte and 0xF0) == 0x20) {
                // Consecutive Frame (21, 22, ...)
                for (i in (dataStart + 1) until parts.size) {
                    parts[i].toIntOrNull(16)?.let { allBytes.add(it) }
                }
            } else {
                // Single Frame: first data byte is length
                for (i in (dataStart + 1) until parts.size) {
                    parts[i].toIntOrNull(16)?.let { allBytes.add(it) }
                }
            }
        }

        return if (allBytes.isNotEmpty()) allBytes else null
    }

    /**
     * Check if response is a positive UDS response for a given service.
     * Service 22 (ReadDataById) → positive response starts with 62
     */
    fun isPositiveResponse(responseBytes: List<Int>?, service: Int): Boolean {
        if (responseBytes == null || responseBytes.isEmpty()) return false
        return responseBytes[0] == service + PSAProtocol.UDS.POSITIVE_RESPONSE_OFFSET
    }

    /**
     * Check if response is a negative response (7F).
     */
    fun isNegativeResponse(response: String): Boolean {
        return response.contains(PSAProtocol.UDS.NEGATIVE_RESPONSE)
    }

    // ═══════════════════════════════════════════════════════
    // UTILITIES
    // ═══════════════════════════════════════════════════════

    /**
     * Map TX address to expected RX address.
     * Standard OBD2: RX = TX + 8 (for 7Ex range)
     * BSI: 765 → 76D
     */
    private fun txToRx(txAddr: String): String {
        return when (txAddr.uppercase()) {
            "7E0" -> "7E8"
            "7E1" -> "7E9"
            "7E2" -> "7EA"
            "7E3" -> "7EB"
            "765" -> "76D"
            "766" -> "76E"
            "767" -> "76F"
            else -> {
                // Generic: try TX + 8
                val txInt = txAddr.toIntOrNull(16) ?: return "7E8"
                String.format("%03X", txInt + 8)
            }
        }
    }
}
