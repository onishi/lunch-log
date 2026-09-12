package app.lunchlog.core.geo

import app.lunchlog.core.model.GeoLocation

/**
 * Geohash のエンコード (SPEC §7.2 `location.geohash`)。
 *
 * 近傍の記録をまとめるための前方一致キーとして使う。精度 7 で約 150m 四方、
 * これは店舗候補の検索半径 (SPEC §6.1) とおおよそ一致する。
 *
 * Places API のキャッシュキー (§6.1「同一座標 100m グリッド」) にも使う。
 */
object Geohash {

    private const val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"
    const val DEFAULT_PRECISION = 7

    fun encode(location: GeoLocation, precision: Int = DEFAULT_PRECISION): String =
        encode(location.lat, location.lng, precision)

    fun encode(lat: Double, lng: Double, precision: Int = DEFAULT_PRECISION): String {
        require(precision in 1..12) { "precision は 1..12 の範囲であること: $precision" }
        require(lat in -90.0..90.0) { "lat は -90..90 の範囲であること: $lat" }
        require(lng in -180.0..180.0) { "lng は -180..180 の範囲であること: $lng" }

        var latMin = -90.0
        var latMax = 90.0
        var lngMin = -180.0
        var lngMax = 180.0

        val hash = StringBuilder(precision)
        var isLng = true
        var bit = 0
        var index = 0

        while (hash.length < precision) {
            if (isLng) {
                val mid = (lngMin + lngMax) / 2
                if (lng >= mid) {
                    index = index * 2 + 1
                    lngMin = mid
                } else {
                    index *= 2
                    lngMax = mid
                }
            } else {
                val mid = (latMin + latMax) / 2
                if (lat >= mid) {
                    index = index * 2 + 1
                    latMin = mid
                } else {
                    index *= 2
                    latMax = mid
                }
            }
            isLng = !isLng

            if (bit < 4) {
                bit++
            } else {
                hash.append(BASE32[index])
                bit = 0
                index = 0
            }
        }
        return hash.toString()
    }
}
