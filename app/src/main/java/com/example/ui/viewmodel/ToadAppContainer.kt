package com.example.ui.viewmodel

import android.content.Context
import com.example.data.local.ToadGalleryDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.GalleryRepository

class ToadAppContainer(context: Context) {
  val appContext: Context = context.applicationContext
  private val database: ToadGalleryDatabase by lazy {
    ToadGalleryDatabase.getInstance(appContext)
  }
  val galleryRepository: GalleryRepository by lazy {
    GalleryRepository(appContext, database.galleryDao())
  }
  val preferencesRepository: UserPreferencesRepository by lazy {
    UserPreferencesRepository(appContext)
  }
}
