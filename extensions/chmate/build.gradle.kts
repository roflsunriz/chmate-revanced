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
    testImplementation("org.json:json:20260814")
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
