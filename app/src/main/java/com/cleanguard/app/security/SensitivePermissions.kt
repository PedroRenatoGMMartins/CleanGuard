package com.cleanguard.app.security

import com.cleanguard.app.security.model.SensitivePermissionInfo

/**
 * Catálogo de permissões sensíveis com explicação em linguagem simples.
 * O peso indica quanto a permissão contribui para a pontuação de risco
 * (0 = apenas informativa). Ter uma permissão sensível NÃO torna um app malicioso.
 */
object SensitivePermissions {

    data class Entry(val group: String, val description: String, val weight: Int)

    private const val P = "android.permission."

    val catalog: Map<String, Entry> = mapOf(
        "${P}READ_SMS" to Entry("SMS", "Ler mensagens SMS, incluindo códigos de verificação.", 2),
        "${P}RECEIVE_SMS" to Entry("SMS", "Receber SMS assim que chegam.", 2),
        "${P}SEND_SMS" to Entry("SMS", "Enviar SMS (pode gerar custos).", 2),
        "${P}RECEIVE_MMS" to Entry("SMS", "Receber mensagens MMS.", 1),
        "${P}RECEIVE_WAP_PUSH" to Entry("SMS", "Receber mensagens WAP push.", 1),
        "${P}READ_CALL_LOG" to Entry("Registro de chamadas", "Ler o histórico de ligações.", 2),
        "${P}WRITE_CALL_LOG" to Entry("Registro de chamadas", "Alterar o histórico de ligações.", 2),
        "${P}PROCESS_OUTGOING_CALLS" to Entry("Registro de chamadas", "Ver e redirecionar ligações feitas.", 2),
        "${P}READ_CONTACTS" to Entry("Contatos", "Ler seus contatos.", 1),
        "${P}WRITE_CONTACTS" to Entry("Contatos", "Alterar seus contatos.", 1),
        "${P}GET_ACCOUNTS" to Entry("Contas", "Ver as contas cadastradas no aparelho.", 1),
        "${P}RECORD_AUDIO" to Entry("Microfone", "Gravar áudio pelo microfone.", 1),
        "${P}CAMERA" to Entry("Câmera", "Tirar fotos e gravar vídeos.", 1),
        "${P}ACCESS_FINE_LOCATION" to Entry("Localização", "Localização precisa (GPS).", 1),
        "${P}ACCESS_COARSE_LOCATION" to Entry("Localização", "Localização aproximada.", 1),
        "${P}ACCESS_BACKGROUND_LOCATION" to Entry("Localização em segundo plano", "Saber onde você está mesmo com o app fechado.", 2),
        "${P}READ_PHONE_STATE" to Entry("Telefone", "Ver estado do telefone e identificadores da linha.", 1),
        "${P}READ_PHONE_NUMBERS" to Entry("Telefone", "Ler o número de telefone.", 1),
        "${P}CALL_PHONE" to Entry("Telefone", "Fazer ligações sem passar pelo discador.", 1),
        "${P}ANSWER_PHONE_CALLS" to Entry("Telefone", "Atender ligações.", 1),
        "${P}BODY_SENSORS" to Entry("Sensores corporais", "Ler sensores de saúde (ex.: batimentos).", 1),
        "${P}READ_CALENDAR" to Entry("Agenda", "Ler eventos da agenda.", 1),
        "${P}MANAGE_EXTERNAL_STORAGE" to Entry("Todos os arquivos", "Acessar todos os arquivos do armazenamento compartilhado.", 2),
        "${P}SYSTEM_ALERT_WINDOW" to Entry("Sobreposição de tela", "Desenhar por cima de outros apps (pode imitar telas de login).", 2),
        "${P}REQUEST_INSTALL_PACKAGES" to Entry("Instalar apps", "Pedir a instalação de outros aplicativos.", 2),
        "${P}WRITE_SETTINGS" to Entry("Configurações do sistema", "Alterar configurações do sistema.", 1),
        "${P}PACKAGE_USAGE_STATS" to Entry("Dados de uso", "Ver quais apps você usa e por quanto tempo.", 1),
        "${P}READ_EXTERNAL_STORAGE" to Entry("Arquivos e mídia", "Ler arquivos e mídia compartilhados.", 0),
        "${P}READ_MEDIA_IMAGES" to Entry("Fotos", "Ler suas fotos.", 0),
        "${P}READ_MEDIA_VIDEO" to Entry("Vídeos", "Ler seus vídeos.", 0),
        "${P}READ_MEDIA_AUDIO" to Entry("Áudios", "Ler seus áudios.", 0),
        "${P}QUERY_ALL_PACKAGES" to Entry("Lista de apps", "Ver todos os apps instalados.", 0),
        "${P}RECEIVE_BOOT_COMPLETED" to Entry("Inicialização", "Iniciar automaticamente quando o aparelho liga.", 0),
    )

    val SMS_PERMISSIONS = setOf("${P}READ_SMS", "${P}RECEIVE_SMS")
    const val OVERLAY = "${P}SYSTEM_ALERT_WINDOW"
    const val INSTALL_PACKAGES = "${P}REQUEST_INSTALL_PACKAGES"
    const val ALL_FILES = "${P}MANAGE_EXTERNAL_STORAGE"
    const val BACKGROUND_LOCATION = "${P}ACCESS_BACKGROUND_LOCATION"

    /** Permissões sensíveis (peso > 0) presentes na lista, com estado de concessão. */
    fun relevant(requested: Collection<String>, granted: Set<String>, includeInformational: Boolean = false): List<SensitivePermissionInfo> =
        requested.distinct().mapNotNull { perm ->
            catalog[perm]?.takeIf { includeInformational || it.weight > 0 }?.let {
                SensitivePermissionInfo(perm, it.group, it.description, it.weight, perm in granted)
            }
        }.sortedWith(compareByDescending<SensitivePermissionInfo> { it.weight }.thenBy { it.group })

    /** Grupos sensíveis distintos (ex.: ["SMS", "Contatos", "Microfone"]). */
    fun sensitiveGroups(requested: Collection<String>): List<String> =
        relevant(requested, emptySet()).map { it.group }.distinct()

    fun shortName(permission: String): String = permission.substringAfterLast('.')
}
