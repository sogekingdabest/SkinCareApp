package es.monsteraltech.skincare_tfm.body.mole.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import es.monsteraltech.skincare_tfm.R

/**
 * Componente reutilizable para mostrar estados vacíos con mensajes informativos
 * Soporta diferentes tipos de estados vacíos y acciones de recuperación
 */
class EmptyStateView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val icon: ImageView
    private val title: TextView
    private val message: TextView
    private val actionButton: Button
    private val secondaryButton: Button
    private val progressBar: ProgressBar
    private val retryText: TextView

    /**
     * Tipos de estados vacíos predefinidos
     */
    enum class EmptyStateType {
        NO_MOLES,
        NO_ANALYSIS,
        NO_SEARCH_RESULTS,
        LOADING_FAILED,
        NETWORK_ERROR,
        AUTHENTICATION_ERROR,
        GENERIC_ERROR
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.component_empty_state, this, true)
        
        icon = findViewById(R.id.emptyStateIcon)
        title = findViewById(R.id.emptyStateTitle)
        message = findViewById(R.id.emptyStateMessage)
        actionButton = findViewById(R.id.emptyStateActionButton)
        secondaryButton = findViewById(R.id.emptyStateSecondaryButton)
        progressBar = findViewById(R.id.emptyStateProgressBar)
        retryText = findViewById(R.id.emptyStateRetryText)
        
        visibility = View.GONE
    }

    /**
     * Configura el estado vacío usando un tipo predefinido
     */
    fun setEmptyState(
        type: EmptyStateType,
        primaryAction: (() -> Unit)? = null,
        secondaryAction: (() -> Unit)? = null
    ) {
        when (type) {
            EmptyStateType.NO_MOLES -> {
                setEmptyState(
                    iconRes = R.drawable.ic_add_circle_outline,
                    titleRes = R.string.empty_state_no_moles_title,
                    messageRes = R.string.empty_state_no_moles_message,
                    primaryActionTextRes = R.string.empty_state_action_create_first,
                    primaryAction = primaryAction
                )
            }
            
            EmptyStateType.NO_ANALYSIS -> {
                setEmptyState(
                    iconRes = R.drawable.ic_analytics_outline,
                    titleRes = R.string.empty_state_no_analysis_title,
                    messageRes = R.string.empty_state_no_analysis_message,
                    primaryActionTextRes = R.string.empty_state_action_refresh,
                    primaryAction = primaryAction
                )
            }
            
            EmptyStateType.NO_SEARCH_RESULTS -> {
                setEmptyState(
                    iconRes = R.drawable.ic_search_off,
                    titleRes = R.string.empty_state_no_search_results_title,
                    messageRes = R.string.empty_state_no_search_results_message,
                    primaryActionTextRes = R.string.empty_state_action_clear_search,
                    secondaryActionTextRes = R.string.empty_state_action_try_again,
                    primaryAction = primaryAction,
                    secondaryAction = secondaryAction
                )
            }
            
            EmptyStateType.LOADING_FAILED -> {
                setEmptyState(
                    iconRes = R.drawable.ic_error_outline,
                    titleRes = R.string.empty_state_loading_failed_title,
                    messageRes = R.string.empty_state_loading_failed_message,
                    primaryActionTextRes = R.string.empty_state_action_try_again,
                    secondaryActionTextRes = R.string.empty_state_action_refresh,
                    primaryAction = primaryAction,
                    secondaryAction = secondaryAction
                )
            }
            
            EmptyStateType.NETWORK_ERROR -> {
                setEmptyState(
                    iconRes = R.drawable.ic_wifi_off,
                    titleRes = R.string.error_network_connection,
                    messageRes = R.string.error_action_check_connection,
                    primaryActionTextRes = R.string.empty_state_action_try_again,
                    primaryAction = primaryAction
                )
            }
            
            EmptyStateType.AUTHENTICATION_ERROR -> {
                setEmptyState(
                    iconRes = R.drawable.ic_account_circle_outline,
                    titleRes = R.string.error_authentication,
                    messageRes = R.string.error_action_login_again,
                    primaryActionTextRes = R.string.empty_state_action_try_again,
                    primaryAction = primaryAction
                )
            }
            
            EmptyStateType.GENERIC_ERROR -> {
                setEmptyState(
                    iconRes = R.drawable.ic_error_outline,
                    titleRes = R.string.error_unknown,
                    messageRes = R.string.error_action_try_again,
                    primaryActionTextRes = R.string.empty_state_action_try_again,
                    primaryAction = primaryAction
                )
            }
        }
    }

    /**
     * Configura el estado vacío con parámetros personalizados
     */
    fun setEmptyState(
        @DrawableRes iconRes: Int? = null,
        @StringRes titleRes: Int? = null,
        @StringRes messageRes: Int? = null,
        @StringRes primaryActionTextRes: Int? = null,
        @StringRes secondaryActionTextRes: Int? = null,
        title: String? = null,
        message: String? = null,
        primaryActionText: String? = null,
        secondaryActionText: String? = null,
        primaryAction: (() -> Unit)? = null,
        secondaryAction: (() -> Unit)? = null
    ) {
        // Configurar icono
        iconRes?.let { icon.setImageResource(it) }
        
        // Configurar título
        when {
            title != null -> this.title.text = title
            titleRes != null -> this.title.text = context.getString(titleRes)
        }
        
        // Configurar mensaje
        when {
            message != null -> this.message.text = message
            messageRes != null -> this.message.text = context.getString(messageRes)
        }
        
        // Configurar botón primario
        if (primaryAction != null) {
            when {
                primaryActionText != null -> actionButton.text = primaryActionText
                primaryActionTextRes != null -> actionButton.text = context.getString(primaryActionTextRes)
            }
            actionButton.setOnClickListener { primaryAction.invoke() }
            actionButton.visibility = View.VISIBLE
        } else {
            actionButton.visibility = View.GONE
        }
        
        // Configurar botón secundario
        if (secondaryAction != null) {
            when {
                secondaryActionText != null -> secondaryButton.text = secondaryActionText
                secondaryActionTextRes != null -> secondaryButton.text = context.getString(secondaryActionTextRes)
            }
            secondaryButton.setOnClickListener { secondaryAction.invoke() }
            secondaryButton.visibility = View.VISIBLE
        } else {
            secondaryButton.visibility = View.GONE
        }
        
        // Ocultar elementos de reintento por defecto
        hideRetryIndicators()
        
        // Mostrar el componente
        visibility = View.VISIBLE
    }

    /**
     * Muestra indicadores de reintento automático
     */
    fun showRetryIndicators(attempt: Int, maxAttempts: Int) {
        progressBar.visibility = View.VISIBLE
        retryText.text = context.getString(R.string.retry_attempting, attempt, maxAttempts)
        retryText.visibility = View.VISIBLE
        
        // Ocultar botones durante el reintento
        actionButton.visibility = View.GONE
        secondaryButton.visibility = View.GONE
    }

    /**
     * Oculta indicadores de reintento
     */
    fun hideRetryIndicators() {
        progressBar.visibility = View.GONE
        retryText.visibility = View.GONE
    }

    /**
     * Oculta completamente el estado vacío
     */
    fun hide() {
        visibility = View.GONE
        hideRetryIndicators()
    }

    /**
     * Actualiza el mensaje de reintento
     */
    fun updateRetryMessage(message: String) {
        retryText.text = message
        retryText.visibility = View.VISIBLE
    }

    /**
     * Actualiza el mensaje de reintento con formato
     */
    fun updateRetryMessage(@StringRes messageRes: Int, vararg formatArgs: Any) {
        retryText.text = context.getString(messageRes, *formatArgs)
        retryText.visibility = View.VISIBLE
    }
}