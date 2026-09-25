/*
 * Kairu SMP Client UI fork.
 * Kairu-owned Compose screen; it deliberately does not render OneConfig's
 * mod browser or the legacy Minecraft AdminScreen.
 */
package uk.kairu.client

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.JsonArray
import com.mojang.blaze3d.platform.InputConstants
import org.polyfrost.oneconfig.api.platform.v1.Platform
import org.polyfrost.oneconfig.internal.ui.compose.ComposeScreen

/** Kairu's responsive menu shell. All operations call [KairuControlBridge]. */
class KairuControlScreen : ComposeScreen() {
    private var page by mutableStateOf(pageFor(KairuControlBridge.currentSection()))
    private var selectedPlayerId by mutableStateOf("")
    private var selectedPlayerName by mutableStateOf("")
    private var selectedWorldId by mutableStateOf("")
    private var selectedWorldName by mutableStateOf("")
    private var responseRevision by mutableIntStateOf(0)
    private var guildName by mutableStateOf("")
    private var guildTag by mutableStateOf("")
    private var guildDescription by mutableStateOf("")
    private var selectedFlagId by mutableStateOf("")
    private var selectedFlagName by mutableStateOf("")
    private var selectedFlagBoolean by mutableStateOf(false)
    private var selectedFlagEnabled by mutableStateOf(false)
    private var selectedFlagChoice by mutableStateOf("")
    private var flagMenuExpanded by mutableStateOf(false)
    private var plotSettingMenuExpanded by mutableStateOf(false)
    private var plotMemberMenuExpanded by mutableStateOf(false)
    private var flagValue by mutableStateOf("")
    private var selectedPlotMemberId by mutableStateOf("")
    private var selectedPlotMemberName by mutableStateOf("")
    private var searchQuery by mutableStateOf("")
    private var guildStep by mutableIntStateOf(0)
    private var worldDetail by mutableStateOf(false)
    private var playerTravel by mutableStateOf(false)
    private var worldTimeExpanded by mutableStateOf(false)
    private var worldWeatherExpanded by mutableStateOf(false)
    private var worldDifficultyExpanded by mutableStateOf(false)
    private var worldTimeChoice by mutableStateOf("Choose time")
    private var worldWeatherChoice by mutableStateOf("Choose weather")
    private var worldDifficultyChoice by mutableStateOf("Choose difficulty")

    init { KairuControlBridge.setRefreshListener { responseRevision++ } }

    /** ComposeScreen consumes Escape by default; explicitly close the Kairu menu. */
    override fun handleKeyPressed(key: Int, modifiers: Int): Boolean {
        if (key == InputConstants.KEY_ESCAPE) {
            Platform.screen().display(null, 0)
            return true
        }
        return super.handleKeyPressed(key, modifiers)
    }

