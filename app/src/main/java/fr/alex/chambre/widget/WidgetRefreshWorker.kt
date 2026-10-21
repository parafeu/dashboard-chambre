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
        val room = inputData.getString(ROOM)?.let { r -> WidgetRoom.entries.firstOrNull { it.name == r } }
        if (room != null) refreshWidget(applicationContext, room) else refreshAllWidgets(applicationContext)
        return Result.success()
    }

    companion object {
        private const val NAME = "foyer-widget-refresh"
        private const val ROOM = "room"

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<WidgetRefreshWorker>(15, TimeUnit.MINUTES).build(),
            )
        }

        /** Redraws one room's widget 3 s and 20 s from now (a moving shutter); a new tap replaces pending ones. */
        fun followUp(context: Context, room: WidgetRoom) {
            val wm = WorkManager.getInstance(context)
            listOf(3L, 20L).forEach { s ->
                wm.enqueueUniqueWork(
                    "$NAME-${room.name}-$s",
                    androidx.work.ExistingWorkPolicy.REPLACE,
                    androidx.work.OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
                        .setInitialDelay(s, TimeUnit.SECONDS)
                        .setInputData(androidx.work.workDataOf(ROOM to room.name))
                        .build(),
                )
            }
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

/** Redraws one room's placed widgets from Home Assistant. */
suspend fun refreshWidget(context: Context, room: WidgetRoom) {
    val widget = widgetFor(room)
    if (GlanceAppWidgetManager(context).getGlanceIds(widget.javaClass).isEmpty()) return
    WidgetSnapshots.publish(room, widgetRepository(context).refresh(room))
    widget.updateAll(context)
}

/** Redraws all placed widgets (each one reads Home Assistant). */
suspend fun refreshAllWidgets(context: Context) {
    WidgetRoom.entries.forEach { refreshWidget(context, it) }
}
