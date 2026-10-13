// Kafka plumbing shared by the services: the transactional outbox and its relay, the idempotent consumer, and
// the DefaultErrorHandler/DLT setup. Spring Boot auto-configuration; see MessagingAutoConfiguration.
plugins {
    id("bankdash.java-library")
}

dependencies {
    api(project(":libs:events"))
    api(libs.spring.boot.starter.kafka)
    api(libs.spring.boot.starter.jdbc)
    api(libs.spring.boot.starter.jackson)

    testImplementation(testFixtures(project(":libs:test-support")))
    testImplementation(libs.spring.boot.starter.flyway)
    testRuntimeOnly(libs.flyway.database.postgresql)
    testRuntimeOnly(libs.postgresql)
}
