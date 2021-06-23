val libraryProjects = rootProject.ext.get("libraryProjects") as java.util.LinkedHashSet<Project>;

dependencies {
    constraints {
        libraryProjects.forEach {
            api(it)
        }
    }
}
