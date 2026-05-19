package com.biasharaai.ui.insights

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import com.biasharaai.R
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class BusinessTelemetryChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = themeColor(com.google.android.material.R.attr.colorOutlineVariant, R.color.biashara_border)
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }
    private val incomePaint = linePaint(R.color.biashara_chart_income)
    private val expensePaint = linePaint(R.color.biashara_chart_expense)
    private val netPaint = linePaint(R.color.biashara_blue)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant, R.color.biashara_muted)
        textSize = sp(11f)
    }

    private var points: List<BusinessTelemetryPoint> = emptyList()

    fun submitPoints(points: List<BusinessTelemetryPoint>) {
        this.points = points
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val left = paddingLeft + dp(34f)
        val top = paddingTop + dp(12f)
        val right = width - paddingRight - dp(8f)
        val bottom = height - paddingBottom - dp(28f)
        if (right <= left || bottom <= top) return

        val chartHeight = bottom - top
        val chartWidth = right - left
        repeat(4) { index ->
            val y = top + chartHeight * index / 3f
            canvas.drawLine(left, y, right, y, gridPaint)
        }

        if (points.isEmpty()) {
            val baseline = bottom
            canvas.drawLine(left, baseline, right, baseline, gridPaint)
            return
        }

        val maxValue = points.maxOf {
            max(max(it.income, it.expenses), it.net)
        }.coerceAtLeast(1.0)
        val minValue = min(0.0, points.minOf { it.net })
        val valueRange = (maxValue - minValue).coerceAtLeast(1.0)
        val zeroY = yForValue(0.0, minValue, valueRange, top, bottom)
        canvas.drawLine(left, zeroY, right, zeroY, gridPaint)

        labelPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(formatAxis(maxValue), left - dp(6f), top + dp(4f), labelPaint)
        canvas.drawText(formatAxis(minValue), left - dp(6f), bottom, labelPaint)

        drawSeries(canvas, points.map { it.income }, incomePaint, left, right, top, bottom, minValue, valueRange)
        drawSeries(canvas, points.map { it.expenses }, expensePaint, left, right, top, bottom, minValue, valueRange)
        drawSeries(canvas, points.map { it.net }, netPaint, left, right, top, bottom, minValue, valueRange)

        labelPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(points.first().label, left, height - paddingBottom - dp(8f), labelPaint)
        labelPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(points.last().label, right, height - paddingBottom - dp(8f), labelPaint)
    }

    private fun drawSeries(
        canvas: Canvas,
        values: List<Double>,
        paint: Paint,
        left: Float,
        right: Float,
        top: Float,
        bottom: Float,
        minValue: Double,
        valueRange: Double,
    ) {
        if (values.isEmpty()) return
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = xForIndex(index, values.lastIndex, left, right)
            val y = yForValue(value, minValue, valueRange, top, bottom)
            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        canvas.drawPath(path, paint)
        val lastX = xForIndex(values.lastIndex, values.lastIndex, left, right)
        val lastY = yForValue(values.last(), minValue, valueRange, top, bottom)
        canvas.drawCircle(lastX, lastY, dp(3f), paint)
    }

    private fun xForIndex(index: Int, lastIndex: Int, left: Float, right: Float): Float {
        if (lastIndex <= 0) return left
        return left + (right - left) * index / lastIndex
    }

    private fun yForValue(value: Double, minValue: Double, valueRange: Double, top: Float, bottom: Float): Float {
        val normalized = ((value - minValue) / valueRange).toFloat()
        return bottom - ((bottom - top) * normalized)
    }

    private fun formatAxis(value: Double): String {
        val magnitude = abs(value)
        return when {
            magnitude >= 1_000_000 -> "${(value / 1_000_000).toInt()}M"
            magnitude >= 1_000 -> "${(value / 1_000).toInt()}K"
            else -> value.toInt().toString()
        }
    }

    private fun linePaint(@ColorRes colorRes: Int): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, colorRes)
            strokeWidth = dp(3f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            style = Paint.Style.STROKE
        }

    private fun themeColor(attr: Int, @ColorRes fallback: Int): Int {
        val typedValue = TypedValue()
        return if (context.theme.resolveAttribute(attr, typedValue, true)) {
            if (typedValue.resourceId != 0) {
                ContextCompat.getColor(context, typedValue.resourceId)
            } else {
                typedValue.data
            }
        } else {
            ContextCompat.getColor(context, fallback)
        }
    }

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)
}
