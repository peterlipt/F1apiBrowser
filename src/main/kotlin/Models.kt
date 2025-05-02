// Pl. Models.kt vagy F1Data.kt fájlban

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// A legbelső objektummal kezdjük
@Serializable
data class Location(
    val lat: String? = null, // Legyenek nullables, hátha hiányzik néha adat
    @SerialName("long") // JSON kulcs: long, Kotlin property: longitude
    val longitude: String? = null,
    val locality: String? = null,
    val country: String? = null
)

@Serializable
data class Circuit(
    val circuitId: String,
    val url: String? = null,
    val circuitName: String,
    @SerialName("Location") // JSON kulcs: Location, Kotlin property: location
    val location: Location
)

@Serializable
data class RaceTable(
    val season: String? = null, // Néha a RaceTable is tartalmazhat szezont
    @SerialName("Races") // JSON kulcs: Races, Kotlin property: races
    val races: List<Race>
)

// A gyökér objektum
@Serializable
data class MRData(
    val xmlns: String? = null,
    val series: String? = null,
    val url: String? = null,
    val limit: String,
    val offset: String,
    val total: String,
    @SerialName("RaceTable")
    val raceTable: RaceTable? = null, // Nullázhatóvá tettük
    // ÚJ: StandingsTable (nullázható, mert nem minden válaszban van)
    @SerialName("StandingsTable")
    val standingsTable: StandingsTable? = null
)

@Serializable
data class ApiResponse( // Adj neki egyértelmű nevet
    @SerialName("MRData") // Megmondja, hogy a JSON "MRData" kulcsát keresse
    val mrData: MRData     // A mező neve lehet bármi, de a típusa MRData
)

@Serializable
data class Driver(
    val driverId: String,
    val permanentNumber: String? = null, // Nem mindig van meg régebbi versenyzőknél
    val code: String? = null, // Pl. VER, HAM (nem mindig van meg)
    val url: String? = null,
    val givenName: String,
    val familyName: String,
    val dateOfBirth: String? = null,
    val nationality: String? = null
)

// ÚJ: Constructor adatmodell (a Results-ban lévő)
@Serializable
data class Constructor(
    val constructorId: String,
    val url: String? = null,
    val name: String,
    val nationality: String? = null
)

// ÚJ: Idő információk (versenyidő vagy köridő)
@Serializable
data class TimeInfo(
    val millis: String? = null, // Nem mindig van meg (pl. köridőnél)
    val time: String // Ez általában megvan (pl. "+1 Lap" vagy "1:06.200")
)

// ÚJ: Átlagsebesség (a FastestLap-ban)
@Serializable
data class AverageSpeed(
    val units: String? = null, // pl. "kph"
    val speed: String? = null
)

// ÚJ: Leggyorsabb kör információk
@Serializable
data class FastestLap(
    val rank: String? = null, // Hanyadik leggyorsabb kör volt a versenyen
    val lap: String? = null,  // Melyik körben futotta
    @SerialName("Time")
    val time: TimeInfo? = null, // A köridő
    @SerialName("AverageSpeed")
    val averageSpeed: AverageSpeed? = null
)

// ÚJ: Egyetlen versenyeredmény sor adatai
@Serializable
data class ResultData( // Result helyett ResultData, hogy ne ütközzön a Kotlin Result-tal
    val number: String? = null, // Rajtszám
    val position: String,     // Helyezés (szám)
    val positionText: String, // Helyezés (szöveg, pl. "R" - retired, "W" - withdrawn)
    val points: String,       // Szerzett pontok
    @SerialName("Driver")
    val driver: Driver,
    @SerialName("Constructor")
    val constructor: Constructor,
    val grid: String,         // Rajthely
    val laps: String,         // Teljesített körök
    val status: String,       // Befejezési státusz (pl. "Finished", "+1 Lap", "Engine")
    @SerialName("Time")
    val time: TimeInfo? = null, // Versenyidő (csak a befejezőknél releváns)
    @SerialName("FastestLap")
    val fastestLap: FastestLap? = null // Leggyorsabb kör adatai (ha van)
)

@Serializable
data class Race(
    val season: String,
    val round: String,
    val url: String? = null,
    val raceName: String,
    @SerialName("Circuit")
    val circuit: Circuit,
    val date: String,
    // ÚJ, de nullázható, mert a /races végpont nem adja vissza
    @SerialName("Results")
    val results: List<ResultData>? = null,
    // Az idő nem mindig van a /races válaszban, de a /results igen
    val time: String? = null
)

@Serializable
data class StandingsList(
    val season: String,
    val round: String? = null,
    @SerialName("DriverStandings")
    val driverStandings: List<DriverStanding>? = null,
    @SerialName("ConstructorStandings")
    val constructorStandings: List<ConstructorStanding>? = null
)

@Serializable
data class StandingsTable(
    val season: String,
    @SerialName("StandingsLists")
    val standingsLists: List<StandingsList> // Általában csak egy elemű lista
)

@Serializable
data class DriverStanding(
    val position: String? = null,
    val positionText: String,
    val points: String,
    val wins: String,
    @SerialName("Driver")
    val driver: Driver,
    @SerialName("Constructors")
    val constructors: List<Constructor>
)

@Serializable
data class ConstructorStanding(
    val position: String,
    val positionText: String,
    val points: String,
    val wins: String,
    @SerialName("Constructor")
    val constructor: Constructor
)