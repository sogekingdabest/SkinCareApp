package com.monsteraltech.skincare.camera

import android.content.Context
import android.os.SystemClock
import com.monsteraltech.skincare.camera.guidance.CaptureGuidanceConfig
import com.monsteraltech.skincare.camera.guidance.SimplifiedGuidanceManager
import com.monsteraltech.skincare.di.DefaultDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class CaptureFrameProcessorFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val decoder: OpenCvCameraFrameDecoder,
    @DefaultDispatcher private val workerDispatcher: CoroutineDispatcher
) {
    fun create(
        scope: CoroutineScope,
        config: CaptureGuidanceConfig,
        semanticGateEnabled: Boolean,
        onResult: (SimplifiedGuidanceManager.GuidanceResult, Int, Int) -> Unit,
        onError: (Exception) -> Unit
    ): CaptureFrameProcessor = CaptureFrameProcessor(
        scope, decoder, SimplifiedGuidanceManager(context, config, semanticGateEnabled),
        workerDispatcher, Dispatchers.Main.immediate, semanticGateEnabled,
        SystemClock::elapsedRealtime, onResult, onError
    )
}
