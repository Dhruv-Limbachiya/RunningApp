package com.dhruvlimbachiya.runningapp.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.view.*
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.dhruvlimbachiya.runningapp.R
import com.dhruvlimbachiya.runningapp.db.Run
import com.dhruvlimbachiya.runningapp.others.Constants.ACTION_PAUSE_SERVICE
import com.dhruvlimbachiya.runningapp.others.Constants.ACTION_START_OR_RESUME_SERVICE
import com.dhruvlimbachiya.runningapp.others.Constants.ACTION_STOP_SERVICE
import com.dhruvlimbachiya.runningapp.others.Constants.CAMERA_ZOOM
import com.dhruvlimbachiya.runningapp.others.Constants.POLYLINE_COLOR
import com.dhruvlimbachiya.runningapp.others.Constants.POLYLINE_WIDTH
import com.dhruvlimbachiya.runningapp.others.TrackingUtility
import com.dhruvlimbachiya.runningapp.service.PolyLine
import com.dhruvlimbachiya.runningapp.service.TrackingService
import com.dhruvlimbachiya.runningapp.ui.viewmodels.MainViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.android.synthetic.main.fragment_tracking.*
import timber.log.Timber
import java.util.*
import javax.inject.Inject
import kotlin.math.round

/**
 * Created by Dhruv Limbachiya on 30-07-2021.
 */

const val CANCEL_TRACKING_DIALOG_TAG = "cancelDialog"

@AndroidEntryPoint
class TrackingFragment : Fragment(R.layout.fragment_tracking) {

    private val mViewModel: MainViewModel by viewModels()

    private var mGoogleMap: GoogleMap? = null

    private var isTracking = false

    private var pathPoints = mutableListOf<PolyLine>()

    private var timeInMills = 0L

    private var mMenu: Menu? = null

    @set:Inject
    var weight = 80f

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView?.onSaveInstanceState(outState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        setHasOptionsMenu(true)
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mapView?.onCreate(savedInstanceState)

        if(savedInstanceState != null) {
            val cancelDialog = parentFragmentManager.findFragmentByTag(CANCEL_TRACKING_DIALOG_TAG) as CancelTrackingDialog?
            cancelDialog?.let {
                it.setPositiveListener {
                    stopRun()
                }
            }
        }

        // Load the Google Map Asynchronously.
        mapView.getMapAsync { map ->
            mGoogleMap = map
            drawAllPolyLines()
        }

        btnToggleRun.setOnClickListener {
            toggleRun()
        }

        btnFinishRun.setOnClickListener {
            zoomOutCameraToSeeTheEntireTrack()
            finishAndSaveRunInDb()
        }

        subscribeToObservers()

    }

    /**
     * Toggle the run from Start to Stop and vice-versa.
     */
    private fun toggleRun() {
        if (isTracking) {
            mMenu?.getItem(0)?.isVisible = true  // Make Cancel Run menu visible.
            sendCommandToService(ACTION_PAUSE_SERVICE)
        } else {
            sendCommandToService(ACTION_START_OR_RESUME_SERVICE)
        }
    }

    /**
     * Observe the changes from the LiveData.
     */
    private fun subscribeToObservers() {
        TrackingService.isTracking.observe(viewLifecycleOwner) {
            updateTrackingStatus(it)
        }

        TrackingService.pathPoints.observe(viewLifecycleOwner) {
            pathPoints = it // Get the fresh list of PolyLines.
            drawPolyLineUsingLatestLatLng()
            moveCameraToRunner()
        }

        TrackingService.totalTimeRunInMillis.observe(viewLifecycleOwner) {
            timeInMills = it
            tvTimer.text = TrackingUtility.getFormattedStopWatchTime(it, true)
        }
    }

    /**
     * Update UI based on isTracking LiveData.
     */
    private fun updateTrackingStatus(isTracking: Boolean) {
        this.isTracking = isTracking
        if (isTracking) {
            mMenu?.getItem(0)?.isVisible = true // Make Cancel Run menu visible.
            btnToggleRun.text = getString(R.string.text_stop)
            btnFinishRun.isVisible = false
        } else if(!isTracking && timeInMills > 0L) {
            btnToggleRun.text = getString(R.string.text_start)
            btnFinishRun.isVisible = true
        }
    }

