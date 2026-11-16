package com.toadgallery.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.toadgallery.app.ui.ToadGalleryApp
import com.toadgallery.app.ui.viewmodel.GalleryViewModel
import com.toadgallery.app.ui.viewmodel.SettingsViewModel
import com.toadgallery.app.ui.viewmodel.ToadAppContainer

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
