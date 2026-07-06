package com.tomo.monetization

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArraySet

object NetworkManager {
    private lateinit var connectivityManager: ConnectivityManager
    private val validNetworks = CopyOnWriteArraySet<Network>()

    private val _networkConnectedFlow = MutableStateFlow(true)
    val networkConnectedFlow: StateFlow<Boolean> = _networkConnectedFlow.asStateFlow()

    fun isNetworkConnected(): Boolean = networkConnectedFlow.value

    fun init(context: Context) {
        _networkConnectedFlow.value = initialNetworkConnected(context)
        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(networkRequest, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                if (capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true) {
                    validNetworks.add(network)
                }
                checkValidNetworks()
            }

            override fun onLost(network: Network) {
                validNetworks.remove(network)
                checkValidNetworks()
            }
        })
    }

    private fun checkValidNetworks() {
        _networkConnectedFlow.value = validNetworks.isNotEmpty()
    }

    private fun initialNetworkConnected(context: Context): Boolean {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val activeNetwork = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
            return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } catch (_: Exception) {
            return true
        }
    }
}
