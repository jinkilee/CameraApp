package com.example.cameraapp

import android.util.Log
import com.mv.engine.FaceBox

class FaceBoxResult {
    var bBoxes: List<FaceBox> = emptyList()

    constructor(bBoxes: List<FaceBox>) {
        for(box in bBoxes) {
            this.bBoxes += box
        }
        if(bBoxes.isNotEmpty()) {
            Log.d("AAA", "Number of faces found -> ${this.bBoxes.size}")
        }
    }
}