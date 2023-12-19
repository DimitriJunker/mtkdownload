package com.mtkdownload

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.mtkdownload.databinding.ActivityGpsSettingsBinding

class GpsSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGpsSettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inflate the layout for this fragment
        binding= ActivityGpsSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //setContentView(R.layout.activity_gps_settings)
    }
    fun writeToMessageField(newText: String) {
        val text=
            """
        $newText
        ${binding.settingsTextview.text}
        """.trimIndent()
        binding.settingsTextview.text=text
    }

}