package com.biasharaai.ui.desktop

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.biasharaai.R
import com.biasharaai.databinding.FragmentDesktopLinkBinding
import com.biasharaai.desktop.DesktopBridgeClient
import com.biasharaai.ui.base.BaseFragment
import com.biasharaai.ui.scanner.BarcodeAnalyzer
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject

@AndroidEntryPoint
class DesktopLinkFragment : BaseFragment() {

    private var _binding: FragmentDesktopLinkBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var desktopBridgeClient: DesktopBridgeClient

    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null
    private var barcodeAnalyzer: BarcodeAnalyzer? = null
    private var scannerRunning = false
    private var pairingInFlight = false
    private var discoveryInFlight = false
    private var syncInFlight = false
    private var reconciliationJob: Job? = null
    private var lastScanValue = ""
    private var lastScanAt = 0L

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (_binding == null) return@registerForActivityResult
            if (granted) {
                startScanner()
            } else {
                showPermissionDenied()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentDesktopLinkBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()
        setupButtons()
        renderSession(updateSyncStatus = true)
        startReconciliationLoop()
    }

    override fun onDestroyView() {
        reconciliationJob?.cancel()
        reconciliationJob = null
        stopScanner()
        if (::cameraExecutor.isInitialized) cameraExecutor.shutdown()
        super.onDestroyView()
        _binding = null
    }

    private fun setupButtons() {
        binding.btnFindDesktop.setOnClickListener { findDesktopAutomatically() }
        binding.btnPairDesktop.setOnClickListener { pairDesktop() }
        binding.btnUseUsbBridge.setOnClickListener {
            binding.inputDesktopUrl.setText(DesktopBridgeClient.DEFAULT_TEST_URL)
            binding.layoutDesktopUrl.error = null
            binding.textConnectionStatus.text = getString(R.string.desktop_link_usb_selected)
        }
        binding.btnDisconnectDesktop.setOnClickListener {
            desktopBridgeClient.disconnect()
            reconciliationJob?.cancel()
            reconciliationJob = null
            stopScanner()
            renderSession(updateSyncStatus = true)
            Snackbar.make(binding.root, R.string.desktop_link_disconnected, Snackbar.LENGTH_SHORT).show()
        }
        binding.btnSyncCatalogDesktop.setOnClickListener { syncCatalog() }
        binding.btnStartDesktopScanner.setOnClickListener { ensureCameraPermissionThenStart() }
        binding.btnStopDesktopScanner.setOnClickListener { stopScanner() }
        binding.btnGrantPermission.setOnClickListener {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun renderSession(
        updateConnectionStatus: Boolean = true,
        updateSyncStatus: Boolean = false,
    ) {
        val b = _binding ?: return
        val session = desktopBridgeClient.currentSession()
        if (b.inputDesktopUrl.text.isNullOrBlank()) {
            b.inputDesktopUrl.setText(session?.baseUrl ?: DesktopBridgeClient.DEFAULT_TEST_URL)
        }
        if (updateConnectionStatus) {
            b.textConnectionStatus.text = if (session == null) {
                getString(R.string.desktop_link_status_disconnected)
            } else {
                getString(R.string.desktop_link_status_connected, session.deviceName, session.baseUrl)
            }
        }
        b.btnPairDesktop.isEnabled = !pairingInFlight && !discoveryInFlight
        b.btnFindDesktop.isEnabled = !pairingInFlight && !discoveryInFlight
        b.btnUseUsbBridge.isEnabled = !pairingInFlight && !discoveryInFlight
        b.btnDisconnectDesktop.isEnabled = session != null && !pairingInFlight && !discoveryInFlight
        b.btnSyncCatalogDesktop.isEnabled = session != null && !pairingInFlight && !discoveryInFlight && !syncInFlight
        b.btnStartDesktopScanner.isEnabled = session != null && !scannerRunning && !pairingInFlight && !discoveryInFlight
        b.btnStopDesktopScanner.isEnabled = scannerRunning
        if (updateSyncStatus) {
            b.textSyncStatus.text = getString(
                if (session == null) {
                    R.string.desktop_link_sync_pair_first
                } else {
                    R.string.desktop_link_sync_idle
                },
            )
        }
    }

    private fun findDesktopAutomatically() {
        if (discoveryInFlight || pairingInFlight) return
        val b = _binding ?: return
        discoveryInFlight = true
        b.layoutDesktopUrl.error = null
        b.layoutPairingCode.error = null
        b.textConnectionStatus.text = getString(R.string.desktop_link_discovering)
        renderSession(updateConnectionStatus = false, updateSyncStatus = false)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val found = desktopBridgeClient.discoverDesktop()
                val current = _binding ?: return@launch
                if (found == null) {
                    current.textConnectionStatus.text = getString(R.string.desktop_link_discovery_not_found)
                    return@launch
                }
                current.inputDesktopUrl.setText(found.baseUrl)
                current.inputPairingCode.setText(found.token)
                current.textConnectionStatus.text = getString(
                    R.string.desktop_link_discovered,
                    found.businessName.ifBlank { found.host },
                )
                pairDesktop()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                Log.w(TAG, "Desktop discovery failed", t)
                _binding?.textConnectionStatus?.text = getString(
                    R.string.desktop_link_discovery_failed,
                    t.desktopLinkMessage(),
                )
            } finally {
                discoveryInFlight = false
                renderSession(updateConnectionStatus = false)
            }
        }
    }

