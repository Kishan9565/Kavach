package com.kishan.kavach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import com.kishan.kavach.app.KavachApp
import com.kishan.kavach.core.ui.KavachTheme
import com.kishan.kavach.core.ui.LocalContainer
import com.kishan.kavach.navigation.KavachRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as KavachApp).container
        setContent {
            CompositionLocalProvider(LocalContainer provides container) {
                KavachTheme { KavachRoot() }
            }
        }
    }
}
