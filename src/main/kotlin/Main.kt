package hu.bme.aut.apitest // Helyettesítsd a saját package neveddel!

import ConstructorStanding
import DriverStanding
import Race
import ResultData
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.clickable // Szükséges a clickable-hez
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState // Szükséges a végtelen görgetéshez
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight // Szükséges a Bold-hoz
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog // Használhatunk Dialog-ot is AlertDialog helyett
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberDialogState // Dialog méret/pozíció mentéséhez
import kotlinx.coroutines.flow.collect // Szükséges a snapshotFlow-hoz
import kotlinx.coroutines.flow.filter // Szükséges a snapshotFlow-hoz
import kotlinx.coroutines.flow.map // Szükséges a snapshotFlow-hoz
import kotlinx.coroutines.launch
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState

// Rendezési mezők definíciója
enum class SortField(val displayName: String) {
    SEASON("Év"),
    DATE("Dátum"),
    RACE_NAME("Verseny neve"),
    CIRCUIT_NAME("Pálya neve"),
    COUNTRY("Ország")
}

// Képernyők definíciója
enum class Screen {
    RACE_LIST,
    STANDINGS
}

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "F1 Data Browser") {
        App()
    }
}

@Composable
@Preview
fun App() {
    // --- State változók ---
    var currentScreen by remember { mutableStateOf(Screen.RACE_LIST) }

    // Race List képernyő state-jei
    var allRaces by remember { mutableStateOf<List<Race>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var yearFilter by remember { mutableStateOf("") }
    var circuitFilter by remember { mutableStateOf("") }
    var countryFilter by remember { mutableStateOf("") }
    var isLoadingRaces by remember { mutableStateOf(false) } // Átnevezve
    var raceListError by remember { mutableStateOf<String?>(null) } // Átnevezve
    var totalRaces by remember { mutableStateOf(0) }
    var progress by remember { mutableStateOf(0f) }
    var reverseOrder by remember { mutableStateOf(false) }
    var sortField by remember { mutableStateOf(SortField.DATE) }
    var sortAscending by remember { mutableStateOf(true) }
    val limit = 50

    // Race Details dialógus state-jei
    var showDetailsDialog by remember { mutableStateOf(false) }
    var selectedRaceForDetails by remember { mutableStateOf<Race?>(null) }
    var raceResultsData by remember { mutableStateOf<List<ResultData>?>(null) }
    var isLoadingDetails by remember { mutableStateOf(false) }
    var detailsError by remember { mutableStateOf<String?>(null) }

    // Standings képernyő state-jei (Újak)
    var standingsSeason by remember { mutableStateOf("current") }
    var driverStandings by remember { mutableStateOf<List<DriverStanding>?>(null) }
    var constructorStandings by remember { mutableStateOf<List<ConstructorStanding>?>(null) }
    var isLoadingStandings by remember { mutableStateOf(false) }
    var standingsError by remember { mutableStateOf<String?>(null) }


    val coroutineScope = rememberCoroutineScope()

    // --- Függvények ---

    // fetchRaces - Módosítva az új state nevekre
    fun fetchRaces(loadMore: Boolean = false) {
        if (isLoadingRaces) return

        isLoadingRaces = true
        if (!loadMore) {
            raceListError = null
        }

        val currentOffset = if (loadMore) allRaces.size else 0

        coroutineScope.launch {
            val result = ApiService.getF1Races(limit = limit, offset = currentOffset)
            result.onSuccess { apiResponse ->
                val mrData = apiResponse.mrData
                val newRaces = mrData?.raceTable?.races ?: emptyList()
                val currentTotal = mrData?.total?.toIntOrNull() ?: 0

                if (currentTotal > totalRaces) {
                    totalRaces = currentTotal
                }

                if (loadMore) {
                    allRaces = allRaces + newRaces
                    println("Added ${newRaces.size} more races. Total: ${allRaces.size}/${totalRaces}")
                } else {
                    allRaces = newRaces
                    if (totalRaces == 0 && newRaces.isNotEmpty()) {
                        println("Warning: totalRaces is 0 after first fetch.")
                    }
                    println("List reloaded, ${newRaces.size} races loaded. Total: ${totalRaces}")
                }
                raceListError = null
            }.onFailure { error ->
                if (!loadMore) {
                    allRaces = emptyList()
                    totalRaces = 0
                }
                raceListError = "Error during fetch (offset: $currentOffset): ${error.message}"
                error.printStackTrace()
            }
            isLoadingRaces = false
        }
    }

    // fetchAllRemainingRaces - Módosítva az új state nevekre
    fun fetchAllRemainingRaces() {
        if (isLoadingRaces || (totalRaces > 0 && allRaces.size >= totalRaces)) {
            println("Fetch All: Skipped (isLoadingRaces=$isLoadingRaces, size=${allRaces.size}, total=$totalRaces)")
            return
        }

        isLoadingRaces = true
        raceListError = null
        progress = if (totalRaces > 0) allRaces.size / totalRaces.toFloat() else 0f

        coroutineScope.launch {
            println("Fetch All: Starting process...")
            try {
                if (totalRaces == 0 && allRaces.isEmpty()) {
                    println("Fetch All: Performing initial fetch to get total count...")
                    val initialResult = ApiService.getF1Races(limit = limit, offset = 0)
                    initialResult.onSuccess { apiResponse ->
                        val mrData = apiResponse.mrData
                        allRaces = mrData?.raceTable?.races ?: emptyList()
                        totalRaces = mrData?.total?.toIntOrNull() ?: 0
                        progress = if (totalRaces > 0) allRaces.size / totalRaces.toFloat() else 0f
                        println("Fetch All: Initial fetch done. Total: $totalRaces, Current: ${allRaces.size}")
                    }.onFailure { error ->
                        raceListError = "Error during initial fetch: ${error.message}"
                        error.printStackTrace()
                        isLoadingRaces = false
                        progress = 0f
                        return@launch
                    }
                }

                while (allRaces.size < totalRaces) {
                    val currentOffset = allRaces.size
                    println("Fetch All: Requesting next batch. Offset: $currentOffset, Limit: $limit")

                    var shouldStop = false
                    var attempt = 0
                    val maxAttempts = 3
                    var lastError: Throwable? = null

                    while (attempt < maxAttempts) {
                        val result = ApiService.getF1Races(limit = limit, offset = currentOffset)
                        var success = false

                        result.onSuccess { apiResponse ->
                            val mrData = apiResponse.mrData
                            val newRaces = mrData?.raceTable?.races ?: emptyList()
                            if (newRaces.isNotEmpty()) {
                                allRaces = allRaces + newRaces
                                println("Fetch All: Added ${newRaces.size}. Total now: ${allRaces.size}/${totalRaces}")
                            } else {
                                println("Fetch All: Received empty list, likely reached the end unexpectedly. Stopping.")
                                shouldStop = true
                            }
                            progress = if (totalRaces > 0) allRaces.size / totalRaces.toFloat() else 1f
                            if (allRaces.size >= totalRaces) {
                                println("Fetch All: Reached or exceeded total count.")
                                progress = 1f
                                shouldStop = true
                            }
                            success = true
                            raceListError = null
                        }.onFailure { error ->
                            lastError = error
                            println("Fetch All: Error on attempt ${attempt + 1} for offset $currentOffset: ${error.message}")
                        }

                        if (success || shouldStop) break
                        attempt++
                        if (attempt < maxAttempts) {
                            println("Fetch All: Retrying in 1 second...")
                            kotlinx.coroutines.delay(1000)
                        }
                    }

                    if (!shouldStop && attempt == maxAttempts && lastError != null) {
                        raceListError = "Error during bulk load after $maxAttempts attempts (offset: $currentOffset): ${lastError.message}"
                        lastError.printStackTrace()
                        println("Fetch All: Stopping due to repeated errors.")
                        break
                    }

                    if (shouldStop) break
                    kotlinx.coroutines.delay(100)
                }

            } catch (e: Exception) {
                raceListError = "Unexpected error during bulk load: ${e.message}"
                e.printStackTrace()
            } finally {
                isLoadingRaces = false
                if (allRaces.size < totalRaces || raceListError != null) {
                    progress = 0f
                } else {
                    progress = 1f
                }
                println("Fetch All: Process finished. isLoadingRaces=$isLoadingRaces, progress=$progress")
            }
        }
    }

    // fetchRaceDetails (dialógushoz - változatlan maradt ebben a körben)
    fun fetchRaceDetails(race: Race) {
        selectedRaceForDetails = race
        raceResultsData = null
        detailsError = null
        isLoadingDetails = true
        showDetailsDialog = true

        coroutineScope.launch {
            val result = ApiService.getRaceResults(race.season, race.round)
            result.onSuccess { apiResponse ->
                val resultsList = apiResponse.mrData?.raceTable?.races?.firstOrNull()?.results
                if (resultsList != null) {
                    raceResultsData = resultsList
                    println("Successfully fetched results for ${race.season} ${race.raceName}: ${resultsList.size} entries.")
                } else {
                    detailsError = "Nem található eredmény a versenyhez."
                    println("No results found in API response for ${race.season} ${race.raceName}.")
                }
            }.onFailure { error ->
                detailsError = "Hiba az eredmények lekérésekor: ${error.message}"
                error.printStackTrace()
            }
            isLoadingDetails = false
        }
    }

    // fetchStandings (Új függvény a pontversenyhez)
    fun fetchStandings(season: String) {
        if (isLoadingStandings) return
        isLoadingStandings = true
        standingsError = null
        driverStandings = null
        constructorStandings = null

        coroutineScope.launch {
            var driverResultOk = false
            var constructorResultOk = false
            var firstError: String? = null

            val driverResult = ApiService.getDriverStandings(season)
            driverResult.onSuccess { response ->
                driverStandings = response.mrData?.standingsTable?.standingsLists?.firstOrNull()?.driverStandings
                if (driverStandings == null) println("Driver standings list is null or empty in response for season $season.")
                driverResultOk = true
            }.onFailure { error ->
                firstError = "Hiba a versenyzői állás lekérésekor: ${error.message}"
                println(firstError)
            }

            val constructorResult = ApiService.getConstructorStandings(season)
            constructorResult.onSuccess { response ->
                constructorStandings = response.mrData?.standingsTable?.standingsLists?.firstOrNull()?.constructorStandings
                if (constructorStandings == null) println("Constructor standings list is null or empty in response for season $season.")
                constructorResultOk = true
            }.onFailure { error ->
                val constructorError = "Hiba a konstruktőri állás lekérésekor: ${error.message}"
                standingsError = firstError?.let { "$it\n$constructorError" } ?: constructorError
                println(constructorError)
            }

            if (firstError != null && standingsError == null) {
                standingsError = firstError
            }

            isLoadingStandings = false
        }
    }

    // Kezdeti adatlekérések (marad)
    LaunchedEffect(Unit) {
        fetchRaces(loadMore = false)
    }

    // filteredRaces és displayedRaces számítása (marad)
    val filteredRaces = remember(searchQuery, yearFilter, circuitFilter, countryFilter, allRaces) {
        var currentlyFiltered = allRaces
        if (yearFilter.isNotBlank()) { currentlyFiltered = currentlyFiltered.filter { it.season == yearFilter } }
        if (circuitFilter.isNotBlank()) { currentlyFiltered = currentlyFiltered.filter { it.circuit.circuitName.contains(circuitFilter, ignoreCase = true) } }
        if (countryFilter.isNotBlank()) { currentlyFiltered = currentlyFiltered.filter { it.circuit.location.country?.contains(countryFilter, ignoreCase = true) == true } }
        if (searchQuery.isNotBlank()) {
            currentlyFiltered = currentlyFiltered.filter { race ->
                val query = searchQuery.lowercase()
                race.raceName.lowercase().contains(query) ||
                        race.circuit.location.locality?.lowercase()?.contains(query) == true
            }
        }
        currentlyFiltered
    }
    val displayedRaces = remember(filteredRaces, sortField, sortAscending, reverseOrder) {
        val sortedList = when (sortField) {
            SortField.SEASON -> if (sortAscending) filteredRaces.sortedBy { it.season.toIntOrNull() ?: Int.MAX_VALUE } else filteredRaces.sortedByDescending { it.season.toIntOrNull() ?: Int.MIN_VALUE }
            SortField.DATE -> if (sortAscending) filteredRaces.sortedBy { it.date } else filteredRaces.sortedByDescending { it.date }
            SortField.RACE_NAME -> if (sortAscending) filteredRaces.sortedBy { it.raceName.lowercase() } else filteredRaces.sortedByDescending { it.raceName.lowercase() }
            SortField.CIRCUIT_NAME -> if (sortAscending) filteredRaces.sortedBy { it.circuit.circuitName.lowercase() } else filteredRaces.sortedByDescending { it.circuit.circuitName.lowercase() }
            SortField.COUNTRY -> if (sortAscending) filteredRaces.sortedWith(compareBy(nullsFirst()) { it.circuit.location.country?.lowercase() }) else filteredRaces.sortedWith(compareByDescending(nullsLast()) { it.circuit.location.country?.lowercase() })
        }
        if (reverseOrder) sortedList.reversed() else sortedList
    }


    MaterialTheme {
        Scaffold( // Scaffold a TopAppBar és a képernyőváltás kezeléséhez
            topBar = {
                TopAppBar(
                    title = { Text("F1 Data Browser") },
                    actions = {
                        Button(
                            onClick = { currentScreen = Screen.RACE_LIST },
                            enabled = currentScreen != Screen.RACE_LIST,
                            colors = ButtonDefaults.buttonColors(backgroundColor = if (currentScreen == Screen.RACE_LIST) MaterialTheme.colors.primaryVariant else MaterialTheme.colors.primary)
                        ) { Text("Races") }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { currentScreen = Screen.STANDINGS },
                            enabled = currentScreen != Screen.STANDINGS,
                            colors = ButtonDefaults.buttonColors(backgroundColor = if (currentScreen == Screen.STANDINGS) MaterialTheme.colors.primaryVariant else MaterialTheme.colors.primary)
                        ) { Text("Standings") }
                    }
                )
            }
        ) { paddingValues -> // Padding a Scaffold tartalmának
            Box(modifier = Modifier.padding(paddingValues)) { // Box a dialógus és a képernyő tartalmának rétegezéséhez
                // Képernyő váltás a currentScreen state alapján
                when (currentScreen) {
                    Screen.RACE_LIST -> RaceListScreen( // Átadjuk a szükséges state-eket és callback-eket
                        allRaces = allRaces,
                        searchQuery = searchQuery, onSearchQueryChange = { searchQuery = it },
                        yearFilter = yearFilter, onYearFilterChange = { yearFilter = it },
                        circuitFilter = circuitFilter, onCircuitFilterChange = { circuitFilter = it },
                        countryFilter = countryFilter, onCountryFilterChange = { countryFilter = it },
                        isLoadingRaces = isLoadingRaces,
                        raceListError = raceListError,
                        totalRaces = totalRaces,
                        progress = progress,
                        reverseOrder = reverseOrder, onReverseOrderChange = { reverseOrder = it },
                        sortField = sortField, onSortFieldChange = { sortField = it },
                        sortAscending = sortAscending, onSortAscendingChange = { sortAscending = it },
                        displayedRaces = displayedRaces,
                        onFetchRaces = ::fetchRaces,
                        onFetchAllRemainingRaces = ::fetchAllRemainingRaces,
                        onRaceClick = ::fetchRaceDetails
                    )
                    Screen.STANDINGS -> StandingsScreen( // Átadjuk a szükséges state-eket és callback-eket
                        selectedSeason = standingsSeason, onSeasonChange = { standingsSeason = it },
                        driverStandings = driverStandings,
                        constructorStandings = constructorStandings,
                        isLoading = isLoadingStandings,
                        error = standingsError,
                        onFetchStandings = ::fetchStandings
                    )
                }

                // Race Details dialógus (marad a fő App szintjén, hogy bármelyik képernyő felett megjelenhessen)
                if (showDetailsDialog && selectedRaceForDetails != null) {
                    RaceResultsDialog(
                        race = selectedRaceForDetails!!,
                        results = raceResultsData,
                        isLoading = isLoadingDetails,
                        error = detailsError,
                        onDismissRequest = { showDetailsDialog = false }
                    )
                }
            }
        }
    }
}


