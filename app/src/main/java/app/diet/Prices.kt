package app.diet

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.jsoup.Jsoup

class PriceWorker(c: Context, p: WorkerParameters) : Worker(c, p) {
    override fun doWork(): Result { PriceCheck.run(applicationContext); return Result.success() }
}

object PriceCheck {
    fun run(ctx: Context) {
        val s = Store(ctx)
        val updated = HashMap<String, Tracked>()
        for (t in s.tracked()) {
            try {
                val (pr, st) = fetch(t.url)
                notifyIfNeeded(ctx, t, pr, st)
                updated[t.url] = t.copy(
                    prev = if (pr != null && pr != t.price) t.price else t.prev,
                    price = pr ?: t.price, inStock = st ?: t.inStock
                )
            } catch (e: Exception) { /* сайт недоступен или заблокировал — пропускаем */ }
        }
        s.saveTracked(s.tracked().map { updated[it.url] ?: it })
    }

    private fun notifyIfNeeded(ctx: Context, t: Tracked, pr: Double?, st: Boolean?) {
        val msg = when {
            st == true && t.inStock == false -> "«${t.name}» снова в наличии (${t.shop})"
            pr != null && t.price != null && pr <= t.price * 0.95 -> "«${t.name}» подешевел: ${t.price} → $pr (${t.shop})"
            else -> return
        }
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("prices", "Цены и наличие", NotificationManager.IMPORTANCE_DEFAULT))
        nm.notify(
            t.url.hashCode(),
            NotificationCompat.Builder(ctx, "prices").setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Диета").setContentText(msg).build()
        )
    }

    // Цена и наличие берутся из schema.org (JSON-LD / meta), их отдаёт большинство магазинов
    fun fetch(url: String): Pair<Double?, Boolean?> {
        val doc = Jsoup.connect(url)
            .userAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126.0 Mobile Safari/537.36")
            .timeout(15000).get()
        var price: Double? = null
        var stock: Boolean? = null
        fun num(v: Any?) = v?.toString()?.replace("\\s".toRegex(), "")?.replace(',', '.')?.toDoubleOrNull()
        fun avail(v: String?): Boolean? = when {
            v == null -> null
            v.contains("InStock") -> true
            v.contains("OutOfStock") || v.contains("SoldOut") -> false
            else -> null
        }
        fun walk(n: Any?) {
            when (n) {
                is JSONObject -> {
                    n.keys().forEach { k ->
                        val v = n.opt(k)
                        if (price == null && (k == "price" || k == "lowPrice")) price = num(v)
                        if (stock == null && k == "availability") stock = avail(v?.toString())
                        walk(v)
                    }
                }
                is JSONArray -> { for (i in 0 until n.length()) walk(n.opt(i)) }
            }
        }
        doc.select("script[type=application/ld+json]").forEach {
            try { walk(JSONTokener(it.data()).nextValue()) } catch (e: Exception) { }
        }
        if (price == null) price = num(doc.selectFirst("meta[itemprop=price], meta[property=product:price:amount]")?.attr("content"))
        if (stock == null) stock = avail(doc.selectFirst("meta[itemprop=availability], link[itemprop=availability]")?.let { it.attr("content").ifEmpty { it.attr("href") } })
        return price to stock
    }
}
