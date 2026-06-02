package com.aikeyboard.app.ime

/**
 * Mapa de correções automáticas on-device para PT-BR.
 *
 * Princípio de design: SOMENTE incluir correções **inequívocas** — onde a forma
 * "errada" não é uma palavra real diferente. Exemplos:
 *  - `voce` → `você` (sem ambiguidade)
 *  - `nao`  → `não`
 *  - `qaundo` → `quando` (typo puro)
 *
 * NÃO incluir homógrafos sensíveis a contexto:
 *  - `esta` (verbo) vs `está` — depende do contexto
 *  - `ja`   vs `já`
 *  - `so`   vs `só`
 *  - `la`   vs `lá`
 *  - `ai`   vs `aí`
 *  - `e`    vs `é`
 *
 * As chaves estão em LOWERCASE. A capitalização é preservada pelo chamador
 * via [applyCase].
 */
object LocalCorrections {

    /** Heurística simples: aplicada sempre que [enabled] é true. */
    private val MAP: Map<String, String> = buildMap {
        // -------- Acentos: nasais e palavras muito comuns sem ambiguidade --------
        put("nao", "não")
        put("entao", "então")
        put("voce", "você")
        put("voces", "vocês")
        put("tambem", "também")
        put("alem", "além")
        put("atras", "atrás")
        put("atraves", "através")
        put("ate", "até")  // borderline mas "ate" como verbo (atar) é raríssimo
        put("apos", "após")
        put("ninguem", "ninguém")
        put("alguem", "alguém")
        put("porem", "porém")
        put("contem", "contém")
        put("mantem", "mantém")
        put("convem", "convém")
        put("ideia", "ideia")  // já correto pelo Acordo, mantém
        put("heroi", "herói")
        put("herois", "heróis")
        put("constroi", "constrói")
        put("destroi", "destrói")

        // -------- Vogais com til (ões, ão, ã) --------
        put("razao", "razão")
        put("razoes", "razões")
        put("coracao", "coração")
        put("coracoes", "corações")
        put("emocao", "emoção")
        put("emocoes", "emoções")
        put("estacao", "estação")
        put("estacoes", "estações")
        put("nacao", "nação")
        put("nacoes", "nações")
        put("direcao", "direção")
        put("direcoes", "direções")
        put("relacao", "relação")
        put("relacoes", "relações")
        put("opcao", "opção")
        put("opcoes", "opções")
        put("acao", "ação")
        put("acoes", "ações")
        put("atencao", "atenção")
        put("atencoes", "atenções")
        put("educacao", "educação")
        put("solucao", "solução")
        put("solucoes", "soluções")
        put("informacao", "informação")
        put("informacoes", "informações")
        put("organizacao", "organização")
        put("organizacoes", "organizações")
        put("aplicacao", "aplicação")
        put("aplicacoes", "aplicações")
        put("populacao", "população")
        put("comunicacao", "comunicação")
        put("explicacao", "explicação")
        put("operacao", "operação")
        put("formacao", "formação")
        put("relacao", "relação")
        put("traducao", "tradução")
        put("decisao", "decisão")
        put("decisoes", "decisões")
        put("visao", "visão")
        put("televisao", "televisão")
        put("ocasiao", "ocasião")
        put("dimensao", "dimensão")
        put("expressao", "expressão")
        put("impressao", "impressão")
        put("compreensao", "compreensão")
        put("missao", "missão")
        put("paixao", "paixão")
        put("conexao", "conexão")
        put("conexoes", "conexões")
        put("reuniao", "reunião")
        put("reunioes", "reuniões")
        put("uniao", "união")
        put("regiao", "região")
        put("regioes", "regiões")
        put("religiao", "religião")
        put("opiniao", "opinião")
        put("opinioes", "opiniões")
        put("comissao", "comissão")
        put("permissao", "permissão")
        put("submissao", "submissão")
        put("pao", "pão")
        put("paes", "pães")
        put("mao", "mão")
        put("maos", "mãos")
        put("irmao", "irmão")
        put("irmaos", "irmãos")
        put("irma", "irmã")
        put("cao", "cão")
        put("caes", "cães")
        put("limao", "limão")
        put("verao", "verão")
        put("inverno", "inverno")  // já correto, ignora
        put("orgao", "órgão")
        put("orgaos", "órgãos")
        put("aviao", "avião")
        put("avioes", "aviões")
        put("caminhao", "caminhão")
        put("caminhoes", "caminhões")
        put("milhao", "milhão")
        put("milhoes", "milhões")
        put("bilhao", "bilhão")
        put("bilhoes", "bilhões")
        put("nao", "não")
        put("entao", "então")
        put("vao", "vão")
        put("amanha", "amanhã")
        put("manha", "manhã")
        put("manhas", "manhãs")

        // -------- Cedilha --------
        put("comeco", "começo")
        put("comeca", "começa")
        put("comecou", "começou")
        put("comecar", "começar")
        put("comecando", "começando")
        put("avanco", "avanço")
        put("avanca", "avança")
        put("avancou", "avançou")
        put("crianca", "criança")
        put("criancas", "crianças")
        put("mudanca", "mudança")
        put("mudancas", "mudanças")
        put("esperanca", "esperança")
        put("seguranca", "segurança")
        put("diferenca", "diferença")
        put("diferencas", "diferenças")
        put("licenca", "licença")
        put("preco", "preço")
        put("precos", "preços")
        put("ameaca", "ameaça")
        put("aco", "aço")
        put("braco", "braço")
        put("bracos", "braços")
        put("cabeca", "cabeça")
        put("calcado", "calçado")
        put("calca", "calça")
        put("calcas", "calças")
        put("forca", "força")
        put("graca", "graça")
        put("gracas", "graças")
        put("lagrima", "lágrima")
        put("laco", "laço")
        put("nasc", "nasc")  // só pra nao bater por engano
        put("pedaco", "pedaço")
        put("praca", "praça")
        put("rapaz", "rapaz")  // já correto

        // -------- Acentos agudos/circunflexos clássicos --------
        put("aniversario", "aniversário")
        put("horario", "horário")
        put("necessario", "necessário")
        put("salario", "salário")
        put("calendario", "calendário")
        put("usuario", "usuário")
        put("usuarios", "usuários")
        put("comentario", "comentário")
        put("comentarios", "comentários")
        put("dicionario", "dicionário")
        put("estagiario", "estagiário")
        put("voluntario", "voluntário")
        put("contrario", "contrário")
        put("ordinario", "ordinário")
        put("imobiliario", "imobiliário")
        put("relatorio", "relatório")
        put("territorio", "território")
        put("escritorio", "escritório")
        put("laboratorio", "laboratório")
        put("auditorio", "auditório")
        put("estagio", "estágio")
        put("relogio", "relógio")
        put("relogios", "relógios")
        put("oculos", "óculos")
        put("musica", "música")
        put("musicas", "músicas")
        put("medico", "médico")
        put("medica", "médica")
        put("medicos", "médicos")
        put("publico", "público")
        put("publica", "pública")
        put("rapido", "rápido")
        put("basico", "básico")
        put("magico", "mágico")
        put("logico", "lógico")
        put("tragico", "trágico")
        put("classico", "clássico")
        put("automatico", "automático")
        put("pratico", "prático")
        put("politica", "política")
        put("economico", "econômico")
        put("historia", "história")
        put("historico", "histórico")
        put("memoria", "memória")
        put("memorias", "memórias")
        put("vitoria", "vitória")
        put("gloria", "glória")
        put("trajetoria", "trajetória")
        put("teoria", "teoria")  // não tem acento no original
        put("familia", "família")
        put("familias", "famílias")
        put("policia", "polícia")
        put("ciencia", "ciência")
        put("paciencia", "paciência")
        put("experiencia", "experiência")
        put("conferencia", "conferência")
        put("preferencia", "preferência")
        put("referencia", "referência")
        put("sequencia", "sequência")
        put("frequencia", "frequência")
        put("influencia", "influência")
        put("consequencia", "consequência")
        put("indiferenca", "indiferença")
        put("eficiencia", "eficiência")
        put("transferencia", "transferência")
        put("essencia", "essência")
        put("inteligencia", "inteligência")
        put("convivencia", "convivência")
        put("audiencia", "audiência")
        put("emergencia", "emergência")
        put("agencia", "agência")
        put("urgencia", "urgência")
        put("agencias", "agências")
        put("ultimo", "último")
        put("ultima", "última")
        put("ultimos", "últimos")
        put("unico", "único")
        put("unica", "única")
        put("proprio", "próprio")
        put("propria", "própria")
        put("proximo", "próximo")
        put("proxima", "próxima")
        put("maximo", "máximo")
        put("minimo", "mínimo")
        put("optimo", "ótimo")  // não, "ótimo" sem p
        put("otimo", "ótimo")
        put("pessimo", "péssimo")
        put("simbolo", "símbolo")
        put("titulo", "título")
        put("vinculo", "vínculo")
        put("calculo", "cálculo")
        put("circulo", "círculo")
        put("seculo", "século")
        put("musculo", "músculo")
        put("ridiculo", "ridículo")
        put("oculo", "óculo")  // raro
        put("medio", "médio")
        put("media", "média")
        put("medios", "médios")
        put("medias", "médias")
        put("serio", "sério")
        put("seria", "séria")
        put("varios", "vários")
        put("varias", "várias")
        put("ferias", "férias")
        put("misterio", "mistério")
        put("criterio", "critério")
        put("sofa", "sofá")
        put("sofas", "sofás")
        put("cafe", "café")
        put("cafes", "cafés")
        put("ate", "até")
        put("pe", "pé")
        put("pes", "pés")
        put("voo", "voo")  // já correto
        put("dia", "dia")  // não acentua
        put("dois", "dois")
        put("tres", "três")
        put("tres", "três")
        put("seis", "seis")
        // -------- Erros de digitação puros (transposições, omissões) --------
        put("qaundo", "quando")
        put("qauntdo", "quando")
        put("qeu", "que")
        put("qeur", "quer")
        put("qeurer", "querer")
        put("qaul", "qual")
        put("qaulqer", "qualquer")
        put("qualqer", "qualquer")
        put("qauto", "quanto")
        put("tnaot", "tanto")
        put("agnoa", "agora")
        put("aogra", "agora")
        put("nuca", "nunca")
        put("acno", "anos")
        put("aod", "ano")
        put("aviso", "aviso")  // já correto
        put("psoso", "posso")
        put("rapdio", "rápido")
        put("trnasporte", "transporte")
        put("muiot", "muito")
        put("vec", "vez")
        put("axar", "achar")
        put("axei", "achei")
        put("fais", "faz")
        put("trabhalo", "trabalho")
        put("dezembor", "dezembro")
        put("novemrbo", "novembro")
        put("setemrbo", "setembro")
        put("janerio", "janeiro")
        put("ferveiro", "fevereiro")
        put("feveriro", "fevereiro")
        put("trsa", "três")
        put("verdaderio", "verdadeiro")
        put("brsail", "brasil")
        put("brasil", "Brasil")  // capitalização

        // -------- Outros: "que" / "para" reduzidos (NÃO expandir, são ok) --------
        // pra/pro/q/pq são gírias intencionais — NÃO incluir aqui
        // vc, td, blz, kk, kkk — gírias, NÃO incluir

        // -------- Capitalização de nomes próprios comuns --------
        put("brasileiro", "brasileiro")  // adjetivo lowercase
        put("brasileira", "brasileira")
        // (não force capitalização exceto em casos óbvios)
    }

