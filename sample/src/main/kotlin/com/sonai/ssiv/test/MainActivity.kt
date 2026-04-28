package com.sonai.ssiv.test

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.sonai.ssiv.test.ui.theme.SSIVTheme
import com.sonai.ssiv.test.animation.AnimationActivity
import com.sonai.ssiv.test.basicfeatures.BasicFeaturesActivity
import com.sonai.ssiv.test.configuration.ConfigurationActivity
import com.sonai.ssiv.test.eventhandling.EventHandlingActivity
import com.sonai.ssiv.test.eventhandlingadvanced.AdvancedEventHandlingActivity
import com.sonai.ssiv.test.extension.ExtensionActivity
import com.sonai.ssiv.test.imagedisplay.ImageDisplayActivity
import com.sonai.ssiv.test.viewpager.ViewPagerActivity

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SSIVTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(title = { Text("SSIV Samples") })
                    }
                ) { innerPadding ->
                    Dashboard(
                        modifier = Modifier.padding(innerPadding),
                        onSampleClick = { sample ->
                            when (sample) {
                                SampleType.COMPOSE -> startActivity(Intent(this, ComposeSampleActivity::class.java))
                                SampleType.BASIC -> startActivity(Intent(this, BasicFeaturesActivity::class.java))
                                SampleType.DISPLAY -> startActivity(Intent(this, ImageDisplayActivity::class.java))
                                SampleType.EVENT -> startActivity(Intent(this, EventHandlingActivity::class.java))
                                SampleType.ADVANCED -> startActivity(Intent(this, AdvancedEventHandlingActivity::class.java))
                                SampleType.PAGER -> startActivity(Intent(this, ViewPagerActivity::class.java))
                                SampleType.ANIMATION -> startActivity(Intent(this, AnimationActivity::class.java))
                                SampleType.EXTENSION -> startActivity(Intent(this, ExtensionActivity::class.java))
                                SampleType.CONFIG -> startActivity(Intent(this, ConfigurationActivity::class.java))
                                SampleType.GITHUB -> {
                                    val intent = Intent(Intent.ACTION_VIEW, "https://github.com/SonAI-Team/ssiv".toUri())
                                    startActivity(intent)
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

enum class SampleType {
    COMPOSE, GITHUB, BASIC, DISPLAY, EVENT, ADVANCED, PAGER, ANIMATION, EXTENSION, CONFIG
}

data class SampleItem(val title: String, val type: SampleType)

@Composable
fun Dashboard(modifier: Modifier = Modifier, onSampleClick: (SampleType) -> Unit) {
    val samples = listOf(
        SampleItem("Jetpack Compose Support", SampleType.COMPOSE),
        SampleItem("Basic Features (Legacy)", SampleType.BASIC),
        SampleItem("Image Display (Legacy)", SampleType.DISPLAY),
        SampleItem("Event Handling (Legacy)", SampleType.EVENT),
        SampleItem("Advanced Event Handling (Legacy)", SampleType.ADVANCED),
        SampleItem("View Pager (Legacy)", SampleType.PAGER),
        SampleItem("Animation (Legacy)", SampleType.ANIMATION),
        SampleItem("Extension (Legacy)", SampleType.EXTENSION),
        SampleItem("Configuration (Legacy)", SampleType.CONFIG),
        SampleItem("View on GitHub", SampleType.GITHUB)
    )

    LazyColumn(modifier = modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text(
                text = "Samples",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    samples.filter { it.type != SampleType.GITHUB }.forEachIndexed { index, sample ->
                        SampleRow(sample = sample, onClick = { onSampleClick(sample.type) })
                        if (index < samples.size - 2) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp)
                    .clickable { onSampleClick(SampleType.GITHUB) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = "View on GitHub",
                    modifier = Modifier.padding(start = 16.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun SampleRow(sample: SampleItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = sample.title, modifier = Modifier.weight(1f))
    }
}
