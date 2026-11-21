package app.diet

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Tracked(
    val name: String, val url: String, val shop: String,
    val price: Double? = null, val prev: Double? = null, val inStock: Boolean? = null
) {
    fun toJson(): JSONObject = JSONObject().put("name", name).put("url", url).put("shop", shop)
        .put("price", price ?: JSONObject.NULL).put("prev", prev ?: JSONObject.NULL)
        .put("stock", inStock ?: JSONObject.NULL)

    companion object {
        fun from(o: JSONObject) = Tracked(
            o.getString("name"), o.getString("url"), o.getString("shop"),
            o.optDouble("price").takeIf { !it.isNaN() }, o.optDouble("prev").takeIf { !it.isNaN() },
            if (o.isNull("stock")) null else o.getBoolean("stock")
        )
    }
}

class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("diet", Context.MODE_PRIVATE)
    private fun list(k: String): List<String> {
        val a = JSONArray(p.getString(k, "[]")); return List(a.length()) { a.getString(it) }
    }
    private fun put(k: String, v: Collection<String>) { p.edit().putString(k, JSONArray(v).toString()).apply() }

    var menu: List<String>
        get() = list("menu")
        set(v) = put("menu", v)
    var have: Set<String>
        get() = list("have").toSet()
        set(v) = put("have", v)
    var eaten: Set<String>
        get() = list("eaten").toSet()
        set(v) = put("eaten", v)

    fun tracked(): List<Tracked> {
        val a = JSONArray(p.getString("tracked", "[]")); return List(a.length()) { Tracked.from(a.getJSONObject(it)) }
    }
    fun saveTracked(l: List<Tracked>) {
        p.edit().putString("tracked", JSONArray(l.map { it.toJson() }).toString()).apply()
    }
}
