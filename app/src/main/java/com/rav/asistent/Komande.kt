package com.rav.asistent

enum class TipJacine { GORE, DOLE, ISKLJUCI, UKLJUCI, POSTAVI }
enum class TipMedija { PUSTI, PAUZA, SLEDECA, PRETHODNA }

sealed class Akcija {
    data class Navigacija(val cilj: String) : Akcija()
    object Kuci : Akcija()
    object NaPosao : Akcija()
    object PonoviRutu : Akcija()
    data class Pretraga(val upit: String) : Akcija()
    data class Jacina(val tip: TipJacine, val vrednost: Int = 1) : Akcija()
    data class Medij(val tip: TipMedija) : Akcija()
    data class PustiPesmu(val upit: String) : Akcija()
    data class Poruka(val tekst: String) : Akcija()
    data class Nepoznato(val tekst: String) : Akcija()

    fun opis(): String = when (this) {
        is Navigacija -> "navigacija do $cilj"
        is Kuci -> "navigacija kući"
        is NaPosao -> "navigacija na posao"
        is PonoviRutu -> "ponovna provera rute"
        is Pretraga -> "pretraga: $upit"
        is Jacina -> "jačina: $tip $vrednost"
        is Medij -> "muzika: $tip"
        is PustiPesmu -> "pusti: $upit"
        is Poruka -> "poruka"
        is Nepoznato -> "nepoznata komanda"
    }
}

data class Rec(val orig: String, val norm: String)

/** Pretvara ćirilicu u latinicu i skida kvačice, da bi komande radile bez obzira na pismo. */
object Tekst {
    private val cirilica = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'ђ' to "đ", 'е' to "e",
        'ж' to "ž", 'з' to "z", 'и' to "i", 'ј' to "j", 'к' to "k", 'л' to "l", 'љ' to "lj",
        'м' to "m", 'н' to "n", 'њ' to "nj", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s",
        'т' to "t", 'ћ' to "ć", 'у' to "u", 'ф' to "f", 'х' to "h", 'ц' to "c", 'ч' to "č",
        'џ' to "dž", 'ш' to "š"
    )
    private val kvacice = mapOf('č' to "c", 'ć' to "c", 'ž' to "z", 'š' to "s", 'đ' to "dj")

    fun uLatinicu(s: String): String {
        val sb = StringBuilder()
        for (c in s.lowercase()) sb.append(cirilica[c] ?: c.toString())
        return sb.toString()
    }

    fun normalizuj(s: String): String {
        val sb = StringBuilder()
        for (c in uLatinicu(s)) sb.append(kvacice[c] ?: c.toString())
        return sb.toString().replace(Regex("[^a-z0-9]+"), " ").trim()
    }

    fun reci(s: String): List<Rec> = uLatinicu(s).split(Regex("\\s+")).mapNotNull { w ->
        val o = w.trim { !it.isLetterOrDigit() }
        val n = normalizuj(o).replace(" ", "")
        if (n.isEmpty()) null else Rec(o, n)
    }
}

object Komande {
    private val brojevi = mapOf(
        "nula" to 0, "jedan" to 1, "jedna" to 1, "dva" to 2, "dve" to 2, "dvije" to 2, "tri" to 3,
        "cetiri" to 4, "pet" to 5, "sest" to 6, "sedam" to 7, "osam" to 8, "devet" to 9,
        "deset" to 10, "jedanaest" to 11, "dvanaest" to 12, "trinaest" to 13, "cetrnaest" to 14,
        "petnaest" to 15, "sesnaest" to 16, "sedamnaest" to 17, "osamnaest" to 18,
        "devetnaest" to 19, "dvadeset" to 20, "trideset" to 30, "maksimum" to 100, "max" to 100,
        "pola" to -1
    )
    private val popuna = setOf("me", "nas", "mi", "nam", "molim", "te", "se", "malo", "neku", "neki", "neko", "nesto", "jednu")
    private val predlozi = setOf("do", "u", "na", "za", "ka", "prema")
    private val genericko = setOf("muziku", "muzika", "pesmu", "pjesmu", "nesto", "opet", "radio", "plejer")

    /** Google vraća više mogućih tumačenja govora; uzimamo prvo koje je prepoznata komanda. */
    fun izaberi(kandidati: List<String>): Pair<String, Akcija> {
        for (k in kandidati) {
            val a = prepoznaj(k)
            if (a !is Akcija.Nepoznato) return Pair(k, a)
        }
        val prvi = kandidati.firstOrNull() ?: ""
        return Pair(prvi, Akcija.Nepoznato(prvi))
    }

