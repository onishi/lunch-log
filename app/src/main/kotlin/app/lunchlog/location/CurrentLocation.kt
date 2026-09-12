package app.lunchlog.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import app.lunchlog.core.model.GeoLocation
import app.lunchlog.core.model.LocationSource
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * 現在地の取得 (SPEC §6.1, §10)。
 *
 * 取得は**記録のときだけ**。バックグラウンドで追跡しない。
 * 権限がない・時間内に取れない場合は null を返し、呼び出し側は履歴の候補に落とす。
 * 位置情報が使えなくてもアプリの全機能が手入力で使えること (SPEC §10)。
 */
class CurrentLocation(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // hasPermission() で確認してから呼ぶ
    suspend fun current(): GeoLocation? {
        if (!hasPermission()) return null

        val client = LocationServices.getFusedLocationProviderClient(context)
        val cancellation = CancellationTokenSource()

        return try {
            // SPEC §6.1: 精度優先 / 10 秒タイムアウト。
            withTimeout(TIMEOUT_MS) {
                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token).await()
            }?.let {
                GeoLocation(
                    lat = it.latitude,
                    lng = it.longitude,
                    accuracy = if (it.hasAccuracy()) it.accuracy else null,
                    source = LocationSource.DEVICE,
                )
            }
        } catch (_: TimeoutCancellationException) {
            cancellation.cancel()
            null
        } catch (_: SecurityException) {
            null
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
