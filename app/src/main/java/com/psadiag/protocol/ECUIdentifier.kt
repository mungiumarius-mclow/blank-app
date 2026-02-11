package com.psadiag.protocol

import android.util.Log
import com.psadiag.bluetooth.ELM327Manager

/**
 * Identifies an ECU by reading its part number (F080) and calibration (F0FE).
 * Uses UDS ReadDataByIdentifier (service 22).
 */
class ECUIdentifier(private val elm: ELM327Manager) {

    companion object {
        private const val TAG = "ECUIdentifier"
    }

    data class ECUIdentification(
        val partNumber: String = "Unknown",
        val calibration: String = "Unknown",
        val hardwareNumber: String = "Unknown",
        val protocolType: String = "UDS"
    )

    /**
     * Identify the currently selected ECU.
     * Must call selectECU() first.
     */
    suspend fun identify(): ECUIdentification {
        Log.d(TAG, "Identifying ECU...")

        // Open extended diagnostic session
        val sessionResp = elm.sendRawSimple(PSAProtocol.UDS.EXTENDED_SESSION)
        Log.d(TAG, "Session 1003 -> $sessionResp")

        if (sessionResp.contains("NO DATA")) {
            Log.w(TAG, "ECU did not respond to extended session request")
            return ECUIdentification()
        }

        // Read part number (F080)
        val partNumber = readDID(PSAProtocol.DIDs.ECU_PART_NUMBER)
        Log.d(TAG, "Part number: $partNumber")

        // Read calibration (F0FE)
        val calibration = readDID(PSAProtocol.DIDs.ECU_CALIBRATION)
        Log.d(TAG, "Calibration: $calibration")

        // Read hardware number (F091)
        val hardware = readDID(PSAProtocol.DIDs.ECU_HARDWARE_NUMBER)
        Log.d(TAG, "Hardware: $hardware")

        return ECUIdentification(
            partNumber = partNumber,
            calibration = calibration,
            hardwareNumber = hardware
        )
    }

    /**
     * Read a DID and return the ASCII string value.
     */
    private suspend fun readDID(did: String): String {
        val command = "${PSAProtocol.UDS.READ_DATA_BY_ID}$did"
        val response = elm.sendRawSimple(command)

        if (response.contains("NO DATA") || response.contains("ERROR")) {
            return "N/A"
        }

        val bytes = elm.parseResponseBytes(response) ?: return "N/A"

        // Positive response for service 22: starts with 62 + DID bytes
        if (bytes.size < 3 || bytes[0] != 0x62) return "N/A"

        // Skip response code (62) and DID (2 bytes) — data starts at index 3
        val dataBytes = bytes.drop(3)
        if (dataBytes.isEmpty()) return "N/A"

        // Convert to ASCII string (filter printable chars)
        return dataBytes
            .filter { it in 0x20..0x7E }
            .map { it.toChar() }
            .joinToString("")
            .trim()
            .ifEmpty { "N/A" }
    }
}
