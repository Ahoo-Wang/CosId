dependencies {
    api(platform("org.axonframework:axon-bom:4.7.4"))
    api(project(":cosid-core"))
    testImplementation(project(":cosid-test"))
    implementation("org.axonframework:axon-messaging")
}
