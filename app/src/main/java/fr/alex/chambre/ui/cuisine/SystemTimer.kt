package fr.alex.chambre.ui.cuisine

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import fr.alex.chambre.mealie.timerLabel

/** Creates a timer in the tablet's Clock app without leaving the recipe; returns what to tell the cook. */
fun startSystemTimer(context: Context, seconds: Int, label: String): String {
    val intent = Intent(AlarmClock.ACTION_SET_TIMER)
        .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
        .putExtra(AlarmClock.EXTRA_MESSAGE, label.take(60))
        .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(intent)
        "Minuterie de ${timerLabel(seconds)} lancée"
    } catch (e: ActivityNotFoundException) {
        "Aucune appli Horloge ne sait créer de minuterie"
    } catch (e: SecurityException) {
        "L'horloge a refusé la minuterie"
    }
}
