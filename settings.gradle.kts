dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven(url = "https://packages.confluent.io/maven/")
        maven {
            url = uri("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
        }
        mavenLocal()
    }
}
rootProject.name = "isfrisktilarbeid"
