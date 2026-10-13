// Sign up, login and refresh. Issues RS256 access tokens, publishes the JWKS, and emits auth.user-registered.v1
// through the outbox.
plugins {
    id("bankdash.spring-service")
}

dependencies {
    implementation(project(":libs:messaging"))
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.security.oauth2.resource.server)
    implementation(libs.spring.boot.starter.validation)
    runtimeOnly(libs.flyway.database.postgresql)
    runtimeOnly(libs.postgresql)

    testImplementation(testFixtures(project(":libs:test-support")))
}
