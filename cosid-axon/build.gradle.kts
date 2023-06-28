dependencies {
    api(platform("org.axonframework:axon-bom:4.8.0"))
    api(project(":cosid-core"))
    testImplementation(project(":cosid-test"))
    implementation("org.axonframework:axon-messaging")
}
