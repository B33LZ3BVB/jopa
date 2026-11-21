package app.diet

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.work.*
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "prices", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<PriceWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        )
        val store = Store(applicationContext)
        setContent { MaterialTheme { Surface { App(store) } } }
    }
}

@Composable
fun App(s: Store) {
    var tab by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(s.menu) }
    var have by remember { mutableStateOf(s.have) }
    var eaten by remember { mutableStateOf(s.eaten) }
    var tracked by remember { mutableStateOf(s.tracked()) }
    Scaffold(bottomBar = {
        NavigationBar {
            listOf("Меню", "Покупки", "Цены").forEachIndexed { i, t ->
                NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = {}, label = { Text(t) })
            }
        }
    }) { pad ->
        Box(Modifier.padding(pad).padding(12.dp)) {
            when (tab) {
                0 -> MenuTab(s, menu, eaten, { menu = it }, { eaten = it })
                1 -> ShopTab(s, menu, have, tracked) { have = it }
                else -> PricesTab(s, tracked) { tracked = it }
            }
        }
    }
}

@Composable
fun MenuTab(s: Store, menu: List<String>, eaten: Set<String>, onMenu: (List<String>) -> Unit, onEaten: (Set<String>) -> Unit) {
    var days by remember { mutableFloatStateOf(5f) }
    var maxMin by remember { mutableFloatStateOf(30f) }
    var iron by remember { mutableFloatStateOf(3f) }
    var avoid by remember { mutableStateOf("") }
    val av = avoid.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Дней: ${days.toInt()}")
            Slider(days, { days = it }, valueRange = 3f..7f, steps = 3)
            Text("Готовка до ${maxMin.toInt()} мин")
            Slider(maxMin, { maxMin = it }, valueRange = 15f..60f, steps = 8)
            Text("Блюд с железом в меню: ${iron.toInt()}")
            Slider(iron, { iron = it }, valueRange = 0f..8f, steps = 7)
            OutlinedTextField(avoid, { avoid = it }, label = { Text("Не ем (через запятую)") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                val m = MenuGen.generate(days.toInt(), maxMin.toInt(), av, iron.toInt())
                s.menu = m; s.eaten = emptySet(); onMenu(m); onEaten(emptySet())
            }) { Text("Составить меню") }
            if (menu.isNotEmpty()) Text("Съедено по плану: ${eaten.size} из ${menu.size}")
        }
        itemsIndexed(menu) { i, name ->
            val d = Dishes.byName[name]
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(eaten.contains("$i"), { c ->
                    val e = if (c) eaten + "$i" else eaten - "$i"
                    s.eaten = e; onEaten(e)
                })
                Column(Modifier.weight(1f)) {
                    Text("День ${i / 3 + 1} · ${Meal.values()[i % 3].ru}", style = MaterialTheme.typography.labelSmall)
                    Text(name, fontWeight = FontWeight.Bold)
                    if (d != null) Text(
                        listOfNotNull(
                            "${d.min} мин", if (d.ahead) "можно заранее" else null, if (d.iron) "железо" else null,
                            if (d.iron && !d.vitC) "добавь лимон или перец, не запивай чаем" else null
                        ).joinToString(" · "), style = MaterialTheme.typography.labelSmall
                    )
                }
                TextButton(onClick = {
                    val m = MenuGen.replace(menu, i, maxMin.toInt(), av); s.menu = m; onMenu(m)
                }) { Text("Заменить") }
            }
        }
    }
}

private data class Need(val item: String, val n: Int, val best: Tracked?, val seen: Boolean)

@Composable
fun ShopTab(s: Store, menu: List<String>, have: Set<String>, tracked: List<Tracked>, onHave: (Set<String>) -> Unit) {
    val rows = menu.flatMap { Dishes.byName[it]?.items ?: emptyList() }
        .groupingBy { it }.eachCount().toList().sortedBy { it.first }.map { (item, n) ->
            val of = tracked.filter { it.name.contains(item, true) }
            Need(item, n, of.filter { it.price != null && it.inStock != false }.minByOrNull { it.price!! }, of.isNotEmpty())
        }
    val todo = rows.filter { it.item !in have }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            if (rows.isEmpty()) Text("Сначала составь меню")
            else {
                Text("Примерно по найденным ценам: ${"%.0f".format(todo.sumOf { it.best?.price ?: 0.0 })} (по одной упаковке)", fontWeight = FontWeight.Bold)
                todo.filter { it.best != null }.groupBy { it.best!!.shop }.forEach { (shop, l) ->
                    Text("$shop: ${l.size} поз., ${"%.0f".format(l.sumOf { it.best!!.price!! })}")
                }
            }
        }
        items(rows) { r ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(r.item in have, { c ->
                    val h = if (c) have + r.item else have - r.item
                    s.have = h; onHave(h)
                })
                Column {
                    Text("${r.item} ×${r.n}" + if (r.item in have) " (есть дома)" else "")
                    Text(
                        when {
                            r.best != null -> "${r.best.price} · ${r.best.shop}"
                            r.seen -> "нет в наличии" + (Dishes.subs[r.item]?.let { " → замена: $it" } ?: "")
                            else -> "не отслеживается"
                        }, style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
fun PricesTab(s: Store, tracked: List<Tracked>, onTracked: (List<Tracked>) -> Unit) {
    val ctx = LocalContext.current.applicationContext
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var shop by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            OutlinedTextField(name, { name = it }, label = { Text("Товар (с продуктом из меню, напр. «Гречка 800 г»)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(url, { url = it }, label = { Text("Ссылка на товар в магазине") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(shop, { shop = it }, label = { Text("Магазин") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    if (name.isNotBlank() && url.trim().startsWith("http")) {
                        val l = tracked + Tracked(name.trim(), url.trim(), shop.trim())
                        s.saveTracked(l); onTracked(l); name = ""; url = ""
                    }
                }) { Text("Добавить") }
                OutlinedButton(enabled = !busy, onClick = {
                    busy = true
                    Thread { PriceCheck.run(ctx); onTracked(s.tracked()); busy = false }.start()
                }) { Text(if (busy) "Проверяю…" else "Проверить сейчас") }
            }
            Text("Фоновая проверка идёт раз в ~6 часов", style = MaterialTheme.typography.labelSmall)
        }
        items(tracked) { t ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${t.name} · ${t.shop}", fontWeight = FontWeight.Bold)
                    Text(
                        (t.price?.let { "$it" + (t.prev?.let { p -> " (было $p)" } ?: "") } ?: "цена не найдена") + " · " +
                            when (t.inStock) { true -> "в наличии"; false -> "нет в наличии"; null -> "наличие неизвестно" }
                    )
                }
                TextButton(onClick = { val l = tracked - t; s.saveTracked(l); onTracked(l) }) { Text("Удалить") }
            }
        }
    }
}
