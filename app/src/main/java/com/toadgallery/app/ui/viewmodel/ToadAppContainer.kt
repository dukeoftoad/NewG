package com.toadgallery.app.ui.viewmodel

import android.content.Context
import com.toadgallery.app.data.ai.DepthAnythingV2Engine
import com.toadgallery.app.data.ai.DepthModelManager
import com.toadgallery.app.data.ai.FaceModelManager
import com.toadgallery.app.data.ai.FaceRecognitionEngine
import com.toadgallery.app.data.ai.FaceIndexManager
import com.toadgallery.app.data.local.ToadGalleryDatabase
import com.toadgallery.app.data.preferences.UserPreferencesRepository
import com.toadgallery.app.data.repository.GalleryRepository

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
  val faceModelManager: FaceModelManager by lazy {
    FaceModelManager(appContext, preferencesRepository)
  }
  val depthModelManager: DepthModelManager by lazy {
    DepthModelManager(appContext, preferencesRepository)
  }
  val depthAnythingV2Engine: DepthAnythingV2Engine by lazy {
    DepthAnythingV2Engine()
  }
  val faceRecognitionEngine: FaceRecognitionEngine by lazy {
    FaceRecognitionEngine()
  }
  val faceIndexManager: FaceIndexManager by lazy {
    FaceIndexManager(appContext, database.galleryDao(), faceModelManager, faceRecognitionEngine)
  }
}