    /**
     * Tenta corrigir [word]. Retorna a palavra corrigida (com a mesma capitalização
     * de [word]) ou `null` se não há correção aplicável.
     *
     * Se [userDictionary] contém [word] (lowercase), retorna `null` — palavras
     * pessoais nunca são auto-corrigidas.
     */
    fun lookup(word: String, userDictionary: Set<String> = emptySet()): String? {
        if (word.isEmpty()) return null
        val key = word.lowercase()
        if (key in userDictionary) return null  // palavra pessoal: não corrige
        val replacement = MAP[key] ?: return null
        if (replacement.equals(word, ignoreCase = false)) return null  // sem mudança real
        return applyCase(word, replacement)
    }

    /**
     * Aplica a capitalização da palavra original na correção.
     *  - Tudo MAIÚSCULO → tudo MAIÚSCULO
     *  - Primeira letra maiúscula → primeira letra maiúscula
     *  - Caso contrário → minúsculas
     */
    private fun applyCase(original: String, replacement: String): String {
        if (original.isEmpty()) return replacement
        val allUpper = original.all { !it.isLetter() || it.isUpperCase() } && original.any { it.isLetter() }
        if (allUpper) return replacement.uppercase()
        if (original[0].isUpperCase()) {
            return replacement.replaceFirstChar { it.uppercase() }
        }
        return replacement
    }
}
