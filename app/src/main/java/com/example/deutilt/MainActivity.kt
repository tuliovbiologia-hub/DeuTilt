package com.example.deutilt

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var gameView: GameView
    private lateinit var btnStart: Button
    private lateinit var btnReset: Button
    private lateinit var btn4s: Button
    private lateinit var btn6s: Button
    private lateinit var btn8s: Button
    private lateinit var btn10s: Button
    private lateinit var indicatorPlayer1: View
    private lateinit var indicatorPlayer2: View

    private var selectedSpeed = 4f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inicializar views
        val gameContainer = findViewById<FrameLayout>(R.id.gameContainer)
        btnStart = findViewById(R.id.btnStart)
        btnReset = findViewById(R.id.btnReset)
        btn4s = findViewById(R.id.btn4s)
        btn6s = findViewById(R.id.btn6s)
        btn8s = findViewById(R.id.btn8s)
        btn10s = findViewById(R.id.btn10s)
        indicatorPlayer1 = findViewById(R.id.indicatorPlayer1)
        indicatorPlayer2 = findViewById(R.id.indicatorPlayer2)

        // Criar e adicionar GameView
        gameView = GameView(this)
        gameContainer.addView(gameView)

        // Configurar callback de fim de jogo
        gameView.onGameOver = { winner ->
            runOnUiThread {
                val loser = if (winner == 1) 2 else 1
                Toast.makeText(
                    this,
                    "Fim de Jogo! Jogador $loser perdeu!",
                    Toast.LENGTH_LONG
                ).show()
                updatePlayerIndicators()
            }
        }

        // Configurar callback de mudança de jogador
        gameView.onPlayerChange = {
            runOnUiThread {
                updatePlayerIndicators()
            }
        }

        // Configurar botões
        btnStart.setOnClickListener {
            if (!gameView.isPlaying) {
                gameView.speed = selectedSpeed
                gameView.startGame()
                btnStart.text = "Jogando..."
                btnStart.isEnabled = false
                updatePlayerIndicators()
            }
        }

        btnReset.setOnClickListener {
            gameView.resetGame()
            btnStart.text = "Iniciar"
            btnStart.isEnabled = true
            updatePlayerIndicators()
        }

        // Configurar botões de velocidade
        btn4s.setOnClickListener { selectSpeed(4f, btn4s) }
        btn6s.setOnClickListener { selectSpeed(6f, btn6s) }
        btn8s.setOnClickListener { selectSpeed(8f, btn8s) }
        btn10s.setOnClickListener { selectSpeed(10f, btn10s) }

        // Selecionar velocidade padrão
        selectSpeed(4f, btn4s)
        updatePlayerIndicators()
    }

    override fun onDestroy() {
        super.onDestroy()
        gameView.cleanup()
    }

    private fun selectSpeed(speed: Float, selectedButton: Button) {
        selectedSpeed = speed

        // Resetar cor de todos os botões
        val normalColor = Color.parseColor("#6200EE")
        val selectedColor = Color.parseColor("#03DAC5")

        btn4s.setBackgroundColor(normalColor)
        btn6s.setBackgroundColor(normalColor)
        btn8s.setBackgroundColor(normalColor)
        btn10s.setBackgroundColor(normalColor)

        // Destacar botão selecionado
        selectedButton.setBackgroundColor(selectedColor)
    }

    private fun updatePlayerIndicators() {
        val activeColor = Color.parseColor("#FFD700")
        val inactiveColor = Color.parseColor("#757575")

        if (gameView.isPlaying) {
            if (gameView.tiltDirection == -1) {
                indicatorPlayer1.setBackgroundColor(activeColor)
                indicatorPlayer2.setBackgroundColor(inactiveColor)
            } else {
                indicatorPlayer1.setBackgroundColor(inactiveColor)
                indicatorPlayer2.setBackgroundColor(activeColor)
            }
        } else {
            indicatorPlayer1.setBackgroundColor(inactiveColor)
            indicatorPlayer2.setBackgroundColor(inactiveColor)
        }
    }
}