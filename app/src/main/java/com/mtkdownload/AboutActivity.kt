package com.mtkdownload

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.mtkdownload.databinding.ActivityAboutBinding

class AboutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding=ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.VersionText.text=String.format("Version: %s",getString(R.string.app_version))
    }
}