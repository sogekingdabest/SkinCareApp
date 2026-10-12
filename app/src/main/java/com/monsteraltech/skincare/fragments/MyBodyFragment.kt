package com.monsteraltech.skincare.fragments
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.monsteraltech.skincare.R
import com.monsteraltech.skincare.body.BodyPartActivity
import com.monsteraltech.skincare.body.domain.BodyRegionId
import com.monsteraltech.skincare.body.presentation.BodyRegions
import com.monsteraltech.skincare.body.presentation.BodyRegionContract
import com.monsteraltech.skincare.utils.MedicalDisclaimerHelper
class MyBodyFragment : Fragment() {
    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_body, container, false)

        val disclaimerHelper = MedicalDisclaimerHelper(requireContext())

        val disclaimerButton = view.findViewById<MaterialButton>(R.id.disclaimerInfoButtonBody)
        disclaimerButton?.setOnClickListener {
            disclaimerHelper.showInformationalDisclaimer(requireActivity())
        }
        
        val frontBodyImageAreas = view.findViewById<ImageView>(R.id.frontBodyImageAreas)
        frontBodyImageAreas.post {
            if (this.view !== view) return@post
            val drawable = frontBodyImageAreas.drawable as? BitmapDrawable ?: return@post
            val bitmap = drawable.bitmap
            frontBodyImageAreas.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    val imageViewWidth = frontBodyImageAreas.width
                    val imageViewHeight = frontBodyImageAreas.height
                    val bitmapWidth = bitmap.width
                    val bitmapHeight = bitmap.height
                    val imageViewAspect = imageViewWidth.toFloat() / imageViewHeight.toFloat()
                    val bitmapAspect = bitmapWidth.toFloat() / bitmapHeight.toFloat()
                    val x: Float
                    val y: Float
                    if (imageViewAspect > bitmapAspect) {
                        val scaledWidth = (bitmapAspect * imageViewHeight).toInt()
                        val horizontalPadding = (imageViewWidth - scaledWidth) / 2
                        x = ((event.x - horizontalPadding) * bitmapWidth / scaledWidth).toInt().toFloat()
                        y = (event.y * bitmapHeight / imageViewHeight).toInt().toFloat()
                    } else {
                        val scaledHeight = (imageViewWidth / bitmapAspect).toInt()
                        val verticalPadding = (imageViewHeight - scaledHeight) / 2
                        x = (event.x * bitmapWidth / imageViewWidth).toInt().toFloat()
                        y = ((event.y - verticalPadding) * bitmapHeight / scaledHeight).toInt().toFloat()
                    }
                    Log.d("MyBodyFragment", "Touch at: x=$x, y=$y")
                    if (x >= 0 && y >= 0 && x < bitmap.width && y < bitmap.height) {
                        val pixelColor = bitmap.getPixel(x.toInt(), y.toInt())
                        Log.d("MyBodyFragment", String.format("Pixel color: #%08X", pixelColor))
                        BodyRegions.fromMaskColor(pixelColor)?.let(::handleRegionClick)
                    }
                }
                true
            }
        }
        return view
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        MedicalDisclaimerHelper(requireContext()).showInitialDisclaimerIfNeeded(requireActivity())
    }
    private fun handleRegionClick(region: BodyRegionId) {
        val intent = Intent(requireContext(), BodyPartActivity::class.java)
        intent.putExtra(BodyRegionContract.EXTRA_REGION_ID, region.value)
        startActivity(intent)
    }
}
