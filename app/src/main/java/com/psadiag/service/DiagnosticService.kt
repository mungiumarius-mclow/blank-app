package com.psadiag.service

import android.util.Log
import com.psadiag.bluetooth.ELM327Manager
import com.psadiag.protocol.*

/**
 * Main diagnostic service — orchestrates all ECU communication.
 * Uses ELM327Manager (which handles ATSH interception automatically).
 */
class DiagnosticService(private val elm: ELM327Manager) {

    companion object {
        private const val TAG = "DiagnosticService"
    }

    val testerPresent = TesterPresentManager(elm)
    val ecuIdentifier = ECUIdentifier(elm)
    val groupScanner = PSAGroupScanner(elm)
    val didReader = PSAGroupDIDReader(elm)
    val dtcManager = DTCManager(elm)

    // ── Data classes for results ──
    data class EngineData(
        val rpm: Double = 0.0,
        val coolantTemp: Double = 0.0,
        val oilTemp: Double = 0.0,
        val boostPressure: Double = 0.0,
        val throttlePosition: Double = 0.0,
        val engineLoad: Double = 0.0,
        val vehicleSpeed: Double = 0.0,
        val intakeAirTemp: Double = 0.0,
        val batteryVoltage: Double = 0.0,
        val fuelRailPressure: Double = 0.0
    )

    data class DPFData(
        val sootLoading: Double = 0.0,
        val tempInlet: Double = 0.0,
        val tempOutlet: Double = 0.0,
        val differentialPressure: Double = 0.0,
        val regenCount: Int = 0,
        val kmSinceRegen: Double = 0.0,
        val regenStatus: String = "Necunoscut"
    )

    data class InjectorData(
        val corrections: List<Double> = emptyList()  // 4 values, one per cylinder
    )

    // ═══════════════════════════════════════════════════════
    // ENGINE DATA
    // ═══════════════════════════════════════════════════════

    /**
     * Read engine data from ECM.
     * Selects ECM, opens extended session, reads DIDs.
     */
    suspend fun readEngineData(): EngineData {
        Log.d(TAG, "Reading engine data...")

        if (!elm.selectECU("ECM")) {
            Log.w(TAG, "Failed to select ECM")
            return EngineData()
        }

        // Open extended diagnostic session
        elm.sendRawSimple(PSAProtocol.UDS.EXTENDED_SESSION)

        val data = EngineData(
            rpm = readDIDValue(PSAProtocol.DIDs.ENGINE_RPM),
            coolantTemp = readDIDValue(PSAProtocol.DIDs.COOLANT_TEMP),
            oilTemp = readDIDValue(PSAProtocol.DIDs.OIL_TEMP),
            boostPressure = readDIDValue(PSAProtocol.DIDs.BOOST_PRESSURE),
            throttlePosition = readDIDValue(PSAProtocol.DIDs.THROTTLE_POSITION),
            engineLoad = readDIDValue(PSAProtocol.DIDs.ENGINE_LOAD),
            vehicleSpeed = readDIDValue(PSAProtocol.DIDs.VEHICLE_SPEED),
            intakeAirTemp = readDIDValue(PSAProtocol.DIDs.INTAKE_AIR_TEMP),
            batteryVoltage = readDIDValue(PSAProtocol.DIDs.BATTERY_VOLTAGE_ENGINE),
            fuelRailPressure = readDIDValue(PSAProtocol.DIDs.FUEL_RAIL_PRESSURE)
        )

        Log.d(TAG, "Engine data: RPM=${data.rpm}, Temp=${data.coolantTemp}°C, Turbo=${data.boostPressure}kPa")
        return data
    }

    // ═══════════════════════════════════════════════════════
    // DPF DATA
    // ═══════════════════════════════════════════════════════

