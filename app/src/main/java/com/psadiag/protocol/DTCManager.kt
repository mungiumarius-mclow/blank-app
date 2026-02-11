package com.psadiag.protocol

import android.util.Log
import com.psadiag.bluetooth.ELM327Manager

/**
 * Manages Diagnostic Trouble Codes (DTC) reading and clearing.
 * Uses UDS services:
 *   - 19 02 FF: ReadDTCInformation (reportDTCByStatusMask)
 *   - 14 FF FF FF: ClearDiagnosticInformation
 */
class DTCManager(private val elm: ELM327Manager) {

    companion object {
        private const val TAG = "DTCManager"
    }

    data class DTC(
        val code: String,           // e.g., "P0420"
        val rawBytes: String,       // e.g., "04 20"
        val statusByte: Int,        // DTC status mask
        val description: String,    // Human-readable description
        val isActive: Boolean,      // Currently active
        val isPending: Boolean,     // Pending (not confirmed yet)
        val isStored: Boolean       // Stored in memory
    )

    /**
     * Read all DTCs from the currently selected ECU.
     * Sends UDS 19 02 FF (ReadDTC by status mask, all statuses).
     */
    suspend fun readDTCs(): List<DTC> {
        Log.d(TAG, "Reading DTCs...")

        // Open extended session first
        elm.sendRawSimple(PSAProtocol.UDS.EXTENDED_SESSION)

        // ReadDTCInformation: SubFunction 02 (reportDTCByStatusMask), mask FF (all)
        val response = elm.sendRawSimple("${PSAProtocol.UDS.READ_DTC_INFO}02FF")
        Log.d(TAG, "19 02 FF -> $response")

        if (response.contains("NO DATA") || response.contains("ERROR")) {
            Log.d(TAG, "No DTCs or ECU not responding")
            return emptyList()
        }

        val bytes = elm.parseResponseBytes(response) ?: return emptyList()

        // Positive response: 59 02 {availabilityMask} {DTC1_high} {DTC1_low} {DTC1_status} ...
        if (bytes.isEmpty() || bytes[0] != 0x59) {
            Log.w(TAG, "Unexpected response: ${bytes.joinToString(" ") { "%02X".format(it) }}")
            return emptyList()
        }

        // Skip: 59 (response), 02 (subfunction), availabilityMask (1 byte)
        val dtcData = bytes.drop(3)
        if (dtcData.size < 3) {
            Log.d(TAG, "No DTCs found")
            return emptyList()
        }

        val dtcList = mutableListOf<DTC>()

        // Each DTC = 3 bytes: highByte, lowByte, statusByte
        var i = 0
        while (i + 2 < dtcData.size) {
            val highByte = dtcData[i]
            val lowByte = dtcData[i + 1]
            val statusByte = dtcData[i + 2]

            val dtcCode = decodeDTC(highByte, lowByte)
            val rawHex = "%02X %02X".format(highByte, lowByte)

            val isActive = (statusByte and 0x01) != 0      // testFailed
            val isPending = (statusByte and 0x04) != 0      // pendingDTC
            val isStored = (statusByte and 0x08) != 0       // confirmedDTC

            dtcList.add(
                DTC(
                    code = dtcCode,
                    rawBytes = rawHex,
                    statusByte = statusByte,
                    description = getDTCDescription(dtcCode),
                    isActive = isActive,
                    isPending = isPending,
                    isStored = isStored
                )
            )

            Log.d(TAG, "DTC: $dtcCode (status=0x${"%02X".format(statusByte)}) active=$isActive stored=$isStored")
            i += 3
        }

        Log.d(TAG, "Found ${dtcList.size} DTC(s)")
        return dtcList
    }

    /**
     * Clear all DTCs from the currently selected ECU.
     * Sends UDS 14 FF FF FF (ClearDiagnosticInformation, all groups).
     */
    suspend fun clearDTCs(): Boolean {
        Log.d(TAG, "Clearing DTCs...")

        // Open extended session
        elm.sendRawSimple(PSAProtocol.UDS.EXTENDED_SESSION)

        // Clear DTCs
        val response = elm.sendRawSimple("${PSAProtocol.UDS.CLEAR_DTC}FFFFFF")
        Log.d(TAG, "14 FF FF FF -> $response")

        val bytes = elm.parseResponseBytes(response)
        val success = bytes != null && bytes.isNotEmpty() && bytes[0] == 0x54 // Positive response for 14

        if (success) {
            Log.d(TAG, "DTCs cleared successfully")
        } else {
            Log.w(TAG, "DTC clear failed: $response")
        }

        return success
    }

