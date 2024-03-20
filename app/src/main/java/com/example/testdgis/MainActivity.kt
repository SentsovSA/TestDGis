package com.example.testdgis

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import ru.dgis.sdk.Context
import ru.dgis.sdk.DGis
import ru.dgis.sdk.Duration
import ru.dgis.sdk.coordinates.GeoPoint
import ru.dgis.sdk.coordinates.Latitude
import ru.dgis.sdk.coordinates.Longitude
import ru.dgis.sdk.geometry.GeoPointWithElevation
import ru.dgis.sdk.map.BearingSource
import ru.dgis.sdk.map.CameraAnimationType
import ru.dgis.sdk.map.CameraPosition
import ru.dgis.sdk.map.LogicalPixel
import ru.dgis.sdk.map.MapObjectManager
import ru.dgis.sdk.map.MapView
import ru.dgis.sdk.map.Marker
import ru.dgis.sdk.map.MarkerOptions
import ru.dgis.sdk.map.MyLocationController
import ru.dgis.sdk.map.MyLocationMapObjectSource
import ru.dgis.sdk.map.RouteMapObject
import ru.dgis.sdk.map.RouteMapObjectSource
import ru.dgis.sdk.map.RouteVisualizationType
import ru.dgis.sdk.map.Tilt
import ru.dgis.sdk.map.Zoom
import ru.dgis.sdk.map.imageFromResource
import ru.dgis.sdk.map.imageFromSvg
import ru.dgis.sdk.positioning.DefaultLocationSource
import ru.dgis.sdk.positioning.registerPlatformLocationSource
import ru.dgis.sdk.routing.CarRouteSearchOptions
import ru.dgis.sdk.routing.RouteIndex
import ru.dgis.sdk.routing.RouteSearchOptions
import ru.dgis.sdk.routing.RouteSearchPoint
import ru.dgis.sdk.routing.TrafficRoute
import ru.dgis.sdk.routing.TrafficRouter
import kotlin.properties.Delegates

class MainActivity : ComponentActivity() {
    private lateinit var sdkContext: Context
    private lateinit var mapView: MapView
    private lateinit var cameraPosition: CameraPosition
    private lateinit var myLatitude: Latitude
    private lateinit var myLongitude: Longitude
    private lateinit var drawerLayout: DrawerLayout

    private var permissionState: Boolean = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        permissionState = isGranted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sdkContext = DGis.initialize(
            this
        )

        setContentView(R.layout.main)
        drawerLayout = findViewById(R.id.myDrawer_layout)

        mapView = findViewById(R.id.mapView)
        lifecycle.addObserver(mapView)

        mapView.getMapAsync { map ->
            val mapObjectManager = MapObjectManager(map)
            val markers = mutableListOf<Marker>()
            val icon = imageFromResource(sdkContext, R.drawable.marker)
            val markerOptions = listOf(
                MarkerOptions(
                    position = GeoPointWithElevation(latitude = 55.756651, longitude = 37.607473),
                    icon = icon,
                    iconWidth = LogicalPixel(47f),
                ),
                MarkerOptions(
                    position = GeoPointWithElevation(latitude = 55.719065, longitude = 37.600507),
                    icon = icon,
                    iconWidth = LogicalPixel(47f)
                ),
                MarkerOptions(
                    position = GeoPointWithElevation(latitude = 55.713026, longitude = 37.658892),
                    icon = icon,
                    iconWidth = LogicalPixel(47f)
                ),
                MarkerOptions(
                    position = GeoPointWithElevation(latitude = 55.759616, longitude = 37.641530),
                    icon = icon,
                    iconWidth = LogicalPixel(47f)
                ),
            )

            checkLocationPermission()
            val locationSource = DefaultLocationSource(context = this)
            registerPlatformLocationSource(sdkContext, locationSource)
            val source = MyLocationMapObjectSource(
                sdkContext,
                MyLocationController(BearingSource.SATELLITE)
            )

            markerOptions.forEach {
                markers.add(Marker(it))
            }
            map.addSource(source)
            mapObjectManager.addObjects(markers)
        }
    }

    fun account(view: View) {
        drawerLayout.openDrawer(GravityCompat.START)
    }

    fun scalePlus(view: View) {
        mapView.getMapAsync { map ->
            cameraPosition = CameraPosition(
                point = map.camera.position.point,
                zoom = Zoom(map.camera.position.zoom.value + 1f),
            )

            map.camera.move(
                cameraPosition,
                Duration.ofSeconds(0.5.toLong()),
                CameraAnimationType.DEFAULT
            )
        }
    }

    fun scaleMinus(view: View) {
        mapView.getMapAsync { map ->
            cameraPosition = CameraPosition(
                point = map.camera.position.point,
                zoom = Zoom(map.camera.position.zoom.value - 1f),
            )
            map.camera.move(
                cameraPosition,
                Duration.ofSeconds(0.5.toLong()),
                CameraAnimationType.DEFAULT
            )
        }
    }

    fun location(view: View) {
        checkLocationPermission()

        val locationManager =
            getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
        val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        Log.i("Location", locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER).toString())
        if (location != null) {
            myLatitude = Latitude(location.latitude)
            myLongitude = Longitude(location.longitude)
        } else {
            Toast.makeText(
                this,
                "Для поиска местоположения необходимо активировать геолокацию",
                Toast.LENGTH_SHORT
            ).show()
        }
        if(::myLatitude.isInitialized && ::myLongitude.isInitialized) {
            mapView.getMapAsync { map ->
                cameraPosition = CameraPosition(
                    point = GeoPoint(myLatitude, myLongitude),
                    zoom = Zoom(16f),
                )

                map.camera.move(cameraPosition, Duration.ofSeconds(1), CameraAnimationType.DEFAULT)
            }
        }
    }

    private fun checkLocationPermission() {
        val permission = Manifest.permission.ACCESS_FINE_LOCATION
        if (ContextCompat.checkSelfPermission(
                this,
                permission
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            permissionState = true
        } else {
            requestPermissionLauncher.launch(permission)
        }
    }

    fun route(view: View) {
        if (::myLatitude.isInitialized && ::myLongitude.isInitialized) {
            mapView.getMapAsync { map ->
                val startSearchPoint = RouteSearchPoint(
                    coordinates = GeoPoint(myLatitude, myLongitude)
                )
                val finishSearchPoint = RouteSearchPoint(
                    coordinates = GeoPoint(latitude = 55.756652, longitude = 37.607473)
                )

                val trafficRouter = TrafficRouter(sdkContext)
                val routesFuture = trafficRouter.findRoute(
                    startSearchPoint,
                    finishSearchPoint,
                    RouteSearchOptions(car = CarRouteSearchOptions())
                )

                val routeMapObjectSource =
                    RouteMapObjectSource(sdkContext, RouteVisualizationType.NORMAL)
                map.addSource(routeMapObjectSource)

                routesFuture.onResult { routes: List<TrafficRoute> ->
                    var isActive = true
                    var routeIndex = RouteIndex(0)
                    for (route in routes) {
                        routeMapObjectSource.addObject(
                            RouteMapObject(route, isActive, routeIndex)
                        )
                        isActive = false
                        routeIndex = RouteIndex(routeIndex.value + 1)
                    }
                }
            }
        } else {
            Toast.makeText(
                this,
                "Для поиска маршрута необходимо активировать геолокацию",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onResume() {
        super.onResume()
    }
}