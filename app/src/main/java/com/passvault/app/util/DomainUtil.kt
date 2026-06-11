package com.passvault.app.util

/**
 * Comparación de dominios para el autofill. Dos URLs solo coinciden si su
 * dominio registrable es exactamente el mismo, de modo que
 * "facebook.malicioso.com" nunca reciba credenciales guardadas para
 * "facebook.com".
 */
object DomainUtil {

    /** Sufijos de dos niveles habituales (no es la PSL completa, pero cubre los casos comunes). */
    private val MULTI_PART_TLDS = setOf(
        "co.uk", "org.uk", "ac.uk", "gov.uk", "me.uk",
        "com.mx", "org.mx", "gob.mx", "edu.mx", "net.mx",
        "com.ar", "com.br", "com.co", "com.pe", "com.ec", "com.uy", "com.ve",
        "com.cl", "com.bo", "com.py", "com.gt", "com.sv", "com.hn", "com.ni",
        "com.do", "com.pa", "com.cu", "com.pr",
        "com.au", "net.au", "org.au", "co.nz",
        "co.jp", "ne.jp", "or.jp", "co.kr", "co.in", "net.in", "org.in",
        "com.cn", "com.hk", "com.tw", "com.sg", "com.my", "co.id", "co.th",
        "com.tr", "com.ua", "com.ru", "co.za", "com.eg", "com.sa", "com.ng",
        "com.ph", "com.vn", "com.pk", "com.bd",
    )

    /** Extrae el host de una URL o texto tipo URL. */
    fun hostOf(input: String): String {
        var s = input.trim().lowercase()
        if (s.isEmpty()) return ""
        s = s.substringAfter("://")
        s = s.substringBefore('/').substringBefore('?').substringBefore('#')
        s = s.substringBefore(':') // quita el puerto
        s = s.substringAfter('@') // quita credenciales en la URL
        return s.removePrefix("www.")
    }

    /**
     * Dominio registrable: "accounts.google.com" → "google.com",
     * "login.banco.com.mx" → "banco.com.mx".
     */
    fun registrableDomain(hostOrUrl: String): String {
        val host = hostOf(hostOrUrl)
        if (host.isEmpty() || host.none { it == '.' }) return host
        val parts = host.split('.')
        if (parts.size <= 2) return host
        val lastTwo = parts.takeLast(2).joinToString(".")
        return if (lastTwo in MULTI_PART_TLDS) {
            parts.takeLast(3).joinToString(".")
        } else {
            lastTwo
        }
    }

    /** ¿La URL guardada pertenece al mismo sitio que el dominio de la página? */
    fun matches(savedUrl: String, pageDomain: String): Boolean {
        val saved = registrableDomain(savedUrl)
        val page = registrableDomain(pageDomain)
        return saved.isNotEmpty() && saved == page
    }
}
