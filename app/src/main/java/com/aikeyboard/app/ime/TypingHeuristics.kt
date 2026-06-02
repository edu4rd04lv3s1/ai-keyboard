package com.aikeyboard.app.ime

/**
 * Heurísticas puras (sem dependência de Android) usadas pelo motor de digitação.
 *
 * Ficam separadas justamente para serem 100% cobertas por unit test — são elas
 * que decidem QUANDO o teclado deve ficar de fora e não mexer no que o usuário
 * escreveu de propósito. É o que diferencia "autocorreção agressiva" de
 * "autocorreção que erra": agressiva nos typos de verdade, intocável no resto.
 */
object TypingHeuristics {

    /**
     * `true` quando [word] deve ser PROTEGIDA da autocorreção automática, mesmo
     * nos níveis mais agressivos (incluindo ULTIMATE). Cobre exatamente o que se
     * digita intencionalmente e não é "erro":
     *
     *  - gírias/abreviações de internet PT-BR (`vc`, `pra`, `pq`, `blz`, `vlw`…);
     *  - risadas e onomatopeias (`kkk`, `rsrs`, `haha`, `huehue`);
     *  - alongamento enfático (`siiim`, `amooo`, `naoo`);
     *  - tokens com dígitos (`10kg`, `b2b`, `x1`);
     *  - CamelCase de marcas/handles (`iPhone`, `WhatsApp`);
     *  - siglas curtas em maiúsculas (`OK`, `API`, `CEP`).
     *
     * A correção manual (tocar na sugestão) continua disponível; só o gatilho
     * automático no espaço é suprimido.
     */
    fun isProtectedFromAutocorrect(word: String): Boolean {
        if (word.isEmpty()) return false

        // Dígito em qualquer posição → token intencional (medidas, códigos, placas).
        if (word.any(Char::isDigit)) return true

        // Maiúscula no interior precedida de minúscula → marca/handle (iPhone, WhatsApp).
        for (i in 1 until word.length) {
            if (word[i].isUpperCase() && word[i - 1].isLetter() && !word[i - 1].isUpperCase()) {
                return true
            }
        }

        val letters = word.count(Char::isLetter)

        // Sigla curta toda em maiúsculas (OK, API, CEP, CPF). Palavras LONGAS em
        // caixa alta (caps lock ligado) continuam corrigíveis: "QAUNDO" -> "QUANDO".
        if (letters in 2..4 && word.all { !it.isLetter() || it.isUpperCase() }) return true

        val normalized = PortugueseLexicon.normalize(word)
        if (normalized.isEmpty()) return true

        // Alongamento enfático: a mesma letra 3+ vezes seguidas (siiim, amooo).
        if (hasRunOfThree(normalized)) return true

        // Risada / onomatopeia (kkk, rsrs, haha, huehue).
        if (LAUGHTER.matches(normalized)) return true

        // Gíria/abreviação conhecida sem expansão canônica desejada.
        if (normalized in SLANG) return true

        return false
    }

    private fun hasRunOfThree(s: String): Boolean {
        var run = 1
        for (i in 1 until s.length) {
            if (s[i] == s[i - 1]) {
                run++
                if (run >= 3) return true
            } else {
                run = 1
            }
        }
        return false
    }

    /**
     * Padrões de risada do PT-BR informal. Casados sobre a forma normalizada
     * (minúscula, sem acentos): `kkk`, `rsrs`, `haha`, `hehe`, `huehue`, `ksks`.
     */
    private val LAUGHTER = Regex(
        "^(?:" +
            "k{2,}" + "|" +                 // kk, kkk, kkkk
            "(?:rs)+" + "|" +               // rs, rsrs, rsrsrs
            "(?:ks){2,}s?" + "|" +          // ksks, ksksks
            "(?:ha|he|hi|hu|ho){2,}h?" + "|" + // haha, hehe, huhu, hahah
            "(?:hue|hua|aue|auhe)+h?" +     // hue, huehue, auheauhe
            ")$"
    )

    /**
     * Gírias e abreviações de internet em PT-BR que o usuário escreve DE PROPÓSITO.
     * Forma normalizada (minúscula, sem acento). NÃO inclui formas que têm uma
     * expansão/acentuação canônica desejada (ex.: `voce`->`você`, `nao`->`não`),
     * que continuam sendo corrigidas normalmente.
     */
    private val SLANG: Set<String> = setOf(
        // pronomes e tratamento (NÃO incluir "voce"/"nao": têm correção canônica)
        "vc", "vcs", "cmg", "ctg",
        // conjunções / advérbios reduzidos
        "pq", "pqp", "pra", "pro", "pras", "pros", "dnv", "agr", "hj", "hje",
        "msm", "tipo", "oq", "neh",
        // quantificadores
        "mt", "mto", "mta", "mtos", "mtas", "mts", "dms",
        // confirmações / despedidas / interjeições
        "blz", "blza", "vlw", "flw", "slk", "slc", "pfv", "pfvr", "obg", "obgd",
        "td", "tds", "tb", "tbm", "tmb", "tmj", "vdd", "vdde", "sdd", "sdds",
        "aff", "affs", "eita", "ata", "uai", "nsei",
        // técnico / produto comuns digitados como estão
        "app", "apps", "link", "links", "msg", "msgs", "add", "ok", "okay"
    )
}
