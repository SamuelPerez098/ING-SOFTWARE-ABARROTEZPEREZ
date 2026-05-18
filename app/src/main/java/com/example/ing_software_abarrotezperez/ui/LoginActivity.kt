package com.example.ing_software_abarrotezperez.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.ing_software_abarrotezperez.MainActivity
import com.example.ing_software_abarrotezperez.R
import com.google.android.material.textfield.TextInputEditText

class LoginActivity : AppCompatActivity() {

    private val usuarios = mapOf(
        "admin"  to "1234",
        "saul"   to "perez123"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPref = getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
        val estaLogueado = sharedPref.getBoolean("isLogged", false)

        if (estaLogueado) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_login)

        val etUsuario  = findViewById<TextInputEditText>(R.id.etUsuario)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val btnEntrar  = findViewById<Button>(R.id.btnEntrar)
        val tvError    = findViewById<TextView>(R.id.tvError)

        btnEntrar.setOnClickListener {
            validarLogin(etUsuario.text.toString().trim(), etPassword.text.toString(), tvError)
        }

        etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                validarLogin(etUsuario.text.toString().trim(), etPassword.text.toString(), tvError)
                true
            } else false
        }
    }

    private fun validarLogin(usuario: String, password: String, tvError: TextView) {
        val passwordEsperada = usuarios[usuario]

        if (passwordEsperada != null && passwordEsperada == password) {
            val sharedPref = getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
            sharedPref.edit().putBoolean("isLogged", true).apply()

            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        } else {
            tvError.visibility = TextView.VISIBLE
        }
    }
}