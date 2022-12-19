package `in`.rouf.map

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import net.osmand.plus.OsmandApplication
import net.osmand.plus.views.MapViewWithLayers
import net.osmand.plus.views.OsmandMapTileView


class MainActivity : AppCompatActivity(){
    private var app: OsmandApplication? = null
    private var mapTileView: OsmandMapTileView? = null
    private var mapViewWithLayers: MapViewWithLayers? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mapViewWithLayers = findViewById(R.id.map_osm)
        app = application as OsmandApplication

        mapTileView = app!!.osmandMap.mapView
        mapTileView!!.setupRenderingView()

        //set start location and zoom for map
        mapTileView!!.setIntZoom(14)
        mapTileView!!.setLatLon(52.3704312, 4.8904288)
    }

    override fun onResume() {
        super.onResume()
        mapViewWithLayers?.onResume()
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