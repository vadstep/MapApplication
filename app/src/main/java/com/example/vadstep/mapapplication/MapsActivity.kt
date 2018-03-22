package com.example.vadstep.mapapplication

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import com.example.vadstep.mapapplication.Model.MyClusterItem
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.maps.android.clustering.Cluster
import com.google.maps.android.clustering.ClusterManager
import com.google.maps.android.clustering.algo.NonHierarchicalDistanceBasedAlgorithm
import com.google.maps.android.clustering.algo.PreCachingAlgorithmDecorator
import com.google.maps.android.clustering.view.DefaultClusterRenderer
import kotlinx.android.synthetic.main.activity_maps.*
import kotlinx.coroutines.experimental.NonCancellable.cancel
import org.jetbrains.anko.*
import org.jetbrains.anko.design.snackbar
import java.util.concurrent.TimeUnit


class MapsActivity : AppCompatActivity(), OnMapReadyCallback, ClusterManager.OnClusterClickListener<MyClusterItem> {
    private var JERUSALEM = LatLng(31.771959, 35.217018)
    private lateinit var mMap: GoogleMap
    private lateinit var mClusterManager: ClusterManager<MyClusterItem>


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        setContentView(R.layout.activity_maps)
        setSupportActionBar(toolbar)
        supportActionBar.let { title = getString(R.string.title_activity_maps) }
        val mapFragment = supportFragmentManager
                .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }


    // Manipulates the map once available.
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(JERUSALEM, 8f))
        showButton()
        setUpCluster()
        getFromFirebase()
    }

    //Show button when map is ready
    private fun showButton() {
        btn_add_pnt.visibility = View.VISIBLE
        btn_add_pnt.animate().alpha(1f)
                .setDuration(TimeUnit.SECONDS.toMillis(1))
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        super.onAnimationEnd(animation)
                        animation.removeAllListeners()
                        //Use let in async code for null pointer safety
                        btn_add_pnt.let { it.clearAnimation() }
                        btn_add_pnt.let { it.setOnClickListener { showNameDialog() } }
                    }
                })
    }


    //Cluster setup
    private fun setUpCluster() {
        mClusterManager = ClusterManager<MyClusterItem>(this, mMap)
        mClusterManager.algorithm = PreCachingAlgorithmDecorator(NonHierarchicalDistanceBasedAlgorithm())
        mClusterManager.renderer = CustomRenderer()
        mClusterManager.setOnClusterClickListener(this)
        mMap.setOnMarkerClickListener(mClusterManager)
        mMap.setOnCameraIdleListener(mClusterManager)
        mMap.setInfoWindowAdapter(mClusterManager.markerManager)
        mClusterManager.setOnClusterItemClickListener {
            (mClusterManager.renderer as CustomRenderer).getMarker(it).showInfoWindow();
            false
        }
    }


    //Prompt user to name marker(Anko)
    private fun showNameDialog() {
        alert {
            title = getString(R.string.marker_name_title)
            customView {
                verticalLayout {
                    val name = editText {
                        hint = getString(R.string.hint_name)
                    }.lparams {
                        width = matchParent
                        topMargin = dip(resources.getDimension(R.dimen.dialog_padding))
                        leftMargin = dip(resources.getDimension(R.dimen.dialog_padding))
                        rightMargin = dip(resources.getDimension(R.dimen.dialog_padding))
                        bottomMargin = dip(resources.getDimension(R.dimen.dialog_padding))
                    }
                    positiveButton(android.R.string.ok) {
                        if (name.text.toString().isNotEmpty()) {
                            addMarker(name.text.toString())
                        } else {
                            snackbar(btn_add_pnt, R.string.name_not_empty)
                        }
                    }
                    negativeButton(android.R.string.cancel) { cancel() }
                }
            }
        }.show()
    }

    //Add marker on Positive Button click
    private fun addMarker(name: String) {
        mClusterManager.addItem(MyClusterItem(mMap.cameraPosition.target, name))
        addToFirebase(mMap.cameraPosition.target, name)
        mClusterManager.cluster();
    }

    //Click on cluster action
    override fun onClusterClick(cluster: Cluster<MyClusterItem>): Boolean {
        // Zoom in the cluster. Need to create LatLngBounds and including all the cluster items
        // inside of bounds, then animate to center of the bounds.
        // Create the builder to collect all essential cluster items for the bounds.
        val builder = LatLngBounds.builder()
        for (item in cluster.items) {
            builder.include(item.position)
        }
        // Get the LatLngBounds
        val bounds = builder.build()
        // Animate camera to the bounds
        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
        return true
    }


    //add to firebase db
    private fun addToFirebase(target: LatLng, name: String) {
        val hasMap = HashMap<String, Any>()
        hasMap[name] = GeoPoint(target.latitude, target.longitude)
        FirebaseFirestore.getInstance().collection("MapMarkers")
                .add(hasMap)
                .addOnSuccessListener { snackbar(btn_add_pnt, R.string.saved) }
                .addOnFailureListener { snackbar(btn_add_pnt, R.string.not_saved) }
    }



    //get from firebaseDB
    private fun getFromFirebase() {
        FirebaseFirestore.getInstance().collection("MapMarkers")
                .get()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        for (document in task.result) {
                            document.data.entries.forEach {
                                mClusterManager.addItem(MyClusterItem(LatLng((it.value as GeoPoint).latitude, (it.value as GeoPoint).longitude), it.key))
                                mClusterManager.cluster();
                            }
                        }
                        snackbar(btn_add_pnt, R.string.fetched)
                    } else {
                        snackbar(btn_add_pnt, R.string.not_fetched)
                    }
                }
    }

    //custom render for name info_window
    private inner class CustomRenderer : DefaultClusterRenderer<MyClusterItem>(applicationContext, mMap, mClusterManager) {
        override fun onBeforeClusterItemRendered(item: MyClusterItem, markerOptions: MarkerOptions) {
            markerOptions.title(item.name)
        }
    }
}
