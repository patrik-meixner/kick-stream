package com.kickstream.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

object QrCodeUtil {

    fun generateQrCode(content: String, size: Int): Bitmap? =
        try {
            val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
            val pixels = IntArray(size * size) { i ->
                val x = i % size
                val y = i / size
                if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
            }
            Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565).apply {
                setPixels(pixels, 0, size, 0, 0, size, size)
            }
        } catch (_: Exception) {
            null
        }
}
