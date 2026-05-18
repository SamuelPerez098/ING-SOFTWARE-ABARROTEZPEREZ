package com.example.ing_software_abarrotezperez.ui

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.ing_software_abarrotezperez.R

class CompraActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_compra)

        val gifImage = findViewById<ImageView>(R.id.gifImage)

        Glide.with(this)
            .asGif()
            .load(R.drawable.doro_nikke)
            .into(gifImage)
    }
}