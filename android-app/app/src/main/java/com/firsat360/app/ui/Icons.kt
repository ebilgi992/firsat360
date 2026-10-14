package com.firsat360.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View

enum class Icon { HOME, STORE, SEARCH, HEART, HEART_FILLED, BACK, CLOSE, EXTERNAL, SHARE, SETTINGS, CALENDAR, CLOCK,
    INFO, SHIELD, TAG, REFRESH, CHEVRON, SORT, GLOBE, CATALOG, LIST, BELL, STAR, STAR_FILLED, PLUS, CHECK, GRID,
    CHECK_BOX, BOX, TRASH, DOWNLOAD, FLAME, SPARK, WIFI, PDF }

/** 24x24 ızgarada çizilen, çözünürlükten bağımsız çizgi ikonlar (AndroidX/vektör kütüphanesi gerektirmez). */
class IconView(ctx: Context, icon: Icon, color: Int, sizeDp: Int = 24) : View(ctx) {
    var icon: Icon = icon
        set(v) { field = v; invalidate() }
    var color: Int = color
        set(v) { field = v; invalidate() }
    private val sizePx = dp(sizeDp)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val path = Path()

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(sizePx, sizePx)

    override fun onDraw(c: Canvas) {
        val s = width / 24f
        c.save()
        c.scale(s, s)
        stroke.color = color
        stroke.strokeWidth = 1.8f
        fill.color = color
        path.reset()
        val p = path
        when (icon) {
            Icon.HOME -> {
                p.moveTo(3.5f, 10.5f); p.lineTo(12f, 3.5f); p.lineTo(20.5f, 10.5f)
                p.moveTo(5.5f, 9f); p.lineTo(5.5f, 20f); p.lineTo(18.5f, 20f); p.lineTo(18.5f, 9f)
                p.moveTo(10f, 20f); p.lineTo(10f, 14.5f); p.lineTo(14f, 14.5f); p.lineTo(14f, 20f)
            }
            Icon.STORE -> {
                c.drawRoundRect(RectF(4.5f, 7.5f, 19.5f, 20.5f), 2.5f, 2.5f, stroke)
                p.moveTo(8.5f, 10f); p.lineTo(8.5f, 6.8f); p.cubicTo(8.5f, 4.7f, 10f, 3.5f, 12f, 3.5f)
                p.cubicTo(14f, 3.5f, 15.5f, 4.7f, 15.5f, 6.8f); p.lineTo(15.5f, 10f)
            }
            Icon.SEARCH -> {
                c.drawCircle(10.8f, 10.8f, 6.8f, stroke)
                p.moveTo(15.8f, 15.8f); p.lineTo(20.5f, 20.5f)
            }
            Icon.HEART, Icon.HEART_FILLED -> {
                p.moveTo(12f, 20f); p.cubicTo(12f, 20f, 3.5f, 14.8f, 3.5f, 9f)
                p.cubicTo(3.5f, 6.3f, 5.5f, 4.3f, 8f, 4.3f); p.cubicTo(9.7f, 4.3f, 11.2f, 5.3f, 12f, 6.7f)
                p.cubicTo(12.8f, 5.3f, 14.3f, 4.3f, 16f, 4.3f); p.cubicTo(18.5f, 4.3f, 20.5f, 6.3f, 20.5f, 9f)
                p.cubicTo(20.5f, 14.8f, 12f, 20f, 12f, 20f); p.close()
                if (icon == Icon.HEART_FILLED) c.drawPath(p, fill)
            }
            Icon.BACK -> { p.moveTo(15f, 5f); p.lineTo(8f, 12f); p.lineTo(15f, 19f) }
            Icon.CLOSE -> { p.moveTo(6.5f, 6.5f); p.lineTo(17.5f, 17.5f); p.moveTo(17.5f, 6.5f); p.lineTo(6.5f, 17.5f) }
            Icon.EXTERNAL -> {
                p.moveTo(14f, 4f); p.lineTo(20f, 4f); p.lineTo(20f, 10f)
                p.moveTo(20f, 4f); p.lineTo(11.5f, 12.5f)
                p.moveTo(18f, 14f); p.lineTo(18f, 19f); p.quadTo(18f, 20f, 17f, 20f); p.lineTo(5f, 20f)
                p.quadTo(4f, 20f, 4f, 19f); p.lineTo(4f, 7f); p.quadTo(4f, 6f, 5f, 6f); p.lineTo(10f, 6f)
            }
            Icon.SHARE -> {
                c.drawCircle(17.5f, 5.5f, 2.4f, stroke); c.drawCircle(6.5f, 12f, 2.4f, stroke); c.drawCircle(17.5f, 18.5f, 2.4f, stroke)
                p.moveTo(8.6f, 10.8f); p.lineTo(15.4f, 6.8f); p.moveTo(8.6f, 13.2f); p.lineTo(15.4f, 17.2f)
            }
            Icon.SETTINGS -> {
                p.moveTo(4f, 7.5f); p.lineTo(6.4f, 7.5f); p.moveTo(11.6f, 7.5f); p.lineTo(20f, 7.5f)
                p.moveTo(4f, 16.5f); p.lineTo(12.4f, 16.5f); p.moveTo(17.6f, 16.5f); p.lineTo(20f, 16.5f)
                c.drawCircle(9f, 7.5f, 2.6f, stroke)
                c.drawCircle(15f, 16.5f, 2.6f, stroke)
            }
            Icon.CALENDAR -> {
                c.drawRoundRect(RectF(4f, 5.5f, 20f, 20f), 2.5f, 2.5f, stroke)
                p.moveTo(4f, 10f); p.lineTo(20f, 10f); p.moveTo(8.5f, 3.5f); p.lineTo(8.5f, 7f); p.moveTo(15.5f, 3.5f); p.lineTo(15.5f, 7f)
            }
            Icon.CLOCK -> {
                c.drawCircle(12f, 12f, 8.5f, stroke)
                p.moveTo(12f, 7.5f); p.lineTo(12f, 12f); p.lineTo(15f, 14f)
            }
            Icon.INFO -> {
                c.drawCircle(12f, 12f, 8.5f, stroke)
                p.moveTo(12f, 11f); p.lineTo(12f, 16.2f)
                c.drawCircle(12f, 7.9f, 1.1f, fill)
            }
            Icon.SHIELD -> {
                p.moveTo(12f, 3.5f); p.lineTo(19f, 6.3f); p.lineTo(19f, 11.5f); p.cubicTo(19f, 15.8f, 16.1f, 19f, 12f, 20.5f)
                p.cubicTo(7.9f, 19f, 5f, 15.8f, 5f, 11.5f); p.lineTo(5f, 6.3f); p.close()
                p.moveTo(9f, 12f); p.lineTo(11.2f, 14.2f); p.lineTo(15.3f, 10f)
            }
            Icon.TAG -> {
                p.moveTo(3.8f, 12.2f); p.lineTo(12f, 4f); p.lineTo(19.5f, 4f); p.quadTo(20f, 4f, 20f, 4.5f); p.lineTo(20f, 12f)
                p.lineTo(11.8f, 20.2f); p.quadTo(11.3f, 20.7f, 10.8f, 20.2f); p.lineTo(3.8f, 13.2f); p.quadTo(3.3f, 12.7f, 3.8f, 12.2f); p.close()
                c.drawCircle(16f, 8f, 1.3f, fill)
            }
            Icon.REFRESH -> {
                p.moveTo(19.5f, 12f); p.cubicTo(19.5f, 16.1f, 16.1f, 19.5f, 12f, 19.5f); p.cubicTo(7.9f, 19.5f, 4.5f, 16.1f, 4.5f, 12f)
                p.cubicTo(4.5f, 7.9f, 7.9f, 4.5f, 12f, 4.5f); p.cubicTo(14.6f, 4.5f, 16.8f, 5.8f, 18.2f, 7.8f)
                p.moveTo(18.8f, 3.5f); p.lineTo(18.8f, 8f); p.lineTo(14.3f, 8f)
            }
            Icon.CHEVRON -> { p.moveTo(9.5f, 5.5f); p.lineTo(16f, 12f); p.lineTo(9.5f, 18.5f) }
            Icon.SORT -> {
                p.moveTo(7.5f, 4.5f); p.lineTo(7.5f, 19.5f); p.moveTo(4.5f, 16.5f); p.lineTo(7.5f, 19.5f); p.lineTo(10.5f, 16.5f)
                p.moveTo(16.5f, 19.5f); p.lineTo(16.5f, 4.5f); p.moveTo(13.5f, 7.5f); p.lineTo(16.5f, 4.5f); p.lineTo(19.5f, 7.5f)
            }
            Icon.GLOBE -> {
                c.drawCircle(12f, 12f, 8.5f, stroke)
                p.moveTo(3.5f, 12f); p.lineTo(20.5f, 12f)
                p.moveTo(12f, 3.5f); p.cubicTo(9.3f, 6.2f, 9.3f, 17.8f, 12f, 20.5f)
                p.moveTo(12f, 3.5f); p.cubicTo(14.7f, 6.2f, 14.7f, 17.8f, 12f, 20.5f)
            }
            Icon.CATALOG -> {
                p.moveTo(12f, 6.5f); p.cubicTo(10f, 5f, 7f, 4.5f, 4f, 5f); p.lineTo(4f, 18.5f); p.cubicTo(7f, 18f, 10f, 18.5f, 12f, 20f)
                p.cubicTo(14f, 18.5f, 17f, 18f, 20f, 18.5f); p.lineTo(20f, 5f); p.cubicTo(17f, 4.5f, 14f, 5f, 12f, 6.5f); p.close()
                p.moveTo(12f, 6.5f); p.lineTo(12f, 20f)
            }
            Icon.LIST -> {
                p.moveTo(9f, 6.5f); p.lineTo(20f, 6.5f); p.moveTo(9f, 12f); p.lineTo(20f, 12f); p.moveTo(9f, 17.5f); p.lineTo(20f, 17.5f)
                c.drawCircle(5f, 6.5f, 1.2f, fill); c.drawCircle(5f, 12f, 1.2f, fill); c.drawCircle(5f, 17.5f, 1.2f, fill)
            }
            Icon.BELL -> {
                p.moveTo(6f, 16.5f); p.lineTo(6f, 10.5f); p.cubicTo(6f, 7f, 8.7f, 4.5f, 12f, 4.5f); p.cubicTo(15.3f, 4.5f, 18f, 7f, 18f, 10.5f)
                p.lineTo(18f, 16.5f); p.lineTo(19.5f, 18f); p.lineTo(4.5f, 18f); p.close()
                p.moveTo(10f, 20.5f); p.lineTo(14f, 20.5f)
            }
            Icon.STAR, Icon.STAR_FILLED -> {
                p.moveTo(12f, 3.8f); p.lineTo(14.5f, 9f); p.lineTo(20.2f, 9.7f); p.lineTo(16f, 13.6f); p.lineTo(17.1f, 19.3f)
                p.lineTo(12f, 16.5f); p.lineTo(6.9f, 19.3f); p.lineTo(8f, 13.6f); p.lineTo(3.8f, 9.7f); p.lineTo(9.5f, 9f); p.close()
                if (icon == Icon.STAR_FILLED) c.drawPath(p, fill)
            }
            Icon.PLUS -> { p.moveTo(12f, 5f); p.lineTo(12f, 19f); p.moveTo(5f, 12f); p.lineTo(19f, 12f) }
            Icon.CHECK -> { p.moveTo(5f, 12.5f); p.lineTo(10f, 17.5f); p.lineTo(19.5f, 7f) }
            Icon.GRID -> {
                c.drawRoundRect(RectF(4f, 4f, 10.5f, 10.5f), 1.5f, 1.5f, stroke); c.drawRoundRect(RectF(13.5f, 4f, 20f, 10.5f), 1.5f, 1.5f, stroke)
                c.drawRoundRect(RectF(4f, 13.5f, 10.5f, 20f), 1.5f, 1.5f, stroke); c.drawRoundRect(RectF(13.5f, 13.5f, 20f, 20f), 1.5f, 1.5f, stroke)
            }
            Icon.BOX -> c.drawRoundRect(RectF(4.5f, 4.5f, 19.5f, 19.5f), 4f, 4f, stroke)
            Icon.CHECK_BOX -> {
                c.drawRoundRect(RectF(4.5f, 4.5f, 19.5f, 19.5f), 4f, 4f, fill)
                stroke.color = 0xFFFFFFFF.toInt(); stroke.strokeWidth = 2.2f
                p.moveTo(8f, 12.3f); p.lineTo(11f, 15.3f); p.lineTo(16.5f, 9f)
            }
            Icon.TRASH -> {
                p.moveTo(4.5f, 6.5f); p.lineTo(19.5f, 6.5f); p.moveTo(9.5f, 6.5f); p.lineTo(9.5f, 4f); p.lineTo(14.5f, 4f); p.lineTo(14.5f, 6.5f)
                p.moveTo(6.5f, 6.5f); p.lineTo(7.5f, 20f); p.lineTo(16.5f, 20f); p.lineTo(17.5f, 6.5f)
            }
            Icon.DOWNLOAD -> {
                p.moveTo(12f, 4f); p.lineTo(12f, 15f); p.moveTo(7.5f, 10.5f); p.lineTo(12f, 15f); p.lineTo(16.5f, 10.5f)
                p.moveTo(5f, 19.5f); p.lineTo(19f, 19.5f)
            }
            Icon.FLAME -> {
                p.moveTo(12f, 21f); p.cubicTo(8f, 21f, 5.5f, 18.2f, 5.5f, 14.8f); p.cubicTo(5.5f, 11f, 8.5f, 9.2f, 9.5f, 5.5f)
                p.cubicTo(11.5f, 7f, 12.2f, 9f, 12f, 11f); p.cubicTo(13.4f, 10.2f, 14.2f, 8.8f, 14.3f, 7.3f)
                p.cubicTo(17f, 9.3f, 18.5f, 12f, 18.5f, 14.8f); p.cubicTo(18.5f, 18.2f, 16f, 21f, 12f, 21f); p.close()
                c.drawPath(p, fill)
            }
            Icon.SPARK -> {
                p.moveTo(12f, 3f); p.cubicTo(12.6f, 8.4f, 15.6f, 11.4f, 21f, 12f); p.cubicTo(15.6f, 12.6f, 12.6f, 15.6f, 12f, 21f)
                p.cubicTo(11.4f, 15.6f, 8.4f, 12.6f, 3f, 12f); p.cubicTo(8.4f, 11.4f, 11.4f, 8.4f, 12f, 3f); p.close()
                c.drawPath(p, fill)
            }
            Icon.WIFI -> {
                p.moveTo(3f, 9.5f); p.cubicTo(8f, 4.8f, 16f, 4.8f, 21f, 9.5f)
                p.moveTo(6f, 12.8f); p.cubicTo(9.4f, 9.8f, 14.6f, 9.8f, 18f, 12.8f)
                p.moveTo(9f, 16f); p.cubicTo(10.8f, 14.6f, 13.2f, 14.6f, 15f, 16f)
                c.drawCircle(12f, 19f, 1.2f, fill)
            }
            Icon.PDF -> {
                p.moveTo(6f, 3.5f); p.lineTo(14.5f, 3.5f); p.lineTo(19f, 8f); p.lineTo(19f, 20.5f); p.lineTo(6f, 20.5f); p.close()
                p.moveTo(14.5f, 3.5f); p.lineTo(14.5f, 8f); p.lineTo(19f, 8f)
                p.moveTo(9f, 13f); p.lineTo(16f, 13f); p.moveTo(9f, 16.5f); p.lineTo(14f, 16.5f)
            }
        }
        c.drawPath(p, stroke)
        c.restore()
    }
}
