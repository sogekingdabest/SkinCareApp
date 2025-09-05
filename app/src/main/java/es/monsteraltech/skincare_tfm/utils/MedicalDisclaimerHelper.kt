package es.monsteraltech.skincare_tfm.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.fragment.app.FragmentActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import es.monsteraltech.skincare_tfm.R

class MedicalDisclaimerHelper(private val context: Context) {

    companion object {
        private const val TAG = "MedicalDisclaimerHelper"
        private const val PREFS_NAME = "medical_disclaimer_prefs"
        private const val KEY_DISCLAIMER_SHOWN = "disclaimer_shown"
        private const val KEY_DISCLAIMER_ACCEPTED = "disclaimer_accepted"
    }

    private val sharedPrefs: SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasUserAcceptedDisclaimer(): Boolean {
        return sharedPrefs.getBoolean(KEY_DISCLAIMER_ACCEPTED, false)
    }

    fun markDisclaimerAsAccepted() {
        sharedPrefs
                .edit()
                .putBoolean(KEY_DISCLAIMER_ACCEPTED, true)
                .putBoolean(KEY_DISCLAIMER_SHOWN, true)
                .apply()
    }

    fun hasDisclaimerBeenShown(): Boolean {
        return sharedPrefs.getBoolean(KEY_DISCLAIMER_SHOWN, false)
    }
    fun showDisclaimerDialog(
            activity: FragmentActivity,
            isInformational: Boolean = false,
            onAccepted: (() -> Unit)? = null,
            onCancelled: (() -> Unit)? = null
    ) {
        Log.d(TAG, "Mostrando diálogo del disclaimer médico - Informativo: $isInformational")

        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_medical_disclaimer, null)

        val dialog =
                MaterialAlertDialogBuilder(activity)
                        .setView(dialogView)
                        .setCancelable(isInformational) // Solo cancelable en modo informativo
                        .create()

        val acceptButton =
                dialogView.findViewById<com.google.android.material.button.MaterialButton>(
                        R.id.disclaimerUnderstoodButton
                )
        val cancelButton =
                dialogView.findViewById<com.google.android.material.button.MaterialButton>(
                        R.id.disclaimerCancelButton
                )

        if (isInformational) {

            acceptButton?.visibility = android.view.View.GONE
            cancelButton?.text = "Entendido"
            cancelButton?.setOnClickListener {
                Log.d(TAG, "Usuario cerró el disclaimer informativo")
                dialog.dismiss()
                onCancelled?.invoke()
            }
        } else {

            acceptButton?.setOnClickListener {
                Log.d(TAG, "Usuario aceptó el disclaimer médico")
                markDisclaimerAsAccepted()
                dialog.dismiss()
                onAccepted?.invoke()
            }

            cancelButton?.setOnClickListener {
                Log.d(TAG, "Usuario presionó salir")
                if (!hasUserAcceptedDisclaimer()) {

                    MaterialAlertDialogBuilder(activity)
                            .setTitle("Confirmar salida")
                            .setMessage(R.string.medical_disclaimer_exit_confirmation)
                            .setPositiveButton("Sí, salir") { _, _ ->
                                dialog.dismiss()
                                activity.finishAffinity()
                            }
                            .setNegativeButton("Cancelar") { confirmDialog, _ ->
                                confirmDialog.dismiss()
                            }
                            .show()
                } else {
                    dialog.dismiss()
                    onCancelled?.invoke()
                }
            }
        }

        dialog.show()
    }

    fun showInformationalDisclaimer(activity: FragmentActivity) {
        showDisclaimerDialog(
                activity = activity,
                isInformational = true,
                onCancelled = { /* Solo cierra el diálogo */}
        )
    }

    fun showInitialDisclaimerIfNeeded(
            activity: FragmentActivity,
            onCompleted: (() -> Unit)? = null
    ) {
        if (!hasUserAcceptedDisclaimer()) {
            Log.d(TAG, "Mostrando disclaimer inicial - usuario no ha aceptado")
            showDisclaimerDialog(
                    activity = activity,
                    onAccepted = {
                        Log.d(TAG, "Disclaimer aceptado, continuando...")
                        onCompleted?.invoke()
                    },
                    onCancelled = {
                        Log.d(TAG, "Disclaimer cancelado, cerrando app")
                        activity.finishAffinity()
                    }
            )
        } else {
            Log.d(TAG, "Usuario ya aceptó disclaimer, continuando...")
            onCompleted?.invoke()
        }
    }
    fun resetDisclaimerState() {
        sharedPrefs.edit().remove(KEY_DISCLAIMER_ACCEPTED).remove(KEY_DISCLAIMER_SHOWN).apply()
    }
    fun forceShowInitialDisclaimer(activity: FragmentActivity, onCompleted: (() -> Unit)? = null) {
        resetDisclaimerState()
        showInitialDisclaimerIfNeeded(activity, onCompleted)
    }
}
