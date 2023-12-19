package com.mtkdownload

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.mtkdownload.databinding.ActivityHelpBinding

class HelpActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHelpBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding=ActivityHelpBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.webView.loadUrl(getString(R.string.helpFile))

    }
}

/*
html-File von HP ändern:

Umbenennen in mtkdownload_de.html bzw mtkdownload_en.html
<tr>-Block mit assets/images/autogen/MTKDownload_NnBanner.gif löschen
width=852 oder 672 durch width=100% ersetzen
 */