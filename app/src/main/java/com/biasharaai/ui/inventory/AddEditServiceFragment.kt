package com.biasharaai.ui.inventory

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.biasharaai.R
import com.biasharaai.databinding.FragmentAddEditServiceBinding
import com.biasharaai.inventory.InventoryLabelGenerator
import com.biasharaai.ui.base.BaseFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.FileOutputStream

@AndroidEntryPoint
class AddEditServiceFragment : BaseFragment() {

    private var _binding: FragmentAddEditServiceBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AddEditServiceViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAddEditServiceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val editing = (arguments?.getLong(ARG_SERVICE_ID) ?: 0L) > 0L
        requireActivity().title = getString(
            if (editing) R.string.service_edit_title else R.string.service_add_title,
        )
        binding.btnShowQr.setOnClickListener { showCatalogueQrFromForm() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { collectService() }
                launch { collectEvents() }
            }
        }
        binding.btnSave.setOnClickListener { save() }
    }

    private suspend fun collectService() {
        viewModel.service.collect { service ->
            val hasToken = service != null && service.catalogueToken.isNotBlank()
            binding.btnShowQr.isVisible = hasToken
            binding.textCatalogueToken.isVisible = hasToken
            if (service != null) {
                binding.editName.setText(service.name)
                binding.editPrice.setText(service.basePrice.toString())
                binding.editDuration.setText(service.durationMinutes.toString())
                binding.editWarranty.setText(service.warrantyDays.toString())
                binding.textCatalogueToken.text =
                    getString(R.string.service_catalogue_qr, service.catalogueToken)
            }
        }
    }

    private suspend fun collectEvents() {
        viewModel.events.collect { event ->
            when (event) {
                is AddEditServiceViewModel.Event.Saved -> {
                    Snackbar.make(binding.root, R.string.product_saved, Snackbar.LENGTH_SHORT).show()
                    if (event.offerPrint) {
                        showQrDialog(
                            token = event.catalogueToken,
                            subtitle = binding.editName.text?.toString()?.trim().orEmpty(),
                            onDismiss = { findNavController().navigateUp() },
                        )
                    } else {
                        findNavController().navigateUp()
                    }
                }
                is AddEditServiceViewModel.Event.Error -> {
                    Snackbar.make(binding.root, event.message, Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun save() {
        val name = binding.editName.text?.toString()?.trim().orEmpty()
        val price = binding.editPrice.text?.toString()?.trim()?.toDoubleOrNull()
        val duration = binding.editDuration.text?.toString()?.trim()?.toIntOrNull() ?: 0
        val warranty = binding.editWarranty.text?.toString()?.trim()?.toIntOrNull() ?: 0
        if (name.isEmpty() || price == null || price < 0) {
            Snackbar.make(binding.root, R.string.service_save_validation, Snackbar.LENGTH_SHORT).show()
            return
        }
        viewModel.save(name, price, duration, warranty)
    }

    private fun showCatalogueQrFromForm() {
        val token = viewModel.service.value?.catalogueToken
        if (token.isNullOrBlank()) {
            Snackbar.make(binding.root, R.string.service_qr_save_first, Snackbar.LENGTH_SHORT).show()
            return
        }
        showQrDialog(
            token = token,
            subtitle = binding.editName.text?.toString()?.trim().orEmpty(),
            onDismiss = null,
        )
    }

    private fun showQrDialog(token: String, subtitle: String, onDismiss: (() -> Unit)?) {
        val bitmap = InventoryLabelGenerator.generateQrBitmap(token)
        val pad = (24 * resources.displayMetrics.density).toInt()
        val imageView = ImageView(requireContext()).apply {
            setImageBitmap(bitmap)
            adjustViewBounds = true
            setPadding(pad, pad, pad, pad / 2)
        }
        val message = buildString {
            if (subtitle.isNotBlank()) appendLine(subtitle)
            append(token)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.service_qr_preview_title)
            .setMessage(message.trim())
            .setView(imageView)
            .setPositiveButton(R.string.barcode_print) { _, _ -> printQr(bitmap, subtitle, token) }
            .setNeutralButton(R.string.barcode_copied) { _, _ ->
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("catalogue_qr", token))
                Snackbar.make(binding.root, R.string.barcode_copied, Snackbar.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
            .also { dialog ->
                dialog.setOnDismissListener { onDismiss?.invoke() }
                dialog.show()
            }
    }

    private fun printQr(bitmap: Bitmap, serviceName: String, token: String) {
        val printManager = requireContext().getSystemService(Context.PRINT_SERVICE) as PrintManager
        printManager.print(
            "ServiceQR_$token",
            object : PrintDocumentAdapter() {
                override fun onLayout(
                    old: PrintAttributes?,
                    new: PrintAttributes,
                    cancel: CancellationSignal?,
                    callback: LayoutResultCallback,
                    extras: Bundle?,
                ) {
                    if (cancel?.isCanceled == true) {
                        callback.onLayoutCancelled()
                        return
                    }
                    callback.onLayoutFinished(
                        PrintDocumentInfo.Builder("service_qr.pdf")
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
                        textSize = 18f
                        textAlign = Paint.Align.CENTER
                        color = Color.BLACK
                        isFakeBoldText = true
                    }
                    val bodyPaint = Paint().apply {
                        textSize = 12f
                        textAlign = Paint.Align.CENTER
                        color = Color.DKGRAY
                    }
                    var y = 48f
                    if (serviceName.isNotBlank()) {
                        canvas.drawText(serviceName, 595 / 2f, y, titlePaint)
                        y += 28f
                    }
                    val qrSize = 220f
                    val qrLeft = (595 - qrSize) / 2f
                    canvas.drawBitmap(bitmap, null, RectF(qrLeft, y, qrLeft + qrSize, y + qrSize), null)
                    y += qrSize + 16f
                    canvas.drawText(token, 595 / 2f, y, bodyPaint)
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_SERVICE_ID = "service_id"
    }
}
