// The shared event contract: the envelope, topic names and one record per event payload.
// Plain Java records with Bean Validation annotations, so producers and consumers agree on the JSON.
plugins {
    id("bankdash.java-library")
}

dependencies {
    api(libs.jakarta.validation.api)

    testImplementation(libs.jackson.databind)
}