    private fun pairDesktop() {
        if (pairingInFlight) return
        val b = _binding ?: return
        val baseUrl = b.inputDesktopUrl.text?.toString().orEmpty()
        val token = b.inputPairingCode.text?.toString().orEmpty()
        b.layoutDesktopUrl.error = null
        b.layoutPairingCode.error = null
        if (baseUrl.isBlank()) {
            b.layoutDesktopUrl.error = getString(R.string.desktop_link_url_required)
            return
        }
        if (token.isBlank()) {
            b.layoutPairingCode.error = getString(R.string.desktop_link_pairing_code_required)
            return
        }
        pairingInFlight = true
        reconciliationJob?.cancel()
        reconciliationJob = null
        syncInFlight = false
        desktopBridgeClient.disconnect()
        b.btnPairDesktop.isEnabled = false
        b.textConnectionStatus.text = getString(R.string.desktop_link_pairing)
        renderSession(updateConnectionStatus = false, updateSyncStatus = true)
        viewLifecycleOwner.lifecycleScope.launch {
            var paired = false
            var syncAfterPair = false
            try {
                val session = desktopBridgeClient.pair(baseUrl, token)
                paired = true
                syncAfterPair = true
                val current = _binding ?: return@launch
                current.inputDesktopUrl.setText(session.baseUrl)
                current.inputPairingCode.text?.clear()
                current.textConnectionStatus.text = getString(
                    R.string.desktop_link_status_connected,
                    session.deviceName,
                    session.baseUrl,
                )
                Snackbar.make(current.root, R.string.desktop_link_pair_success, Snackbar.LENGTH_SHORT).show()
                startReconciliationLoop()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                val current = _binding ?: return@launch
                Log.w(TAG, "Desktop pairing failed", t)
                current.textConnectionStatus.text = getString(
                    R.string.desktop_link_pair_failed,
                    t.desktopLinkMessage(),
                )
            } finally {
                pairingInFlight = false
                _binding?.let {
                    it.btnPairDesktop.isEnabled = true
                    renderSession(updateConnectionStatus = paired, updateSyncStatus = true)
                }
                if (syncAfterPair && _binding != null) {
                    syncCatalog()
                }
            }
        }
    }

