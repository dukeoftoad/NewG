package com.example.ui.viewmodel

import android.content.Context
import com.example.data.ai.DepthAnythingV2Engine
import com.example.data.ai.DepthModelManager
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
  val depthModelManager: DepthModelManager by lazy {
    DepthModelManager(appContext, preferencesRepository)
  }
  val depthAnythingV2Engine: DepthAnythingV2Engine by lazy {
    DepthAnythingV2Engine()
  }
}
