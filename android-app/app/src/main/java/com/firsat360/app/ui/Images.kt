package com.firsat360.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Matrix
import android.graphics.Outline
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView
import com.firsat360.app.data.ImageLoader

/** Köşeleri yuvarlatılmış, ağdan yüklenen görsel. Yüklenemezse [onFail] çağrılır (ör. monograma dön). */
class NetImage(ctx: Context, radiusDp: Float = 14f) : ImageView(ctx) {
    init {
        scaleType = ScaleType.CENTER_CROP
        setBackgroundColor(Theme.p.surface2)
        if (radiusDp > 0) {
            val r = dp(radiusDp).toFloat()
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) = outline.setRoundRect(0, 0, view.width, view.height, r)
            }
            clipToOutline = true
        }
    }

    fun load(url: String?, maxPx: Int, fallback: String? = null, onFail: (() -> Unit)? = null) {
        setImageDrawable(ColorDrawable(0))
        ImageLoader.load(this, url, maxPx, fallback) { ok -> if (!ok) onFail?.invoke() }
    }
}

/**
 * Katalog sayfası için yakınlaştırılabilir görsel: iki parmakla 1x–6x, çift dokunuşla 2,5x/geri, sürükleyerek gezinme.
 * Yakınlaştırılmamışken yatay kaydırma [onSwipe] (+1 sonraki / -1 önceki), tek dokunuş [onTap].
 */
@SuppressLint("ClickableViewAccessibility")
class ZoomImageView(ctx: Context) : ImageView(ctx) {
    var onSwipe: ((Int) -> Unit)? = null
    var onTap: (() -> Unit)? = null
    private val m = Matrix()
    private val base = Matrix()
    private val vals = FloatArray(9)
    private var minScale = 1f

    private val scaleDetector = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            val cur = currentScale()
            val target = (cur * d.scaleFactor).coerceIn(minScale, minScale * 6f)
            val f = target / cur
            m.postScale(f, f, d.focusX, d.focusY)
            clamp(); imageMatrix = m
            return true
        }
    })

    private val gestures = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean { onTap?.invoke(); return true }
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (currentScale() > minScale * 1.05f) reset() else {
                val f = 2.5f
                m.postScale(f, f, e.x, e.y); clamp(); imageMatrix = m
            }
            return true
        }
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            if (!isZoomed()) return false
            m.postTranslate(-dx, -dy); clamp(); imageMatrix = m
            return true
        }
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            if (isZoomed() || Math.abs(vx) < Math.abs(vy) * 1.2f || Math.abs(vx) < 600) return false
            onSwipe?.invoke(if (vx < 0) 1 else -1)
            return true
        }
    })

    init {
        scaleType = ScaleType.MATRIX
        isClickable = true
    }

    fun isZoomed() = currentScale() > minScale * 1.02f

    private fun currentScale(): Float { m.getValues(vals); return vals[Matrix.MSCALE_X] }

    fun reset() {
        val d = drawable ?: return
        if (width == 0 || height == 0 || d.intrinsicWidth <= 0 || d.intrinsicHeight <= 0) return
        val s = Math.min(width.toFloat() / d.intrinsicWidth, height.toFloat() / d.intrinsicHeight)
        base.reset()
        base.postScale(s, s)
        base.postTranslate((width - d.intrinsicWidth * s) / 2f, (height - d.intrinsicHeight * s) / 2f)
        minScale = s
        m.set(base)
        imageMatrix = m
    }

    private fun clamp() {
        val d = drawable ?: return
        val r = RectF(0f, 0f, d.intrinsicWidth.toFloat(), d.intrinsicHeight.toFloat())
        m.mapRect(r)
        var dx = 0f; var dy = 0f
        if (r.width() <= width) dx = (width - r.width()) / 2f - r.left
        else if (r.left > 0) dx = -r.left else if (r.right < width) dx = width - r.right
        if (r.height() <= height) dy = (height - r.height()) / 2f - r.top
        else if (r.top > 0) dy = -r.top else if (r.bottom < height) dy = height - r.bottom
        m.postTranslate(dx, dy)
    }

    override fun setImageDrawable(drawable: android.graphics.drawable.Drawable?) {
        super.setImageDrawable(drawable)
        post { reset() }
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        reset()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestures.onTouchEvent(event)
        parent?.requestDisallowInterceptTouchEvent(isZoomed() || event.pointerCount > 1)
        return true
    }
}
