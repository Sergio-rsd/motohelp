package ru.gb.motohelp.ui.fragments

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import org.osmdroid.api.IMapController
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.MinimapOverlay
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import ru.gb.motohelp.R
import ru.gb.motohelp.databinding.MapScreenFragmentBinding
import ru.gb.motohelp.sharedPreferences
import ru.gb.motohelp.sharedPreferencesEditor

class MapScreenFragment : Fragment(R.layout.map_screen_fragment) {

    companion object {

        fun newInstance(): Fragment {
            val args = Bundle()

            val fragment = MapScreenFragment()
            fragment.arguments = args
            return fragment
        }

        const val KEY_FILE_SETTING = "MAP_SETTING" // названия констант в shared pref
        const val KEY_SET_ZOOM = "SET_ZOOM"
        const val DEFAULT_ZOOM = "10.0"
        const val KEY_LAST_LOCATIONS_LAT = "LAST_LOCATIONS_LAT" // последняя позиция широты
        const val KEY_LAST_LOCATIONS_LON = "LAST_LOCATIONS_LON" // последняя позиция долготы
    }

    private val REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS = 124 // константа для запроса

    private var _binding: MapScreenFragmentBinding? = null
    private val binding get() = _binding!!
    private lateinit var mapView: MapView
    private lateinit var mapController: IMapController

    //для shared pref
    private var setZoomSharedPreferences: String? = null // зададим позже
    private var setLastLocationLatSharedPreferences: String? = null // зададим позже
    private var setLastLocationLonSharedPreferences: String? = null // зададим позже
    private var setStartGeoPoint: GeoPoint? = null // зададим позже

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = MapScreenFragmentBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sharedPreferences =
            requireActivity().getSharedPreferences(KEY_FILE_SETTING, Context.MODE_PRIVATE)
        // сборка карты и объявление источника
        mapView = binding.mapView
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapController = mapView.controller


        // масштабирование и жесты
        mapView.setBuiltInZoomControls(true) // зум
        mapView.setMultiTouchControls(true) // управление касанием
        mapView.overlays.add(CopyrightOverlay(requireContext())) //копирайт

        // временно вынес сюда уровень минимального и максимального зума
        mapView.minZoomLevel = 3.8
        mapView.maxZoomLevel = 18.0

        // это вынес отдельно в функции
        rotateMap() // вращение
        scaleBarOverlay(false) // строка масштаба
        miniMapOverlay(false) // миникарта
        compassOverlay(false) // компас
        myLocationFun(mapController)
        sharedPreferences() // shared pref

        // если убрать, то шрифт будет очень мелкий на карте, эксперементировал только со своим телефоном
        // у кого как, отпишите потом в комментариях
        mapView.isTilesScaledToDpi = false


        binding.fabMyLocationButton.setOnClickListener {
            // перенос на текущее местоположение
            myLocationFun(mapController)
        }

        binding.fabMySettingButton.setOnClickListener {

            requireActivity().supportFragmentManager.commit {
                setReorderingAllowed(true)
                replace(R.id.main_fragment_container, SettingsFragment())
                addToBackStack("settings")
            }

        }

        binding.fabMyEvent.setOnClickListener {
            val mLocationOverlay =
                MyLocationNewOverlay(GpsMyLocationProvider(requireContext()), mapView)
            // val startPoint = GeoPoint(mLocationOverlay.myLocation.latitude,mLocationOverlay.myLocation.longitude)
            // пока не разобрался как подставить координаты текущей позиции в GeoPoint
            val startPoint = GeoPoint(55.5992, 37.9342)
            val startMarker = Marker(mapView)
            startMarker.position = startPoint
            startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            mapView.overlays.add(startMarker)
        }
        checkPermissions()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


    private fun sharedPreferences() {
        setZoomSharedPreferences = sharedPreferences.getString(KEY_SET_ZOOM, "")
        if (setZoomSharedPreferences.isNullOrBlank()) { // если значения нет или оно пусто
            sharedPreferencesEditor = sharedPreferences.edit()
            sharedPreferencesEditor.putString(
                KEY_SET_ZOOM,
                DEFAULT_ZOOM
            ) // в поле что объявляли выше KEY_SET_ZOOM = "SET_ZOOM"
            // записываем const val DEFAULT_ZOOM = "10.0"
            sharedPreferencesEditor.apply() // применяем
            mapController.setZoom(DEFAULT_ZOOM.toDouble()) // устанавливаем на карте этот зум
        } else { //иначе
            val getSetZoomSharedPreferences =
                sharedPreferences.getString(KEY_SET_ZOOM, "")
            mapController.setZoom(getSetZoomSharedPreferences!!.toDouble())// берем сохраненное ранее значение
        }
        // координаты
        // тот же принцип что и выше
        setLastLocationLatSharedPreferences =
            sharedPreferences.getString(KEY_LAST_LOCATIONS_LAT, "")
        setLastLocationLonSharedPreferences =
            sharedPreferences.getString(KEY_LAST_LOCATIONS_LON, "")
        if (setLastLocationLatSharedPreferences.isNullOrBlank() && setLastLocationLonSharedPreferences.isNullOrBlank()) {
            // если данных нет, то записываем эти данные
            val aLat = 55.755864
            val aLon = 37.617698
            setStartGeoPoint = GeoPoint(aLat, aLon)
            sharedPreferencesEditor = sharedPreferences.edit()
            sharedPreferencesEditor.putString(
                KEY_LAST_LOCATIONS_LAT,
                setStartGeoPoint!!.latitude.toString()
            )
            sharedPreferencesEditor.putString(
                KEY_LAST_LOCATIONS_LON,
                setStartGeoPoint!!.longitude.toString()
            )
            sharedPreferencesEditor.apply()
            mapController.setCenter(setStartGeoPoint) // и ставим по центру карты
        } else {
            val aLat: Double =
                sharedPreferences.getString(KEY_LAST_LOCATIONS_LAT, "")!!.toDouble()
            val aLon: Double =
                sharedPreferences.getString(KEY_LAST_LOCATIONS_LON, "")!!.toDouble()
            val position = GeoPoint(aLat, aLon)
            mapController.setCenter(position)
        }
    }