    fun prepoznaj(tekst: String): Akcija {
        val r = Tekst.reci(tekst)
        if (r.isEmpty()) return Akcija.Nepoznato(tekst)
        val n = r.map { it.norm }

        fun ima(vararg koreni: String) = n.any { w -> koreni.any { k -> w.startsWith(k) } }
        fun indeks(vararg koreni: String) = n.indexOfFirst { w -> koreni.any { k -> w.startsWith(k) } }
        fun preskoci(od: Int): Int {
            var i = od
            while (i < n.size && n[i] in popuna) i++
            return i
        }
        fun ostatak(od: Int) = r.drop(od).joinToString(" ") { it.orig }.trim()

        val imaDo = n.contains("do")
        val muzicko = ima("pesm", "pjesm", "numer", "muzik", "stanic", "radio")

        // 1. Ponovo proveri rutu / najbrža ruta
        if (!imaDo && (ima("preracun", "osvez", "azurir", "guzv") ||
                    (ima("rut", "put", "navig") && ima("prover", "ponov", "opet", "najbrz", "brz", "bolj")))
        ) return Akcija.PonoviRutu

        // 2. Gašenje navigacije ne može spolja
        if (ima("navig") && ima("zaustav", "prekin", "ugas", "otkaz", "stop", "iskljuc"))
            return Akcija.Poruka("Navigaciju ugasi na ekranu, Google mape to ne dozvoljavaju drugim aplikacijama.")

        // 3. Navigacija: "vodi me do...", "navigacija do...", "idemo u..."
        val nav = indeks("vodi", "odvedi", "navig", "idemo", "idi", "vozi", "rut")
        if (nav >= 0 && !muzicko) {
            var i = preskoci(nav + 1)
            if (i < n.size && n[i] in predlozi) i++
            val cilj = n.drop(i)
            if (cilj.any { it.startsWith("kuc") }) return Akcija.Kuci
            if (cilj.any { it == "posao" || it.startsWith("posl") }) return Akcija.NaPosao
            if (cilj.isNotEmpty()) return Akcija.Navigacija(ostatak(i))
        }
        if (!muzicko && n.size <= 3) {
            if (ima("kuc")) return Akcija.Kuci
            if (ima("posao", "poslu", "posla")) return Akcija.NaPosao
        }

        // 4. Pretraga u blizini: "najbliža pumpa", "nađi apoteku"
        val tr = indeks("najbliz", "nadji", "pronadj", "potraz", "trazi")
        if (tr >= 0 && !muzicko) {
            var i = preskoci(tr + 1)
            if (i < n.size && n[i].startsWith("najbliz")) i = preskoci(i + 1)
            if (i < n.size) {
                val prva = n[i]
                val upit = when {
                    prva.startsWith("pump") || prva.startsWith("benzin") -> "benzinska pumpa"
                    prva.startsWith("apotek") -> "apoteka"
                    prva.startsWith("bankomat") -> "bankomat"
                    prva.startsWith("parking") -> "parking"
                    prva.startsWith("restoran") -> "restoran"
                    prva.startsWith("kafic") || prva.startsWith("kafan") -> "kafić"
                    prva.startsWith("prodavnic") || prva.startsWith("market") -> "prodavnica"
                    prva.startsWith("vulkan") -> "vulkanizer"
                    prva.startsWith("punjac") -> "punjač za električne automobile"
                    else -> ostatak(i)
                }
                return Akcija.Pretraga(upit)
            }
        }

        // 5. Jačina zvuka
        val broj = n.firstNotNullOfOrNull { it.toIntOrNull() ?: brojevi[it] }
        if (broj != null && ima("jacin", "glasn", "zvuk", "volum", "pojac", "smanj", "ton"))
            return Akcija.Jacina(TipJacine.POSTAVI, broj)
        if (ima("mute", "mjut") || (ima("iskljuc", "ugas", "utisa") && ima("zvuk", "ton", "skroz", "potpun")))
            return Akcija.Jacina(TipJacine.ISKLJUCI)
        if (ima("ukljuc", "vrat") && ima("zvuk", "ton"))
            return Akcija.Jacina(TipJacine.UKLJUCI)
        val koliko = when {
            ima("mnogo", "dosta", "jos", "jako", "skroz", "bas") -> 3
            ima("malo", "malko") -> 1
            else -> 2
        }
        if (ima("pojac", "glasnij", "jace")) return Akcija.Jacina(TipJacine.GORE, koliko)
        if (ima("smanj", "tise", "tisi", "stisa", "utisa", "slabij")) return Akcija.Jacina(TipJacine.DOLE, koliko)

        // 6. Muzika
        if (ima("pauz", "stop", "stani", "zaustav", "prekin") || (ima("ugas", "iskljuc") && muzicko))
            return Akcija.Medij(TipMedija.PAUZA)
        if (ima("sledec", "sljedec", "naredn", "preskoc"))
            return Akcija.Medij(TipMedija.SLEDECA)
        if (ima("prethodn", "predhodn", "nazad") || (ima("vrat") && muzicko))
            return Akcija.Medij(TipMedija.PRETHODNA)
        val pu = indeks("pusti", "pustaj", "sviraj", "nastavi", "play", "plej")
        if (pu >= 0) {
            val i = preskoci(pu + 1)
            val ostali = n.drop(i)
            return if (ostali.isEmpty() || ostali.all { it in genericko }) Akcija.Medij(TipMedija.PUSTI)
            else Akcija.PustiPesmu(ostatak(i))
        }

        return Akcija.Nepoznato(tekst)
    }
}
