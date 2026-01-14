package com.example.deutilt

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.sign
import kotlin.random.Random

class GameView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Vibração
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    // Sons
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 80)

    // Estado do jogo
    var isPlaying = false
    var tiltDirection = -1 // -1 = esquerda (Jogador 1), 1 = direita (Jogador 2)
    var speed = 4f // segundos para completar percurso do CENTRO até o FIM

    // Posição da bolinha: -1.0 (extrema esquerda) a +1.0 (extrema direita), 0 = centro
    private var ballPosition = 0f

    // Sistema de zigue-zague visual
    private var zigzagOffset = 0f

    // Animação de transição suave
    private var targetTiltAngle = -15f
    private var currentTiltAngle = -15f
    private val angleSmoothness = 0.2f

    // Sistema de partículas para game over
    private val particles = mutableListOf<Particle>()
    private var showParticles = false

    // Callback para quando o jogo termina
    var onGameOver: ((winner: Int) -> Unit)? = null

    // Callback para mudança de jogador
    var onPlayerChange: (() -> Unit)? = null

    // Última atualização de tempo
    private var lastUpdateTime = 0L

    init {
        setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN && isPlaying) {
                // Apenas inverte a direção, NÃO reseta a posição
                tiltDirection *= -1
                targetTiltAngle = tiltDirection * 15f

                vibrateOnTilt()
                playSoundTilt()
                onPlayerChange?.invoke()

                performClick()
                true
            } else {
                false
            }
        }
    }

    // Classe de partícula para efeito de explosão
    private data class Particle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var life: Float,
        val color: Int
    )

    private fun createParticles(x: Float, y: Float, loserSide: Int) {
        particles.clear()
        // Cor do lado que PERDEU
        val particleColor = if (loserSide == -1) Color.parseColor("#FFD700") else Color.parseColor("#00CED1")

        for (i in 0 until 30) {
            val angle = Random.nextFloat() * 2 * Math.PI.toFloat()
            val speed = Random.nextFloat() * 8 + 3
            particles.add(
                Particle(
                    x = x,
                    y = y,
                    vx = kotlin.math.cos(angle) * speed,
                    vy = kotlin.math.sin(angle) * speed,
                    life = 1f,
                    color = particleColor
                )
            )
        }
        showParticles = true
    }

    private fun updateParticles(deltaTime: Float) {
        if (!showParticles) return

        particles.forEach { p ->
            p.x += p.vx
            p.y += p.vy
            p.vy += 0.5f // Gravidade
            p.life -= deltaTime * 0.8f
        }

        particles.removeAll { it.life <= 0 }

        if (particles.isEmpty()) {
            showParticles = false
        }
    }

    private fun drawParticles(canvas: Canvas) {
        if (!showParticles) return

        paint.style = Paint.Style.FILL
        particles.forEach { p ->
            paint.color = p.color
            paint.alpha = (p.life * 255).toInt().coerceIn(0, 255)
            canvas.drawCircle(p.x, p.y, 8f, paint)
        }
    }

    private fun vibrateOnTilt() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(50)
        }
    }

    private fun vibrateGameOver() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(200)
        }
    }

    private fun playSoundTilt() {
        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
    }

    private fun playSoundGameOver() {
        Thread {
            toneGenerator.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 150)
            Thread.sleep(200)
            toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_NETWORK_LITE, 300)
        }.start()
    }

    private fun playSoundStart() {
        toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 150)
    }

    fun cleanup() {
        toneGenerator.release()
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    fun startGame() {
        isPlaying = true
        lastUpdateTime = System.currentTimeMillis()
        targetTiltAngle = -15f
        currentTiltAngle = -15f
        ballPosition = 0f // Começa no CENTRO
        zigzagOffset = 0f
        particles.clear()
        showParticles = false
        playSoundStart()
        invalidate()
    }

    fun resetGame() {
        isPlaying = false
        tiltDirection = -1
        targetTiltAngle = -15f
        currentTiltAngle = -15f
        ballPosition = 0f // Volta para o CENTRO
        zigzagOffset = 0f
        particles.clear()
        showParticles = false
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()
        val centerX = width / 2
        val centerY = height / 2

        // Atualizar posição da bolinha se o jogo estiver rodando
        if (isPlaying) {
            val currentTime = System.currentTimeMillis()
            val deltaTime = (currentTime - lastUpdateTime) / 1000f
            lastUpdateTime = currentTime

            // Velocidade: leva 'speed' segundos para ir de 0 (centro) até 1 (extremidade)
            val moveSpeed = (1f / speed) * deltaTime
            ballPosition += tiltDirection * moveSpeed

            // Calcular efeito de zigue-zague baseado na posição
            val absPos = abs(ballPosition)
            val numZigzags = 6
            val zigzagFrequency = numZigzags * Math.PI.toFloat()
            zigzagOffset = kotlin.math.sin(absPos * zigzagFrequency) * 20f

            // Suavizar rotação da gangorra
            currentTiltAngle += (targetTiltAngle - currentTiltAngle) * angleSmoothness

            // Verifica fim de jogo (chegou na extremidade)
            if (abs(ballPosition) >= 1f) {
                isPlaying = false

                // Quem perde é o jogador do lado onde a bolinha chegou
                val loserSide = sign(ballPosition).toInt()
                val winner = if (loserSide == -1) 2 else 1

                // Calcular posição final da bolinha no espaço mundial (considerando rotação)
                val maxHorizontalOffset = width * 0.7f * 0.4f
                val ballXLocal = ballPosition * maxHorizontalOffset
                val ballYLocal = zigzagOffset

                // Converter coordenadas locais (rotacionadas) para coordenadas mundiais
                val angleRad = Math.toRadians(currentTiltAngle.toDouble())
                val ballXWorld = centerX + (ballXLocal * kotlin.math.cos(angleRad) - ballYLocal * kotlin.math.sin(angleRad)).toFloat()
                val ballYWorld = centerY + (ballXLocal * kotlin.math.sin(angleRad) + ballYLocal * kotlin.math.cos(angleRad)).toFloat()

                createParticles(ballXWorld, ballYWorld, loserSide)
                vibrateGameOver()
                playSoundGameOver()
                onGameOver?.invoke(winner)
                ballPosition = if (ballPosition > 0) 1f else -1f
            }

            invalidate()
        }

        // Atualizar partículas
        if (showParticles) {
            val currentTime = System.currentTimeMillis()
            val deltaTime = (currentTime - lastUpdateTime) / 1000f
            lastUpdateTime = currentTime
            updateParticles(deltaTime)
            invalidate()
        }

        // Desenhar base da gangorra com gradiente
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            centerX - 15f, centerY + 150,
            centerX + 15f, centerY + 230,
            Color.parseColor("#424242"),
            Color.parseColor("#757575"),
            Shader.TileMode.CLAMP
        )
        val baseWidth = 30f
        val baseHeight = 80f
        canvas.drawRoundRect(
            centerX - baseWidth / 2,
            centerY + 150,
            centerX + baseWidth / 2,
            centerY + 150 + baseHeight,
            10f,
            10f,
            paint
        )
        paint.shader = null

        // Desenhar rampa (gangorra) com ângulo suavizado
        canvas.save()
        canvas.rotate(currentTiltAngle, centerX, centerY)

        val rampWidth = width * 0.7f
        val rampHeight = 120f
        val rampLeft = centerX - rampWidth / 2
        val rampTop = centerY - rampHeight / 2

        // Gradiente na rampa
        paint.shader = LinearGradient(
            rampLeft, centerY,
            rampLeft + rampWidth, centerY,
            intArrayOf(
                Color.parseColor("#FFD700"),
                Color.parseColor("#FF9800"),
                Color.parseColor("#00CED1")
            ),
            null,
            Shader.TileMode.CLAMP
        )
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(
            rampLeft,
            rampTop,
            rampLeft + rampWidth,
            rampTop + rampHeight,
            20f,
            20f,
            paint
        )
        paint.shader = null

        // Desenhar aletas de zigue-zague (mais realistas)
        paint.color = Color.parseColor("#FFFFFF")
        paint.alpha = 120
        paint.strokeWidth = 6f
        paint.style = Paint.Style.STROKE

        val numZigzags = 6
        val aletaSegmentWidth = rampWidth / (numZigzags + 1)
        for (i in 1..numZigzags) {
            val lineX = rampLeft + aletaSegmentWidth * i
            val isUp = i % 2 == 1
            val startY = if (isUp) rampTop + 15 else rampTop + rampHeight / 2 + 5
            val endY = if (isUp) rampTop + rampHeight / 2 - 5 else rampTop + rampHeight - 15
            canvas.drawLine(lineX, startY, lineX, endY, paint)
        }

        // IMPORTANTE: Desenhar a bolinha DENTRO do sistema rotacionado da gangorra
        // para que ela acompanhe a inclinação

        // Calcular posição da bolinha no eixo da gangorra (coordenadas locais)
        // ballPosition vai de -1 (esquerda) a +1 (direita), 0 = centro
        val maxHorizontalOffset = rampWidth * 0.4f
        val ballXLocal = centerX + (ballPosition * maxHorizontalOffset)
        val ballYLocal = centerY + zigzagOffset

        // Sombra da bolinha (no sistema rotacionado)
        paint.color = Color.parseColor("#000000")
        paint.alpha = 80
        paint.style = Paint.Style.FILL
        canvas.drawCircle(ballXLocal + 4, ballYLocal + 4, 22f, paint)

        // Bolinha principal com gradiente
        paint.shader = LinearGradient(
            ballXLocal - 20, ballYLocal - 20,
            ballXLocal + 20, ballYLocal + 20,
            Color.parseColor("#FFFFFF"),
            Color.parseColor("#E0E0E0"),
            Shader.TileMode.CLAMP
        )
        paint.alpha = 255
        canvas.drawCircle(ballXLocal, ballYLocal, 20f, paint)
        paint.shader = null

        // Borda da bolinha
        paint.color = Color.parseColor("#FF0000")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 6f
        canvas.drawCircle(ballXLocal, ballYLocal, 20f, paint)

        // Efeito de brilho na bolinha
        paint.color = Color.parseColor("#FFFFFF")
        paint.alpha = 180
        paint.style = Paint.Style.FILL
        canvas.drawCircle(ballXLocal - 6, ballYLocal - 6, 7f, paint)

        canvas.restore() // Restaura o sistema de coordenadas normal DEPOIS de desenhar tudo

        // Desenhar partículas (fora do sistema rotacionado)
        drawParticles(canvas)
    }
}