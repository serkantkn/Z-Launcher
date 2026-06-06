package com.serkantkn.zunelauncher.util

import android.graphics.Bitmap
import android.graphics.Color

object SimpleBlur {
    fun blur(image: Bitmap, radius: Int, iterations: Int = 2): Bitmap {
        var current = image
        for (i in 0 until iterations) {
            current = boxBlur(current, radius)
        }
        return current
    }

    private fun boxBlur(image: Bitmap, radius: Int): Bitmap {
        val width = image.width
        val height = image.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        val pixels = IntArray(width * height)
        image.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val resultPixels = IntArray(width * height)
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                var rTotal = 0
                var gTotal = 0
                var bTotal = 0
                var count = 0
                
                for (dy in -radius..radius) {
                    val ny = y + dy
                    if (ny in 0 until height) {
                        for (dx in -radius..radius) {
                            val nx = x + dx
                            if (nx in 0 until width) {
                                val color = pixels[ny * width + nx]
                                rTotal += (color shr 16) and 0xFF
                                gTotal += (color shr 8) and 0xFF
                                bTotal += color and 0xFF
                                count++
                            }
                        }
                    }
                }
                
                resultPixels[y * width + x] = (-0x1000000) or 
                    ((rTotal / count) shl 16) or 
                    ((gTotal / count) shl 8) or 
                    (bTotal / count)
            }
        }
        
        result.setPixels(resultPixels, 0, width, 0, 0, width, height)
        return result
    }
}
