package com.biasharaai.ui.cash

import android.graphics.Bitmap
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.biasharaai.R
import com.biasharaai.data.local.db.LedgerDirection
import com.biasharaai.databinding.ItemQrCardBinding
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

class QrCardAdapter(
    private val cards: List<BiasharaQrPayload>,
    private val onShare: (BiasharaQrPayload, Bitmap) -> Unit,
) : RecyclerView.Adapter<QrCardAdapter.VH>() {

    private val qrCache = mutableMapOf<String, Bitmap>()

    inner class VH(val binding: ItemQrCardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemQrCardBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = cards.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val card = cards[position]
        val b = holder.binding
        val ctx = b.root.context

        b.tvLabel.text = card.label
        b.tvDirection.text = if (card.direction == LedgerDirection.MONEY_IN)
            ctx.getString(R.string.cash_confirm_direction_in)
        else
            ctx.getString(R.string.cash_confirm_direction_out)

        val badgeColor = if (card.direction == LedgerDirection.MONEY_IN)
            ContextCompat.getColor(ctx, R.color.biashara_success_green)
        else
            ContextCompat.getColor(ctx, R.color.biashara_red)
        b.tvDirection.background.mutate().setTint(badgeColor)

        val encoded = card.encode()
        val qrBitmap = qrCache.getOrPut(encoded) { generateQr(encoded) }
        b.ivQr.setImageBitmap(qrBitmap)

        b.btnShare.setOnClickListener { onShare(card, qrBitmap) }
    }

    private fun generateQr(content: String, size: Int = 512): Bitmap {
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val bits = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bmp.setPixel(x, y, if (bits[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bmp
    }
}
