import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

fun main() {
    val moshi = Moshi.Builder().build()
    val mapType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    val adapter = moshi.adapter<Map<String, Any?>>(mapType)
    
    val query = mapOf(
        "type" to 60,
        "daysLength" to 7,
        "frequency" to 3
    )
    println(adapter.toJson(query))
}