    /**
     * Read DPF/FAP data from ECM.
     */
    suspend fun readDPFData(): DPFData {
        Log.d(TAG, "Reading DPF data...")

        if (!elm.selectECU("ECM")) {
            Log.w(TAG, "Failed to select ECM")
            return DPFData()
        }

        elm.sendRawSimple(PSAProtocol.UDS.EXTENDED_SESSION)

        val regenStatusByte = readDIDRawValue(PSAProtocol.DIDs.DPF_REGEN_STATUS)

        val data = DPFData(
            sootLoading = readDIDValue(PSAProtocol.DIDs.DPF_SOOT_LOADING),
            tempInlet = readDIDValue(PSAProtocol.DIDs.DPF_TEMP_INLET),
            tempOutlet = readDIDValue(PSAProtocol.DIDs.DPF_TEMP_OUTLET),
            differentialPressure = readDIDValue(PSAProtocol.DIDs.DPF_DIFFERENTIAL_PRESSURE),
            regenCount = readDIDValue(PSAProtocol.DIDs.DPF_REGEN_COUNT).toInt(),
            kmSinceRegen = readDIDValue(PSAProtocol.DIDs.DPF_KM_SINCE_REGEN),
            regenStatus = PSARealDIDs.getDPFRegenStatus(regenStatusByte)
        )

        Log.d(TAG, "DPF: soot=${data.sootLoading}g/l, tempIn=${data.tempInlet}°C, regens=${data.regenCount}")
        return data
    }

    // ═══════════════════════════════════════════════════════
    // INJECTOR DATA
    // ═══════════════════════════════════════════════════════

    /**
     * Read injector correction data.
     */
    suspend fun readInjectorData(): InjectorData {
        Log.d(TAG, "Reading injector data...")

        if (!elm.selectECU("ECM")) {
            return InjectorData()
        }

        elm.sendRawSimple(PSAProtocol.UDS.EXTENDED_SESSION)

        val corrections = didReader.readInjectorCorrections()
        Log.d(TAG, "Injector corrections: $corrections")

        return InjectorData(corrections = corrections)
    }

    // ═══════════════════════════════════════════════════════
    // DTC OPERATIONS
    // ═══════════════════════════════════════════════════════

    /**
     * Read DTCs from specified ECU (default ECM).
     */
    suspend fun readDTCs(ecuCode: String = "ECM"): List<DTCManager.DTC> {
        if (!elm.selectECU(ecuCode)) {
            return emptyList()
        }
        return dtcManager.readDTCs()
    }

    /**
     * Clear DTCs on specified ECU (default ECM).
     */
    suspend fun clearDTCs(ecuCode: String = "ECM"): Boolean {
        if (!elm.selectECU(ecuCode)) {
            return false
        }
        return dtcManager.clearDTCs()
    }

    // ═══════════════════════════════════════════════════════
    // ECU IDENTIFICATION
    // ═══════════════════════════════════════════════════════

    /**
     * Identify an ECU and get its part number/calibration.
     */
    suspend fun identifyECU(ecuCode: String): ECUIdentifier.ECUIdentification {
        if (!elm.selectECU(ecuCode)) {
            return ECUIdentifier.ECUIdentification()
        }
        return ecuIdentifier.identify()
    }

    // ═══════════════════════════════════════════════════════
    // GROUP SCANNING
    // ═══════════════════════════════════════════════════════

    /**
     * Scan for active DID groups on the currently selected ECU.
     */
    suspend fun scanDIDGroups(ecuCode: String = "ECM"): List<PSAGroupScanner.GroupScanResult> {
        if (!elm.selectECU(ecuCode)) {
            return emptyList()
        }
        return groupScanner.scanGroups()
    }

    // ═══════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════

    /**
     * Read a single DID and return its decoded numeric value.
     */
    private suspend fun readDIDValue(did: String): Double {
        val result = didReader.readDID(did)
        return result?.value ?: 0.0
    }

    /**
     * Read a single DID and return the raw first byte value (for status bytes etc.).
     */
    private suspend fun readDIDRawValue(did: String): Int {
        val result = didReader.readDID(did)
        return result?.value?.toInt() ?: 0
    }
}
