package com.psadiag.protocol

import android.util.Log
import com.psadiag.bluetooth.ELM327Manager

/**
 * Scans for active DID groups (0-F) on the currently selected ECU.
 * Sends 22Dx00 for each group and checks for positive response (62) vs negative (7F).
 */
class PSAGroupScanner(private val elm: ELM327Manager) {

    companion object {
        private const val TAG = "PSAGroupScanner"
    }

    data class GroupScanResult(
        val groupIndex: Int,        // 0-F
        val groupPrefix: String,    // "D0"-"DF"
        val isActive: Boolean,
        val description: String
    )

    /**
     * Scan all 16 DID groups (D0xx through DFxx).
     * Must be in extended diagnostic session first.
     */
    suspend fun scanGroups(): List<GroupScanResult> {
        Log.d(TAG, "Starting DID group scan...")

        // Ensure extended session
        elm.sendRawSimple(PSAProtocol.UDS.EXTENDED_SESSION)

        val results = mutableListOf<GroupScanResult>()

        for (groupIndex in 0..0xF) {
            val groupPrefix = "D${"%X".format(groupIndex)}"
            val did = "${groupPrefix}00"
            val command = "${PSAProtocol.UDS.READ_DATA_BY_ID}$did"

            val response = elm.sendRawSimple(command)
            val isActive = !response.contains("NO DATA") &&
                    !response.contains("ERROR") &&
                    !elm.isNegativeResponse(response)

            val description = getGroupDescription(groupIndex)

            results.add(GroupScanResult(groupIndex, groupPrefix, isActive, description))

            if (isActive) {
                Log.d(TAG, "Group $groupPrefix ($description): ACTIVE")
            }
        }

        val activeCount = results.count { it.isActive }
        Log.d(TAG, "Scan complete: $activeCount active groups out of 16")

        return results
    }

    /**
     * Get description for each DID group.
     */
    private fun getGroupDescription(groupIndex: Int): String {
        return when (groupIndex) {
            0x0 -> "Grup D0"
            0x1 -> "Grup D1"
            0x2 -> "Grup D2"
            0x3 -> "Grup D3"
            0x4 -> "Motor (RPM, Temp, Turbo)"
            0x5 -> "DPF/FAP (Funingine, Temp, Regen)"
            0x6 -> "Grup D6"
            0x7 -> "Baterie/Aditiv (Eolys, Tensiune)"
            0x8 -> "Grup D8"
            0x9 -> "Grup D9"
            0xA -> "Grup DA"
            0xB -> "Grup DB"
            0xC -> "Grup DC"
            0xD -> "Grup DD"
            0xE -> "Grup DE"
            0xF -> "Grup DF"
            else -> "Necunoscut"
        }
    }
}
