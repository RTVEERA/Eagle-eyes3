package com.example

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ViraDatabase
import com.example.data.ViraRepository
import com.example.ui.ViraMainApp
import com.example.ui.ViraViewModel
import com.example.ui.ViraViewModelFactory
import com.example.ui.theme.ViraTheme

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable premium modern Edge-to-Edge full bleed overlays
        enableEdgeToEdge()

        // Core Offline database and repository wiring
        val database = ViraDatabase.getDatabase(applicationContext)
        val repository = ViraRepository(database)

        setContent {
            // Instantiate ViraViewModel safely
            val viewModel: ViraViewModel by viewModels {
                ViraViewModelFactory(repository)
            }

            val isDark by viewModel.isDarkMode.collectAsStateWithLifecycle()
            val accentHex by viewModel.appAccentColorHex.collectAsStateWithLifecycle()

            // Dynamic Vira Indian Accents & Themes
            ViraTheme(
                darkTheme = isDark,
                accentColorHex = accentHex
            ) {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    ViraMainApp(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
