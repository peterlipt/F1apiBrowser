import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.launch

@Composable
@Preview
fun App() {
    var allRaces by remember { mutableStateOf<List<Race>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()

    fun fetchRaces(offset: Int) {
        isLoading = true
        errorMessage = null
        coroutineScope.launch {
            ApiService.getF1Races(limit = 30, offset = offset).onSuccess { mrData ->
                allRaces = mrData.raceTable.races
            }.onFailure {
                errorMessage = "Failed to load races: ${it.message}"
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        fetchRaces(0)
    }

    val filteredRaces = remember(searchQuery, allRaces) {
        allRaces.filter { race ->
            race.raceName.contains(searchQuery, ignoreCase = true) ||
                    race.circuit.circuitName.contains(searchQuery, ignoreCase = true) ||
                    race.circuit.location.locality.contains(searchQuery, ignoreCase = true) ||
                    race.circuit.location.country.contains(searchQuery, ignoreCase = true) ||
                    race.season.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search") },
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Button(onClick = { fetchRaces(0) }) {
            Text("Refresh")
        }
        if (isLoading) {
            CircularProgressIndicator()
        }
        if (errorMessage != null) {
            Text(errorMessage!!)
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(filteredRaces) { race ->
                RaceCard(race)
            }
        }
    }
}

@Composable
fun RaceCard(race: Race) {
    Card(modifier = Modifier.padding(8.dp)) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text("Season: ${race.season}", style = MaterialTheme.typography.body1.copy(fontWeight = FontWeight.Bold))
            Text("Race Name: ${race.raceName}")
            Text("Round: ${race.round}")
            Text("Date: ${race.date}")
            Text("Circuit: ${race.circuit.circuitName}")
            Text("Location: ${race.circuit.location.locality}, ${race.circuit.location.country}")
        }
    }
}

fun main() = application {
    Window(onCloseRequest = ::exitApplication) {
        App()
    }}
