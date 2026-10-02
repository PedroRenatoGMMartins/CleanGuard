# Regras do R8 para o CleanGuard.
# O app não usa reflexão, serialização por reflexão nem carregamento dinâmico de código,
# então as regras padrão do Android Gradle Plugin e as regras embutidas nas bibliotecas
# AndroidX/Compose são suficientes.

# Mantém nomes de arquivo e linha para stack traces legíveis em relatórios de erro.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
