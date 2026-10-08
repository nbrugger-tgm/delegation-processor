import eu.nitonfx.gradle.PublishedLibraryExtension

plugins {
    id("java-library")
    id("maven-publish")
}

group = "eu.nitonfx.processors"

val publishedLibrary = extensions.create<PublishedLibraryExtension>("publishedLibrary")
publishedLibrary.artifactId.convention(provider { project.name })
publishedLibrary.description.convention(provider { project.description ?: project.name })

base.archivesName = publishedLibrary.artifactId

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        // `register` (not `create`) keeps the publication lazy, so the values configured by
        // the consuming build script are already set by the time this block runs.
        register<MavenPublication>("maven") {
            from(components["java"])

            artifactId = publishedLibrary.artifactId.get()

            pom {
                name = publishedLibrary.artifactId
                description = publishedLibrary.description
                url = "https://github.com/nitonfx/delegation-processor"
                inceptionYear = "2026"

                licenses {
                    license {
                        name = "Apache License, Version 2.0"
                        url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                        distribution = "repo"
                    }
                }
                developers {
                    developer {
                        id = "nitonfx"
                        name = "Niton"
                        url = "https://github.com/nitonfx"
                    }
                }
                scm {
                    connection = "scm:git:https://github.com/nitonfx/delegation-processor.git"
                    developerConnection = "scm:git:ssh://git@github.com/nitonfx/delegation-processor.git"
                    url = "https://github.com/nitonfx/delegation-processor"
                }
            }
        }
    }
}
