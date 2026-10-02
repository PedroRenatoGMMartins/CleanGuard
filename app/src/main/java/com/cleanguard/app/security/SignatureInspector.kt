package com.cleanguard.app.security

import android.content.pm.PackageInfo
import android.content.pm.Signature
import android.os.Build
import com.cleanguard.app.core.util.HashUtils
import com.cleanguard.app.security.model.SignerCertificate
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/** Extrai os certificados de assinatura de um app (informação pública do PackageManager). */
object SignatureInspector {

    @Suppress("DEPRECATION")
    fun certificates(info: PackageInfo): List<SignerCertificate> {
        val signatures: Array<Signature> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signing = info.signingInfo ?: return emptyList()
            if (signing.hasMultipleSigners()) {
                signing.apkContentsSigners ?: emptyArray()
            } else {
                // O último item do histórico é o certificado atual (os anteriores são de rotação de chave).
                signing.signingCertificateHistory?.takeLast(1)?.toTypedArray() ?: emptyArray()
            }
        } else {
            info.signatures ?: emptyArray()
        }
        return signatures.map { parse(it.toByteArray()) }
    }

    fun parse(encoded: ByteArray): SignerCertificate {
        val x509 = try {
            CertificateFactory.getInstance("X.509")
                .generateCertificate(ByteArrayInputStream(encoded)) as? X509Certificate
        } catch (e: Exception) {
            null
        }
        return SignerCertificate(
            sha256 = HashUtils.sha256(encoded),
            subject = x509?.subjectX500Principal?.name,
            issuer = x509?.issuerX500Principal?.name,
            notAfter = x509?.notAfter?.time,
        )
    }

    /** Flag adequada para pedir as assinaturas em cada versão. */
    @Suppress("DEPRECATION")
    val signatureFlag: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            android.content.pm.PackageManager.GET_SIGNATURES
        }
}
