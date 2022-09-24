package mchehab.com.googlemapsplaces

import android.location.Location

interface LocationResultListener {
    fun getLocation(location: Location)
}