    /**
     * Function responsible for displaying all the polyline by moving camera to the bounded area.
     */
    private fun zoomOutCameraToSeeTheEntireTrack() {
        val bounds = LatLngBounds.builder() // Create bound(limit) based on Latitude & Longitude.
        for(polyLine in pathPoints){
            for(latLng in polyLine){
                bounds.include(latLng) // include Lat & Lng to create a bounded area.
            }
        }

        // Move Camera to the Bounded Area.
        mGoogleMap?.moveCamera(
            CameraUpdateFactory.newLatLngBounds(
                bounds.build(),
                mapView.width,
                mapView.height,
                (mapView.height * 0.05f).toInt()
            )
        )
    }

    /**
     * Function responsible for finishing the run and save Run data in the Database.
     */
    private fun finishAndSaveRunInDb() {
        mGoogleMap?.snapshot { bitmap ->
            var distanceInMeters: Int = 0

            val timeStamp = Calendar.getInstance().timeInMillis

            for(polyLine in pathPoints){
                distanceInMeters += TrackingUtility.calculateDistanceInMeters(polyLine)
            }

            val avgSpeedInKMH = round((distanceInMeters / 1000f) / (timeInMills / 1000f / 60 / 60) * 10f) / 10f

            val caloriesBurned = ((distanceInMeters / 1000f) * weight).toInt()

            val run = Run(
                bitmap,
                timeStamp,
                avgSpeedInKMH,
                distanceInMeters,
                timeInMills,
                caloriesBurned
            )
            mViewModel.insertRun(run)
            Snackbar.make(
                requireActivity().findViewById(R.id.rootView),
                "Run saved successfully",
                Snackbar.LENGTH_LONG
            ).show()
            stopRun()
        }
    }
    

    /**
     * Move camera to latest position(LatLng) of Runner.
     */
    private fun moveCameraToRunner() {
        if (pathPoints.isNotEmpty() && pathPoints.last().isNotEmpty()) {
            mGoogleMap?.moveCamera(
                CameraUpdateFactory.newLatLngZoom(
                    pathPoints.last().last(), // get the latest LatLng
                    CAMERA_ZOOM
                )
            )
        }
    }

    /**
     * Draw all the polyLines in case of Activity re-creation
     */
    private fun drawAllPolyLines() {
        for (polyLine in pathPoints) {
            val polyLineOptions = PolylineOptions().apply {
                width(POLYLINE_WIDTH)
                color(POLYLINE_COLOR)
                addAll(polyLine) // add the entire list of PolyLine.
            }
            mGoogleMap?.addPolyline(polyLineOptions)
        }
    }


    /**
     * Function will draw polyLine using last second and last element(LatLng) from list of PolyLine.
     */
    private fun drawPolyLineUsingLatestLatLng() {
        if (pathPoints.isNotEmpty() && pathPoints.last().size > 1) { // PathPoints should not be empty and its last(current) elements at-least contains 2 LatLng Objects.
            val lastSecondLatLng =
                pathPoints.last()[pathPoints.last().size - 2]// Get the last second item.
            val lastLatLng = pathPoints.last().last() // Get the last item.

            val polylineOptions = PolylineOptions().apply {
                width(POLYLINE_WIDTH)
                color(POLYLINE_COLOR)
                add(lastSecondLatLng)
                add(lastLatLng)
            }

            mGoogleMap?.addPolyline(polylineOptions) // Add a polyLine in the map.
        }
    }

    /**
     * Send commands to the [TrackingService]
     */
    private fun sendCommandToService(command: String) =
        Intent(requireContext(), TrackingService::class.java).apply {
            this.action = command
            requireContext().startService(this)
        }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.tracking_toolbar_menu, menu)
        mMenu = menu
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        // User is running. Show Cancel Run Menu to cancel the current run.
        mMenu?.getItem(0)?.isVisible = timeInMills > 0L
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_cancel_run -> {
                showCancelTrackingDialog()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * Shows a Alert Dialog on Cancel icon click.
     */
    private fun showCancelTrackingDialog() {
       CancelTrackingDialog().apply {
           setPositiveListener {
               stopRun()
           }
       }.show(parentFragmentManager,CANCEL_TRACKING_DIALOG_TAG)
    }

    /**
     * It will stop the run by sending stop action to the service and navigate back to the [RunFragment]
     */
    private fun stopRun() {
        tvTimer.text = "00:00:00:00"
        sendCommandToService(ACTION_STOP_SERVICE)
        // Navigate back to [RunFragment]
        findNavController().navigate(
            TrackingFragmentDirections.actionTrackingFragmentToRunFragment()
        )
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
    }

    override fun onStart() {
        super.onStart()
        mapView?.onStart()
    }

    override fun onStop() {
        super.onStop()
        mapView?.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView?.onDestroy()
    }
}