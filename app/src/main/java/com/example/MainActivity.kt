package com.example

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.ToadGalleryApp
import com.example.ui.viewmodel.GalleryViewModel
import com.example.ui.viewmodel.SettingsViewModel
import com.example.ui.viewmodel.ToadAppContainer

class MainActivity : FragmentActivity() {
  private val container: ToadAppContainer by lazy { ToadAppContainer(applicationContext) }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val galleryViewModel: GalleryViewModel =
        viewModel(factory = GalleryViewModel.factory(container))
      val settingsViewModel: SettingsViewModel =
        viewModel(factory = SettingsViewModel.factory(container))
      ToadGalleryApp(
        galleryViewModel = galleryViewModel,
        settingsViewModel = settingsViewModel,
      )
    }
  }
}
