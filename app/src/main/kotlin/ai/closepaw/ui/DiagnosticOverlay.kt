package ai.closepaw.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import ai.closepaw.util.CrashReport
import ai.closepaw.util.CrashReportStore
import ai.closepaw.util.RuntimeLogger
import ai.closepaw.util.SensitiveDataFilter

/** Best-effort crash diagnostics UI; it never replaces Android's normal crash handler. */
class DiagnosticOverlayController(
    context: Context,
    private val logger: RuntimeLogger,
    private val reportStore: CrashReportStore?,
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    private val filter = SensitiveDataFilter()
    @Volatile private var overlayView: View? = null

    fun show(report: CrashReport) {
        mainHandler.post { runCatching { if (canOverlay()) showWindow(report) else showFallbackNotification(report) } }
    }

    fun close() {
        mainHandler.post { runCatching { overlayView?.let { windowManager?.removeView(it) }; overlayView = null } }
    }

    private fun canOverlay(): Boolean = runCatching { Settings.canDrawOverlays(appContext) }.getOrDefault(false)

    private fun showWindow(report: CrashReport) {
        val manager = windowManager ?: return showFallbackNotification(report)
        overlayView?.let { runCatching { manager.removeView(it) } }
        val view = buildView(report)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.CENTER; title = "HMX diagnostics" }
        runCatching { manager.addView(view, params); overlayView = view }
            .onFailure { showFallbackNotification(report) }
    }

    private fun buildView(report: CrashReport): View {
        val root = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
            setBackgroundColor(Color.rgb(28, 28, 30))
        }
        val title = TextView(appContext).apply {
            text = "HMX diagnostic report"
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            textSize = 20f
        }
        val error = TextView(appContext).apply {
            text = filter.redact("${report.exceptionType}: ${report.message}").take(2000)
            setTextColor(Color.WHITE)
            textSize = 15f
            setPadding(0, 16, 0, 12)
        }
        val logs = TextView(appContext).apply {
            text = report.recentLogs.joinToString("\n").let(filter::redact).take(12000)
            setTextColor(Color.LTGRAY)
            textSize = 12f
        }
        val scroll = ScrollView(appContext).apply { addView(logs) }
        val buttons = LinearLayout(appContext).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(actionButton("COPY LOG") { copy(report) })
        buttons.addView(actionButton("SAVE REPORT") { save(report) })
        buttons.addView(actionButton("CLOSE") { close() })
        root.addView(title)
        root.addView(error)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(buttons)
        return root
    }

    private fun actionButton(label: String, action: () -> Unit): Button = Button(appContext).apply {
        text = label
        setOnClickListener { runCatching(action).onFailure { logger.warn("overlay_action", "Diagnostic overlay action failed") } }
    }

    private fun copy(report: CrashReport) {
        val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("HMX diagnostic log", report.asText(filter).take(64 * 1024)))
        Toast.makeText(appContext, "Diagnostic log copied", Toast.LENGTH_SHORT).show()
    }

    private fun save(report: CrashReport) {
        if (reportStore?.save(report) != null) Toast.makeText(appContext, "Report saved", Toast.LENGTH_SHORT).show()
    }

    private fun showFallbackNotification(report: CrashReport) {
        runCatching {
            val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channel = NotificationChannel(CHANNEL_ID, "HMX diagnostics", NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(channel)
            val summary = filter.redact("${report.exceptionType}: ${report.message}").take(180)
            val notification = android.app.Notification.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("HMX diagnostic report")
                .setContentText(summary)
                .setStyle(android.app.Notification.BigTextStyle().bigText(summary))
                .setAutoCancel(true)
                .build()
            manager.notify(NOTIFICATION_ID, notification)
        }.onFailure { logger.warn("overlay_fallback", "Diagnostic notification failed") }
    }

    companion object {
        private const val CHANNEL_ID = "hmx_diagnostics"
        private const val NOTIFICATION_ID = 0x484d58
    }
}
