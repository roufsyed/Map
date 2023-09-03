package `in`.rouf.map

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentSender.SendIntentException
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.widget.Button
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.tasks.Task
import com.google.android.material.floatingactionbutton.FloatingActionButton
import net.osmand.data.LatLon
import net.osmand.data.PointDescription
import net.osmand.plus.NavigationService
import net.osmand.plus.OsmAndLocationProvider
import net.osmand.plus.OsmandApplication
import net.osmand.plus.settings.backend.ApplicationMode
import net.osmand.plus.utils.NativeUtilities
import net.osmand.plus.views.MapViewWithLayers
import net.osmand.plus.views.OsmandMapTileView
import net.osmand.plus.activities.MapActivity

class MainActivity : AppCompatActivity() {
    private var app: OsmandApplication? = null
    private var mapTileView: OsmandMapTileView? = null
    private var mapViewWithLayers: MapViewWithLayers? = null
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private val REQUEST_CHECK_SETTINGS = 10001
    private val TAG:String = MainActivity::class.java.simpleName
    private var currentDriverLocation:Location? = null
    private var lat:Double = 0.0
    private var lon:Double = 0.0
    private var clickListener: OsmandMapTileView.OnLongClickListener? = null
    private var start: LatLon? = null
    private var finish: LatLon? = null
    private var rideInProgress: Boolean = false
    private val locationService:LocationService? = null

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActivityCompat.requestPermissions(
            this,
            arrayOf(ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION),0
        )
        setContentView(R.layout.activity_main)


        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
       Log.d(TAG, "onCreate: askPermission = ${applicationContext.hasLocationPermission()}")

        askToEnableGps()
        val locationTask = askLocation()
        setMap(locationTask)

        Log.d(TAG, "onCreate: latLong = $lat $lon")
        Log.d(TAG, "onCreate: currentDriverLocation = $currentDriverLocation")

