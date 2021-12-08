package com.example.cameraapp

import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if(allPermissionGranted()) {
            Log.d("AAA", "permission granted")
        }
        else {
            Log.d("AAA", "no permission granted")
        }

        for (item in REQUIRED_PERMISSIONS) {
            Log.d("AAA", "item -> $item") // item -> android.permission.CAMERA
        }
    }

    private fun allPermissionGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onStart() {
        super.onStart()
        Log.d("AAA", "onStart executed")
    }

    override fun onResume() {
        super.onResume()
        Log.d("AAA", "onResume executed")
    }

    override fun onPause() {
        super.onPause()
        Log.d("AAA", "onPause executed")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("AAA", "onDestroy executed")
    }

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}