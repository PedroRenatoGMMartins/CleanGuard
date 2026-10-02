package com.cleanguard.app

import com.cleanguard.app.core.util.HashUtils
import com.cleanguard.app.security.db.LocalDemoMalwareDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

/** Testes de SHA-256 com vetores oficiais (FIPS 180-2). */
class Sha256Test {

    @Test
    fun `vetores conhecidos`() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", HashUtils.sha256(""))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", HashUtils.sha256("abc"))
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            HashUtils.sha256("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"),
        )
    }

    @Test
    fun `stream com buffer pequeno gera o mesmo hash`() {
        val data = ByteArray(200_000) { (it % 251).toByte() }
        var progress = 0L
        val streamed = HashUtils.sha256(ByteArrayInputStream(data), bufferSize = 1024) { progress = it }
        assertEquals(HashUtils.sha256(data), streamed)
        assertEquals(data.size.toLong(), progress)
    }

    @Test
    fun `hash do arquivo de teste EICAR confere com o banco demo`() {
        // Montado em partes para que este arquivo-fonte não seja sinalizado por antivírus.
        val eicar = "X5O!P%@AP[4\\PZX54(P^)7CC)7}" + "\$EICAR-STANDARD-" + "ANTIVIRUS-TEST-FILE!\$H+H*"
        assertEquals(LocalDemoMalwareDatabase.EICAR_SHA256, HashUtils.sha256(eicar))
    }

    @Test
    fun `validacao normalizacao e impressao digital`() {
        val hash = HashUtils.sha256("abc")
        assertTrue(HashUtils.isValidSha256(hash))
        assertTrue(HashUtils.isValidSha256(hash.uppercase()))
        assertFalse(HashUtils.isValidSha256("xyz"))
        assertFalse(HashUtils.isValidSha256(null))
        assertEquals(hash, HashUtils.normalize(HashUtils.fingerprint(hash)))
        assertTrue(HashUtils.fingerprint(hash).startsWith("BA:78:16:BF"))
    }
}
