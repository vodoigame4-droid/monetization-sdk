package com.tomo.monetization.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import androidx.annotation.ColorInt
import androidx.appcompat.widget.AppCompatButton
import com.tomo.monetization.R

class AdsFlashButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.buttonStyle
) : AppCompatButton(context, attrs, defStyleAttr) {

    private val flashPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val flashPath: Path = Path()

    private val clipPath: Path = Path()
    private val rectF: RectF = RectF()

    var cornerRadius: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    private var moveStep: Int = 0
    private var currentOffset: Int = 0
    private var isWaiting: Boolean = false
    private var isFirstRun: Boolean = true

    var isFlashEnabled: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    private val restartRunnable = Runnable {
        isWaiting = false
        invalidate()
    }

    init {
        if (attrs != null) {
            val typedArray = context.obtainStyledAttributes(attrs, R.styleable.AdsFlashButton)
            try {
                cornerRadius = typedArray.getDimension(R.styleable.AdsFlashButton_afb_cornerRadius, 0f)
                val color = typedArray.getColor(R.styleable.AdsFlashButton_afb_flashColor, -1)
                flashPaint.color = color
                isFlashEnabled = typedArray.getBoolean(R.styleable.AdsFlashButton_afb_flashEnabled, true)
            } finally {
                typedArray.recycle()
            }
        }

        flashPaint.style = Paint.Style.STROKE
        if (flashPaint.color == 0) flashPaint.color = -1
        flashPaint.strokeWidth = 100.0f

        val density = context.resources.displayMetrics.density
        val step = (density * 8.0f).toInt()
        moveStep = if (step != 0) step else 1
    }

    @ColorInt
    fun getFlashColor(): Int {
        return flashPaint.color
    }

    fun setFlashColor(@ColorInt color: Int) {
        flashPaint.color = color
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(restartRunnable)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        if (cornerRadius > 0f) {
            rectF.set(0f, 0f, width.toFloat(), height.toFloat())
            clipPath.reset()
            clipPath.addRoundRect(rectF, cornerRadius, cornerRadius, Path.Direction.CW)
            canvas.clipPath(clipPath)
        }

        super.onDraw(canvas)

        if (isEnabled && isFlashEnabled && !isWaiting) {
            val w = width
            val h = height

            if (isFirstRun) {
                isFirstRun = false
                currentOffset = -h
                isWaiting = true
                postDelayed(restartRunnable, 2000)
                return
            }

            flashPath.reset()
            flashPath.moveTo((currentOffset - 50).toFloat(), (h + 50).toFloat())
            flashPath.lineTo((currentOffset + h + 50).toFloat(), -50.0f)
            flashPath.close()

            val threshold = ((h * 2 + w).toDouble() * 0.3) - h.toDouble()
            val currentPos = currentOffset.toDouble()

            val alphaFactor = if (currentPos < threshold) {
                ((currentPos + h) / (threshold + h) * 0.19999999999999998) + 0.1
            } else {
                0.3 - ((currentPos - threshold) / ((w - threshold) + h) * 0.19999999999999998)
            }

            flashPaint.alpha = (alphaFactor * 255.0).toInt().coerceIn(0, 255)

            canvas.drawPath(flashPath, flashPaint)

            currentOffset += moveStep
            if (currentOffset >= w + h + 50) {
                currentOffset = -h
                isWaiting = true
                postDelayed(restartRunnable, 2000)
                return
            }
            postInvalidate()
        }
    }
}
