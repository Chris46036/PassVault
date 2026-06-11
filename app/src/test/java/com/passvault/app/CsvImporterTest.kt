package com.passvault.app

import com.passvault.app.util.CsvImporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CsvImporterTest {

    @Test
    fun `importa el formato de Chrome`() {
        val csv = """
            name,url,username,password,note
            Correo,https://mail.ejemplo.com,chris@ejemplo.com,S3creta!,una nota
            Banco,https://banco.com.mx,chris,0tr4Clave,
        """.trimIndent()

        val entries = CsvImporter.parse(csv)
        assertNotNull(entries)
        assertEquals(2, entries!!.size)
        assertEquals("Correo", entries[0].title)
        assertEquals("chris@ejemplo.com", entries[0].username)
        assertEquals("S3creta!", entries[0].password)
        assertEquals("https://mail.ejemplo.com", entries[0].url)
        assertEquals("una nota", entries[0].notes)
    }

    @Test
    fun `importa el formato de Bitwarden con TOTP`() {
        val csv = "folder,favorite,type,name,notes,fields,reprompt,login_uri,login_username,login_password,login_totp\n" +
            ",,login,GitHub,,,0,https://github.com,chris,clave123,JBSWY3DPEHPK3PXP\n"

        val entries = CsvImporter.parse(csv)
        assertNotNull(entries)
        assertEquals(1, entries!!.size)
        assertEquals("GitHub", entries[0].title)
        assertEquals("JBSWY3DPEHPK3PXP", entries[0].totpSecret)
    }

    @Test
    fun `maneja campos entrecomillados con comas y saltos de linea`() {
        val csv = "name,url,username,password,note\n" +
            "\"Sitio, con coma\",https://x.com,user,\"cla\"\"ve\",\"línea1\nlínea2\"\n"

        val entries = CsvImporter.parse(csv)
        assertNotNull(entries)
        assertEquals("Sitio, con coma", entries!![0].title)
        assertEquals("cla\"ve", entries[0].password)
        assertEquals("línea1\nlínea2", entries[0].notes)
    }

    @Test
    fun `extrae el secreto de una URI otpauth`() {
        val secret = CsvImporter.extractTotpSecret(
            "otpauth://totp/Ejemplo:chris?secret=JBSWY3DPEHPK3PXP&issuer=Ejemplo"
        )
        assertEquals("JBSWY3DPEHPK3PXP", secret)
        assertEquals("ABC123", CsvImporter.extractTotpSecret("ABC123"))
    }

    @Test
    fun `un archivo que no es de credenciales devuelve null`() {
        assertNull(CsvImporter.parse("col1,col2\na,b\n"))
        assertNull(CsvImporter.parse(""))
    }
}
