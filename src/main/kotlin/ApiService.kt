import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

object ApiService {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
            })
        }
    }

    suspend fun getF1Races(limit: Int, offset: Int): Result<MRData> {
        return try {
            val url = "https://api.jolpi.ca/ergast/f1/races.json?limit=$limit&offset=$offset"
            val response: HttpResponse = client.get(url)
            Result.success(response.body())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}