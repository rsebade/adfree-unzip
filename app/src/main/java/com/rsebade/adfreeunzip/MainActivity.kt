package com.rsebade.adfreeunzip

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.rsebade.adfreeunzip.ui.theme.AppTheme
import com.rsebade.adfreeunzip.ui.unzip.UnzipRoute

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A zip opened via "Open with"; only on first launch, since the ViewModel survives rotation.
        val openedArchive = intent.data?.takeIf { savedInstanceState == null && intent.action == Intent.ACTION_VIEW }
        setContent {
            AppTheme {
                UnzipRoute(initialArchiveUri = openedArchive?.toString())
            }
        }
    }
}
