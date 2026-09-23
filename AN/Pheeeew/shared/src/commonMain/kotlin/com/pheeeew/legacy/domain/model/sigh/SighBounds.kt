package com.pheeeew.legacy.domain.model.sigh

data class SighBounds(
    val minLongitude: Double,
    val minLatitude: Double,
    val maxLongitude: Double,
    val maxLatitude: Double,
) {
    companion object {
        private const val WORLD_LONGITUDE_SPAN = 360.0
        private const val MIN_LONGITUDE = -180.0
        private const val MAX_LONGITUDE = 180.0

        fun fromViewport(
            west: Double,
            south: Double,
            east: Double,
            north: Double,
        ): SighBounds {
            val longitudeSpan = east - west
            if (longitudeSpan >= WORLD_LONGITUDE_SPAN) {
                return SighBounds(
                    minLongitude = MIN_LONGITUDE,
                    minLatitude = south,
                    maxLongitude = MAX_LONGITUDE,
                    maxLatitude = north,
                )
            }
            return SighBounds(
                minLongitude = normalizeLongitude(west),
                minLatitude = south,
                maxLongitude = normalizeLongitude(east),
                maxLatitude = north,
            )
        }

        private fun normalizeLongitude(longitude: Double): Double {
            val normalized = ((longitude + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
            return if (normalized == MIN_LONGITUDE && longitude > 0.0) MAX_LONGITUDE else normalized
        }
    }
}
