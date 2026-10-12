package com.monsteraltech.skincare.camera

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.monsteraltech.skincare.body.domain.BodyRegionId
import com.monsteraltech.skincare.data.identity.ActiveProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Owns the handoff after CameraX writes a photo, including a stopped/recreated screen. */
@HiltViewModel
class CameraCaptureViewModel @Inject constructor(
    private val drafts: CaptureDraftStore,
    private val profile: ActiveProfile,
    private val saved: SavedStateHandle
) : ViewModel() {
    private val _preview = MutableStateFlow<CaptureArgs?>(null)
    val preview = _preview.asStateFlow()
    private val _isCapturing = MutableStateFlow(false)
    val isCapturing = _isCapturing.asStateFlow()

    init {
        saved.get<String>("pendingCapture")?.let { id ->
            if (saved.get<String>("captureOwner") == profile.ownerId && drafts.isReady(id)) {
                _preview.value = arguments(id)
            } else {
                if (saved.get<String>("captureOwner") == profile.ownerId) drafts.discard(id)
                saved.remove<String>("pendingCapture")
            }
        }
    }

    fun prepare(front: Boolean, region: BodyRegionId?, moleId: String?): CaptureDraftStore.Draft {
        check(!_isCapturing.value && _preview.value == null)
        val draft = drafts.create()
        saved["captureOwner"] = profile.requireOwnerId()
        saved["pendingCapture"] = draft.id
        saved["captureFront"] = front
        saved["captureRegion"] = region?.value
        saved["captureMole"] = moleId
        _isCapturing.value = true
        return draft
    }

    fun completed(id: String) {
        if (saved.get<String>("pendingCapture") != id || saved.get<String>("captureOwner") != profile.ownerId) return
        drafts.markReady(id)
        _preview.value = arguments(id)
        _isCapturing.value = false
    }

    fun failed(id: String) {
        if (saved.get<String>("pendingCapture") != id) return
        if (saved.get<String>("captureOwner") == profile.ownerId) drafts.discard(id)
        saved.remove<String>("pendingCapture")
        _isCapturing.value = false
    }

    /** Called only after the ActivityResult launcher has accepted the destination. */
    fun previewOpened(id: String) {
        if (_preview.value?.captureId == id) {
            _preview.value = null
            saved.remove<String>("pendingCapture")
        }
    }

    private fun arguments(id: String) = CaptureArgs(id, saved["captureFront"] ?: false,
        BodyRegionId.fromValue(saved["captureRegion"]), saved["captureMole"])
}
