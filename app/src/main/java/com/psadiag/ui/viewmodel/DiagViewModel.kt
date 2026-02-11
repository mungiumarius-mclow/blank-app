package com.psadiag.ui.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.psadiag.bluetooth.ELM327Manager
import com.psadiag.protocol.*
import com.psadiag.service.DiagnosticService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DiagViewModel(application: Application) : AndroidViewModel(application) {

    // ── Managers ──
    val elm = ELM327Manager(application.applicationContext)
    val diagnosticService = DiagnosticService(elm)

    // ── Connection State ──
    private val _connectionState = MutableStateFlow(ELM327Manager.ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ELM327Manager.ConnectionState> = _connectionState.asStateFlow()

    private val _connectedDeviceName = MutableStateFlow("")
    val connectedDeviceName: StateFlow<String> = _connectedDeviceName.asStateFlow()

    // ── ECU List ──
    private val _detectedECUs = MutableStateFlow<List<ELM327Manager.ECUInfo>>(emptyList())
    val detectedECUs: StateFlow<List<ELM327Manager.ECUInfo>> = _detectedECUs.asStateFlow()

    private val _selectedECU = MutableStateFlow<ELM327Manager.ECUInfo?>(null)
    val selectedECU: StateFlow<ELM327Manager.ECUInfo?> = _selectedECU.asStateFlow()

    // ── Engine Data ──
    private val _engineData = MutableStateFlow(DiagnosticService.EngineData())
    val engineData: StateFlow<DiagnosticService.EngineData> = _engineData.asStateFlow()

    // ── DPF Data ──
    private val _dpfData = MutableStateFlow(DiagnosticService.DPFData())
    val dpfData: StateFlow<DiagnosticService.DPFData> = _dpfData.asStateFlow()

    // ── Injector Data ──
    private val _injectorData = MutableStateFlow(DiagnosticService.InjectorData())
    val injectorData: StateFlow<DiagnosticService.InjectorData> = _injectorData.asStateFlow()

    // ── DTC List ──
    private val _dtcList = MutableStateFlow<List<DTCManager.DTC>>(emptyList())
    val dtcList: StateFlow<List<DTCManager.DTC>> = _dtcList.asStateFlow()

    // ── ECU Identification ──
    private val _ecuIdentification = MutableStateFlow(ECUIdentifier.ECUIdentification())
    val ecuIdentification: StateFlow<ECUIdentifier.ECUIdentification> = _ecuIdentification.asStateFlow()

    // ── DID Groups ──
    private val _didGroups = MutableStateFlow<List<PSAGroupScanner.GroupScanResult>>(emptyList())
    val didGroups: StateFlow<List<PSAGroupScanner.GroupScanResult>> = _didGroups.asStateFlow()

    // ── Logs ──
    private val _logMessages = MutableStateFlow<List<String>>(emptyList())
    val logMessages: StateFlow<List<String>> = _logMessages.asStateFlow()

    // ── Loading / Error ──
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // ── Live data refresh ──
    private var liveDataJob: Job? = null

    init {
        elm.setOnStateChanged { state ->
            _connectionState.value = state
            _connectedDeviceName.value = elm.connectedDeviceName
        }
        elm.setOnLog { msg ->
            val currentLogs = _logMessages.value.toMutableList()
            currentLogs.add(msg)
            // Keep last 200 log lines
            if (currentLogs.size > 200) {
                _logMessages.value = currentLogs.takeLast(200)
            } else {
                _logMessages.value = currentLogs
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    // BLUETOOTH
    // ═══════════════════════════════════════════════════════

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDevice> = elm.getPairedDevices()

    fun connect(device: BluetoothDevice) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            val success = elm.connect(device)
            if (success) {
                _detectedECUs.value = elm.detectedECUs
                _selectedECU.value = elm.currentECU

                // Start TesterPresent
                diagnosticService.testerPresent.start(viewModelScope)
            } else {
                _errorMessage.value = "Conectare esuata"
            }

            _isLoading.value = false
        }
    }

    fun disconnect() {
        liveDataJob?.cancel()
        diagnosticService.testerPresent.stop()
        elm.disconnect()
        _detectedECUs.value = emptyList()
        _selectedECU.value = null
        _engineData.value = DiagnosticService.EngineData()
        _dpfData.value = DiagnosticService.DPFData()
        _injectorData.value = DiagnosticService.InjectorData()
        _dtcList.value = emptyList()
        _didGroups.value = emptyList()
    }

    // ═══════════════════════════════════════════════════════
    // ECU SELECTION
    // ═══════════════════════════════════════════════════════

    fun selectECU(ecu: ELM327Manager.ECUInfo) {
        viewModelScope.launch {
            _isLoading.value = true
            val success = elm.selectECU(ecu.txAddress, ecu.rxAddress)
            if (success) {
                _selectedECU.value = ecu
            } else {
                _errorMessage.value = "Nu s-a putut selecta ECU ${ecu.code}"
            }
            _isLoading.value = false
        }
    }

    // ═══════════════════════════════════════════════════════
    // ENGINE DATA (LIVE)
    // ═══════════════════════════════════════════════════════

    fun startLiveData() {
        liveDataJob?.cancel()
        liveDataJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive && elm.isConnected) {
                try {
                    val data = diagnosticService.readEngineData()
                    _engineData.value = data
                } catch (e: Exception) {
                    _errorMessage.value = "Eroare citire date: ${e.message}"
                }
                delay(500) // Refresh every 500ms
            }
        }
    }

    fun stopLiveData() {
        liveDataJob?.cancel()
        liveDataJob = null
    }

    // ═══════════════════════════════════════════════════════
    // DPF DATA
    // ═══════════════════════════════════════════════════════

    fun readDPFData() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _dpfData.value = diagnosticService.readDPFData()
            } catch (e: Exception) {
                _errorMessage.value = "Eroare citire DPF: ${e.message}"
            }
            _isLoading.value = false
        }
    }

    // ═══════════════════════════════════════════════════════
    // INJECTOR DATA
    // ═══════════════════════════════════════════════════════

    fun readInjectorData() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _injectorData.value = diagnosticService.readInjectorData()
            } catch (e: Exception) {
                _errorMessage.value = "Eroare citire injectoare: ${e.message}"
            }
            _isLoading.value = false
        }
    }

    // ═══════════════════════════════════════════════════════
    // DTC
    // ═══════════════════════════════════════════════════════

    fun readDTCs(ecuCode: String = "ECM") {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _dtcList.value = diagnosticService.readDTCs(ecuCode)
            } catch (e: Exception) {
                _errorMessage.value = "Eroare citire DTC: ${e.message}"
            }
            _isLoading.value = false
        }
    }

    fun clearDTCs(ecuCode: String = "ECM") {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val success = diagnosticService.clearDTCs(ecuCode)
                if (success) {
                    _dtcList.value = emptyList()
                    _errorMessage.value = null
                } else {
                    _errorMessage.value = "Stergere DTC esuata"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Eroare stergere DTC: ${e.message}"
            }
            _isLoading.value = false
        }
    }

    // ═══════════════════════════════════════════════════════
    // ECU IDENTIFICATION
    // ═══════════════════════════════════════════════════════

    fun identifyECU(ecuCode: String = "ECM") {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _ecuIdentification.value = diagnosticService.identifyECU(ecuCode)
            } catch (e: Exception) {
                _errorMessage.value = "Eroare identificare ECU: ${e.message}"
            }
            _isLoading.value = false
        }
    }

    // ═══════════════════════════════════════════════════════
    // DID GROUP SCANNING
    // ═══════════════════════════════════════════════════════

    fun scanDIDGroups(ecuCode: String = "ECM") {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _didGroups.value = diagnosticService.scanDIDGroups(ecuCode)
            } catch (e: Exception) {
                _errorMessage.value = "Eroare scanare grupuri: ${e.message}"
            }
            _isLoading.value = false
        }
    }

    // ═══════════════════════════════════════════════════════
    // UTILS
    // ═══════════════════════════════════════════════════════

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearLogs() {
        _logMessages.value = emptyList()
    }

    override fun onCleared() {
        super.onCleared()
        disconnect()
    }
}
