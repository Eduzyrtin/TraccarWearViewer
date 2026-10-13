package io.github.eduzyrtin.traccarwear.viewer

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.cancellation.CancellationException

object PhoneProximity {
    private const val TAG = "PhoneProximity"

    /**
     * true si el teléfono emparejado está conectado directamente por Bluetooth.
     * Con LTE o Wi-Fi el teléfono puede seguir apareciendo como conectado a través de la nube,
     * pero entonces isNearby es false.
     */
    suspend fun isNearby(context: Context): Boolean = try {
        withTimeout(5_000) {
            Wearable.getNodeClient(context).connectedNodes.await().any { it.isNearby }
        }
    } catch (e: Exception) {
        if (e is CancellationException && e !is TimeoutCancellationException) throw e
        Log.w(TAG, "No se pudo consultar la conexión con el teléfono", e)
        // Ante la duda, como si no estuviera: mejor un aviso repetido que uno perdido
        false
    }
}
