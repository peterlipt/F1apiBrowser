import Race
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons // Szükséges lehet az ikonokhoz
import androidx.compose.material.icons.filled.ArrowDropDown // Szükséges lehet az ikonokhoz
import androidx.compose.material.icons.filled.KeyboardArrowDown // Szükséges lehet az ikonokhoz
import androidx.compose.material.icons.filled.KeyboardArrowUp // Szükséges lehet az ikonokhoz
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberDialogState
import androidx.compose.foundation.clickable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState

// Rendezési mezők definíciója
enum class SortField(val displayName: String) {
    DATE("Dátum"),
    RACE_NAME("Verseny neve"),
    CIRCUIT_NAME("Pálya neve"),
    COUNTRY("Ország")
}

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "F1 Race Browser") {
        App()
    }
}

@Composable
@Preview
fun App() {
    var allRaces by remember { mutableStateOf<List<Race>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") } // Általános keresés
    var yearFilter by remember { mutableStateOf("") }   // Év szűrő
    var circuitFilter by remember { mutableStateOf("") } // Pálya szűrő
    var countryFilter by remember { mutableStateOf("") } // Ország szűrő
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var totalRaces by remember { mutableStateOf(0) }
    var progress by remember { mutableStateOf(0f) } // Progress az "Összes betöltése"-hez
    var sortField by remember { mutableStateOf(SortField.DATE) } // Alapértelmezett rendezés Dátum szerint
    var sortAscending by remember { mutableStateOf(true) } // Alapértelmezetten növekvő (legkorábbi elöl)
    var showDetailsDialog by remember { mutableStateOf(false) }
    var selectedRaceForDetails by remember { mutableStateOf<Race?>(null) }
    var raceResultsData by remember { mutableStateOf<List<ResultData>?>(null) }
    var isLoadingDetails by remember { mutableStateOf(false) } // Dialógus tartalmának töltése
    var detailsError by remember { mutableStateOf<String?>(null) } // Dialógus hiba
    val limit = 50

    val coroutineScope = rememberCoroutineScope()

    // Függvény egy oldalnyi adat lekéréséhez vagy a lista teljes újratöltéséhez
    fun fetchRaces(loadMore: Boolean = false) {
        if (isLoading) return

        isLoading = true
        errorMessage = null

        val currentOffset = if (loadMore) allRaces.size else 0

        coroutineScope.launch {
            val result = ApiService.getF1Races(limit = limit, offset = currentOffset)
            result.onSuccess { apiResponse ->
                val mrData = apiResponse.mrData
                val newRaces = mrData.raceTable.races

                totalRaces = mrData.total.toIntOrNull() ?: 0

                if (loadMore) {
                    allRaces = allRaces + newRaces
                    println("Added ${newRaces.size} more races. Total: ${allRaces.size}/${totalRaces}")
                } else {
                    allRaces = newRaces
                    println("List reloaded, ${newRaces.size} races loaded. Total: ${totalRaces}")
                }
            }.onFailure { error ->
                if (!loadMore) {
                    allRaces = emptyList()
                    totalRaces = 0
                }
                errorMessage = "Error during fetch (offset: $currentOffset): ${error.message}"
                error.printStackTrace()
            }
            isLoading = false
        }
    }

    // Függvény az összes hátralévő adat szekvenciális lekérdezéséhez
    fun fetchAllRemainingRaces() {
        if (isLoading || (totalRaces > 0 && allRaces.size >= totalRaces)) {
            println("Fetch All: Skipped (isLoading=$isLoading, size=${allRaces.size}, total=$totalRaces)")
            return
        }

        isLoading = true
        errorMessage = null
        progress = if (totalRaces > 0) allRaces.size / totalRaces.toFloat() else 0f // Kezdeti progress beállítása

        coroutineScope.launch {
            println("Fetch All: Starting process...")
            try {
                // Kezdeti lekérdezés, ha szükséges
                if (totalRaces == 0 && allRaces.isEmpty()) {
                    println("Fetch All: Performing initial fetch to get total count...")
                    val initialResult = ApiService.getF1Races(limit = limit, offset = 0)
                    initialResult.onSuccess { apiResponse ->
                        val mrData = apiResponse.mrData
                        allRaces = mrData.raceTable.races
                        totalRaces = mrData.total.toIntOrNull() ?: 0
                        progress = if (totalRaces > 0) allRaces.size / totalRaces.toFloat() else 0f
                        println("Fetch All: Initial fetch done. Total: $totalRaces, Current: ${allRaces.size}")
                    }.onFailure { error ->
                        errorMessage = "Error during initial fetch: ${error.message}"
                        error.printStackTrace()
                        isLoading = false
                        progress = 0f // Hiba esetén nullázzuk a progress-t is
                        return@launch
                    }
                }

                // Lekérdezési ciklus
                while (allRaces.size < totalRaces) {
                    val currentOffset = allRaces.size
                    println("Fetch All: Requesting next batch. Offset: $currentOffset, Limit: $limit")

                    var shouldStop = false
                    var attempt = 0
                    val maxAttempts = 3 // Max próbálkozás hiba esetén
                    var lastError: Throwable? = null

                    // Újrapróbálkozási logika
                    while (attempt < maxAttempts) {
                        val result = ApiService.getF1Races(limit = limit, offset = currentOffset)
                        var success = false

                        result.onSuccess { apiResponse ->
                            val mrData = apiResponse.mrData
                            val newRaces = mrData.raceTable.races
                            if (newRaces.isNotEmpty()) {
                                allRaces = allRaces + newRaces
                                println("Fetch All: Added ${newRaces.size}. Total now: ${allRaces.size}/${totalRaces}")
                            } else {
                                println("Fetch All: Received empty list, likely reached the end unexpectedly. Stopping.")
                                shouldStop = true // Leállunk, ha üres listát kapunk
                            }
                            // Frissítjük a progress bart minden sikeres batch után
                            progress = if (totalRaces > 0) allRaces.size / totalRaces.toFloat() else 1f
                            if (allRaces.size >= totalRaces) {
                                println("Fetch All: Reached or exceeded total count.")
                                progress = 1f // Biztosan 100%
                                shouldStop = true
                            }
                            success = true // Sikeres volt a batch feldolgozása
                        }.onFailure { error ->
                            lastError = error // Elmentjük az utolsó hibát
                            println("Fetch All: Error on attempt ${attempt + 1} for offset $currentOffset: ${error.message}")
                        }

                        if (success || shouldStop) break // Ha sikerült vagy le kell állni, kilépünk a próbálkozás ciklusból
                        attempt++
                        if (attempt < maxAttempts) {
                            println("Fetch All: Retrying in 1 second...")
                            kotlinx.coroutines.delay(1000) // Várakozás újrapróbálás előtt
                        }
                    } // Újrapróbálkozás ciklus vége

                    // Ha max próbálkozás után sem sikerült és volt hiba
                    if (!shouldStop && attempt == maxAttempts && lastError != null) {
                        errorMessage = "Error during bulk load after $maxAttempts attempts (offset: $currentOffset): ${lastError!!.message}"
                        lastError!!.printStackTrace()
                        println("Fetch All: Stopping due to repeated errors.")
                        break // Kilépünk a fő while ciklusból
                    }

                    if (shouldStop) break // Kilépünk a fő while ciklusból, ha jelzettük
                    // Opcionális kis szünet a kérések között
                    kotlinx.coroutines.delay(100)
                } // Fő while ciklus vége

            } catch (e: Exception) {
                errorMessage = "Unexpected error during bulk load: ${e.message}"
                e.printStackTrace()
            } finally {
                isLoading = false // A teljes folyamat végén leállítjuk a töltést
                // A progress-t csak akkor nullázzuk, ha nem értük el a végét, vagy hiba volt
                if (allRaces.size < totalRaces || errorMessage != null) {
                    progress = 0f
                } else {
                    progress = 1f // Ha sikeresen végigment, maradjon 100%
                }
                println("Fetch All: Process finished. isLoading=$isLoading, progress=$progress")
            }
        }
    }

    fun fetchRaceDetails(race: Race) {
        selectedRaceForDetails = race // Mentsük el, melyik versenyt nézzük
        raceResultsData = null      // Töröljük az előző eredményeket
        detailsError = null         // Töröljük az előző hibát
        isLoadingDetails = true     // Indul a töltés a dialógusban
        showDetailsDialog = true    // Mutassuk a dialógust

        coroutineScope.launch {
            val result = ApiService.getRaceResults(race.season, race.round)
            result.onSuccess { apiResponse ->
                // Az API válaszban a RaceTable->Races egy listát ad vissza, de itt csak egy verseny lesz benne
                val resultsList = apiResponse.mrData.raceTable.races.firstOrNull()?.results
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
            isLoadingDetails = false // Töltés vége (siker vagy hiba)
        }
    }

    // Adatok lekérése az alkalmazás indulásakor (első oldal)
    LaunchedEffect(Unit) {
        fetchRaces(loadMore = false)
    }

    // Szűrt lista a keresés és a specifikus filterek alapján
    val filteredRaces = remember(searchQuery, yearFilter, circuitFilter, countryFilter, allRaces) {
        var currentlyFiltered = allRaces // Kezdjük a teljes listával

        // 1. Év szűrő alkalmazása
        if (yearFilter.isNotBlank()) {
            currentlyFiltered = currentlyFiltered.filter { race ->
                race.season == yearFilter // Pontos egyezés az évre
            }
        }

        // 2. Pálya szűrő alkalmazása
        if (circuitFilter.isNotBlank()) {
            currentlyFiltered = currentlyFiltered.filter { race ->
                race.circuit.circuitName.contains(circuitFilter, ignoreCase = true) // Tartalmazza, kis/nagybetű érzéketlen
            }
        }

        // 3. Ország szűrő alkalmazása
        if (countryFilter.isNotBlank()) {
            currentlyFiltered = currentlyFiltered.filter { race ->
                // Biztonságos hívás a nullázható 'country' mező miatt
                race.circuit.location.country?.contains(countryFilter, ignoreCase = true) == true
            }
        }

        // 4. Általános kereső alkalmazása a már szűrt listára
        if (searchQuery.isNotBlank()) {
            currentlyFiltered = currentlyFiltered.filter { race ->
                val query = searchQuery.lowercase()
                race.raceName.lowercase().contains(query) ||
                        race.circuit.location.locality?.lowercase()?.contains(query) == true
            }
        }

        currentlyFiltered // Visszaadjuk a végső szűrt listát
    }

    // A megjelenített lista a szűrés, RENDEZÉS és a sorrend alapján
    val displayedRaces = remember(filteredRaces, sortField, sortAscending) {
        val sortedList = when (sortField) {
            SortField.DATE -> {
                if (sortAscending) {
                    filteredRaces.sortedBy { it.date }
                } else {
                    filteredRaces.sortedByDescending { it.date }
                }
            }
            SortField.RACE_NAME -> if (sortAscending) {
                filteredRaces.sortedBy { it.raceName.lowercase() }
            } else {
                filteredRaces.sortedByDescending { it.raceName.lowercase() }
            }
            SortField.CIRCUIT_NAME -> if (sortAscending) {
                filteredRaces.sortedBy { it.circuit.circuitName.lowercase() }
            } else {
                filteredRaces.sortedByDescending { it.circuit.circuitName.lowercase() }
            }
            SortField.COUNTRY -> if (sortAscending) {
                filteredRaces.sortedWith(compareBy(nullsFirst()) { it.circuit.location.country?.lowercase() })
            } else {
                filteredRaces.sortedWith(compareByDescending(nullsLast()) { it.circuit.location.country?.lowercase() })
            }
        }
    }


    MaterialTheme {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            // Felső sor: Általános keresés és fordított sorrend kapcsoló
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Általános keresés...") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Specifikus szűrőmezők sora
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = yearFilter,
                    onValueChange = { yearFilter = it.filter { char -> char.isDigit() } },
                    label = { Text("Év") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = circuitFilter,
                    onValueChange = { circuitFilter = it },
                    label = { Text("Pálya") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = countryFilter,
                    onValueChange = { countryFilter = it },
                    label = { Text("Ország") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Rendezési vezérlők sora
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Rendezés:", modifier = Modifier.align(Alignment.CenterVertically))

                // Dropdown a rendezési mező kiválasztásához
                var expanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { expanded = true }) {
                        Text(sortField.displayName)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Rendezési mező kiválasztása")
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        SortField.entries.forEach { field ->
                            DropdownMenuItem(onClick = {
                                sortField = field
                                expanded = false
                            }) {
                                Text(field.displayName)
                            }
                        }
                    }
                }

                // Gomb/Ikon a rendezési irány váltásához
                IconButton(onClick = { sortAscending = !sortAscending }) {
                    Icon(
                        if (sortAscending) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (sortAscending) "Növekvő sorrend" else "Csökkenő sorrend"
                    )
                }
                Text(if (sortAscending) "Növ." else "Csökk.", modifier = Modifier.align(Alignment.CenterVertically))

            } // Rendezési vezérlők sorának vége

            Spacer(modifier = Modifier.height(8.dp))

            // Progress bar az "Összes betöltése" alatt
            if (isLoading && progress > 0f) {
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                )
                Text(
                    text = "Összes verseny betöltése: ${allRaces.size} / $totalRaces (${(progress * 100).toInt()}%)",
                    style = MaterialTheme.typography.caption,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(modifier = Modifier.height(8.dp))
            } else {
                Spacer(modifier = Modifier.height(14.dp)) // Helykitöltő
            }


            // Gombok sora
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = { fetchRaces(loadMore = false) }, enabled = !isLoading) {
                    Text("Újratöltés")
                }

                Button(
                    onClick = ::fetchAllRemainingRaces,
                    enabled = !isLoading && (totalRaces == 0 || allRaces.size < totalRaces)
                ) {
                    Text("Összes Betöltése")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Hibaüzenet megjelenítése
            if (errorMessage != null && !isLoading) {
                Text("Hiba: $errorMessage", color = MaterialTheme.colors.error)
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Versenyek listája
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(filteredRaces) { race -> // A displayedRaces már tartalmazza a szűrést ÉS a rendezést
                    RaceCard(
                        race = race,
                        onClick = { fetchRaceDetails(race) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // "Több betöltése" gomb vagy betöltés jelző a lista végén
                item {
                    if (allRaces.size < totalRaces || isLoading) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            if (isLoading) {
                                CircularProgressIndicator()
                            } else {
                                Button(
                                    onClick = { fetchRaces(loadMore = true) },
                                    enabled = !isLoading
                                ) {
                                    Text("Több betöltése (${allRaces.size}/${totalRaces})")
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    } else if (allRaces.isNotEmpty() && !isLoading) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Minden verseny betöltve (${allRaces.size})",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
            if (showDetailsDialog && selectedRaceForDetails != null) {
                RaceResultsDialog(
                    race = selectedRaceForDetails!!, // Biztosan nem null itt
                    results = raceResultsData,
                    isLoading = isLoadingDetails,
                    error = detailsError,
                    onDismissRequest = { showDetailsDialog = false } // Dialógus bezárása
                )
            }
        }
    }
}


@Composable
fun RaceCard(race: Race, onClick: () -> Unit) { // Új onClick paraméter
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick), // Kattinthatóvá tesszük
        elevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "${race.season} ${race.raceName}",
                style = MaterialTheme.typography.h6
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Kör: ${race.round}, Dátum: ${race.date}",
                style = MaterialTheme.typography.body2
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Helyszín: ${race.circuit.circuitName}",
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

//Composable: A dialógusablak a versenyeredmények megjelenítésére
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
        title = "${race.season} ${race.raceName} - Eredmények"
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
                            Text("Hiba: $error", color = MaterialTheme.colors.error)
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
                                    Text("Versenyző", fontWeight = FontWeight.Bold, modifier = Modifier.weight(2f))
                                    Text("Csapat", fontWeight = FontWeight.Bold, modifier = Modifier.weight(2f))
                                    Text("Kör", fontWeight = FontWeight.Bold, modifier = Modifier.width(50.dp))
                                    Text("Idő/Státusz", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f))
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
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        result.constructor.name,
                                        modifier = Modifier.weight(2f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(result.laps, modifier = Modifier.width(50.dp))
                                    Text(
                                        result.time?.time ?: result.status,
                                        modifier = Modifier.weight(1.5f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(result.points, modifier = Modifier.width(40.dp))
                                }
                                Divider()
                            }
                        }
                    }
                    else -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Nincsenek megjeleníthető eredmények.")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismissRequest,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Bezárás")
                }
            }
        }
    }
}