package com.monsteraltech.skincare.backup

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.monsteraltech.skincare.R
import com.monsteraltech.skincare.databinding.ActivityBackupBinding
import com.monsteraltech.skincare.databinding.DialogBackupPasswordBinding
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BackupActivity : AppCompatActivity() {
    private lateinit var binding: ActivityBackupBinding

    @Inject lateinit var backupService: LocalBackupService

    private var pendingExportPassword: CharArray? = null

    private val createBackupDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument(LocalBackupService.MIME_TYPE)
    ) { uri ->
        val password = pendingExportPassword.also { pendingExportPassword = null }
        if (uri == null || password == null) {
            password?.fill('\u0000')
            setBusy(false)
            return@registerForActivityResult
        }
        lifecycleScope.launch {
            setBusy(true)
            val result = try {
                backupService.exportTo(uri, password)
            } finally {
                password.fill('\u0000')
                setBusy(false)
            }
            result.fold(
                onSuccess = { summary ->
                    clearPasswords()
                    showStatus(
                        getString(
                            R.string.backup_export_success,
                            summary.moleCount,
                            summary.analysisCount,
                            summary.imageCount
                        ),
                        isError = false
                    )
                },
                onFailure = { error ->
                    showStatus(
                        error.message ?: getString(R.string.backup_export_error),
                        isError = true
                    )
                }
            )
        }
    }

    private val openBackupDocument = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        setBusy(false)
        uri?.let(::showImportPasswordDialog)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBackupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.exportButton.setOnClickListener { startExport() }
        binding.importButton.setOnClickListener { chooseBackupToImport() }
    }

    private fun startExport() {
        val password = binding.passwordInput.text?.toString().orEmpty()
        val confirmation = binding.passwordConfirmationInput.text?.toString().orEmpty()
        when {
            password.length < LocalBackupService.MIN_PASSWORD_LENGTH -> {
                binding.passwordLayout.error = getString(
                    R.string.backup_password_too_short,
                    LocalBackupService.MIN_PASSWORD_LENGTH
                )
            }
            password != confirmation -> {
                binding.passwordLayout.error = null
                binding.passwordConfirmationLayout.error =
                    getString(R.string.backup_passwords_do_not_match)
            }
            else -> {
                clearInputErrors()
                pendingExportPassword?.fill('\u0000')
                pendingExportPassword = password.toCharArray()
                setBusy(true)
                createBackupDocument.launch(defaultBackupFileName())
            }
        }
    }

    private fun chooseBackupToImport() {
        clearInputErrors()
        setBusy(true)
        openBackupDocument.launch(
            arrayOf(LocalBackupService.MIME_TYPE, "application/zip")
        )
    }

    private fun showImportPasswordDialog(uri: Uri) {
        val dialogBinding = DialogBackupPasswordBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.backup_import_confirm_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.backup_import_action, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val passwordText = dialogBinding.importPasswordInput.text?.toString().orEmpty()
                if (passwordText.isEmpty()) {
                    dialogBinding.importPasswordLayout.error =
                        getString(R.string.backup_password_required)
                    return@setOnClickListener
                }
                dialog.dismiss()
                importBackup(uri, passwordText.toCharArray())
            }
        }
        dialog.show()
    }

    private fun importBackup(uri: Uri, password: CharArray) {
        lifecycleScope.launch {
            setBusy(true)
            val result = try {
                backupService.importFrom(uri, password)
            } finally {
                password.fill('\u0000')
                setBusy(false)
            }
            result.fold(
                onSuccess = { summary ->
                    showStatus(
                        getString(
                            R.string.backup_import_success,
                            summary.moleCount,
                            summary.analysisCount,
                            summary.imageCount
                        ),
                        isError = false
                    )
                },
                onFailure = {
                    showStatus(getString(R.string.backup_import_error), isError = true)
                }
            )
        }
    }

    private fun setBusy(busy: Boolean) {
        binding.progressIndicator.visibility = if (busy) View.VISIBLE else View.GONE
        binding.exportButton.isEnabled = !busy
        binding.importButton.isEnabled = !busy
        binding.passwordInput.isEnabled = !busy
        binding.passwordConfirmationInput.isEnabled = !busy
    }

    private fun showStatus(message: String, isError: Boolean) {
        binding.statusText.apply {
            text = message
            setTextColor(
                getColor(if (isError) R.color.risk_high else R.color.risk_low)
            )
            visibility = View.VISIBLE
        }
    }

    private fun clearPasswords() {
        binding.passwordInput.text?.clear()
        binding.passwordConfirmationInput.text?.clear()
    }

    private fun clearInputErrors() {
        binding.passwordLayout.error = null
        binding.passwordConfirmationLayout.error = null
        binding.statusText.visibility = View.GONE
    }

    private fun defaultBackupFileName(): String {
        val date = SimpleDateFormat("yyyyMMdd_HHmm", Locale.ROOT).format(Date())
        return "skincare_backup_$date.${LocalBackupService.FILE_EXTENSION}"
    }

    override fun onDestroy() {
        pendingExportPassword?.fill('\u0000')
        pendingExportPassword = null
        super.onDestroy()
    }
}