// --- RaceListScreen Composable (Új/Kiszervezett + Végtelen Görgetés) ---
@Composable
fun RaceListScreen(
    allRaces: List<Race>,
    searchQuery: String, onSearchQueryChange: (String) -> Unit,
    yearFilter: String, onYearFilterChange: (String) -> Unit,
    circuitFilter: String, onCircuitFilterChange: (String) -> Unit,
    countryFilter: String, onCountryFilterChange: (String) -> Unit,
    isLoadingRaces: Boolean,
    raceListError: String?,
    totalRaces: Int,
    progress: Float,
    reverseOrder: Boolean, onReverseOrderChange: (Boolean) -> Unit,
    sortField: SortField, onSortFieldChange: (SortField) -> Unit,
    sortAscending: Boolean, onSortAscendingChange: (Boolean) -> Unit,
    displayedRaces: List<Race>,
    onFetchRaces: (Boolean) -> Unit,
    onFetchAllRemainingRaces: () -> Unit,
    onRaceClick: (Race) -> Unit
) {
    val listState = rememberLazyListState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // --- UI elemek (keresők, rendezés, gombok) ---
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                label = { Text("General Search...") },
                modifier = Modifier.weight(1f),
                singleLine = true
            ) // Angolra váltva
            Spacer(modifier = Modifier.width(8.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = yearFilter, onValueChange = { onYearFilterChange(it.filter { c->c.isDigit() }) }, label = { Text("Year") }, modifier = Modifier.weight(1f), singleLine = true)
            OutlinedTextField(value = circuitFilter, onValueChange = onCircuitFilterChange, label = { Text("Circuit") }, modifier = Modifier.weight(1f), singleLine = true) // Angolra váltva
            OutlinedTextField(value = countryFilter, onValueChange = onCountryFilterChange, label = { Text("Country") }, modifier = Modifier.weight(1f), singleLine = true) // Angolra váltva
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Sort by:", modifier = Modifier.align(Alignment.CenterVertically)) // Angolra váltva
            var expanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { expanded = true }) { Text(sortField.displayName); Icon(Icons.Default.ArrowDropDown, contentDescription = "Select sort field") } // Angolra váltva
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    SortField.entries.forEach { field -> DropdownMenuItem(onClick = { onSortFieldChange(field); expanded = false }) { Text(field.displayName) } }
                }
            }
            IconButton(onClick = { onSortAscendingChange(!sortAscending) }) { Icon(if (sortAscending) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = if (sortAscending) "Ascending" else "Descending") } // Angolra váltva
            Text(if (sortAscending) "Asc" else "Desc", modifier = Modifier.align(Alignment.CenterVertically)) // Angolra váltva

        }
        Spacer(modifier = Modifier.height(8.dp))
        if (isLoadingRaces && progress > 0f) {
            LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth().height(6.dp))
            Text("Loading all races: ${allRaces.size} / $totalRaces (${(progress * 100).toInt()}%)", style = MaterialTheme.typography.caption, modifier = Modifier.align(Alignment.CenterHorizontally)) // Angolra váltva
            Spacer(modifier = Modifier.height(8.dp))
        } else { Spacer(modifier = Modifier.height(14.dp)) }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Button(onClick = { onFetchRaces(false) }, enabled = !isLoadingRaces) { Text("Reload") } // Angolra váltva
            Button(onClick = onFetchAllRemainingRaces, enabled = !isLoadingRaces && (totalRaces == 0 || allRaces.size < totalRaces)) { Text("Load All") } // Angolra váltva
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (raceListError != null && !isLoadingRaces) { Text("Error: $raceListError", color = MaterialTheme.colors.error); Spacer(modifier = Modifier.height(8.dp)) }


        // Versenyek listája - Végtelen görgetéssel
        LazyColumn(
            modifier = Modifier.weight(1f),
            state = listState
        ) {
            items(displayedRaces) { race ->
                RaceCard(
                    race = race,
                    onClick = { onRaceClick(race) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            item {
                if (isLoadingRaces || (allRaces.isNotEmpty() && allRaces.size >= totalRaces)) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (isLoadingRaces && allRaces.size < totalRaces) {
                            CircularProgressIndicator()
                        }
                        else if (!isLoadingRaces && allRaces.size >= totalRaces && totalRaces > 0) {
                            Text("All races loaded (${allRaces.size})", textAlign = androidx.compose.ui.text.style.TextAlign.Center) // Angolra váltva
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        // LaunchedEffect a görgetés figyelésére
        if (progress < 1f && totalRaces > 0) {
            LaunchedEffect(listState, isLoadingRaces, allRaces.size, totalRaces) {
                snapshotFlow { listState.layoutInfo }
                    .map { layoutInfo ->
                        layoutInfo.visibleItemsInfo.lastOrNull()?.index
                    }
                    .filter { lastVisibleIndex ->
                        lastVisibleIndex != null &&
                                !isLoadingRaces &&
                                allRaces.size < totalRaces &&
                                lastVisibleIndex >= allRaces.size - 5 // Küszöbérték
                    }
                    .collect {
                        println("Reached end of list (last visible index: $it), loading more...")
                        onFetchRaces(true)
                    }
            }
        }
    }
}


// --- StandingsScreen Composable (Új) ---
@Composable
fun StandingsScreen(
    selectedSeason: String,
    onSeasonChange: (String) -> Unit,
    driverStandings: List<DriverStanding>?,
    constructorStandings: List<ConstructorStanding>?,
    isLoading: Boolean,
    error: String?,
    onFetchStandings: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = selectedSeason,
                onValueChange = onSeasonChange,
                label = { Text("Season ('current' or year)") }, // Angolra váltva
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Button(
                onClick = { onFetchStandings(selectedSeason) },
                enabled = !isLoading && selectedSeason.isNotBlank()
            ) {
                Text("Fetch Standings") // Angolra váltva
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (error != null) {
            Text("Error: $error", color = MaterialTheme.colors.error) // Angolra váltva
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Driver Standings", style = MaterialTheme.typography.h6) // Angolra váltva
                    Spacer(modifier = Modifier.height(8.dp))
                    StandingsTableComposable(standings = driverStandings) { standing ->
                        DriverStandingRow(standing as DriverStanding)
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Constructor Standings", style = MaterialTheme.typography.h6) // Angolra váltva
                    Spacer(modifier = Modifier.height(8.dp))
                    StandingsTableComposable(standings = constructorStandings) { standing ->
                        ConstructorStandingRow(standing as ConstructorStanding)
                    }
                }
            }
        }
    }
}

// --- Általános Composable a pontverseny táblázathoz (Új) ---
@Composable
fun <T> StandingsTableComposable(
    standings: List<T>?,
    header: @Composable () -> Unit = { DefaultStandingsHeader() },
    itemContent: @Composable (T) -> Unit
) {
    Card(elevation = 2.dp) {
        Column {
            if (standings.isNullOrEmpty()) {
                Text("No data available.", modifier = Modifier.padding(16.dp)) // Angolra váltva
            } else {
                LazyColumn {
                    item {
                        Box(modifier = Modifier.padding(horizontal = 8.dp)) { header() }
                        Divider()
                    }
                    items(standings) { standingItem ->
                        Box(modifier = Modifier.padding(horizontal = 8.dp)) { itemContent(standingItem) }
                        Divider()
                    }
                }
            }
        }
    }
}

// --- Alapértelmezett fejléc a pontversenyhez (Új) ---
@Composable
fun DefaultStandingsHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Pos", fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp))
        Text("Name", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)) // Angolra váltva
        Text("Pts", fontWeight = FontWeight.Bold, modifier = Modifier.width(50.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
        Text("Wins", fontWeight = FontWeight.Bold, modifier = Modifier.width(50.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

// --- Composable egy DriverStanding sorhoz (Új) ---
@Composable
fun DriverStandingRow(standing: DriverStanding) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(standing.position ?: "-", modifier = Modifier.width(40.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${standing.driver.givenName} ${standing.driver.familyName}",
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                style = MaterialTheme.typography.body2,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                standing.constructors.joinToString { it.name },
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                style = MaterialTheme.typography.caption
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(standing.points, modifier = Modifier.width(50.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End, style = MaterialTheme.typography.body2)
        Text(standing.wins, modifier = Modifier.width(50.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End, style = MaterialTheme.typography.body2)
    }
}

// --- Composable egy ConstructorStanding sorhoz (Új) ---
@Composable
fun ConstructorStandingRow(standing: ConstructorStanding) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(standing.position, modifier = Modifier.width(40.dp))
        Text(
            standing.constructor.name,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            style = MaterialTheme.typography.body2,
            fontWeight = FontWeight.SemiBold
        )
        Text(standing.points, modifier = Modifier.width(50.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End, style = MaterialTheme.typography.body2)
        Text(standing.wins, modifier = Modifier.width(50.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End, style = MaterialTheme.typography.body2)
    }
}


// --- RaceCard és RaceResultsDialog (Változatlan ebben a lépésben) ---
@Composable
fun RaceCard(race: Race, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "${race.season} ${race.raceName}",
                style = MaterialTheme.typography.h6
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Round: ${race.round}, Date: ${race.date}", // Angolra váltva
                style = MaterialTheme.typography.body2
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Circuit: ${race.circuit.circuitName}", // Angolra váltva
                style = MaterialTheme.typography.subtitle1
            )
            Text(
                text = "${race.circuit.location.locality ?: "N/A"}, ${race.circuit.location.country ?: "N/A"}",
                style = MaterialTheme.typography.body2,
                color = LocalContentColor.current.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun RaceResultsDialog(
    race: Race,
    results: List<ResultData>?,
    isLoading: Boolean,
    error: String?,
    onDismissRequest: () -> Unit
) {
    DialogWindow(
        onCloseRequest = onDismissRequest,
        state = rememberDialogState(width = 800.dp, height = 600.dp),
        title = "${race.season} ${race.raceName} - Results"
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "${race.season} ${race.raceName}",
                    style = MaterialTheme.typography.h5,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Text(
                    "${race.circuit.circuitName} (${race.date})",
                    style = MaterialTheme.typography.subtitle1,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(modifier = Modifier.height(16.dp))

                when {
                    isLoading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    error != null -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Error: $error", color = MaterialTheme.colors.error) // Angolra váltva
                        }
                    }
                    !results.isNullOrEmpty() -> {
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Pos", fontWeight = FontWeight.Bold, modifier = Modifier.width(50.dp))
                                    Text("No", fontWeight = FontWeight.Bold, modifier = Modifier.width(50.dp))
                                    Text("Driver", fontWeight = FontWeight.Bold, modifier = Modifier.weight(2f)) // Angolra váltva
                                    Text("Team", fontWeight = FontWeight.Bold, modifier = Modifier.weight(2f)) // Angolra váltva
                                    Text("Laps", fontWeight = FontWeight.Bold, modifier = Modifier.width(50.dp)) // Angolra váltva
                                    Text("Time/Status", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f)) // Angolra váltva
                                    Text("Pts", fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp))
                                }
                                Divider()
                            }

                            items(results) { result ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(result.position, modifier = Modifier.width(50.dp))
                                    Text(result.number ?: "-", modifier = Modifier.width(50.dp))
                                    Text(
                                        "${result.driver.givenName} ${result.driver.familyName}",
                                        modifier = Modifier.weight(2f),
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Text(
                                        result.constructor.name,
                                        modifier = Modifier.weight(2f),
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Text(result.laps, modifier = Modifier.width(50.dp))
                                    Text(
                                        result.time?.time ?: result.status,
                                        modifier = Modifier.weight(1.5f),
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Text(result.points, modifier = Modifier.width(40.dp))
                                }
                                Divider()
                            }
                        }
                    }
                    else -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No results available.") // Angolra váltva
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismissRequest,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Close") // Angolra váltva
                }
            }
        }
    }
}