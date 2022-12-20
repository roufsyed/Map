package `in`.rouf.map

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.content.Intent
import android.content.IntentSender.SendIntentException
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.util.Log
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
import net.osmand.plus.OsmAndLocationProvider
import net.osmand.plus.OsmandApplication
import net.osmand.plus.settings.backend.ApplicationMode
import net.osmand.plus.utils.NativeUtilities
import net.osmand.plus.views.MapViewWithLayers
import net.osmand.plus.views.OsmandMapTileView


class MainActivity : AppCompatActivity(){
    private var app: OsmandApplication? = null
    private var mapTileView: OsmandMapTileView? = null
    private var mapViewWithLayers: MapViewWithLayers? = null
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private val REQUEST_CHECK_SETTINGS = 10001
    private val TAG:String = MainActivity::class.java.simpleName
    private var lat:Double = 52.3704312
    private var lon:Double= 4.8904288
    private var clickListener: OsmandMapTileView.OnLongClickListener? = null
    private var start: LatLon? = null
    private var finish: LatLon? = null

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        askLocationPermission()
        askToEnableLocation()

        mapViewWithLayers = findViewById(R.id.map_osm)
        app = application as OsmandApplication
        mapTileView = app!!.osmandMap.mapView
        mapTileView!!.setupRenderingView()

        //set start location and zoom for map
        mapTileView!!.setIntZoom(14)
        mapTileView!!.setLatLon(lat, lon)

        downloadMap(app)
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

    private fun askLocationPermission() {
        val task: Task<Location> = fusedLocationProviderClient.lastLocation
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED){

            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), 101)
            return
        }
        task.addOnSuccessListener {
            if (it != null){
                lat = it.latitude
                lon = it.longitude
                Log.d(TAG, "askLocationPermission: latLong = $lat $lon")
            }
        }
    }

    private fun askToEnableLocation() {
        val locationRequest = LocationRequest.create().apply {
            interval = 10000
            fastestInterval = 5000
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

    private fun startNavigation() {
        val settings = app!!.settings
        val routingHelper = app!!.routingHelper

        settings.applicationMode = ApplicationMode.CAR

        val targetPointsHelper = app!!.targetPointsHelper

        targetPointsHelper.setStartPoint(
            start,
            false,
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
        routingHelper.setCurrentLocation(app!!.locationProvider.lastKnownLocation, false)

        OsmAndLocationProvider.requestFineLocationPermissionIfNeeded(this)

        app!!.showShortToastMessage(
            "StartNavigation from " + start!!.latitude + " " + start!!.longitude
                    + " to " + finish!!.latitude + " " + finish!!.longitude
        )

        start = null
        finish = null
    }

    private fun getClickListener(): OsmandMapTileView.OnLongClickListener? {
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
                    startNavigation()
                }

                true
            }
        }
        return clickListener
    }

    override fun onResume() {
        super.onResume()
        mapViewWithLayers?.onResume()
        askLocationPermission()
        askToEnableLocation()
        mapTileView!!.setOnLongClickListener(getClickListener())
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