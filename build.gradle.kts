plugins {
    base
}

allprojects {
    dependencyLocking {
        lockAllConfigurations()
    }

    tasks.register("resolveAdditionalLockConfigurations") {
        doLast {
            configurations.filter {
                it.isCanBeResolved && (
                    it.name.startsWith("_internal-unified-test-platform") ||
                        it.name == "kotlinBouncyCastleConfiguration" ||
                        it.name == "runtimeClasspath"
                    )
            }.forEach { it.resolve() }
        }
    }

    configurations.configureEach {
        resolutionStrategy.eachDependency {
            val securedVersion = when {
                requested.group == "io.netty" -> "4.2.19.Final"
                requested.group == "org.bouncycastle" -> "1.86"
                requested.group == "com.google.protobuf" -> "4.36.2"
                requested.group == "org.apache.commons" && requested.name == "commons-lang3" ->
                    "3.21.0"
                requested.group == "org.apache.httpcomponents" && requested.name == "httpclient" ->
                    "4.5.14"
                else -> null
            }

            if (securedVersion != null) {
                useVersion(securedVersion)
                because("依存監査で検出した既知脆弱性を修正し、推移依存を検証済みの版へ統一するため")
            }
        }
    }
}
