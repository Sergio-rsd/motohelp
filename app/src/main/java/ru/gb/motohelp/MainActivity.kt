package ru.gb.motohelp

import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import ru.gb.motohelp.ui.fragments.MapScreenFragment

lateinit var sharedPreferences: SharedPreferences
lateinit var sharedPreferencesEditor: SharedPreferences.Editor

class MainActivity : AppCompatActivity(R.layout.activity_main) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                replace(R.id.main_fragment_container, MapScreenFragment.newInstance())
            }
        }
    }
}