package com.biasharaai.whatsapp

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

object WhatsAppIntegration {
    private const val WHATSAPP_PACKAGE = "com.whatsapp"
    private const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"

    fun installedPackage(context: Context): String? =
        listOf(WHATSAPP_BUSINESS_PACKAGE, WHATSAPP_PACKAGE).firstOrNull { pkg ->
            runCatching {
                context.packageManager.getPackageInfo(pkg, 0)
            }.isSuccess
        }

    fun isInstalled(context: Context): Boolean = installedPackage(context) != null

    fun open(context: Context): Boolean {
        val pkg = installedPackage(context)
        val intent = if (pkg != null) {
            context.packageManager.getLaunchIntentForPackage(pkg)
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$WHATSAPP_PACKAGE"))
        } ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://www.whatsapp.com/download"))
        return start(context, intent)
    }

    fun sendText(context: Context, text: String, phone: String? = null): Boolean {
        val body = text.trim()
        if (body.isBlank()) return false
        val pkg = installedPackage(context)
        val digits = normalizePhone(phone)
        val targeted = if (digits != null) {
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://wa.me/$digits?text=${Uri.encode(body)}"),
            )
        } else {
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, body)
            }
        }
        if (pkg != null) targeted.setPackage(pkg)
        if (start(context, targeted)) return true
        val fallback = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, body)
        }
        return start(context, Intent.createChooser(fallback, null))
    }

    private fun normalizePhone(phone: String?): String? {
        val raw = phone?.trim().orEmpty()
        if (raw.isBlank()) return null
        val digits = raw.filter { it.isDigit() }
            .removePrefix("00")
            .takeIf { it.length >= 7 }
        return digits
    }

    private fun start(context: Context, intent: Intent): Boolean =
        runCatching {
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }.isSuccess
}
