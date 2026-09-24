package fr.vueconfort.vision

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.TextPaint
import android.text.TextUtils
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/** A vertical-drag rotary control. Assigning [value] never invokes [onValueChanged]. */
class VisionRotaryControl(
    context: Context,
    val label: String,
    val minValue: Double,
    val maxValue: Double,
    initialValue: Double,
    val formatValue: (Double) -> String,
    val onValueChanged: (Double) -> Unit
) : View(context) {
    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val arcBounds = RectF()
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var downY = 0f
    private var lastY = 0f
    private var dragging = false
    private var centerX = 0f
    private var centerY = 0f
    private var radius = 0f

    var value: Double = initialValue
        set(newValue) {
            require(newValue.isFinite()) { "The control value must be finite" }
            val bounded = newValue.coerceIn(minValue, maxValue)
            if (field == bounded) return
            field = bounded
            updateDescription()
            invalidate()
        }

    init {
        require(minValue.isFinite() && maxValue.isFinite() && minValue < maxValue &&
            (maxValue - minValue).isFinite()) { "The control requires a finite increasing range" }
        require(initialValue.isFinite()) { "The initial value must be finite" }
        value = initialValue.coerceIn(minValue, maxValue)
        isFocusable = true
        isClickable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        updateDescription()
    }

    private fun dp(amount: Float) = amount * density

    private fun updateDescription() {
        contentDescription = "$label : ${formatValue(value)}"
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            resolveSizeAndState((dp(90f) + paddingLeft + paddingRight).toInt(), widthMeasureSpec, 0),
            resolveSizeAndState((dp(106f) + paddingTop + paddingBottom).toInt(), heightMeasureSpec, 0)
        )
    }

    private fun updateGeometry() {
        val availableWidth = (width - paddingLeft - paddingRight).coerceAtLeast(0).toFloat()
        val availableHeight = (height - paddingTop - paddingBottom).coerceAtLeast(0).toFloat()
        val textBand = min(dp(22f), availableHeight / 3f)
        val dialHeight = (availableHeight - 2f * textBand).coerceAtLeast(0f)
        centerX = paddingLeft + availableWidth / 2f
        centerY = paddingTop + textBand + dialHeight / 2f
        radius = min(availableWidth / 2f - dp(10f), dialHeight / 2f - dp(4f)).coerceAtLeast(0f)
        arcBounds.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius)
    }

    private fun visibleColor(color: Int): Int = if (isEnabled) color else
        Color.argb(100, Color.red(color), Color.green(color), Color.blue(color))

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        updateGeometry()
        val availableWidth = (width - paddingLeft - paddingRight - dp(8f)).coerceAtLeast(0f)
        val top = paddingTop.toFloat()
        val bottom = (height - paddingBottom).toFloat()

        if (hasFocus() && isEnabled) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(1.5f)
            paint.color = Color.rgb(111, 53, 180)
            canvas.drawRoundRect(dp(2f), dp(2f), width - dp(2f), height - dp(2f), dp(8f), dp(8f), paint)
        }

        drawText(canvas, label, top + dp(13f), 12f, availableWidth, false)
        if (radius > 0f) {
            paint.style = Paint.Style.FILL
            paint.color = visibleColor(if (isPressed) Color.rgb(220, 235, 255) else Color.rgb(241, 244, 248))
            canvas.drawCircle(centerX, centerY, (radius - dp(4f)).coerceAtLeast(0f), paint)

            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeWidth = dp(3f)
            paint.color = visibleColor(Color.rgb(198, 207, 219))
            canvas.drawArc(arcBounds, 135f, 270f, false, paint)

            val fraction = ((value - minValue) / (maxValue - minValue)).coerceIn(0.0, 1.0)
            paint.color = visibleColor(Color.rgb(111, 53, 180))
            if (fraction > 0.0) canvas.drawArc(arcBounds, 135f, (270.0 * fraction).toFloat(), false, paint)
            val angle = Math.toRadians(135.0 + 270.0 * fraction)
            val needleRadius = (radius - dp(8f)).coerceAtLeast(0f)
            paint.strokeWidth = dp(2.5f)
            canvas.drawLine(centerX, centerY,
                centerX + cos(angle).toFloat() * needleRadius,
                centerY + sin(angle).toFloat() * needleRadius, paint)
            paint.style = Paint.Style.FILL
            canvas.drawCircle(centerX, centerY, dp(2.7f), paint)
        }
        drawText(canvas, formatValue(value), bottom - dp(10f), 13f, availableWidth, true)
    }

    private fun drawText(canvas: Canvas, text: String, baseline: Float, sizeSp: Float, availableWidth: Float, bold: Boolean) {
        if (availableWidth <= 0f) return
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        textPaint.textSize = sizeSp * scaledDensity
        textPaint.color = visibleColor(Color.BLACK)
        val display = TextUtils.ellipsize(text, textPaint, availableWidth, TextUtils.TruncateAt.END)
        canvas.drawText(display.toString(), centerX, baseline, textPaint)
    }

    private fun changeFromUser(newValue: Double) {
        val before = value
        value = newValue.coerceIn(minValue, maxValue)
        if (value != before) {
            onValueChanged(value)
            sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED)
        }
    }

    private fun finishGesture() {
        activePointerId = MotionEvent.INVALID_POINTER_ID
        dragging = false
        isPressed = false
        parent?.requestDisallowInterceptTouchEvent(false)
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                updateGeometry()
                // Expand the disk's hit area to at least the recommended 48 dp diameter.
                val touchRadius = maxOf(radius + dp(8f), dp(24f))
                if (hypot(event.x - centerX, event.y - centerY) > touchRadius) return false
                activePointerId = event.getPointerId(0)
                downY = event.y
                lastY = event.y
                dragging = false
                isPressed = true
                requestFocus()
                parent?.requestDisallowInterceptTouchEvent(true)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(activePointerId)
                if (index < 0) {
                    finishGesture()
                    return false
                }
                val y = event.getY(index)
                if (!dragging && abs(y - downY) > touchSlop) dragging = true
                if (dragging) {
                    val delta = (lastY - y) / dp(160f)
                    changeFromUser(value + delta * (maxValue - minValue))
                    lastY = y
                }
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) == activePointerId) {
                    val replacement = if (event.actionIndex == 0) 1 else 0
                    if (replacement < event.pointerCount) {
                        activePointerId = event.getPointerId(replacement)
                        downY = event.getY(replacement)
                        lastY = downY
                    } else finishGesture()
                }
                return activePointerId != MotionEvent.INVALID_POINTER_ID
            }
            MotionEvent.ACTION_UP -> {
                if (activePointerId == MotionEvent.INVALID_POINTER_ID) return false
                val clicked = !dragging
                finishGesture()
                if (clicked) performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                val handled = activePointerId != MotionEvent.INVALID_POINTER_ID
                finishGesture()
                return handled
            }
        }
        return activePointerId != MotionEvent.INVALID_POINTER_ID
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        if (!enabled) finishGesture()
        invalidate()
    }

    override fun onDetachedFromWindow() {
        finishGesture()
        super.onDetachedFromWindow()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!isEnabled) return super.onKeyDown(keyCode, event)
        val direction = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_PLUS, KeyEvent.KEYCODE_EQUALS, KeyEvent.KEYCODE_NUMPAD_ADD -> 1
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> -1
            else -> return super.onKeyDown(keyCode, event)
        }
        changeFromUser(value + direction * (maxValue - minValue) / 100.0)
        return true
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.SeekBar"
        info.rangeInfo = AccessibilityNodeInfo.RangeInfo.obtain(
            AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT,
            minValue.toFloat(), maxValue.toFloat(), value.toFloat()
        )
        info.isScrollable = isEnabled
        if (isEnabled) {
            if (value < maxValue) info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            if (value > minValue) info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS)
            }
        }
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        if (!isEnabled) return false
        when (action) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> {
                changeFromUser(value + (maxValue - minValue) / 100.0)
                return true
            }
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> {
                changeFromUser(value - (maxValue - minValue) / 100.0)
                return true
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            action == AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id) {
            if (arguments == null || !arguments.containsKey(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE)) return false
            val progress = arguments.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE).toDouble()
            if (!progress.isFinite()) return false
            changeFromUser(progress)
            return true
        }
        return super.performAccessibilityAction(action, arguments)
    }
}
