package com.sonai.ssiv.test

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Modifier
import com.sonai.ssiv.ImageSource
import com.sonai.ssiv.compose.SubsamplingImage
import com.sonai.ssiv.compose.rememberSubsamplingImageState
import com.sonai.ssiv.test.ui.theme.SSIVTheme

class ComposeSampleActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SSIVTheme {
                val state = rememberSubsamplingImageState()
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(title = { Text("Compose Sample") })
                    }
                ) { innerPadding ->
                    SubsamplingImage(
                        imageSource = ImageSource.asset("sanmartino.jpg"),
                        state = state,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}
