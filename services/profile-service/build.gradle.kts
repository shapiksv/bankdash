// Personal data, preferences and avatar. Creates a profile for every auth.user-registered.v1 event.
plugins {
    id("bankdash.spring-service")
}

dependencies {
    implementation(project(":libs:messaging"))
    implementation(project(":libs:web-common"))
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.starter.flyway)
    runtimeOnly(libs.flyway.database.postgresql)
    runtimeOnly(libs.postgresql)

    testImplementation(testFixtures(project(":libs:test-support")))
}
