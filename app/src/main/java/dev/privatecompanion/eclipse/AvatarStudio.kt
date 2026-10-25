package dev.privatecompanion.eclipse

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/** Generates simple vector portraits or a stylized variation of a user-provided image, entirely offline.
 * NOT a neural image-to-image generator; diffusion is a future milestone.
 */
object AvatarStudio {
    private fun color(name: String, kind: String): Int = when (kind) {
        "hair" -> when (name) {
            "Silver" -> Color.rgb(190, 203, 223); "Rose" -> Color.rgb(187, 86, 127)
            "Chestnut" -> Color.rgb(106, 63, 49); "Blonde" -> Color.rgb(229, 188, 109)
            else -> Color.rgb(45, 37, 63)
        }
        "eyes" -> when (name) {
            "Blue" -> Color.rgb(70, 145, 215); "Green" -> Color.rgb(73, 164, 125)
            "Amber" -> Color.rgb(191, 132, 61); "Brown" -> Color.rgb(122, 86, 76)
            else -> Color.rgb(140, 91, 190)
        }
        "skin" -> when (name) {
            "Fair" -> Color.rgb(243, 207, 191); "Deep" -> Color.rgb(121, 72, 60)
            "Olive" -> Color.rgb(182, 135, 105); "Golden" -> Color.rgb(210, 157, 104)
            else -> Color.rgb(223, 172, 145)
        }
        else -> when (name) {
            "Formal" -> Color.rgb(42, 52, 81); "Fantasy" -> Color.rgb(81, 45, 113)
            "Cozy" -> Color.rgb(145, 87, 108); else -> Color.rgb(73, 89, 117)
        }
    }

    fun fromReference(context: Context, uri: Uri) {
        val resolver = context.contentResolver
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, opts) }
        require(opts.outWidth in 16..15000 && opts.outHeight in 16..15000) { "Not a supported image" }
        var sample = 1
        while (max(opts.outWidth, opts.outHeight) / sample > 1400) sample *= 2
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, decodeOpts) }
            ?: error("Cannot decode image")
        val scaled = centerCrop(bitmap, 640)
        bitmap.recycle()
        val target = File(context.filesDir, "reference.png")
        FileOutputStream(target).use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
        scaled.recycle()
    }

    private fun centerCrop(b: Bitmap, size: Int): Bitmap {
        val target = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(target)
        val edge = minOf(b.width, b.height)
        val left = (b.width - edge) / 2
        val top = (b.height - edge) / 2
        canvas.drawBitmap(b, android.graphics.Rect(left, top, left+edge, top+edge),
            android.graphics.Rect(0,0,size,size), Paint(Paint.FILTER_BITMAP_FLAG))
        return target
    }

    fun create(context: Context, appearance: Appearance): Bitmap {
        val w = 640f
        val image = Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888)
        val c = Canvas(image)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 640f, 640f,
            Color.rgb(45, 38, 72), Color.rgb(113, 69, 114), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, w, p)
        p.shader = null
        val ref = File(context.filesDir, "reference.png")
        if (ref.exists()) {
            val bitmap = BitmapFactory.decodeFile(ref.absolutePath)
            if (bitmap != null) {
                val cm = ColorMatrix()
                cm.setSaturation(when (appearance.style) {
                    "Soft" -> 0.65f; "Vivid" -> 1.35f; "Graphic Novel" -> 1.15f
                    "Monochrome" -> 0.0f; else -> 1.0f
                })
                val overlay = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                overlay.colorFilter = ColorMatrixColorFilter(cm)
                if (appearance.style == "Graphic Novel") {
                    // 5-level RGB posterization: a simple local filter, NOT generative AI.
                    val width = bitmap.width
                    val height = bitmap.height
                    val pixels = IntArray(width * height)
                    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                    for (i in pixels.indices) {
                        val pixel = pixels[i]
                        fun band(v: Int): Int = ((v / 51) * 51).coerceIn(0, 255)
                        pixels[i] = Color.argb(Color.alpha(pixel), band(Color.red(pixel)),
                            band(Color.green(pixel)), band(Color.blue(pixel)))
                    }
                    val poster = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
                    c.drawBitmap(poster, null, RectF(0f,0f,w,w), overlay)
                    poster.recycle()
                } else {
                    c.drawBitmap(bitmap, null, RectF(0f,0f,w,w), overlay)
                }
                bitmap.recycle()
                p.color = color(appearance.hair, "hair")
                p.alpha = 32
                c.drawRect(0f, 0f, w, w, p)
                p.alpha = 255
            }
        } else {
            p.color = color(appearance.outfit, "outfit")
            c.drawOval(RectF(65f, 445f, 575f, 780f), p)
            p.color = color(appearance.hair, "hair")
            c.drawOval(RectF(138f, 75f, 502f, 555f), p)
            p.color = color(appearance.skin, "skin")
            c.drawOval(RectF(189f, 145f, 455f, 475f), p)
            p.color = color(appearance.hair, "hair")
            val fringe = Path().apply { moveTo(165f, 220f); lineTo(205f, 95f); lineTo(435f, 95f)
                lineTo(479f, 243f); lineTo(411f, 176f); lineTo(340f, 200f); lineTo(267f, 174f); close() }
            c.drawPath(fringe, p)
            p.color = Color.rgb(251,247,252)
            c.drawOval(RectF(233f,300f,292f,331f),p)
            c.drawOval(RectF(350f,300f,409f,331f),p)
            p.color = color(appearance.eyes, "eyes")
            c.drawCircle(265f, 315f, 14f, p)
            c.drawCircle(380f, 315f, 14f, p)
            p.color = Color.rgb(128, 70, 87); p.style = Paint.Style.STROKE; p.strokeWidth = 5f
            c.drawArc(RectF(292f,374f,352f,405f), 6f, 167f, false, p)
            p.style = Paint.Style.FILL
        }
        p.color = Color.WHITE; p.alpha = 27; p.style = Paint.Style.STROKE; p.strokeWidth = 10f
        c.drawRoundRect(RectF(18f,18f,622f,622f),30f,30f,p)
        p.style = Paint.Style.FILL; p.alpha = 255
        FileOutputStream(File(context.filesDir, "portrait.png")).use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        return image
    }
}
