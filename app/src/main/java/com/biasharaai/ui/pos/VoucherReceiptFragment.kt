package com.biasharaai.ui.pos

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.biasharaai.R
import com.biasharaai.databinding.FragmentVoucherReceiptBinding
import com.biasharaai.inventory.InventoryLabelGenerator
import com.biasharaai.service.ServiceQrGenerator
import com.biasharaai.service.VoucherReceiptFormatter
import com.biasharaai.ui.base.BaseFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class VoucherReceiptFragment : BaseFragment() {

    private var _binding: FragmentVoucherReceiptBinding? = null
    private val binding get() = _binding!!

    private val viewModel: VoucherReceiptViewModel by viewModels()

    private var qrBitmap: Bitmap? = null
    private var barcodeBitmap: Bitmap? = null
    private val dateFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentVoucherReceiptBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.btnNewSale.setOnClickListener {
            findNavController().popBackStack(R.id.posFragment, false)
        }
        binding.btnShare.setOnClickListener { shareQr() }
        binding.btnPrint.setOnClickListener { printVoucherCard() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (state.loading) return@collect
                    binding.textBusiness.text = state.businessName
                    binding.textService.text = state.serviceName
                    binding.textUses.text = getString(R.string.voucher_receipt_uses, state.totalUses)
                    binding.textValidUntil.text = state.expiresAt?.let {
                        getString(R.string.voucher_receipt_valid_until, dateFormat.format(Date(it)))
                    } ?: ""
                    binding.textCustomer.isVisible = !state.customerName.isNullOrBlank()
                    binding.textCustomer.text = getString(R.string.voucher_receipt_for, state.customerName)
                    qrBitmap = ServiceQrGenerator.generateQrBitmap(state.qrToken, 512)
                    binding.imageQr.setImageBitmap(qrBitmap)
                    if (state.qrToken.isNotBlank()) {
                        barcodeBitmap = generateBarcodeBitmap(state.qrToken)
                        binding.imageBarcode.setImageBitmap(barcodeBitmap)
                        binding.textVoucherId.text = state.voucherId
                    }
                }
            }
        }
    }

    private fun shareQr() {
        val bitmap = qrBitmap ?: return
        val cacheDir = File(requireContext().cacheDir, "shared").apply { mkdirs() }
        val file = File(cacheDir, "voucher_qr.png")
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        val uri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            file,
        )
        startActivity(
            Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
        )
    }

    private fun printVoucherCard() {
        val state = viewModel.uiState.value
        val qr = qrBitmap ?: return
        val barcode = barcodeBitmap
        val printManager = requireContext().getSystemService(android.content.Context.PRINT_SERVICE) as PrintManager
        printManager.print(
            "Voucher_${state.voucherId}",
            object : PrintDocumentAdapter() {
                override fun onLayout(
                    old: PrintAttributes?,
                    new: PrintAttributes,
                    cancel: CancellationSignal?,
                    callback: LayoutResultCallback,
                    extras: Bundle?,
                ) {
                    if (cancel?.isCanceled == true) { callback.onLayoutCancelled(); return }
                    callback.onLayoutFinished(
                        PrintDocumentInfo.Builder("voucher_${state.voucherId}.pdf")
                            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                            .setPageCount(1)
                            .build(),
                        new != old,
                    )
                }

                override fun onWrite(
                    pages: Array<out PageRange>?,
                    dest: ParcelFileDescriptor?,
                    cancel: CancellationSignal?,
                    callback: WriteResultCallback,
                ) {
                    val pdf = PdfDocument()
                    val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
                    val canvas = page.canvas
                    val titlePaint = Paint().apply {
                        textSize = 18f; textAlign = Paint.Align.CENTER; color = Color.BLACK
                        isFakeBoldText = true
                    }
                    val bodyPaint = Paint().apply {
                        textSize = 13f; textAlign = Paint.Align.CENTER; color = Color.DKGRAY
                    }
                    var y = 60f
                    canvas.drawText(state.businessName, 595 / 2f, y, titlePaint)
                    y += 26f
                    canvas.drawText(state.serviceName, 595 / 2f, y, bodyPaint)
                    y += 20f
                    canvas.drawText(getString(R.string.voucher_receipt_uses, state.totalUses), 595 / 2f, y, bodyPaint)
                    // QR code
                    val qrSize = 180f
                    val qrLeft = (595 - qrSize) / 2f
                    y += 20f
                    canvas.drawBitmap(qr, null, RectF(qrLeft, y, qrLeft + qrSize, y + qrSize), null)
                    y += qrSize + 10f
                    // Barcode
                    if (barcode != null) {
                        val bW = 360f
                        val bH = barcode.height * bW / barcode.width
                        val bLeft = (595 - bW) / 2f
                        canvas.drawBitmap(barcode, null, RectF(bLeft, y, bLeft + bW, y + bH), null)
                        y += bH + 8f
                        canvas.drawText(state.voucherId, 595 / 2f, y, bodyPaint)
                    }
                    pdf.finishPage(page)
                    try {
                        pdf.writeTo(FileOutputStream(dest!!.fileDescriptor))
                        callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    } catch (e: Exception) {
                        callback.onWriteFailed(e.message)
                    } finally {
                        pdf.close()
                    }
                }
            },
            PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build(),
        )
    }

    private fun generateBarcodeBitmap(value: String): Bitmap? =
        InventoryLabelGenerator.generateBarcodeBitmap(value)

    override fun onDestroyView() {
        qrBitmap = null
        barcodeBitmap = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        fun args(voucherId: String) = bundleOf(VoucherReceiptViewModel.ARG_VOUCHER_ID to voucherId)
    }
}
