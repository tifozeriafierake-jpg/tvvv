package com.eightk.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.eightk.tv.data.Library
import com.eightk.tv.data.Prefs
import com.eightk.tv.ui.AppTheme
import com.eightk.tv.ui.HomeScreen
import com.eightk.tv.ui.LoginScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ctx = this
        Library.init(this)
        setContent {
            AppTheme {
                var client by remember { mutableStateOf(Prefs.load(ctx)) }
                val c = client
                if (c == null) {
                    LoginScreen(onLoggedIn = { Prefs.save(ctx, it); client = it })
                } else {
                    key(c.server) {
                        HomeScreen(
                            c,
                            onLogout = { Prefs.clear(ctx); client = null },
                            onClientChanged = { Prefs.save(ctx, it); client = it },
                        )
                    }
                }
            }
        }
    }
}