    /**
     * Decode 2 DTC bytes into standard format (P/C/B/U + 4 hex digits).
     * Byte1: [TT][CC][XXXX] where TT=type, CC=second digit
     * Byte2: [XXXX][XXXX]
     */
    private fun decodeDTC(highByte: Int, lowByte: Int): String {
        val typeCode = (highByte shr 6) and 0x03
        val prefix = when (typeCode) {
            0 -> "P"  // Powertrain
            1 -> "C"  // Chassis
            2 -> "B"  // Body
            3 -> "U"  // Network
            else -> "P"
        }
        val secondDigit = (highByte shr 4) and 0x03
        val thirdDigit = highByte and 0x0F
        val fourthDigit = (lowByte shr 4) and 0x0F
        val fifthDigit = lowByte and 0x0F

        return "$prefix$secondDigit${"%X".format(thirdDigit)}${"%X".format(fourthDigit)}${"%X".format(fifthDigit)}"
    }

    /**
     * Get human-readable description for common PSA DTCs.
     */
    private fun getDTCDescription(code: String): String {
        return PSA_DTC_DESCRIPTIONS[code] ?: "Unknown DTC"
    }

    companion object DTCDescriptions {
        val PSA_DTC_DESCRIPTIONS = mapOf(
            // Engine / Powertrain
            "P0100" to "Mass Air Flow Circuit Malfunction",
            "P0105" to "MAP Sensor Circuit Malfunction",
            "P0110" to "Intake Air Temperature Circuit Malfunction",
            "P0115" to "Engine Coolant Temperature Circuit Malfunction",
            "P0120" to "Throttle Position Sensor Circuit Malfunction",
            "P0130" to "O2 Sensor Circuit Malfunction (Bank 1 Sensor 1)",
            "P0170" to "Fuel Trim Malfunction (Bank 1)",
            "P0190" to "Fuel Rail Pressure Sensor Circuit Malfunction",
            "P0215" to "Engine Shutoff Solenoid Malfunction",
            "P0220" to "Throttle Position Sensor B Circuit Malfunction",
            "P0234" to "Turbocharger Overboost Condition",
            "P0235" to "Turbocharger Boost Sensor A Circuit Malfunction",
            "P0299" to "Turbocharger Underboost Condition",
            "P0380" to "Glow Plug Circuit Malfunction",
            "P0400" to "EGR Flow Malfunction",
            "P0401" to "EGR Insufficient Flow",
            "P0402" to "EGR Excessive Flow",
            "P0420" to "Catalyst Efficiency Below Threshold",
            "P0470" to "Exhaust Pressure Sensor Malfunction",
            "P0480" to "Cooling Fan Relay Circuit Malfunction",
            "P0500" to "Vehicle Speed Sensor Malfunction",
            "P0600" to "Serial Communication Link Malfunction",
            "P0700" to "Transmission Control System Malfunction",
            // DPF related
            "P1434" to "DPF Differential Pressure Sensor",
            "P1497" to "DPF Soot Accumulation Too High",
            "P1498" to "DPF Regeneration Failure",
            "P2002" to "DPF Efficiency Below Threshold",
            "P2452" to "DPF Differential Pressure Sensor Circuit",
            "P2463" to "DPF Soot Accumulation",
            // PSA specific
            "P0049" to "Turbocharger Wastegate Actuator",
            "P0069" to "MAP/Barometric Pressure Correlation",
            "P0093" to "Fuel System Large Leak Detected",
            "P0251" to "Injection Pump Fuel Metering Control A Malfunction",
            "P1164" to "Fuel Rail Pressure Too Low",
            "P1165" to "Fuel Rail Pressure Too High",
            "P1351" to "Glow Plug Control Module",
            "U0001" to "High Speed CAN Communication Bus",
            "U0100" to "Lost Communication With ECM/PCM",
            "U0121" to "Lost Communication With ABS",
            "U0140" to "Lost Communication With BCM"
        )
    }
}
