package com.tomo.monetization.ads.natives

import android.content.Context
import android.util.Log
import com.google.android.gms.ads.nativead.NativeAd
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue

data class PooledNativeAd(
    val nativeAd: NativeAd,
    val loadedTimestamp: Long
)

class NativeAdPool(
    private val adUnit: NativeAdUnit,
    private val poolSize: Int = 3,
    private val adTimeExpiration: Long = 60 * 60 * 1000L, // 1 hour
) {

    companion object {
        private const val TAG = "NativeAdPool"
    }

    private val adPool: ConcurrentLinkedQueue<PooledNativeAd> = ConcurrentLinkedQueue()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var loadJob: Job? = null

    val clickedFlow = MutableStateFlow(false)

    @Volatile var isPoolFilling: Boolean = false
        private set

    /**
     * Fills the pool with ads up to [poolSize].
     * 
     * - Removes expired ads before calculating how many to load
     * - Loads ads sequentially using the provided [adUnit]
     * - If an ad fails to load, it will NOT retry - continues to next ad
     * - Only refills if pool is not currently being filled
     */
    fun fillPool(context: Context) {
        if (isPoolFilling) {
            Log.d(TAG, "fillPool: Already filling, skipping")
            return
        }

        // Remove expired ads first
        removeExpiredAds()

        val adsNeeded = poolSize - adPool.size
        if (adsNeeded <= 0) {
            Log.d(TAG, "fillPool: Pool is already full (size=${adPool.size})")
            return
        }

        Log.d(TAG, "fillPool: Starting to load $adsNeeded ads")

        loadJob = scope.launch {
            isPoolFilling = true
            
            repeat(adsNeeded) { index ->
                Log.d(TAG, "fillPool: Loading ad ${index + 1}/$adsNeeded")
                
                val success = adUnit.loadAd(context, 30_000L, ::handleInternalClick)
                
                val loadedAd = adUnit.ad
                if (success && loadedAd != null) {
                    val pooledAd = PooledNativeAd(
                        nativeAd = loadedAd,
                        loadedTimestamp = System.currentTimeMillis()
                    )
                    adPool.add(pooledAd)
                    // Clear the ad reference from unit so next load creates new ad
                    adUnit.release()
                    Log.d(TAG, "fillPool: Ad ${index + 1} loaded successfully, pool size=${adPool.size}")
                } else {
                    Log.d(TAG, "fillPool: Ad ${index + 1} failed to load, continuing to next")
                    // Don't retry on failure - just continue to next ad
                }
            }
            
            isPoolFilling = false
            Log.d(TAG, "fillPool: Completed, final pool size=${adPool.size}")
        }
    }

    private fun handleInternalClick() {
        clickedFlow.value = true
    }

    /**
     * Retrieves a valid (non-expired) ad from the pool.
     * 
     * @return A valid NativeAd, or null if pool is empty or all ads are expired
     */
    fun getAd(context: Context): NativeAd? {
        while (adPool.isNotEmpty()) {
            val pooledAd = adPool.poll() ?: return null
            
            if (isAdExpired(pooledAd)) {
                Log.d(TAG, "getAd: Discarding expired ad")
                pooledAd.nativeAd.destroy()
                continue
            }
            
            Log.d(TAG, "getAd: Returning valid ad, remaining pool size=${adPool.size}")
            
            // Auto-refill when pool is low
            if (!isPoolFilling) {
                Log.d(TAG, "getAd: Pool is low, triggering refill")
                fillPool(context)
            }
            
            return pooledAd.nativeAd
        }
        
        Log.d(TAG, "getAd: Pool is empty")
        // Try to refill when empty
        if (!isPoolFilling) {
            fillPool(context)
        }
        return null
    }

    /**
     * Releases all ads in the pool and cancels any ongoing loading.
     */
    fun releaseAll() {
        Log.d(TAG, "releaseAll: Releasing ${adPool.size} ads")
        
        loadJob?.cancel()
        loadJob = null
        isPoolFilling = false
        
        while (adPool.isNotEmpty()) {
            val pooledAd = adPool.poll()
            pooledAd?.nativeAd?.destroy()
        }
        
        Log.d(TAG, "releaseAll: Complete")
    }

    /**
     * Checks if a pooled ad has expired.
     */
    private fun isAdExpired(pooledAd: PooledNativeAd): Boolean {
        return System.currentTimeMillis() - pooledAd.loadedTimestamp > adTimeExpiration
    }

    /**
     * Removes all expired ads from the pool.
     */
    private fun removeExpiredAds() {
        val iterator = adPool.iterator()
        var removedCount = 0
        
        while (iterator.hasNext()) {
            val pooledAd = iterator.next()
            if (isAdExpired(pooledAd)) {
                pooledAd.nativeAd.destroy()
                iterator.remove()
                removedCount++
            }
        }
        
        if (removedCount > 0) {
            Log.d(TAG, "removeExpiredAds: Removed $removedCount expired ads")
        }
    }
}

