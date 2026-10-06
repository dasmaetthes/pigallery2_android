package com.example.data

import android.graphics.Bitmap
import android.util.LruCache

/**
 * An LRU (Least Recently Used) memory cache for gallery thumbnails and loaded images.
 * Complements the disk-based Room cache and Coil disk cache, preventing memory churn
 * and re-decoding overhead when scrolling through large image libraries.
 */
object ThumbnailLruCache {
    // Determine maximum available heap memory in kilobytes
    private val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt().coerceAtLeast(1024)
    
    // Allocate 30% of available app heap memory for LRU thumbnail bitmap caching
    private val cacheSizeKb = (maxMemoryKb * 0.30).toInt().coerceAtLeast(1024 * 16) // at least 16MB

    private val lruCache = object : LruCache<String, Bitmap>(cacheSizeKb) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            // Bitmap size in kilobytes
            return (bitmap.byteCount / 1024).coerceAtLeast(1)
        }
    }

    fun get(key: String): Bitmap? {
        return synchronized(lruCache) {
            lruCache.get(key)
        }
    }

    fun put(key: String, bitmap: Bitmap) {
        if (get(key) == null) {
            synchronized(lruCache) {
                lruCache.put(key, bitmap)
            }
        }
    }

    fun remove(key: String): Bitmap? {
        return synchronized(lruCache) {
            lruCache.remove(key)
        }
    }

    fun clear() {
        synchronized(lruCache) {
            lruCache.evictAll()
        }
    }

    fun sizeKb(): Int {
        return synchronized(lruCache) {
            lruCache.size()
        }
    }

    fun maxSizeKb(): Int {
        return synchronized(lruCache) {
            lruCache.maxSize()
        }
    }

    fun hitCount(): Int {
        return synchronized(lruCache) {
            lruCache.hitCount()
        }
    }

    fun missCount(): Int {
        return synchronized(lruCache) {
            lruCache.missCount()
        }
    }
}
