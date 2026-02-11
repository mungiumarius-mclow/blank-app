package com.psadiag.protocol

/**
 * PSA diagnostic protocol constants.
 * CAN bus: ISO 15765-4, 11-bit, 500kbps
 * Diagnostic: UDS (ISO 14229) / KWP2000
 */
object PSAProtocol {

    // ── ECU Addresses (TX / RX) ──
    object ECU {
        const val ECM_TX = "7E0"    // Engine Control Module
        const val ECM_RX = "7E8"
        const val TCM_TX = "7E1"    // Transmission Control Module
        const val TCM_RX = "7E9"
        const val BSI_TX = "765"    // Built-in Systems Interface (BSI)
        const val BSI_RX = "76D"

        val ALL = listOf(
            ECUAddress("ECM", "Motor", ECM_TX, ECM_RX),
            ECUAddress("TCM", "Cutie Viteze", TCM_TX, TCM_RX),
            ECUAddress("BSI", "BSI", BSI_TX, BSI_RX)
        )
    }

    data class ECUAddress(
        val code: String,
        val name: String,
        val txAddress: String,
        val rxAddress: String
    )

    // ── Broadcast ──
    const val BROADCAST_TX = "7DF"  // OBD2 broadcast (singurul care merge pe clone ELM327)

    // ── UDS Services ──
    object UDS {
        const val DIAGNOSTIC_SESSION_CONTROL = "10"
        const val DEFAULT_SESSION = "1001"
        const val EXTENDED_SESSION = "1003"
        const val READ_DATA_BY_ID = "22"
        const val READ_DTC_INFO = "19"
        const val CLEAR_DTC = "14"
        const val TESTER_PRESENT = "3E00"

        // Positive response offset
        const val POSITIVE_RESPONSE_OFFSET = 0x40  // service + 0x40 = positive response

        // Negative response
        const val NEGATIVE_RESPONSE = "7F"
    }

    // ── DID Groups ──
    object DIDGroups {
        const val GROUP_0 = 0xD0
        const val GROUP_1 = 0xD1
        const val GROUP_2 = 0xD2
        const val GROUP_3 = 0xD3
        const val GROUP_4 = 0xD4  // Motor
        const val GROUP_5 = 0xD5  // DPF/FAP
        const val GROUP_6 = 0xD6
        const val GROUP_7 = 0xD7  // Baterie/Aditiv
        const val GROUP_8 = 0xD8
        const val GROUP_9 = 0xD9
        const val GROUP_A = 0xDA
        const val GROUP_B = 0xDB
        const val GROUP_C = 0xDC
        const val GROUP_D = 0xDD
        const val GROUP_E = 0xDE
        const val GROUP_F = 0xDF

        val ALL_GROUPS = (0..0xF).map { 0xD0 + it }
    }

    // ── Key DIDs ──
    object DIDs {
        // Motor (Group 4 - D4xx)
        const val ENGINE_RPM = "D41F"
        const val COOLANT_TEMP = "D460"
        const val BOOST_PRESSURE = "D421"
        const val ENGINE_LOAD = "D415"
        const val INTAKE_AIR_TEMP = "D468"
        const val OIL_TEMP = "D462"
        const val THROTTLE_POSITION = "D440"
        const val VEHICLE_SPEED = "D404"
        const val BATTERY_VOLTAGE_ENGINE = "D464"
        const val FUEL_RAIL_PRESSURE = "D423"
        const val INJECTOR_CORRECTION = "D482"

        // DPF/FAP (Group 5 - D5xx)
        const val DPF_SOOT_LOADING = "D546"
        const val DPF_TEMP_INLET = "D547"
        const val DPF_TEMP_OUTLET = "D548"
        const val DPF_DIFFERENTIAL_PRESSURE = "D549"
        const val DPF_REGEN_COUNT = "D54A"
        const val DPF_KM_SINCE_REGEN = "D54B"
        const val DPF_REGEN_STATUS = "D54C"

        // Baterie/Aditiv (Group 7 - D7xx)
        const val EOLYS_LEVEL = "D711"
        const val BATTERY_VOLTAGE = "D714"

        // ECU Identification
        const val ECU_PART_NUMBER = "F080"
        const val ECU_CALIBRATION = "F0FE"
        const val ECU_HARDWARE_NUMBER = "F091"
    }

    // ── AT Commands ──
    object AT {
        // Initialization (safe for clone ELM327)
        const val ECHO_OFF = "ATE0"
        const val HEADERS_ON = "ATH1"
        const val SPACES_ON = "ATS1"
        const val PROTOCOL_CAN_500K_11BIT = "ATSP6"  // ISO 15765-4 CAN 500kbps 11-bit
        const val SET_TIMEOUT = "ATST64"              // Timeout 100 * 4ms = 400ms
        const val LINEFEED_OFF = "ATL0"
        const val ADAPTIVE_TIMING_ON = "ATAT1"

        // CAN filter/flow control (FAPlite mode for clones)
        fun setReceiveAddress(rxAddr: String) = "ATCRA$rxAddr"
        fun setFlowControlHeader(txAddr: String) = "ATFCSH$txAddr"
        const val FC_DATA = "ATFCSD300000"        // ContinueToSend, no limit
        const val FC_MODE_USER = "ATFCSM1"        // User-defined flow control

        // Broadcast header (ONLY header that works on clones)
        const val SET_HEADER_BROADCAST = "ATSH7DF"

        // NEVER USE THESE ON ACTIVE CAN (destroys connection on clones):
        // ATZ, ATD, ATWS
        // NEVER switch headers off: ATH0, ATS0
    }

    // ── OBD2 Standard PIDs ──
    object OBD2 {
        const val SUPPORTED_PIDS = "0100"
        const val ENGINE_RPM = "010C"
        const val VEHICLE_SPEED = "010D"
        const val COOLANT_TEMP = "0105"
        const val INTAKE_AIR_TEMP = "010F"
        const val THROTTLE_POSITION = "0111"
        const val ENGINE_LOAD = "0104"
        const val MAF_RATE = "0110"
    }
}
