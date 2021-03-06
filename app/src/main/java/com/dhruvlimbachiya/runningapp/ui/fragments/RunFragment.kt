package com.dhruvlimbachiya.runningapp.ui.fragments

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.dhruvlimbachiya.runningapp.R
import com.dhruvlimbachiya.runningapp.adapters.RunAdapter
import com.dhruvlimbachiya.runningapp.others.Constants.REQUEST_CODE_LOCATION_PERMISSION
import com.dhruvlimbachiya.runningapp.others.SortType
import com.dhruvlimbachiya.runningapp.others.TrackingUtility
import com.dhruvlimbachiya.runningapp.ui.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.android.synthetic.main.fragment_run.*
import pub.devrel.easypermissions.AppSettingsDialog
import pub.devrel.easypermissions.EasyPermissions

/**
 * Created by Dhruv Limbachiya on 30-07-2021.
 */

@AndroidEntryPoint
class RunFragment : Fragment(R.layout.fragment_run), EasyPermissions.PermissionCallbacks {

    private val mViewModel: MainViewModel by viewModels()

    private lateinit var mAdapter : RunAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requestPermission()

        setUpRecyclerView()

        fab.setOnClickListener {
            findNavController()
                .navigate(
                    RunFragmentDirections.actionRunFragmentToTrackingFragment()
                )
        }

        subscribeToObserver()

        // Set the spinner item.
        when(mViewModel.sortType){
            SortType.DATE -> spFilter.setSelection(0)
            SortType.RUNNING_TIME -> spFilter.setSelection(1)
            SortType.DISTANCE -> spFilter.setSelection(2)
            SortType.AVG_SPEED -> spFilter.setSelection(3)
            SortType.CALORIES_BURNED -> spFilter.setSelection(4)
        }

        // Sort runs based on spinner item select.
        spFilter.onItemSelectedListener  = object : AdapterView.OnItemSelectedListener{
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                when(position){
                    0 -> mViewModel.sortRuns(SortType.DATE)
                    1 -> mViewModel.sortRuns(SortType.RUNNING_TIME)
                    2 -> mViewModel.sortRuns(SortType.DISTANCE)
                    3 -> mViewModel.sortRuns(SortType.AVG_SPEED)
                    4 -> mViewModel.sortRuns(SortType.CALORIES_BURNED)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    /**
     * Function will observe the changes in LiveData.
     */
    private fun subscribeToObserver() {
        mViewModel.runs.observe(viewLifecycleOwner){
            if(it.isNotEmpty()){
                mAdapter.submitList(it)
            }
        }
    }

    /**
     * Set up the Runs RecyclerView.
     */
    private fun setUpRecyclerView() {
        rvRuns.apply {
            mAdapter = RunAdapter()
            adapter = mAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    /**
     * Function responsible for requesting LOCATION permissions using EasyPermission Library.
     */
    private fun requestPermission() {
        if(TrackingUtility.hasLocationPermission(requireContext())){
            return
        }

        // Check OS version and request "ACCESS_BACKGROUND_LOCATION" permission accordingly.
        if(Build.VERSION.SDK_INT > Build.VERSION_CODES.Q){
            EasyPermissions.requestPermissions(
                this,
                "You need to accept location permissions to use this app.",
                REQUEST_CODE_LOCATION_PERMISSION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            )
        }else{
            EasyPermissions.requestPermissions(
                this,
                "You need to accept location permissions to use this app.",
                REQUEST_CODE_LOCATION_PERMISSION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        }
    }

    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>) {}

    override fun onPermissionsDenied(requestCode: Int, perms: MutableList<String>) {
        if(EasyPermissions.somePermissionPermanentlyDenied(this,perms)){
            AppSettingsDialog.Builder(this).build().show() // A dialog conveying to grant permission from the App Settings.
        }else {
            requestPermission()
        }
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        // Delegating onRequestPermissionResult to EasyPermissions.
        EasyPermissions.onRequestPermissionsResult(requestCode,permissions,grantResults,this)
    }
}