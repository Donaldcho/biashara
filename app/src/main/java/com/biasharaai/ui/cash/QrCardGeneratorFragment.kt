package com.biasharaai.ui.cash

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.biasharaai.databinding.FragmentQrCardGeneratorBinding
import com.biasharaai.ui.base.BaseFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class QrCardGeneratorFragment : BaseFragment() {

    private var _binding: FragmentQrCardGeneratorBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentQrCardGeneratorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        val adapter = QrCardAdapter(
            cards = BiasharaQrPayload.DEFAULT_CARDS,
            onShare = { card, bitmap -> shareQrCard(card, bitmap) },
        )
        binding.rvQrCards.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvQrCards.adapter = adapter
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun shareQrCard(card: BiasharaQrPayload, bitmap: Bitmap) {
        val ctx = context ?: return
        lifecycleScope.launch {
            val uri = withContext(Dispatchers.IO) {
                val name = "qr_${card.direction.name}_${card.entryType.name}_${card.label.replace(" ", "_")}.png"
                runCatching {
                    val file = File(ctx.cacheDir, name)
                    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
                }.getOrNull()
            } ?: return@launch
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "${card.label} – BiasharaAI QR Card")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, card.label))
        }
    }
}
