import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MRData(
    val xmlns: String,
    val series: String,
    val url: String,
    val limit: String,
    val offset: String,
    val total: String,
    @SerialName("RaceTable")
    val raceTable: RaceTable
)

@Serializable
data class RaceTable(
    val season: String,
    @SerialName("Races")
    val races: List<Race>
)

@Serializable
data class Race(
    val season: String,
    val round: String,
    val url: String,
    val raceName: String,
    @SerialName("Circuit")
    val circuit: Circuit,
    val date: String,
    val time: String?
)

@Serializable
data class Circuit(
    val circuitId: String,
    val url: String,
    val circuitName: String,
    @SerialName("Location")
    val location: Location
)

@Serializable
data class Location(
    val lat: String,
    val long: String,
    val locality: String,
    val country: String
)