package com.sonai.ssiv.test

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.net.toUri
import com.sonai.ssiv.test.R.id
import com.sonai.ssiv.test.animation.AnimationActivity
import com.sonai.ssiv.test.basicfeatures.BasicFeaturesActivity
import com.sonai.ssiv.test.configuration.ConfigurationActivity
import com.sonai.ssiv.test.eventhandling.EventHandlingActivity
import com.sonai.ssiv.test.eventhandlingadvanced.AdvancedEventHandlingActivity
import com.sonai.ssiv.test.extension.ExtensionActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.sonai.ssiv.test.imagedisplay.ImageDisplayActivity
import com.sonai.ssiv.test.viewpager.ViewPagerActivity

class MainActivity : AppCompatActivity(), View.OnClickListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(id.appbar)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, systemBars.top, v.paddingRight, v.paddingBottom)
            insets
        }

        setSupportActionBar(findViewById(id.toolbar))
        findViewById<View>(id.basicFeatures).setOnClickListener(this)
        findViewById<View>(id.imageDisplay).setOnClickListener(this)
        findViewById<View>(id.eventHandling).setOnClickListener(this)
        findViewById<View>(id.advancedEventHandling).setOnClickListener(this)
        findViewById<View>(id.viewPagerGalleries).setOnClickListener(this)
        findViewById<View>(id.animation).setOnClickListener(this)
        findViewById<View>(id.extension).setOnClickListener(this)
        findViewById<View>(id.configuration).setOnClickListener(this)
        findViewById<View>(id.github).setOnClickListener(this)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                moveTaskToBack(true)
            }
        })
    }

    override fun onClick(view: View) {
        when (view.id) {
            id.basicFeatures -> startActivity(BasicFeaturesActivity::class.java)
            id.imageDisplay -> startActivity(ImageDisplayActivity::class.java)
            id.eventHandling -> startActivity(EventHandlingActivity::class.java)
            id.advancedEventHandling -> startActivity(AdvancedEventHandlingActivity::class.java)
            id.viewPagerGalleries -> startActivity(ViewPagerActivity::class.java)
            id.animation -> startActivity(AnimationActivity::class.java)
            id.extension -> startActivity(ExtensionActivity::class.java)
            id.configuration -> startActivity(ConfigurationActivity::class.java)
            id.github -> openGitHub()
        }
    }

    private fun startActivity(activity: Class<out Activity>) {
        val intent = Intent(this, activity)
        startActivity(intent)
    }

    private fun openGitHub() {
        val i = Intent(Intent.ACTION_VIEW)
        i.data = "https://github.com/SonAI-Team/ssiv".toUri()
        startActivity(i)
    }
}
