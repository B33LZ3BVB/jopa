package app.diet

enum class Meal(val ru: String) { B("Завтрак"), L("Обед"), D("Ужин") }

data class Dish(
    val name: String, val meals: Set<Meal>, val items: List<String>,
    val iron: Boolean = false, val vitC: Boolean = false, val min: Int = 20, val ahead: Boolean = false
)

object Dishes {
    private val fb = setOf(Meal.B)
    private val ld = setOf(Meal.L, Meal.D)
    private val lo = setOf(Meal.L)
    private val dn = setOf(Meal.D)

    // Добавляй свои блюда сюда
    val all = listOf(
        Dish("Овсянка с тыквенными семечками и яблоком", fb, listOf("овсянка", "тыквенные семечки", "яблоко", "молоко"), iron = true, min = 10),
        Dish("Омлет со шпинатом и помидором", fb, listOf("яйца", "шпинат", "помидоры"), iron = true, vitC = true, min = 15),
        Dish("Творог с ягодами и орехами", fb, listOf("творог", "ягоды", "грецкие орехи"), min = 5),
        Dish("Бутерброды с печёночным паштетом и перцем", fb, listOf("хлеб", "печёночный паштет", "болгарский перец"), iron = true, vitC = true, min = 10),
        Dish("Гречневая каша с яйцом и огурцом", fb + lo, listOf("гречка", "яйца", "огурцы"), iron = true, min = 20),
        Dish("Чечевичный суп с лимоном", lo, listOf("чечевица", "морковь", "лук", "лимон"), iron = true, vitC = true, min = 35, ahead = true),
        Dish("Гречка с говядиной и перцем", ld, listOf("гречка", "говядина", "болгарский перец", "лук"), iron = true, vitC = true, min = 40),
        Dish("Борщ с говядиной", lo, listOf("свёкла", "капуста", "говядина", "картофель", "лук"), iron = true, vitC = true, min = 60, ahead = true),
        Dish("Салат с фасолью и перцем", ld, listOf("фасоль", "болгарский перец", "лук", "зелень"), iron = true, vitC = true, min = 15),
        Dish("Куриный суп с лапшой", lo, listOf("куриное филе", "лапша", "морковь", "лук"), min = 40, ahead = true),
        Dish("Плов с курицей", ld, listOf("рис", "куриное филе", "морковь", "лук"), min = 50, ahead = true),
        Dish("Куриная печень с луком и гречкой", dn, listOf("куриная печень", "лук", "гречка"), iron = true, min = 25),
        Dish("Паста с томатами и фасолью", ld, listOf("макароны", "томаты", "фасоль", "лук"), iron = true, vitC = true, min = 25),
        Dish("Рыба с рисом и брокколи", dn, listOf("филе рыбы", "рис", "брокколи"), vitC = true, min = 30),
        Dish("Тушёная чечевица с овощами", ld, listOf("чечевица", "морковь", "лук", "помидоры"), iron = true, vitC = true, min = 30, ahead = true),
        Dish("Курица с картофелем и квашеной капустой", dn, listOf("куриное филе", "картофель", "капуста"), vitC = true, min = 45, ahead = true),
        Dish("Гуляш из говядины с картофелем", dn, listOf("говядина", "картофель", "лук"), iron = true, min = 60, ahead = true),
    )
    val byName = all.associateBy { it.name }

    val subs = mapOf(
        "чечевица" to "фасоль", "фасоль" to "чечевица", "говядина" to "куриная печень",
        "куриная печень" to "говядина", "шпинат" to "зелень", "гречка" to "рис",
        "тыквенные семечки" to "грецкие орехи"
    )
}

object MenuGen {
    fun generate(days: Int, maxMin: Int, avoid: List<String>, ironMin: Int): List<String> {
        val out = mutableListOf<String>()
        repeat(days) { Meal.values().forEach { m -> out += pick(m, out, maxMin, avoid, false, null) } }
        var iron = out.count { Dishes.byName[it]?.iron == true }
        var guard = 0
        while (iron < ironMin && guard++ < 40) {
            val i = out.indices.filter { it % 3 != 0 && Dishes.byName[out[it]]?.iron != true }.randomOrNull() ?: break
            val n = pick(Meal.values()[i % 3], out, maxMin, avoid, true, null)
            if (Dishes.byName[n]?.iron == true) { out[i] = n; iron++ }
        }
        return out
    }

    fun replace(menu: List<String>, i: Int, maxMin: Int, avoid: List<String>): List<String> =
        menu.toMutableList().also { it[i] = pick(Meal.values()[i % 3], menu, maxMin, avoid, false, menu[i]) }

    private fun pick(m: Meal, used: List<String>, maxMin: Int, avoid: List<String>, ironOnly: Boolean, exclude: String?): String {
        var c = Dishes.all.filter { d ->
            m in d.meals && d.name != exclude && d.min <= maxMin && avoid.none { a -> d.items.any { it.contains(a, true) } }
        }
        if (ironOnly) c = c.filter { it.iron }.ifEmpty { c }
        if (c.isEmpty()) c = Dishes.all.filter { m in it.meals && it.name != exclude }
        val recent = used.takeLast(6)
        val score = { d: Dish -> used.count { it == d.name } + if (d.name in recent) 3 else 0 }
        val best = c.minOf(score)
        return c.filter { score(it) == best }.random().name
    }
}