    private fun syncCatalog() {
        if (syncInFlight) return
        val b = _binding ?: return
        syncInFlight = true
        b.btnSyncCatalogDesktop.isEnabled = false
        b.textSyncStatus.text = getString(R.string.desktop_link_sync_running)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = desktopBridgeClient.syncCatalog()
                val current = _binding ?: return@launch
                current.textSyncStatus.text = result.message
                Snackbar.make(current.root, result.message, Snackbar.LENGTH_LONG).show()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                val current = _binding ?: return@launch
                Log.w(TAG, "Desktop catalog sync failed", t)
                current.textSyncStatus.text = t.message ?: getString(R.string.desktop_link_sync_failed)
            } finally {
                syncInFlight = false
                renderSession()
            }
        }
    }

    private fun startReconciliationLoop() {
        reconciliationJob?.cancel()
        if (desktopBridgeClient.currentSession() == null) return
        reconciliationJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(RECONCILE_INITIAL_DELAY_MS)
            while (isActive && desktopBridgeClient.currentSession() != null) {
                reconcileOnce(showOnlyChanges = true)
                delay(RECONCILE_INTERVAL_MS)
            }
        }
    }

    private suspend fun reconcileOnce(showOnlyChanges: Boolean) {
        if (pairingInFlight || syncInFlight || desktopBridgeClient.currentSession() == null) return
        syncInFlight = true
        renderSession(updateConnectionStatus = false)
        try {
            val result = desktopBridgeClient.reconcile()
            val current = _binding ?: return
            if (!showOnlyChanges || result.hasChanges) {
                current.textSyncStatus.text = result.message
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            Log.w(TAG, "Desktop reconciliation failed", t)
            _binding?.textSyncStatus?.text = t.desktopLinkMessage()
        } finally {
            syncInFlight = false
            renderSession(updateConnectionStatus = false)
        }
    }

    private fun ensureCameraPermissionThenStart() {
        if (desktopBridgeClient.currentSession() == null) {
            Snackbar.make(binding.root, R.string.desktop_link_pair_first, Snackbar.LENGTH_SHORT).show()
            renderSession(updateSyncStatus = true)
            return
        }
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startScanner()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startScanner() {
        if (scannerRunning) return
        val b = _binding ?: return
        scannerRunning = true
        b.cardScanner.visibility = View.VISIBLE
        b.permissionDeniedGroup.visibility = View.GONE
        b.previewView.visibility = View.VISIBLE
        b.viewfinderBorder.visibility = View.VISIBLE
        b.textScannerHint.text = getString(R.string.desktop_link_scanner_hint_active)
        renderSession()

        val ctx = context ?: return
        val future = ProcessCameraProvider.getInstance(ctx)
        future.addListener(
            {
                if (_binding == null || !scannerRunning) return@addListener
                runCatching {
                    val provider = future.get()
                    cameraProvider = provider
                    bindCamera(provider)
                }.onFailure {
                    Log.w(TAG, "Desktop scanner camera failed", it)
                    _binding?.textScannerHint?.text = getString(R.string.desktop_link_camera_failed)
                }
            },
            ContextCompat.getMainExecutor(ctx),
        )
    }

    private fun bindCamera(provider: ProcessCameraProvider) {
        val b = _binding ?: return
        val preview = Preview.Builder()
            .build()
            .also { it.surfaceProvider = b.previewView.surfaceProvider }
        val analyzer = BarcodeAnalyzer { rawValue ->
            view?.post { handleBarcode(rawValue) }
        }
        barcodeAnalyzer = analyzer
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { it.setAnalyzer(cameraExecutor, analyzer) }
        provider.unbindAll()
        provider.bindToLifecycle(
            viewLifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            analysis,
        )
    }

    private fun handleBarcode(rawValue: String) {
        val now = SystemClock.elapsedRealtime()
        if (rawValue == lastScanValue && now - lastScanAt < DUPLICATE_SCAN_WINDOW_MS) {
            barcodeAnalyzer?.reset()
            return
        }
        lastScanValue = rawValue
        lastScanAt = now
        val b = _binding ?: return
        b.textScannerHint.text = getString(R.string.desktop_link_sending_scan, rawValue)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = desktopBridgeClient.sendScan(rawValue)
                val current = _binding ?: return@launch
                current.textScannerHint.text = result.message
                if (!result.success) {
                    Snackbar.make(current.root, result.message, Snackbar.LENGTH_SHORT).show()
                }
            } finally {
                barcodeAnalyzer?.reset()
            }
        }
    }

    private fun stopScanner() {
        scannerRunning = false
        cameraProvider?.unbindAll()
        cameraProvider = null
        barcodeAnalyzer?.close()
        barcodeAnalyzer = null
        val b = _binding ?: return
        b.cardScanner.visibility = View.GONE
        b.previewView.visibility = View.GONE
        b.viewfinderBorder.visibility = View.GONE
        b.textScannerHint.text = getString(R.string.desktop_link_scanner_hint_idle)
        renderSession()
    }

    private fun showPermissionDenied() {
        val b = _binding ?: return
        scannerRunning = false
        b.cardScanner.visibility = View.VISIBLE
        b.previewView.visibility = View.GONE
        b.viewfinderBorder.visibility = View.GONE
        b.permissionDeniedGroup.visibility = View.VISIBLE
        b.textScannerHint.text = getString(R.string.desktop_link_permission_denied)
        renderSession()
    }

    companion object {
        private const val TAG = "DesktopLinkFragment"
        private const val DUPLICATE_SCAN_WINDOW_MS = 1200L
        private const val RECONCILE_INITIAL_DELAY_MS = 2_000L
        private const val RECONCILE_INTERVAL_MS = 60_000L
    }
}

private fun Throwable.desktopLinkMessage(): String {
    val networkCause = generateSequence(this) { it.cause }.firstOrNull {
        it is ConnectException || it is SocketTimeoutException || it is UnknownHostException
    }
    if (networkCause != null) {
        return "Desktop bridge is not reachable. Keep both devices on the same Wi-Fi and keep the desktop app open. For USB testing, run adb reverse tcp:8865 tcp:8865 and use http://127.0.0.1:8865."
    }
    return message?.takeIf { it.isNotBlank() } ?: "Unknown error"
}