    @Composable override fun compose() {
        // Reading this Compose state makes live SMPPlatform replies redraw the page.
        responseRevision
        val background = Color(0xFF12131A)
        val surface = Color(0xFF20222D)
        val accent = Color(0xFFA97CFF)
        Box(Modifier.fillMaxSize().background(Color(0xD9070811)), contentAlignment = Alignment.Center) {
            Row(
                // This deliberately follows the OneConfig shell: fixed design ratio,
                // narrow sidebar, header, then a scrollable content canvas.
                Modifier.sizeIn(minWidth = 960.dp, minHeight = 520.dp, maxWidth = 1391.dp, maxHeight = 700.dp)
                    .fillMaxSize().background(background)
            ) {
                Column(
                    Modifier.width(236.dp).fillMaxHeight().background(Color(0xFF11121A)).padding(horizontal = 18.dp, vertical = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("KAIRU", color = Color(0xFF5BDBFF), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("SMP CONTROL CENTRE", color = Color(0xFFC6C4D2), fontSize = 12.sp)
                    Text("PLAYER", color = Color(0xFF807D91), fontSize = 11.sp, modifier = Modifier.padding(top = 12.dp))
                    nav("Player", accent)
                    nav("Guilds", accent)
                    nav("Points", accent)
                    nav("Auction", accent)
                    nav("Search", accent)
                    nav("Atrium", accent)
                    // Administration is only exposed when SMPPlatform has granted
                    // an actual staff capability.  A normal player must not even see
                    // the administration section in the client UI.
                    if (KairuControlBridge.hasAdministrationAccess()) {
                        Text("ADMINISTRATION", color = Color(0xFF807D91), fontSize = 11.sp, modifier = Modifier.padding(top = 14.dp))
                        nav("Players", accent)
                        nav("Worlds", accent)
                    }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = { Platform.screen().display(null, 0) },
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF2A2C3A), contentColor = Color.White),
                        modifier = Modifier.padding(top = 12.dp)
                    ) { Text("Close") }
                }
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                ) {
                    Row(
                        Modifier.fillMaxWidth().height(74.dp).background(Color(0xFF171822)).padding(horizontal = 28.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        Text(page, color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        kairuField(searchQuery, { searchQuery = it }, "Search this page", Modifier.width(250.dp))
                    }
                    Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(KairuControlBridge.message(), color = Color(0xFFAAA7B9), fontSize = 14.sp)
                    when (page) {
                        "Player" -> playerHome(surface)
                        "Claims" -> claims(surface)
                        "Guilds" -> guilds(surface)
                        "Points" -> points(surface)
                        "Auction" -> auction(surface)
                        "Search" -> searchPlayers(surface)
                        "Atrium" -> atrium(surface)
                        "Players" -> players(surface)
                        "Worlds" -> worlds(surface)
                    }
                }
            }
        }
    }
    }

    @Composable private fun guilds(surface: Color) {
        val guild = KairuControlBridge.state()?.getAsJsonObject("guild")
        val leaderboard = guild?.getAsJsonArray("leaderboard") ?: JsonArray()
        // Compact actions stay above the guild directory; the directory is the main page content.
        controls(surface, listOf("Refresh" to "guild-summary", "Invitations" to "guild-invites", "Leaderboard" to "guild-top"), 4)
        if (guild != null && guild.has("inGuild") && guild.get("inGuild").asBoolean) {
            sectionTitle("Your guild", "Manage members and view guild progress.")
            Text("${guild.get("name").asString} [${guild.get("tag").asString}]", color = Color.White, fontSize = 20.sp)
            Text("Rank: ${guild.get("rank").asString}  •  Guild points: ${guild.get("points").asLong}", color = Color(0xFFAAA7B9))
            guild.getAsJsonArray("members")?.forEach { member ->
                val row = member.asJsonObject
                Text("${row.get("rank").asString}  •  ${playerName(row.get("id").asString)}", color = Color(0xFFAAA7B9))
            }
            if (guild.get("rank").asString in setOf("Leader", "Officer")) {
                Text("Invite an online player", color = Color(0xFF5BDBFF))
                KairuControlBridge.state()?.getAsJsonArray("players")?.forEach { player ->
                    val row = player.asJsonObject
                    Button(onClick = { KairuControlBridge.request("guild-invite", row.get("id").asString) }, colors = ButtonDefaults.buttonColors(backgroundColor = surface, contentColor = Color.White), modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) { Text("Invite ${row.get("name").asString}") }
                }
            }
        } else {
            guildWizard(surface, guild)
        }
        if (guildStep == 0) guildDirectory(leaderboard)
    }

    @Composable private fun guildDirectory(rows: JsonArray) {
        sectionTitle("Guild directory", "Ranked by guild points. Use the smaller controls above to refresh or see invitations.")
        if (rows.size() == 0) {
            Text("Loading the guild directory…", color = Color(0xFFAAA7B9))
            return
        }
        Text("RANK     GUILD                         MEMBERS     POINTS", color = Color(0xFF807D91), fontSize = 12.sp)
        rows.forEach { element ->
            val row = element.asJsonObject
            Row(Modifier.fillMaxWidth().background(Color(0xFF20222D)).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("#${row.get("rank").asInt}", color = Color(0xFF5BDBFF), modifier = Modifier.width(58.dp))
                Text("${row.get("name").asString} [${row.get("tag").asString}]", color = Color.White, modifier = Modifier.weight(1f))
                Text(row.get("members").asInt.toString(), color = Color(0xFFC6C4D2), modifier = Modifier.width(84.dp))
                Text(row.get("points").asLong.toString(), color = Color(0xFFA97CFF), modifier = Modifier.width(90.dp))
            }
        }
    }

    /** A small, explicit journey is much safer than asking players to fill a wall of fields. */
    @Composable private fun guildWizard(surface: Color, guild: com.google.gson.JsonObject?) {
        val cost = guild?.get("creationCost")?.asLong ?: 500L
        val currency = guild?.get("creationCurrency")?.asString ?: "KAIRU_POINTS"
        when (guildStep) {
            0 -> {
                sectionTitle("Guilds", "Create a guild for $cost $currency, or check an invitation.")
                selectionTile("Create a guild", "Step-by-step setup: name, tag, then confirmation") { guildStep = 1 }
            }
            1 -> {
                sectionTitle("Create guild · 1 of 3", "Choose a clear guild name.")
                kairuField(guildName, { guildName = it.take(32) }, "Guild name", Modifier.fillMaxWidth())
                wizardButtons("Back", { guildStep = 0 }, "Continue", { if (guildName.isNotBlank()) guildStep = 2 }, surface)
            }
            2 -> {
                sectionTitle("Create guild · 2 of 3", "Choose a short, recognisable tag (up to 6 characters).")
                kairuField(guildTag, { guildTag = it.uppercase().filter(Char::isLetterOrDigit).take(6) }, "Guild tag", Modifier.fillMaxWidth())
                wizardButtons("Back", { guildStep = 1 }, "Continue", { if (guildTag.isNotBlank()) guildStep = 3 }, surface)
            }
            else -> {
                sectionTitle("Create guild · 3 of 3", "Review the details. Creating costs $cost $currency.")
                selectionTile(guildName, "Tag: $guildTag") { }
                kairuField(guildDescription, { guildDescription = it.take(160) }, "Description (optional)", Modifier.fillMaxWidth())
                wizardButtons("Back", { guildStep = 2 }, "Create for $cost $currency", {
                    val raw = "$guildName|$guildTag|$guildDescription"
                    val encoded = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray(Charsets.UTF_8))
                    guildStep = 0
                    KairuControlBridge.request("guild-create", encoded)
                }, surface)
            }
        }
    }

    @Composable private fun playerHome(surface: Color) {
        val worlds = KairuControlBridge.state()?.getAsJsonArray("travelWorlds")
        if (!playerTravel) {
            val profile = KairuControlBridge.state()?.getAsJsonObject("profile")
            sectionTitle("Your SMP dashboard", "Your live information and the things you can do on Kairu SMP.")
            if (profile != null) {
                profileCard(profile, surface)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                dashboardTile("World travel", "Choose a world", { playerTravel = true }, Modifier.weight(1f))
                dashboardTile("Guilds", "Create or manage", { page = "Guilds"; KairuControlBridge.open("guilds") }, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                dashboardTile("Points", "Balances and rewards", { page = "Points"; KairuControlBridge.open("points") }, Modifier.weight(1f))
                dashboardTile("The Atrium", "Plots and builds", { page = "Atrium"; KairuControlBridge.open("atrium") }, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                dashboardTile("Land claims", "Protect your build", { page = "Claims"; KairuControlBridge.request("claim-info") }, Modifier.weight(1f))
                Spacer(Modifier.weight(1f))
            }
            return
        }
        if (worlds == null) {
            sectionTitle("World travel", "Loading worlds you are allowed to enter.")
            controls(surface, listOf("Refresh available worlds" to "status"))
        } else {
            sectionTitle("World travel", "Only worlds you have permission to enter are shown.")
            Button(onClick = { playerTravel = false }, colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF2A2C3A), contentColor = Color.White)) { Text("Back to dashboard") }
            selectionGrid(worlds.map { element ->
                val world = element.asJsonObject
                Triple(world.get("name").asString, "Travel", { KairuControlBridge.request("travel", world.get("id").asString) })
            }, 4)
        }
    }

    @Composable private fun claims(surface: Color) {
        val players = KairuControlBridge.state()?.getAsJsonArray("players") ?: JsonArray()
        sectionTitle("Land claims", "Create a protected building area and choose who can build with you.")
        Text("Recommended size: radius 32 (a 65×65 block area). Claims protect builds and stop TNT damage inside them.", color = Color(0xFFAAA7B9), fontSize = 13.sp)
        Text("Create a claim", color = Color(0xFF5BDBFF), fontSize = 16.sp)
        controls(surface, listOf("Small · 33×33" to "claim-create 16", "Recommended · 65×65" to "claim-create 32", "Large · 97×97" to "claim-create 48", "Maximum · 129×129" to "claim-create 64"), 4)
        Text("Your current location", color = Color(0xFF5BDBFF), fontSize = 16.sp)
        controls(surface, listOf("Check claim here" to "claim-info", "Arm abandon claim" to "claim-abandon-arm", "Confirm abandon" to "claim-abandon-confirm"), 4)
        Text("Builders", color = Color(0xFF5BDBFF), fontSize = 16.sp)
        Text("Stand inside your claim, select an online player, then grant or remove their building access.", color = Color(0xFFAAA7B9), fontSize = 13.sp)
        if (players.size() == 0) Text("No online players are available.", color = Color(0xFFAAA7B9))
        else selectionGrid(players.map { entry ->
            val player = entry.asJsonObject
            Triple(player.get("name").asString, "Add or remove builder", { selectedPlayerId = player.get("id").asString; selectedPlayerName = player.get("name").asString })
        }, 4)
        if (selectedPlayerId.isNotBlank()) {
            Text("Selected builder: $selectedPlayerName", color = Color(0xFFC6C4D2), fontSize = 13.sp)
            controls(surface, listOf("Allow building" to "claim-trust $selectedPlayerId", "Remove building access" to "claim-untrust $selectedPlayerId"), 4)
        }
        Button(onClick = { page = "Player" }, colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF2A2C3A), contentColor = Color.White)) { Text("Back to dashboard") }
    }

    @Composable private fun nav(label: String, accent: Color) {
        Button(
            onClick = { page = label; searchQuery = ""; if (label == "Worlds") worldDetail = false; if (label == "Player") playerTravel = false; KairuControlBridge.open(label.lowercase()) },
            colors = ButtonDefaults.buttonColors(
                backgroundColor = if (page == label) Color(0xFF302B45) else Color.Transparent,
                contentColor = if (page == label) Color.White else Color(0xFFAAA7B9)
            ),
            modifier = Modifier.fillMaxWidth().height(38.dp)
        ) { Text(label) }
    }

    @Composable private fun auction(surface: Color) {
        sectionTitle("Player Auction", "Browse listings, sell an item, or bid using your Kairu Points.")
        controls(surface, listOf(
            "Browse auction" to "auction-browse",
            "Sell held item" to "auction-sell 1 60"
        ), 2)
        Text("Auction commands", color = Color(0xFF5BDBFF), fontSize = 16.sp)
        Text("Use the server auction command for the full listing ID and bid amount.", color = Color(0xFFAAA7B9), fontSize = 13.sp)
        controls(surface, listOf(
            "Open auction chat view" to "auction-browse"
        ), 2)
    }

    @Composable private fun searchPlayers(surface: Color) {
        sectionTitle("Player Search", "Find online players by name. Results are returned by SMPPlatform.")
        kairuField(searchQuery, { searchQuery = it.take(32) }, "Player name", Modifier.fillMaxWidth())
        controls(surface, listOf(
            "Search player" to "search " + searchQuery.trim()
        ), 2)
        Text("Search is permission checked by SMPPlatform; staff may also see hidden/offline matches.", color = Color(0xFFAAA7B9), fontSize = 13.sp)
    }

    @Composable private fun points(surface: Color) {
        // Accept both the current SMPPlatform envelope and older gateway envelopes.
        val state = KairuControlBridge.state()
        val pointsData = state?.getAsJsonObject("points") ?: state
        val balances = pointsData?.getAsJsonArray("balances") ?: state?.getAsJsonArray("balances")
        sectionTitle("Your balances", "Select a currency to view its history or leaderboard.")
        if (balances == null) {
            Text("Balances have not loaded yet.", color = Color(0xFFAAA7B9))
        }
        balances?.forEach { balance ->
            val row = balance.asJsonObject
            val currency = row.get("currency").asString
            selectionTile(currency.replace('_', ' '), "Balance: ${row.get("balance").asLong}") { KairuControlBridge.request("points-history", currency) }
            controls(surface, listOf("$currency history" to "points-history $currency", "$currency leaderboard" to "points-top $currency"))
        }
        controls(surface, listOf("Refresh balances" to "points-summary"), 4)
    }

    @Composable private fun atrium(surface: Color) {
        val state = KairuControlBridge.state()
        val available = state?.get("plots")?.asBoolean == true
        sectionTitle("Atrium plots", if (available) "Manage your current plot's permissions and members." else "Travel to The Atrium before editing a plot.")
        if (available) {
            sectionTitle("Quick plot actions", "Start with the common PlotSquared actions, then choose a permission below.")
            controls(surface, listOf("Plot information" to "plot-info", "Go home" to "plot-home", "Claim current plot" to "plot-claim", "Find a free plot" to "plot-auto"), 4)
            val flags = state?.getAsJsonArray("flags") ?: JsonArray()
            dropdownSelector("Plot setting", selectedFlagName.ifBlank { "Choose a plot setting" }, flags.map { value ->
                val flag = value.asJsonObject
                flag.get("name").asString to flag.get("id").asString
            }, plotSettingMenuExpanded, { plotSettingMenuExpanded = it }) { _, id ->
                flags.firstOrNull { it.asJsonObject.get("id").asString == id }?.asJsonObject?.let { flag ->
                    selectedFlagId = flag.get("id").asString
                    selectedFlagName = flag.get("name").asString
                    selectedFlagBoolean = flag.get("boolean").asBoolean
                    selectedFlagEnabled = false
                    selectedFlagChoice = ""
                    flagValue = ""
                }
            }
            if (selectedFlagId.isNotBlank()) {
                sectionTitle("$selectedFlagName", "Choose the setting you want to apply to your current plot.")
                if (selectedFlagBoolean) {
                    Row(Modifier.fillMaxWidth().background(Color(0xFF20222D)).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Allowed", color = Color.White, fontWeight = FontWeight.Medium)
                            Text("Slide to allow or block this permission.", color = Color(0xFFAAA7B9), fontSize = 12.sp)
                        }
                        Switch(checked = selectedFlagEnabled, onCheckedChange = { enabled ->
                            selectedFlagEnabled = enabled
                            KairuControlBridge.request("plot-flag", selectedFlagId, enabled.toString())
                        })
                    }
                } else if (selectedFlagId == "gamemode") {
                    choiceControl("Gamemode", listOf("survival", "creative", "adventure", "spectator")) { applyTypedFlag(it) }
                } else if (selectedFlagId == "time") {
                    choiceControl("Time", listOf("day" to "1000", "noon" to "6000", "night" to "13000", "midnight" to "18000", "custom" to "")) { choice ->
                        if (choice == "custom") { selectedFlagChoice = "custom" } else applyTypedFlag(choice)
                    }
                    if (selectedFlagChoice == "custom") {
                        kairuField(flagValue, { flagValue = it.filter(Char::isDigit).take(5) }, "Custom time (0–24000)", Modifier.fillMaxWidth())
                        Button(onClick = { if (flagValue.isNotBlank()) applyTypedFlag(flagValue) }, colors = ButtonDefaults.buttonColors(backgroundColor = surface, contentColor = Color.White)) { Text("Apply custom time") }
                    }
                } else {
                    kairuField(flagValue, { flagValue = it }, "Flag value", Modifier.fillMaxWidth())
                    Button(onClick = {
                        if (flagValue.isNotBlank()) {
                            applyTypedFlag(flagValue)
                        }
                    }, colors = ButtonDefaults.buttonColors(backgroundColor = surface, contentColor = Color.White), modifier = Modifier.fillMaxWidth()) { Text("Apply value") }
                }
            }
            Text("Plot members", color = Color.White, fontSize = 20.sp)
            val onlinePlayers = state?.getAsJsonArray("players") ?: JsonArray()
            dropdownSelector("Player", selectedPlotMemberName.ifBlank { "Choose an online player" }, onlinePlayers.map { element ->
                val player = element.asJsonObject
                player.get("name").asString to player.get("id").asString
            }, plotMemberMenuExpanded, { plotMemberMenuExpanded = it }) { display, id -> selectedPlotMemberId = id; selectedPlotMemberName = display }
            if (selectedPlotMemberId.isNotBlank()) {
                Text("Selected: $selectedPlotMemberName", color = Color(0xFF5BDBFF))
                controls(surface, listOf("Add member" to "plot-add $selectedPlotMemberId", "Trust member" to "plot-trust $selectedPlotMemberId", "Remove member" to "plot-remove $selectedPlotMemberId", "Deny player" to "plot-deny $selectedPlotMemberId"))
            }
        }
        controls(surface, listOf("Refresh plot details" to "status", "Open build submissions" to "build-showcase"), 4)
    }

    @Composable private fun players(surface: Color) {
        val players = KairuControlBridge.state()?.getAsJsonArray("players")
        sectionTitle("Player administration", "Select an online player, then choose an action.")
        if (players == null) { controls(surface, listOf("Refresh online players" to "players")); return }
        players.forEach { value ->
            val player = value.asJsonObject
            selectionTile(player.get("name").asString, "Currently in ${player.get("world").asString}", selectedPlayerId == player.get("id").asString) { selectedPlayerId = player.get("id").asString; selectedPlayerName = player.get("name").asString }
        }
        if (selectedPlayerId.isNotBlank()) {
            sectionTitle("Selected player: $selectedPlayerName", "Actions are recorded by SMPPlatform.")
            if (KairuControlBridge.can("players")) {
                Text("Player tools", color = Color(0xFF5BDBFF), fontSize = 16.sp)
                controls(surface, listOf(
                "Heal" to "heal $selectedPlayerId", "Feed" to "feed $selectedPlayerId", "Teleport to player" to "teleport $selectedPlayerId",
                "Open ender chest" to "enderchest $selectedPlayerId", "Clear effects" to "clear-effects $selectedPlayerId"))
                Text("Inventory & progression", color = Color(0xFF5BDBFF), fontSize = 16.sp)
                controls(surface, listOf("Clear inventory" to "clear-inventory $selectedPlayerId", "Set XP to 0" to "xp-zero $selectedPlayerId"))
                Text("Game mode", color = Color(0xFF5BDBFF), fontSize = 16.sp)
                controls(surface, listOf(
                "Survival mode" to "gamemode-survival $selectedPlayerId", "Creative mode" to "gamemode-creative $selectedPlayerId",
                "Adventure mode" to "gamemode-adventure $selectedPlayerId", "Spectator mode" to "gamemode-spectator $selectedPlayerId"))
            }
            if (KairuControlBridge.can("moderation")) { Text("Moderation", color = Color(0xFFFF8AA7), fontSize = 16.sp); controls(surface, listOf("Freeze / unfreeze" to "freeze $selectedPlayerId", "Mute" to "mute $selectedPlayerId", "Ban" to "ban $selectedPlayerId")) }
            if (KairuControlBridge.can("kick")) controls(surface, listOf("Kick player" to "kick $selectedPlayerId"))
            if (KairuControlBridge.can("roles")) {
                Text("Permission roles", color = Color(0xFF5BDBFF), fontSize = 16.sp)
                Text("LuckPerms groups are assigned by the server and are audit logged.", color = Color(0xFFAAA7B9), fontSize = 13.sp)
                val roles = KairuControlBridge.state()?.getAsJsonArray("roles") ?: JsonArray()
                controls(surface, roles.flatMap { role ->
                    val name = role.asJsonObject.get("name").asString
                    listOf("Grant $name" to "role-add $selectedPlayerId $name", "Remove $name" to "role-remove $selectedPlayerId $name")
                }, 4)
            }
        }
    }

    @Composable private fun worlds(surface: Color) {
        val worlds = KairuControlBridge.state()?.getAsJsonArray("worlds")
        sectionTitle("World administration", "Select a registered world, then choose a management action.")
        if (worlds == null) { controls(surface, listOf("Refresh registered worlds" to "worlds")); return }
        if (!worldDetail) {
            selectionGrid(worlds.map { value ->
                val world = value.asJsonObject
                val status = buildString { append(if (world.get("loaded").asBoolean) "Loaded" else "Not loaded"); if (world.get("maintenance").asBoolean) append(" • Maintenance") }
                Triple(world.get("name").asString, status, {
                    selectedWorldId = world.get("id").asString; selectedWorldName = world.get("name").asString
                    worldTimeChoice = "Choose time"; worldWeatherChoice = "Choose weather"
                    worldDifficultyChoice = world.get("difficulty")?.asString?.replaceFirstChar { it.uppercase() } ?: "Choose difficulty"
                    worldDetail = true
                })
            }, 4)
        } else if (selectedWorldId.isNotBlank() && KairuControlBridge.can("worlds")) {
            val selected = worlds.firstOrNull { it.asJsonObject.get("id").asString == selectedWorldId }?.asJsonObject
            val protectionEnabled = selected?.get("protection")?.asBoolean == true
            val placedProtection = selected?.get("placedProtection")?.asBoolean == true
            sectionTitle("Selected world: $selectedWorldName", "Changes apply only to this world.")
            Button(onClick = { worldDetail = false }, colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF2A2C3A), contentColor = Color.White)) { Text("Back to worlds") }
            Text("World overview", color = Color(0xFF5BDBFF), fontSize = 16.sp)
            Text("${if (selected?.get("loaded")?.asBoolean == true) "Loaded" else "Not loaded"}  •  ${selected?.get("difficulty")?.asString ?: "No difficulty data"}${if (selected?.get("maintenance")?.asBoolean == true) "  •  Maintenance" else ""}", color = Color(0xFFAAA7B9), fontSize = 13.sp)
            Text("Time, weather & difficulty", color = Color(0xFF5BDBFF), fontSize = 16.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                worldDropdown("Time", worldTimeChoice, listOf("Day" to "day", "Night" to "night"), worldTimeExpanded, { worldTimeExpanded = it }, Modifier.weight(1f)) { display, value -> worldTimeChoice = display; KairuControlBridge.request(value, selectedWorldId) }
                worldDropdown("Weather", worldWeatherChoice, listOf("Clear" to "clear", "Rain" to "rain", "Thunder" to "thunder"), worldWeatherExpanded, { worldWeatherExpanded = it }, Modifier.weight(1f)) { display, value -> worldWeatherChoice = display; KairuControlBridge.request(value, selectedWorldId) }
                worldDropdown("Difficulty", worldDifficultyChoice, listOf("Peaceful" to "peaceful", "Easy" to "easy", "Normal" to "normal", "Hard" to "hard"), worldDifficultyExpanded, { worldDifficultyExpanded = it }, Modifier.weight(1f)) { display, value -> worldDifficultyChoice = display; KairuControlBridge.request("world-difficulty", selectedWorldId, value) }
            }
            Text("Multiverse lifecycle", color = Color(0xFF5BDBFF), fontSize = 16.sp)
            Text("Only registered Kairu worlds are managed here. Loading/unloading stays with Multiverse-Core.", color = Color(0xFFAAA7B9), fontSize = 13.sp)
            controls(surface, listOf("Load world" to "world-load $selectedWorldId", "Teleport there" to "world-teleport $selectedWorldId", "Set spawn here" to "world-set-spawn $selectedWorldId", "Arm unload" to "world-unload-arm $selectedWorldId", "Confirm unload" to "world-unload-confirm $selectedWorldId"), 4)
            Text("Availability", color = Color(0xFF5BDBFF), fontSize = 16.sp)
            controls(surface, listOf("Enable maintenance" to "world-maintenance $selectedWorldId true", "Disable maintenance" to "world-maintenance $selectedWorldId false"), 4)
            Text("World rules", color = Color(0xFF5BDBFF), fontSize = 16.sp)
            controls(surface, listOf("Enable PvP" to "pvp-on $selectedWorldId", "Disable PvP" to "pvp-off $selectedWorldId", "Keep inventory on" to "world-rule $selectedWorldId keep-inventory true", "Keep inventory off" to "world-rule $selectedWorldId keep-inventory false"), 4)
            Text("Build protection", color = Color(0xFF5BDBFF), fontSize = 16.sp)
            Text("Claims are a 65×65 default square (radius 32). ${if (protectionEnabled) "Claims are enabled." else "Claims are disabled."}", color = Color(0xFFAAA7B9), fontSize = 13.sp)
            controls(surface, listOf(
                if (protectionEnabled) "Disable claims" to "world-protection $selectedWorldId false" else "Enable claims" to "world-protection $selectedWorldId true",
                if (placedProtection) "Disable placed-block protection" to "world-placed-protection $selectedWorldId false" else "Enable placed-block protection" to "world-placed-protection $selectedWorldId true"
            ), 4)
            if (selectedWorldId == "quarry" && KairuControlBridge.can("quarry")) {
                Text("Quarry reset", color = Color(0xFFFFC36B), fontSize = 16.sp)
                Text("A reset evacuates players to Spawn Hub and recreates a normal resource world.", color = Color(0xFFAAA7B9), fontSize = 13.sp)
                controls(surface, listOf("Arm Quarry reset" to "quarry-reset-arm", "Confirm Quarry reset" to "quarry-reset-confirm"), 4)
            }
            if (KairuControlBridge.can("monitor")) {
                Text("Server controls", color = Color(0xFF5BDBFF), fontSize = 16.sp)
                controls(surface, listOf("Save all worlds" to "server-save", "Enable whitelist" to "server-whitelist true", "Disable whitelist" to "server-whitelist false"), 4)
            }
        }
    }

    @Composable private fun sectionTitle(title: String, detail: String) {
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        Text(detail, color = Color(0xFFAAA7B9), fontSize = 13.sp)
    }

    @Composable private fun selectionTile(label: String, detail: String, selected: Boolean = false, onClick: () -> Unit) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(backgroundColor = if (selected) Color(0xFF504B65) else Color(0xFF20222D), contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(58.dp)
        ) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(detail, color = Color(0xFFC6C4D2), fontSize = 12.sp)
            }
        }
    }

    @Composable private fun dashboardTile(title: String, detail: String, onClick: () -> Unit, modifier: Modifier) {
        Button(onClick = onClick, colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF20222D), contentColor = Color.White), modifier = modifier.height(92.dp)) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Text(detail, color = Color(0xFFAAA7B9), fontSize = 12.sp)
            }
        }
    }

    @Composable private fun profileCard(profile: com.google.gson.JsonObject, surface: Color) {
        val name = profile.get("name")?.asString ?: "Player"
        val world = profile.get("world")?.asString ?: "Unknown world"
        val mode = profile.get("gamemode")?.asString ?: "Unknown mode"
        val health = profile.get("health")?.asString ?: "—"
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            dashboardTile(name, "Currently in $world", {}, Modifier.weight(1f))
            dashboardTile("$health health", "$mode", {}, Modifier.weight(1f))
        }
    }

    @Composable private fun choiceControl(label: String, options: List<Any>, onSelected: (String) -> Unit) {
        val entries = options.map { if (it is Pair<*, *>) it.first.toString() to it.second.toString() else it.toString() to it.toString() }
        Column(Modifier.fillMaxWidth()) {
            Button(onClick = { flagMenuExpanded = true }, colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF20222D), contentColor = Color.White), modifier = Modifier.fillMaxWidth().height(54.dp)) {
                Text(if (selectedFlagChoice.isBlank()) "Choose $label" else selectedFlagChoice)
            }
            DropdownMenu(expanded = flagMenuExpanded, onDismissRequest = { flagMenuExpanded = false }) {
                entries.forEach { (display, value) -> DropdownMenuItem(onClick = { flagMenuExpanded = false; selectedFlagChoice = display; onSelected(value) }) { Text(display) } }
            }
        }
    }

    /** A single selector keeps the Atrium editor in view instead of a long card list. */
    @Composable private fun dropdownSelector(label: String, selected: String, entries: List<Pair<String, String>>, expanded: Boolean, setExpanded: (Boolean) -> Unit, onSelected: (String, String) -> Unit) {
        Column(Modifier.fillMaxWidth()) {
            Text(label, color = Color(0xFFC6C4D2), fontSize = 12.sp)
            Button(onClick = { setExpanded(true) }, colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF20222D), contentColor = Color.White), modifier = Modifier.fillMaxWidth().height(54.dp)) { Text(selected) }
            DropdownMenu(expanded = expanded, onDismissRequest = { setExpanded(false) }) {
                entries.forEach { (display, value) -> DropdownMenuItem(onClick = { setExpanded(false); onSelected(display, value) }) { Text(display) } }
            }
        }
    }

    /** Compact control for the three most common world settings; avoids a full-screen stack of fields. */
    @Composable private fun worldDropdown(label: String, selected: String, entries: List<Pair<String, String>>, expanded: Boolean, setExpanded: (Boolean) -> Unit, modifier: Modifier, onSelected: (String, String) -> Unit) {
        Column(modifier) {
            Text(label, color = Color(0xFFC6C4D2), fontSize = 12.sp)
            Button(onClick = { setExpanded(true) }, colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF20222D), contentColor = Color.White), modifier = Modifier.fillMaxWidth().height(46.dp)) { Text(selected) }
            DropdownMenu(expanded = expanded, onDismissRequest = { setExpanded(false) }) {
                entries.forEach { (display, value) -> DropdownMenuItem(onClick = { setExpanded(false); onSelected(display, value) }) { Text(display) } }
            }
        }
    }

    private fun applyTypedFlag(value: String) {
        val name = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(selectedFlagId.toByteArray(Charsets.UTF_8))
        val encodedValue = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))
        KairuControlBridge.request("plot-flag-value", name, encodedValue)
    }

    /** Four-column cards keep travel, worlds, flags, and member selection compact and scannable. */
    @Composable private fun selectionGrid(items: List<Triple<String, String, () -> Unit>>, columns: Int = 4) {
        items.filter { searchQuery.isBlank() || it.first.contains(searchQuery, ignoreCase = true) }.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (title, detail, onClick) ->
                    Button(onClick = onClick, colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF20222D), contentColor = Color.White), modifier = Modifier.weight(1f).height(82.dp)) {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(detail, color = Color(0xFFAAA7B9), fontSize = 11.sp)
                        }
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }

    @Composable private fun kairuField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier) {
        TextField(
            value, onChange, label = { Text(label) }, modifier = modifier,
            colors = TextFieldDefaults.textFieldColors(textColor = Color.White, backgroundColor = Color(0xFF171822), cursorColor = Color(0xFF5BDBFF), focusedIndicatorColor = Color(0xFF5BDBFF), unfocusedIndicatorColor = Color(0xFF383A4A), focusedLabelColor = Color(0xFF5BDBFF), unfocusedLabelColor = Color(0xFFC6C4D2))
        )
    }

    @Composable private fun wizardButtons(backLabel: String, back: () -> Unit, nextLabel: String, next: () -> Unit, surface: Color) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = back, colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF2A2C3A), contentColor = Color.White), modifier = Modifier.weight(1f).height(52.dp)) { Text(backLabel) }
            Button(onClick = next, colors = ButtonDefaults.buttonColors(backgroundColor = surface, contentColor = Color.White), modifier = Modifier.weight(1f).height(52.dp)) { Text(nextLabel) }
        }
    }

    @Composable private fun controls(surface: Color, actions: List<Pair<String, String>>, columns: Int = 2) {
        val visible = actions.filter { searchQuery.isBlank() || it.first.contains(searchQuery, ignoreCase = true) }
        visible.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (label, action) ->
                    Button(
                        onClick = { KairuControlBridge.request(*action.split(" ").toTypedArray()) },
                        colors = ButtonDefaults.buttonColors(backgroundColor = surface, contentColor = Color.White),
                        modifier = Modifier.weight(1f).height(62.dp)
                    ) { Text(label) }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (visible.isEmpty() && searchQuery.isNotBlank()) Text("No actions match \"$searchQuery\".", color = Color(0xFFAAA7B9))
    }

    private fun playerName(id: String): String {
        val players = KairuControlBridge.state()?.getAsJsonArray("players") ?: return id.take(8)
        return players.firstOrNull { it.asJsonObject.get("id").asString == id }?.asJsonObject?.get("name")?.asString ?: id.take(8)
    }

    private fun pageFor(section: String): String = when (section.lowercase()) {
        "guilds" -> "Guilds"; "points" -> "Points"; "claims" -> "Claims"; "auction" -> "Auction"; "search" -> "Search"; "atrium", "plots" -> "Atrium"; "players" -> "Players"; "worlds" -> "Worlds"; else -> "Player"
    }
}