        downloadMap(app)
        startStopRide()
    }

    private fun startStopRide(){
        val startStopRide = findViewById<Button>(R.id.startStopRide)
        startStopRide.setOnClickListener{
            if (!rideInProgress){
                rideInProgress = true
                Log.d(TAG, "startStopRide: rideInProgress = $rideInProgress")
                startStopRide.text = "Stop Ride"
                startStopRide.setBackgroundColor(Color.RED)
                Intent(applicationContext, LocationService::class.java).apply {
                    action = LocationService.ACTION_START
                    startService(this)
                }
                trackDriver()
            } else {
                rideInProgress = false
                Log.d(TAG, "startStopRide: rideInProgress = $rideInProgress")
                startStopRide.text = "Start Ride"
                startStopRide.setBackgroundColor(Color.GREEN)
                Intent(applicationContext, LocationService::class.java).apply {
                    action = LocationService.ACTION_START
                    startService(this)
                }
            }
        }
    }

    private fun trackDriver() {
        mapViewWithLayers = findViewById(R.id.map_osm)
        app = application as OsmandApplication
        mapTileView = app!!.osmandMap.mapView
        mapTileView = this.mapTileView!!
        mapTileView!!.setTrackBallDelegate {
            mapTileView!!.showAndHideMapPosition()
            onTrackballEvent(it)
        }

        mapTileView!!.setupRenderingView()

        mapTileView!!.setIntZoom(14)
        if (locationService != null){
            mapTileView!!.setLatLon(locationService.lat!!, locationService.lon!!)
        }
    }

    private fun setMap(location: Task<Location>) {

        mapViewWithLayers = findViewById(R.id.map_osm)
        app = application as OsmandApplication
        mapTileView = app!!.osmandMap.mapView

        mapTileView!!.setupRenderingView()

        mapTileView!!.setIntZoom(14)

        //set start location and zoom for map
        location.addOnSuccessListener{
            if(it != null){
                Log.d(TAG, "setMap: lat long = ${it.latitude} ${it.longitude}")
                mapTileView!!.setLatLon(it.latitude, it.longitude)
            } else {
                mapTileView!!.setLatLon(lat, lon)
                Log.d(TAG, "setMap: location = $it")
            }
            return@addOnSuccessListener
        }
    }

    private fun continuouslyTrackRide() {
        while (rideInProgress){
            trackDriver()
        }
    }

    private fun downloadMap(app: OsmandApplication?) {
        val osmDownloadClass = app!!.appCustomization.downloadActivity

        val mapDownloadFab = findViewById<FloatingActionButton>(R.id.downloadMap_fab)
        mapDownloadFab.setOnClickListener{
            Log.d(TAG, "onCreate: $osmDownloadClass")
            val mapDownloadIntent = Intent(this, osmDownloadClass)
            startActivity(mapDownloadIntent)
        }
    }

    @SuppressLint("MissingPermission")
    private fun askLocation(): Task<Location> {
        if (!applicationContext.hasLocationPermission()){
            ActivityCompat.requestPermissions(this,
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION),
                101)
        }
        return fusedLocationProviderClient.lastLocation
    }

    private fun askToEnableGps() {
        val locationRequest = LocationRequest.create().apply {
            interval = 1000
            fastestInterval = 1000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        val builder = locationRequest.let {
            LocationSettingsRequest.Builder().addLocationRequest(it)
        }
        builder.setAlwaysShow(true)

        builder.let {
            LocationServices.getSettingsClient(applicationContext).checkLocationSettings(it.build())
        }.addOnCompleteListener { task ->
            try {
                val response = task.getResult(ApiException::class.java)
                Toast.makeText(this@MainActivity, "GPS is already turned on", Toast.LENGTH_SHORT)
                    .show()
            } catch (e: ApiException) {
                when (e.statusCode) {
                    LocationSettingsStatusCodes.RESOLUTION_REQUIRED -> try {
                        val resolvableApiException = e as ResolvableApiException
                        resolvableApiException.startResolutionForResult(
                            this@MainActivity,
                            REQUEST_CHECK_SETTINGS
                        )
                    } catch (ex: SendIntentException) {
                        ex.printStackTrace()
                    }
                    LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE -> {}
                }
            }
        }
    }

    private fun startNavigation(location: Task<Location>) {
        val settings = app!!.settings
        val routingHelper = app!!.routingHelper

        settings.applicationMode = ApplicationMode.CAR

        val targetPointsHelper = app!!.targetPointsHelper

        targetPointsHelper.setStartPoint(
            start,
            true,
            PointDescription(start!!.latitude, start!!.longitude)
        )

        targetPointsHelper.navigateToPoint(
            finish,
            true,
            -1,
            PointDescription(finish!!.latitude, finish!!.longitude)
        )

        app!!.osmandMap.mapActions.enterRoutePlanningModeGivenGpx(null, start,
            null, true, false)

        settings.FOLLOW_THE_ROUTE.set(true)
        routingHelper.isFollowingMode = true
        routingHelper.isRoutePlanningMode = false
        routingHelper.notifyIfRouteIsCalculated()
        routingHelper.setCurrentLocation(app!!.locationProvider.lastKnownLocation, true)

        OsmAndLocationProvider.requestFineLocationPermissionIfNeeded(this)

        app!!.showShortToastMessage(
            "StartNavigation from " + start!!.latitude + " " + start!!.longitude
                    + " to " + finish!!.latitude + " " + finish!!.longitude
        )

        // Start turn-by-turn navigation
        val navigationService = NavigationService()
        navigationService.startCarNavigation()

        start = null
        finish = null
    }

    private fun getClickListener(location: Task<Location>): OsmandMapTileView.OnLongClickListener? {
        if (clickListener == null) {
            clickListener = OsmandMapTileView.OnLongClickListener { point ->
                val tileBox = mapTileView!!.currentRotatedTileBox
                val latLon = NativeUtilities.getLatLonFromPixel(
                    mapTileView!!.mapRenderer, tileBox, point.x, point.y
                )

                if (start == null) {
                    start = latLon
                    app!!.showShortToastMessage("Start point " + latLon.latitude + " " + latLon.longitude)
                } else if (finish == null) {
                    finish = latLon
                    app!!.showShortToastMessage("Finish point " + latLon.latitude + " " + latLon.longitude)
                    startNavigation(location)
                }

                true
            }
        }
        return clickListener
    }

    override fun onResume() {
        super.onResume()
        mapViewWithLayers?.onResume()
        askToEnableGps()
        val locationTask = askLocation()
        setMap(locationTask)
        mapTileView!!.setOnLongClickListener(getClickListener(locationTask))
    }

    override fun onPause() {
        super.onPause()
        mapViewWithLayers?.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapViewWithLayers?.onDestroy()
    }


}