val query = "person:\"Alvin the Squirrel\" and (some other)"
val tokens = Regex("""(?:[^\s"()]|"[^"]*"|\([^)]*\))+""").findAll(query).map { it.value }.toList()
println(tokens)
