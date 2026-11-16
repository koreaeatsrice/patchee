plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

// The README guard test reads README.md, which Gradle cannot see on its own: without this a
// README-only change leaves `test` UP-TO-DATE and the guard silently never runs.
tasks.withType<Test>().configureEach {
    inputs.file(rootProject.file("README.md"))
        .withPropertyName("readme")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
