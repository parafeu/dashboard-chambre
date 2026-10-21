package fr.alex.chambre.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Redraws every placed widget from Home Assistant; runs every 15 min (Android's minimum) while a widget exists. */
class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        refreshAllWidgets(applicationContext)
        return Result.success()
    }

    companion object {
        private const val NAME = "foyer-widget-refresh"

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<WidgetRefreshWorker>(15, TimeUnit.MINUTES).build(),
            )
        }

        fun cancelIfNoWidget(context: Context) {
            // Called from a receiver's onDisabled: other room widgets may still be placed.
            WorkManager.getInstance(context).enqueueUniqueWork(
                "$NAME-check",
                androidx.work.ExistingWorkPolicy.REPLACE,
                androidx.work.OneTimeWorkRequestBuilder<NoWidgetCheck>().build(),
            )
        }
    }
}

class NoWidgetCheck(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val manager = GlanceAppWidgetManager(applicationContext)
        val placed = listOf(ChambreWidget::class.java, SalonWidget::class.java, CuisineWidget::class.java)
            .sumOf { manager.getGlanceIds(it).size }
        if (placed == 0) WorkManager.getInstance(applicationContext).cancelUniqueWork("foyer-widget-refresh")
        return Result.success()
    }
}

/** Redraws all placed widgets (each one reads Home Assistant). */
suspend fun refreshAllWidgets(context: Context) {
    val manager = GlanceAppWidgetManager(context)
    val repo = widgetRepository(context)
    WidgetRoom.entries.forEach { room ->
        val widget = widgetFor(room)
        if (manager.getGlanceIds(widget.javaClass).isEmpty()) return@forEach
        WidgetSnapshots.publish(room, repo.refresh(room))
        widget.updateAll(context)
    }
}
