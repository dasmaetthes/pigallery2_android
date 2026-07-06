import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

fun main() {
    val moshi = Moshi.Builder().build()
    val mapType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    val adapter = moshi.adapter<Map<String, Any?>>(mapType)
    
    val query = mapOf(
        "type" to 60.0,
        "daysLength" to 7.0,
        "frequency" to 3.0
    )
    println("SERIALIZED: " + adapter.toJson(query))
}
