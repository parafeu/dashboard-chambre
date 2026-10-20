package fr.alex.chambre.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/** The app's one settings file (the dashboard and the boot receiver both read it). */
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
