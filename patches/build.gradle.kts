group = "io.github.chmate.revanced"

patches {
    about {
        name = "ChMate ReVanced Patches"
        description = "Privacy and usability patches for ChMate"
        source = "https://github.com/roflsunriz/chmate-revanced"
        author = "chmate-revanced contributors"
        contact = "https://github.com/roflsunriz/chmate-revanced/issues"
        website = "https://github.com/roflsunriz/chmate-revanced"
        license = "GNU General Public License v3.0"
    }
}

dependencies {
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
