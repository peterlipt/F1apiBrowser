// ApiService.kt

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

object ApiService {

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true // Fontos!
            })
        }
    }

    private const val BASE_URL = "https://api.jolpi.ca/ergast/f1"

    // Meglévő függvény a versenyek listájához
    suspend fun getF1Races(limit: Int = 30, offset: Int = 0): Result<ApiResponse> {
        val safeLimit = limit.coerceAtLeast(1)
        val safeOffset = offset.coerceAtLeast(0)
        val url = "$BASE_URL/races.json?limit=$safeLimit&offset=$safeOffset"
        println("Fetching URL: $url")
        return makeApiCall(url) // Kiszervezzük a hívást egy segédfüggvénybe
    }

    // ÚJ függvény egy adott verseny eredményeinek lekérdezéséhez
    suspend fun getRaceResults(season: String, round: String): Result<ApiResponse> {
        // Validáljuk, hogy a season és round nem üres
        if (season.isBlank() || round.isBlank()) {
            return Result.failure(IllegalArgumentException("Season and round cannot be empty"))
        }
        // Összeállítjuk az URL-t a szezonnal és a fordulóval
        val url = "$BASE_URL/$season/$round/results.json"
        println("Fetching URL: $url")
        return makeApiCall(url) // Ugyanazt a segédfüggvényt használjuk
    }

    // Segédfüggvény az API hívás logikájának közösítésére
    private suspend fun makeApiCall(url: String): Result<ApiResponse> {
        return try {
            val response = client.get(url)
            if (response.status.isSuccess()) {
                Result.success(response.body<ApiResponse>()) // A válasz struktúrája ugyanaz (ApiResponse)
            } else {
                val errorBody = try { response.body<String>() } catch (e: Exception) { "N/A" }
                Result.failure(Exception("API hiba: ${response.status} - $errorBody"))
            }
        } catch (e: Exception) {
            println("Hiba történt a lekérdezés során ($url): ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }
}