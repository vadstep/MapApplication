package com.example.vadstep.mapapplication.Model

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.clustering.ClusterItem


/**
 * Created by User1 on 22/03/2018.
 */
class MyClusterItem(private var mPosition: LatLng, var name: String) : ClusterItem {
    override fun getPosition(): LatLng {
        return mPosition
    }
}