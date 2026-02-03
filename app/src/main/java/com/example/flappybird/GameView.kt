package com.example.flappybird

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.max
import kotlin.random.Random

class GameView(context: Context) : SurfaceView(context), Runnable, SurfaceHolder.Callback {
    private val holderRef = holder
    private var gameThread: Thread? = null
    private var isRunning = false

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 64f
    }

    private var screenWidth = 0
    private var screenHeight = 0

    private var birdX = 0f
    private var birdY = 0f
    private var birdRadius = 40f
    private var birdVelocity = 0f

    private val gravity = 1.2f
    private val flapStrength = -18f

    private var groundHeight = 160f
    private var pipeWidth = 0f
    private var pipeGap = 0f
    private var pipeSpeed = 6f

    private val pipes = mutableListOf<Pipe>()

    private var score = 0
    private var bestScore = 0
    private var isGameOver = false
    private var hasStarted = false

    init {
        holderRef.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        screenWidth = width
        screenHeight = height
        birdX = screenWidth * 0.25f
        birdY = screenHeight * 0.5f
        birdRadius = screenWidth * 0.04f
        groundHeight = screenHeight * 0.15f
        pipeWidth = screenWidth * 0.18f
        pipeGap = screenHeight * 0.28f
        pipeSpeed = max(6f, screenWidth * 0.005f)
        resetGame()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        screenWidth = width
        screenHeight = height
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        pause()
    }

    override fun run() {
        while (isRunning) {
            if (!holderRef.surface.isValid) {
                continue
            }
            val canvas = holderRef.lockCanvas() ?: continue
            updateGame()
            drawGame(canvas)
            holderRef.unlockCanvasAndPost(canvas)
            try {
                Thread.sleep(16L)
            } catch (interrupted: InterruptedException) {
                return
            }
        }
    }

    private fun resetGame() {
        score = 0
        birdY = screenHeight * 0.5f
        birdVelocity = 0f
        pipes.clear()
        val spacing = screenWidth * 0.65f
        var pipeX = screenWidth * 1.1f
        repeat(3) {
            pipes.add(createPipe(pipeX))
            pipeX += spacing
        }
        isGameOver = false
        hasStarted = false
    }

    private fun createPipe(xPosition: Float): Pipe {
        val minGapY = screenHeight * 0.2f
        val maxGapY = screenHeight - groundHeight - pipeGap - minGapY
        val gapTop = Random.nextFloat() * (maxGapY - minGapY) + minGapY
        return Pipe(xPosition, gapTop, false)
    }

    private fun updateGame() {
        if (!hasStarted || isGameOver) {
            return
        }

        birdVelocity += gravity
        birdY += birdVelocity

        val groundTop = screenHeight - groundHeight
        if (birdY + birdRadius >= groundTop || birdY - birdRadius <= 0f) {
            isGameOver = true
            bestScore = max(bestScore, score)
        }

        val spacing = screenWidth * 0.65f
        var furthestX = pipes.maxOfOrNull { it.x } ?: screenWidth.toFloat()
        pipes.forEach { pipe ->
            pipe.x -= pipeSpeed
            if (pipe.x + pipeWidth < 0f) {
                pipe.x = furthestX + spacing
                pipe.gapTop = createPipe(pipe.x).gapTop
                pipe.passed = false
                furthestX = pipe.x
            }

            if (!pipe.passed && pipe.x + pipeWidth < birdX) {
                pipe.passed = true
                score += 1
            }

            if (checkCollision(pipe)) {
                isGameOver = true
                bestScore = max(bestScore, score)
            }
        }
    }

    private fun checkCollision(pipe: Pipe): Boolean {
        val birdRect = RectF(
            birdX - birdRadius,
            birdY - birdRadius,
            birdX + birdRadius,
            birdY + birdRadius
        )
        val topRect = RectF(pipe.x, 0f, pipe.x + pipeWidth, pipe.gapTop)
        val bottomRect = RectF(
            pipe.x,
            pipe.gapTop + pipeGap,
            pipe.x + pipeWidth,
            screenHeight - groundHeight
        )
        return RectF.intersects(birdRect, topRect) || RectF.intersects(birdRect, bottomRect)
    }

    private fun drawGame(canvas: Canvas) {
        canvas.drawColor(Color.parseColor("#87CEEB"))

        paint.color = Color.parseColor("#4CAF50")
        pipes.forEach { pipe ->
            canvas.drawRect(pipe.x, 0f, pipe.x + pipeWidth, pipe.gapTop, paint)
            canvas.drawRect(
                pipe.x,
                pipe.gapTop + pipeGap,
                pipe.x + pipeWidth,
                screenHeight - groundHeight,
                paint
            )
        }

        paint.color = Color.parseColor("#8D6E63")
        canvas.drawRect(0f, screenHeight - groundHeight, screenWidth.toFloat(), screenHeight.toFloat(), paint)

        paint.color = Color.parseColor("#FFD54F")
        canvas.drawCircle(birdX, birdY, birdRadius, paint)

        canvas.drawText("Score: $score", 32f, 80f, textPaint)

        if (!hasStarted) {
            drawCenteredText(canvas, "Tap to start")
        } else if (isGameOver) {
            drawCenteredText(canvas, "Game Over\nBest: $bestScore\nTap to retry")
        }
    }

    private fun drawCenteredText(canvas: Canvas, message: String) {
        val lines = message.split("\n")
        val lineHeight = textPaint.textSize * 1.2f
        val startY = screenHeight / 2f - (lines.size - 1) * lineHeight / 2f
        lines.forEachIndexed { index, line ->
            val textWidth = textPaint.measureText(line)
            val x = (screenWidth - textWidth) / 2f
            val y = startY + index * lineHeight
            canvas.drawText(line, x, y, textPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            if (!hasStarted) {
                hasStarted = true
                birdVelocity = flapStrength
            } else if (isGameOver) {
                resetGame()
                hasStarted = true
                birdVelocity = flapStrength
            } else {
                birdVelocity = flapStrength
            }
            return true
        }
        return super.onTouchEvent(event)
    }

    fun resume() {
        if (isRunning) {
            return
        }
        isRunning = true
        gameThread = Thread(this)
        gameThread?.start()
    }

    fun pause() {
        isRunning = false
        try {
            gameThread?.join()
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private data class Pipe(var x: Float, var gapTop: Float, var passed: Boolean)
}