    override fun onResume() {
        super.onResume()
        //это обновит конфигурацию приложения при возобновлении работы
        Configuration.getInstance()
            .load(requireContext(), PreferenceManager.getDefaultSharedPreferences(requireContext()))
        sharedPreferences()
        mapView.onResume() //требуется для компаса v6.0.0 и выше
    }

    override fun onPause() {
        super.onPause()
        // тоже самое что и в onResume()
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        Configuration.getInstance().save(requireContext(), prefs)
        sharedPreferences()
        mapView.onPause()  //тоже самое что и в onResume()
    }

    override fun onDestroy() {
        sharedPreferences()// сохраняются последние значения
        super.onDestroy()
    }


    //TODO починить onTouchEvent для фрагмента
    /*override fun onTouchEvent(event: MotionEvent?): Boolean {
        sharedPreferencesEditor =
            sharedPreferences.edit() // когда касаемся экрана записываем изменения в  sharedPref
        sharedPreferencesEditor.putString(Companion.KEY_SET_ZOOM, mapView.zoomLevelDouble.toString())
        sharedPreferencesEditor.apply()
        return super.onTouchEvent(event)
    }*/

    private fun compassOverlay(flag: Boolean) {
        //компас (хрень полная конечно, проще его заменить чем-то своим)
        val mCompassOverlay =
            CompassOverlay(
                requireContext(),
                InternalCompassOrientationProvider(requireContext()),
                mapView
            )
        mCompassOverlay.enableCompass()
        mCompassOverlay.isEnabled = flag
        mapView.overlays.add(mCompassOverlay)
    }

    private fun miniMapOverlay(flag: Boolean) {
        //mini карта
        val dm = requireContext().resources.displayMetrics
        val mMinimapOverlay =
            MinimapOverlay(requireContext(), mapView.tileRequestCompleteHandler)
        mMinimapOverlay.width = dm.widthPixels / 5
        mMinimapOverlay.height = dm.heightPixels / 6
        mMinimapOverlay.isEnabled = flag
        mapView.overlays.add(mMinimapOverlay)
    }

    private fun scaleBarOverlay(flag: Boolean) {
        // шкала масштаба
        val mScaleBarOverlay = ScaleBarOverlay(mapView)
        mScaleBarOverlay.setCentred(true)
        //поиграйте с этими значениями, чтобы получить местоположение на экране в нужном месте для вашего приложения
        // с основной докементации
        //val dm = applicationContext.getResources().getDisplayMetrics()
        //mScaleBarOverlay.setScaleBarOffset(dm.widthPixels / 2, 50);
        mScaleBarOverlay.setAlignBottom(true)
        mScaleBarOverlay.textPaint.color =
            ContextCompat.getColor(requireContext(), R.color.teal_700)
        mScaleBarOverlay.setTextSize(50f)
        mScaleBarOverlay.isEnabled = flag
        mapView.overlays.add(mScaleBarOverlay)
    }

    private fun rotateMap() {
        // вращение карты
        val mRotationGestureOverlay = RotationGestureOverlay(requireContext(), mapView)
        mRotationGestureOverlay.isEnabled = true
        mapView.setMultiTouchControls(true)
        mapView.overlays.add(mRotationGestureOverlay)
    }

    private fun myLocationFun(mapController: IMapController) {
        val mLocationOverlay =
            MyLocationNewOverlay(GpsMyLocationProvider(requireContext()), mapView)
        mLocationOverlay.enableMyLocation()
        mLocationOverlay.enableFollowLocation()
        mapController.setCenter(mLocationOverlay.myLocation)
        mapView.overlays.add(mLocationOverlay)
        sharedPreferences()
    }

    fun checkPermissions() {
        val permissions: MutableList<String> = ArrayList()
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (permissions.isNotEmpty()) {
            val params = permissions.toTypedArray()
            ActivityCompat.requestPermissions(
                requireActivity(),
                params,
                REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS
            )
        }
    }

}