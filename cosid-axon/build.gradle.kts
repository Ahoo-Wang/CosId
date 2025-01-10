dependencies {
    api(platform(libs.axon.bom))
    api(project(":cosid-core"))
    testImplementation(project(":cosid-test"))
    implementation("org.axonframework:axon-messaging")
}
