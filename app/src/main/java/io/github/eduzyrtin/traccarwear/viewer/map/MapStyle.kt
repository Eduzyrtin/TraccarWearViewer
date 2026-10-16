package io.github.eduzyrtin.traccarwear.viewer.map

/**
 * Mapas raster (mosaicos XYZ) que la app sabe pintar: mundiales y, como mucho, con una clave.
 * Los id y los atributos de clave son los mismos que usa la web de Traccar, así que las claves se
 * configuran una vez en Traccar (preferencias del usuario o del servidor) y la app las lee de ahí.
 */
enum class MapType(val id: String, val title: String, val keyAttribute: String? = null) {
    // El que tenga configurado el servidor, si la app lo soporta
    SERVER("server", ""),
    OSM("osm", "OpenStreetMap"),
    OPEN_TOPO_MAP("openTopoMap", "OpenTopoMap"),
    LOCATION_IQ_STREETS("locationIqStreets", "LocationIQ Streets", "locationIqKey"),
    LOCATION_IQ_DARK("locationIqDark", "LocationIQ Dark", "locationIqKey"),
    MAPTILER_BASIC("mapTilerBasic", "MapTiler Basic", "mapTilerKey"),
    MAPTILER_HYBRID("mapTilerHybrid", "MapTiler Hybrid", "mapTilerKey"),
    TOMTOM_BASIC("tomTomBasic", "TomTom Basic", "tomTomKey"),
    HERE_BASIC("hereBasic", "HERE Basic", "hereKey"),
    HERE_HYBRID("hereHybrid", "HERE Hybrid", "hereKey"),
    HERE_SATELLITE("hereSatellite", "HERE Satellite", "hereKey"),
    MAPBOX_STREETS("mapboxStreets", "Mapbox Streets", "mapboxAccessToken"),
    MAPBOX_DARK("mapboxStreetsDark", "Mapbox Dark", "mapboxAccessToken"),
    MAPBOX_OUTDOORS("mapboxOutdoors", "Mapbox Outdoors", "mapboxAccessToken"),
    MAPBOX_SATELLITE("mapboxSatellite", "Mapbox Satellite", "mapboxAccessToken");

    fun isAvailable(keys: Map<String, String>): Boolean =
        keyAttribute == null || !keys[keyAttribute].isNullOrBlank()

    companion object {
        // Atributos de Traccar que contienen claves de mapas
        val KEY_ATTRIBUTES = entries.mapNotNull { it.keyAttribute }.distinct()

        fun fromId(id: String?): MapType? = entries.find { it.id == id }
    }
}

data class MapStyle(
    private val urls: List<String>,
    val attribution: String,
    val maxZoom: Int,
) {
    fun tileUrl(zoom: Int, x: Int, y: Int): String =
        urls[Math.floorMod(x + y, urls.size)]
            .replace("{z}", zoom.toString())
            .replace("{x}", x.toString())
            .replace("{y}", y.toString())

    companion object {
        const val MIN_ZOOM = 3

        /**
         * Estilo para la elección del usuario. Con [MapType.SERVER] se usa el mapa del servidor; si la
         * elección no es posible (falta la clave o el servidor usa un mapa vectorial), OpenStreetMap.
         */
        fun resolve(choice: String?, serverMap: String?, serverMapUrl: String?, keys: Map<String, String>): MapStyle {
            val type = MapType.fromId(choice) ?: MapType.SERVER
            if (type == MapType.SERVER) {
                if (serverMap == "custom" && serverMapUrl != null && isRasterTemplate(serverMapUrl)) {
                    return MapStyle(listOf(serverMapUrl), "", 19)
                }
                val fromServer = MapType.fromId(serverMap)?.takeIf { it != MapType.SERVER && it.isAvailable(keys) }
                return build(fromServer ?: MapType.OSM, keys)
            }
            return build(if (type.isAvailable(keys)) type else MapType.OSM, keys)
        }

        private fun build(type: MapType, keys: Map<String, String>): MapStyle {
            val key = type.keyAttribute?.let { keys[it] }.orEmpty()
            val abc = listOf("a", "b", "c")
            return when (type) {
                MapType.SERVER, MapType.OSM ->
                    // Política de uso: identificar la app (User-Agent), cachear y mostrar la atribución
                    MapStyle(listOf("https://tile.openstreetmap.org/{z}/{x}/{y}.png"), "© OpenStreetMap", 19)
                MapType.OPEN_TOPO_MAP ->
                    MapStyle(abc.map { "https://$it.tile.opentopomap.org/{z}/{x}/{y}.png" }, "© OpenTopoMap © OpenStreetMap", 17)
                MapType.LOCATION_IQ_STREETS, MapType.LOCATION_IQ_DARK -> {
                    val variant = if (type == MapType.LOCATION_IQ_DARK) "dark" else "streets"
                    MapStyle(
                        abc.map { "https://$it-tiles.locationiq.com/v3/$variant/r/{z}/{x}/{y}.png?key=$key" },
                        "© LocationIQ © OpenStreetMap",
                        18,
                    )
                }
                MapType.MAPTILER_BASIC ->
                    MapStyle(listOf("https://api.maptiler.com/maps/basic-v2/256/{z}/{x}/{y}@2x.png?key=$key"), "© MapTiler © OpenStreetMap", 19)
                MapType.MAPTILER_HYBRID ->
                    MapStyle(listOf("https://api.maptiler.com/maps/hybrid/256/{z}/{x}/{y}@2x.jpg?key=$key"), "© MapTiler © OpenStreetMap", 19)
                MapType.TOMTOM_BASIC ->
                    MapStyle(listOf("https://api.tomtom.com/map/1/tile/basic/main/{z}/{x}/{y}.png?key=$key"), "© TomTom", 19)
                MapType.HERE_BASIC ->
                    MapStyle(listOf("https://maps.hereapi.com/v3/base/mc/{z}/{x}/{y}/png8?apiKey=$key"), "© HERE", 19)
                MapType.HERE_HYBRID ->
                    MapStyle(listOf("https://maps.hereapi.com/v3/base/mc/{z}/{x}/{y}/jpeg?style=explore.satellite.day&apiKey=$key"), "© HERE", 19)
                MapType.HERE_SATELLITE ->
                    MapStyle(listOf("https://maps.hereapi.com/v3/base/mc/{z}/{x}/{y}/jpeg?style=satellite.day&apiKey=$key"), "© HERE", 19)
                MapType.MAPBOX_STREETS -> mapbox("streets-v12", key)
                MapType.MAPBOX_DARK -> mapbox("dark-v11", key)
                MapType.MAPBOX_OUTDOORS -> mapbox("outdoors-v12", key)
                MapType.MAPBOX_SATELLITE -> mapbox("satellite-streets-v12", key)
            }
        }

        private fun mapbox(style: String, token: String) = MapStyle(
            listOf("https://api.mapbox.com/styles/v1/mapbox/$style/tiles/256/{z}/{x}/{y}@2x?access_token=$token"),
            "© Mapbox © OpenStreetMap",
            19,
        )

        private fun isRasterTemplate(url: String) =
            "{z}" in url && "{x}" in url && "{y}" in url && !url.contains(".json")
    }
}
