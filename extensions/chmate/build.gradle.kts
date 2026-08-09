extension {
    name = "extensions/chmate.rve"
}

android {
    namespace = "app.revanced.extension.chmate"

    defaultConfig {
        minSdk = 23
    }
}

dependencies {
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
