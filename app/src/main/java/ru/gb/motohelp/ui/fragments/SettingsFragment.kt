package ru.gb.motohelp.ui.fragments

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import ru.gb.motohelp.R

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_fragment, rootKey)
    }



}
