package com.psadiag.protocol

/**
 * Real PSA DID catalog with descriptions, units, and decoding formulas.
 * Groups: D4xx=Motor, D5xx=DPF, D7xx=Battery/Additive
 */
object PSARealDIDs {

    data class DIDDefinition(
        val did: String,
        val name: String,
        val unit: String,
        val group: String,
        val dataLength: Int = 2,   // Expected data bytes
        val decode: (List<Int>) -> Double  // Decoding function: takes raw bytes, returns value
    )

    // ── Motor DIDs (Group 4 — D4xx) ──
    val ENGINE_DIDS = listOf(
        DIDDefinition("D41F", "RPM Motor", "rpm", "Motor", 2) { bytes ->
            if (bytes.size >= 2) (bytes[0] * 256.0 + bytes[1]) / 4.0 else 0.0
        },
        DIDDefinition("D404", "Viteza Vehicul", "km/h", "Motor", 2) { bytes ->
            if (bytes.size >= 2) (bytes[0] * 256.0 + bytes[1]) / 100.0 else 0.0
        },
        DIDDefinition("D415", "Sarcina Motor", "%", "Motor", 2) { bytes ->
            if (bytes.size >= 2) (bytes[0] * 256.0 + bytes[1]) / 100.0 else 0.0
        },
        DIDDefinition("D421", "Presiune Turbo", "kPa", "Motor", 2) { bytes ->
            if (bytes.size >= 2) (bytes[0] * 256.0 + bytes[1]) * 0.1 else 0.0
        },
        DIDDefinition("D423", "Presiune Rampa Combustibil", "bar", "Motor", 2) { bytes ->
            if (bytes.size >= 2) (bytes[0] * 256.0 + bytes[1]) * 0.1 else 0.0
        },
        DIDDefinition("D440", "Pozitie Acceleratie", "%", "Motor", 2) { bytes ->
            if (bytes.size >= 2) (bytes[0] * 256.0 + bytes[1]) / 100.0 else 0.0
        },
        DIDDefinition("D460", "Temperatura Apa", "°C", "Motor", 1) { bytes ->
            if (bytes.isNotEmpty()) bytes[0] - 40.0 else 0.0
        },
        DIDDefinition("D462", "Temperatura Ulei", "°C", "Motor", 1) { bytes ->
            if (bytes.isNotEmpty()) bytes[0] - 40.0 else 0.0
        },
        DIDDefinition("D464", "Tensiune Baterie", "V", "Motor", 2) { bytes ->
            if (bytes.size >= 2) (bytes[0] * 256.0 + bytes[1]) / 1000.0 else 0.0
        },
        DIDDefinition("D468", "Temperatura Aer Admisie", "°C", "Motor", 1) { bytes ->
            if (bytes.isNotEmpty()) bytes[0] - 40.0 else 0.0
        },
        DIDDefinition("D482", "Corectie Injectoare", "mm³", "Motor", 8) { bytes ->
            // Returns correction for injector 1 (first 2 bytes, signed)
            if (bytes.size >= 2) {
                val raw = bytes[0] * 256 + bytes[1]
                val signed = if (raw > 32767) raw - 65536 else raw
                signed * 0.01
            } else 0.0
        }
    )

    // ── DPF/FAP DIDs (Group 5 — D5xx) ──
    val DPF_DIDS = listOf(
        DIDDefinition("D546", "Incarcare Funingine DPF", "g/l", "DPF", 2) { bytes ->
            if (bytes.size >= 2) (bytes[0] * 256.0 + bytes[1]) * 0.01 else 0.0
        },
        DIDDefinition("D547", "Temperatura Intrare DPF", "°C", "DPF", 2) { bytes ->
            if (bytes.size >= 2) (bytes[0] * 256.0 + bytes[1]) * 0.1 - 40.0 else 0.0
        },
        DIDDefinition("D548", "Temperatura Iesire DPF", "°C", "DPF", 2) { bytes ->
            if (bytes.size >= 2) (bytes[0] * 256.0 + bytes[1]) * 0.1 - 40.0 else 0.0
        },
        DIDDefinition("D549", "Presiune Diferentiala DPF", "kPa", "DPF", 2) { bytes ->
            if (bytes.size >= 2) (bytes[0] * 256.0 + bytes[1]) * 0.01 else 0.0
        },
        DIDDefinition("D54A", "Numar Regenerari DPF", "", "DPF", 2) { bytes ->
            if (bytes.size >= 2) (bytes[0] * 256.0 + bytes[1]) else 0.0
        },
        DIDDefinition("D54B", "Km de la Ultima Regenerare", "km", "DPF", 2) { bytes ->
            if (bytes.size >= 2) (bytes[0] * 256.0 + bytes[1]) else 0.0
        },
        DIDDefinition("D54C", "Status Regenerare DPF", "", "DPF", 1) { bytes ->
            if (bytes.isNotEmpty()) bytes[0].toDouble() else 0.0
        }
    )

    // ── Battery/Additive DIDs (Group 7 — D7xx) ──
    val BATTERY_DIDS = listOf(
        DIDDefinition("D711", "Nivel Aditiv Eolys", "%", "Baterie", 2) { bytes ->
            if (bytes.size >= 2) (bytes[0] * 256.0 + bytes[1]) * 0.1 else 0.0
        },
        DIDDefinition("D714", "Tensiune Baterie", "V", "Baterie", 2) { bytes ->
            if (bytes.size >= 2) (bytes[0] * 256.0 + bytes[1]) / 1000.0 else 0.0
        }
    )

    // ── All DIDs combined ──
    val ALL_DIDS = ENGINE_DIDS + DPF_DIDS + BATTERY_DIDS

    /**
     * Find DID definition by DID code.
     */
    fun findDID(did: String): DIDDefinition? {
        return ALL_DIDS.find { it.did.equals(did, ignoreCase = true) }
    }

    /**
     * Get all DIDs for a specific group prefix.
     */
    fun getDIDsForGroup(groupPrefix: String): List<DIDDefinition> {
        return ALL_DIDS.filter { it.did.startsWith(groupPrefix, ignoreCase = true) }
    }

    /**
     * Decode injector corrections for all 4 cylinders.
     * DID D482 returns 8 bytes: 2 bytes per injector (signed).
     */
    fun decodeInjectorCorrections(bytes: List<Int>): List<Double> {
        val corrections = mutableListOf<Double>()
        var i = 0
        while (i + 1 < bytes.size && corrections.size < 4) {
            val raw = bytes[i] * 256 + bytes[i + 1]
            val signed = if (raw > 32767) raw - 65536 else raw
            corrections.add(signed * 0.01)
            i += 2
        }
        return corrections
    }

    /**
     * Get DPF regeneration status description.
     */
    fun getDPFRegenStatus(statusByte: Int): String {
        return when (statusByte) {
            0 -> "Inactiva"
            1 -> "In curs"
            2 -> "Cerere de regenerare"
            3 -> "Solicitata de ECU"
            else -> "Necunoscuta ($statusByte)"
        }
    }
}
