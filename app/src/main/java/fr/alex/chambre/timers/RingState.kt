package fr.alex.chambre.timers

/** The timers this device rings for: none on the phone or when « Sonner pour les minuteries » is off. */
fun ringingShown(ringing: List<SharedTimer>, enabled: Boolean, tablet: Boolean): List<SharedTimer> =
    if (enabled && tablet) ringing else emptyList()
