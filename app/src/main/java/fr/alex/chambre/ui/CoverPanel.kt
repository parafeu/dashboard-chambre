package fr.alex.chambre.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import fr.alex.chambre.ha.EntityState

@Composable
fun CoverPanel(cover: EntityState?, online: Boolean, controller: DashboardController, modifier: Modifier = Modifier) {
    Section("Volet", modifier) {}
}
