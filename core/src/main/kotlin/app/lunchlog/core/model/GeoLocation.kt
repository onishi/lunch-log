package app.lunchlog.core.model

/** 位置情報の取得元 (SPEC §7.2 `location.source`)。 */
enum class LocationSource(val id: String) {
    DEVICE("device"),
    EXIF("exif"),
    PLACE("place"),
    MANUAL("manual"),
    ;

    companion object {
        fun fromId(id: String?): LocationSource = entries.firstOrNull { it.id == id } ?: DEVICE
    }
}

/** 記録に紐づく位置 (SPEC §7.2 `location`)。 */
data class GeoLocation(
    val lat: Double,
    val lng: Double,
    val accuracy: Float? = null,
    val source: LocationSource = LocationSource.DEVICE,
) {
    init {
        require(lat in -90.0..90.0) { "lat は -90..90 の範囲であること: $lat" }
        require(lng in -180.0..180.0) { "lng は -180..180 の範囲であること: $lng" }
    }
}
