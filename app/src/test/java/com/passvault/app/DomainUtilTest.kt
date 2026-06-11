package com.passvault.app

import com.passvault.app.util.DomainUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainUtilTest {

    @Test
    fun `extrae el host de URLs variadas`() {
        assertEquals("ejemplo.com", DomainUtil.hostOf("https://www.ejemplo.com/login?x=1"))
        assertEquals("ejemplo.com", DomainUtil.hostOf("ejemplo.com"))
        assertEquals("sub.ejemplo.com", DomainUtil.hostOf("http://sub.ejemplo.com:8080/ruta"))
    }

    @Test
    fun `calcula el dominio registrable`() {
        assertEquals("google.com", DomainUtil.registrableDomain("accounts.google.com"))
        assertEquals("banco.com.mx", DomainUtil.registrableDomain("https://login.banco.com.mx/entrar"))
        assertEquals("ejemplo.co.uk", DomainUtil.registrableDomain("www.ejemplo.co.uk"))
        assertEquals("ejemplo.com", DomainUtil.registrableDomain("ejemplo.com"))
    }

    @Test
    fun `los subdominios del mismo sitio coinciden`() {
        assertTrue(DomainUtil.matches("https://www.facebook.com", "m.facebook.com"))
        assertTrue(DomainUtil.matches("facebook.com/login", "facebook.com"))
    }

    @Test
    fun `un dominio de phishing no coincide`() {
        // El clásico: facebook.malicioso.com NO debe recibir la contraseña de facebook.com
        assertFalse(DomainUtil.matches("https://facebook.com", "facebook.malicioso.com"))
        assertFalse(DomainUtil.matches("https://banco.com.mx", "banco.com.mx.phish.io"))
        assertFalse(DomainUtil.matches("google.com", "g00gle.com"))
    }

    @Test
    fun `urls vacias no coinciden con nada`() {
        assertFalse(DomainUtil.matches("", "ejemplo.com"))
        assertFalse(DomainUtil.matches("ejemplo.com", ""))
    }
}
