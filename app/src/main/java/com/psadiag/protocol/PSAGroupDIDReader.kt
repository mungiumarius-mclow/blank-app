package com.psadiag.protocol

import android.util.Log
import com.psadiag.bluetooth.ELM327Manager

/**
 * Reads individual DIDs from active groups.
 * After PSAGroupScanner detects which groups are active,
 * this class reads the actual DID values using the catalog from PSARealDIDs.
 */
class PSAGroupDIDReader(private val elm: ELM327Manager) {

    companion object {
        private const val TAG = "PSAGroupDIDReader"
    }

    data class DIDValue(
        val did: String,
        val name: String,
        val value: Double,
        val unit: String,
        val rawBytes: List<Int>,
        val formattedValue: String
    )

    /**
     * Read a single DID and decode its value.
     */
    suspend fun readDID(did: String): DIDValue? {
        val definition = PSARealDIDs.findDID(did)
        val command = "${PSAProtocol.UDS.READ_DATA_BY_ID}$did"

        val response = elm.sendRawSimple(command)

        if (response.contains("NO DATA") || response.contains("ERROR")) {
            Log.d(TAG, "DID $did: NO DATA")
            return null
        }

        val allBytes = elm.parseResponseBytes(response) ?: return null

        // Positive response: 62 + DID(2 bytes) + data
        if (allBytes.size < 3 || allBytes[0] != 0x62) {
            Log.w(TAG, "DID $did: unexpected response")
            return null
        }

        // Extract data bytes (after 62 + DID high + DID low)
        val dataBytes = allBytes.drop(3)
        if (dataBytes.isEmpty()) return null

        val value = definition?.decode?.invoke(dataBytes) ?: 0.0
        val name = definition?.name ?: "DID $did"
        val unit = definition?.unit ?: ""

        val formatted = formatValue(value, unit)

        Log.d(TAG, "DID $did ($name): $formatted")

        return DIDValue(
            did = did,
            name = name,
            value = value,
            unit = unit,
            rawBytes = dataBytes,
            formattedValue = formatted
        )
    }

    /**
     * Read multiple DIDs from a list.
     */
    suspend fun readDIDs(dids: List<String>): List<DIDValue> {
        return dids.mapNotNull { readDID(it) }
    }

    /**
     * Read all known DIDs for a specific group.
     */
    suspend fun readGroup(groupPrefix: String): List<DIDValue> {
        val groupDIDs = PSARealDIDs.getDIDsForGroup(groupPrefix)
        return groupDIDs.mapNotNull { readDID(it.did) }
    }

    /**
     * Read all engine DIDs (Group D4).
     */
    suspend fun readEngineData(): List<DIDValue> {
        return PSARealDIDs.ENGINE_DIDS.mapNotNull { readDID(it.did) }
    }

    /**
     * Read all DPF DIDs (Group D5).
     */
    suspend fun readDPFData(): List<DIDValue> {
        return PSARealDIDs.DPF_DIDS.mapNotNull { readDID(it.did) }
    }

    /**
     * Read injector corrections for all 4 cylinders.
     */
    suspend fun readInjectorCorrections(): List<Double> {
        val response = elm.sendRawSimple("${PSAProtocol.UDS.READ_DATA_BY_ID}${PSAProtocol.DIDs.INJECTOR_CORRECTION}")

        if (response.contains("NO DATA") || response.contains("ERROR")) {
            return emptyList()
        }

        val allBytes = elm.parseResponseBytes(response) ?: return emptyList()
        if (allBytes.size < 3 || allBytes[0] != 0x62) return emptyList()

        val dataBytes = allBytes.drop(3)
        return PSARealDIDs.decodeInjectorCorrections(dataBytes)
    }

    private fun formatValue(value: Double, unit: String): String {
        return when {
            unit == "rpm" || unit == "km" || unit == "" -> "%.0f %s".format(value, unit).trim()
            unit == "°C" -> "%.1f %s".format(value, unit)
            unit == "V" -> "%.2f %s".format(value, unit)
            unit == "mm³" -> "%.2f %s".format(value, unit)
            else -> "%.1f %s".format(value, unit)
        }
    }
}
