package com.monsteraltech.skincare.camera

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.monsteraltech.skincare.body.domain.BodyRegionId
import com.monsteraltech.skincare.body.presentation.BodyRegionContract

/** A capture reference and identity hints, without copies of mole or analysis data. */
data class CaptureArgs(
    val captureId: String,
    val isFrontCamera: Boolean = false,
    val regionId: BodyRegionId? = null,
    val moleId: String? = null
) {
    fun toIntent(context: Context, destination: Class<*>): Intent = Intent(context, destination).apply {
        putExtra(MoleCaptureContract.EXTRA_CAPTURE_ID, captureId)
        putExtra(MoleCaptureContract.EXTRA_IS_FRONT_CAMERA, isFrontCamera)
        regionId?.let { putExtra(MoleCaptureContract.EXTRA_BODY_REGION_ID, it.value) }
        moleId?.let { putExtra(MoleCaptureContract.EXTRA_MOLE_ID, it) }
    }

    fun save(out: Bundle) {
        out.putString(MoleCaptureContract.EXTRA_CAPTURE_ID, captureId)
    }

    companion object {
        fun read(intent: Intent, saved: Bundle?, drafts: CaptureDraftStore): CaptureArgs {
            val id = saved?.getString(MoleCaptureContract.EXTRA_CAPTURE_ID)
                ?: intent.getStringExtra(MoleCaptureContract.EXTRA_CAPTURE_ID)
                ?: drafts.adoptLegacy(requireNotNull(intent.getStringExtra(MoleCaptureContract.EXTRA_PHOTO_PATH))).id
            // Validate even when recovering an already committed observation without its draft.
            drafts.file(id)
            intent.putExtra(MoleCaptureContract.EXTRA_CAPTURE_ID, id)
            intent.removeExtra(MoleCaptureContract.EXTRA_PHOTO_PATH)
            return CaptureArgs(id, intent.getBooleanExtra(MoleCaptureContract.EXTRA_IS_FRONT_CAMERA, false),
                BodyRegionContract.read(intent), intent.getStringExtra(MoleCaptureContract.EXTRA_MOLE_ID)?.takeIf(String::isNotBlank))
        }
    }
}
