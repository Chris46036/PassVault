package com.passvault.app.util

import java.security.SecureRandom

object PasswordGenerator {

    private val random = SecureRandom()

    private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val DIGITS = "0123456789"
    private const val SYMBOLS = "!@#\$%^&*()-_=+[]{};:,.<>?/"
    private const val AMBIGUOUS = "Il1O0o|`'\""

    fun generate(
        length: Int,
        useLower: Boolean = true,
        useUpper: Boolean = true,
        useDigits: Boolean = true,
        useSymbols: Boolean = true,
        excludeAmbiguous: Boolean = false,
    ): String {
        val sets = buildList {
            if (useLower) add(LOWER)
            if (useUpper) add(UPPER)
            if (useDigits) add(DIGITS)
            if (useSymbols) add(SYMBOLS)
        }.map { set ->
            if (excludeAmbiguous) set.filter { it !in AMBIGUOUS } else set
        }.filter { it.isNotEmpty() }
        if (sets.isEmpty()) return ""

        val pool = sets.joinToString("")
        val chars = CharArray(length)
        // Garantiza al menos un carácter de cada clase seleccionada
        sets.forEachIndexed { i, set ->
            if (i < length) chars[i] = set[random.nextInt(set.length)]
        }
        for (i in sets.size.coerceAtMost(length) until length) {
            chars[i] = pool[random.nextInt(pool.length)]
        }
        // Mezcla Fisher-Yates
        for (i in chars.indices.reversed()) {
            val j = random.nextInt(i + 1)
            val t = chars[i]; chars[i] = chars[j]; chars[j] = t
        }
        return String(chars)
    }

    private val WORDS = listOf(
        "abismo", "acero", "aguila", "alamo", "alga", "alondra", "ambar", "ancla",
        "andes", "anillo", "antena", "arce", "arena", "armonia", "aroma", "arroyo",
        "astro", "atlas", "aurora", "avena", "azafran", "azul", "bahia", "balcon",
        "bambu", "barco", "bosque", "brisa", "bruma", "cabra", "cactus", "cafe",
        "caliza", "camino", "campo", "canela", "canto", "caoba", "carbon", "castor",
        "cedro", "cenit", "cielo", "ciervo", "cifra", "cobre", "colina", "cometa",
        "condor", "coral", "corona", "cristal", "cuarzo", "cumbre", "delfin", "delta",
        "diamante", "duna", "ebano", "eclipse", "enebro", "esfera", "espuma", "estepa",
        "faro", "fresno", "fuego", "fuente", "galaxia", "garza", "geiser", "girasol",
        "glaciar", "granito", "halcon", "helecho", "hielo", "hierro", "horizonte", "huerto",
        "imán", "isla", "jade", "jaguar", "jardin", "jazmin", "junco", "laguna",
        "lapiz", "laurel", "lava", "lince", "lirio", "llama", "llanura", "loto",
        "lucero", "luna", "madera", "magma", "manantial", "marfil", "marea", "matiz",
        "meseta", "mirlo", "montaña", "musgo", "nacar", "nevada", "niebla", "nogal",
        "nube", "oasis", "obsidiana", "ocaso", "olivo", "onda", "orquidea", "oso",
        "palma", "pampa", "panal", "paramo", "perla", "pinar", "plata", "playa",
        "pluma", "polen", "prado", "puma", "quasar", "rayo", "relampago", "rio",
        "roble", "rocio", "rubi", "salvia", "sauce", "selva", "sendero", "sierra",
        "silex", "sol", "sombra", "tigre", "topacio", "tornado", "trebol", "trueno",
        "tulipan", "valle", "vela", "viento", "violeta", "volcan", "zafiro", "zorro",
    )

    fun generatePassphrase(
        wordCount: Int,
        separator: String = "-",
        capitalize: Boolean = true,
        addNumber: Boolean = true,
    ): String {
        val words = (1..wordCount).map {
            val w = WORDS[random.nextInt(WORDS.size)]
            if (capitalize) w.replaceFirstChar { c -> c.uppercase() } else w
        }.toMutableList()
        if (addNumber && words.isNotEmpty()) {
            val i = random.nextInt(words.size)
            words[i] = words[i] + random.nextInt(100)
        }
        return words.joinToString(separator)
    }
}
