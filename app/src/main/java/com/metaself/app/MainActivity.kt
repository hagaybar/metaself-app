package com.metaself.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.metaself.app.ui.root.MetaSelfRoot
import com.metaself.app.ui.theme.MetaSelfTheme
import com.metaself.app.ui.theme.ProvideSystemMotion
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MetaSelfTheme {
                // Whether anything may move at all, from the system's "Remove animations" (#16).
                // Here rather than in the theme, so a render test or a preview is still by default.
                ProvideSystemMotion {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        MetaSelfRoot(
                            versionName = BuildConfig.VERSION_NAME,
                            versionCode = BuildConfig.VERSION_CODE,
                        )
                    }
                }
            }
        }
    }
}
