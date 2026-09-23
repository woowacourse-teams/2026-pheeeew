package com.pheeeew.legacy

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.pheeeew.legacy.core.network.ConnectivityObserver
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

class AndroidConnectivityObserver(
    context: Context,
) : com.pheeeew.legacy.core.network.ConnectivityObserver {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override val isConnected: Flow<Boolean> =
        callbackFlow {
            fun currentConnectionIsValidated(): Boolean {
                val network = connectivityManager.activeNetwork ?: return false
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
                return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }

            val callback =
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        trySend(currentConnectionIsValidated())
                    }

                    override fun onCapabilitiesChanged(
                        network: Network,
                        capabilities: NetworkCapabilities,
                    ) {
                        trySend(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
                    }

                    override fun onLost(network: Network) {
                        trySend(currentConnectionIsValidated())
                    }
                }
            connectivityManager.registerDefaultNetworkCallback(callback)
            trySend(currentConnectionIsValidated())
            awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
        }.distinctUntilChanged()
}
