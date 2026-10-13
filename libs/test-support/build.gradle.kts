// Shared test setup, published as test fixtures so that Testcontainers never lands on a
// service's runtime classpath. Services consume it with:
//   testImplementation(testFixtures(project(":libs:test-support")))
plugins {
    id("bankdash.java-library")
    `java-test-fixtures`
}

dependencies {
    testFixturesApi(libs.spring.boot.testcontainers)
    testFixturesApi(libs.testcontainers.junit.jupiter)
    testFixturesApi(libs.testcontainers.postgresql)
    testFixturesApi(libs.testcontainers.kafka)
    testFixturesImplementation(libs.spring.boot.starter.test)

    // Only for the smoke test that proves the containers work with Spring Boot.
    testImplementation(libs.spring.boot.starter.jdbc)
    testImplementation(libs.spring.boot.starter.kafka)
    testRuntimeOnly(libs.postgresql)
}
