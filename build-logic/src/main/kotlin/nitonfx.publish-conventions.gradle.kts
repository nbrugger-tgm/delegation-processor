plugins {
    id("java-library")
    id("maven-publish")
}

group = "eu.nitonfx.processors"

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        // artifactId defaults to the project name, which is the artifact id by convention here.
        create<MavenPublication>("maven") {
            from(components["java"])

            pom {
                name = project.name
                description = provider { project.description }
                url = "https://github.com/nbrugger-tgm/delegation-processor"
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
                        id = "nbrugger-tgm"
                        name = "Niton"
                        url = "https://github.com/nbrugger-tgm"
                    }
                }
                scm {
                    connection = "scm:git:https://github.com/nbrugger-tgm/delegation-processor.git"
                    developerConnection = "scm:git:ssh://git@github.com/nbrugger-tgm/delegation-processor.git"
                    url = "https://github.com/nbrugger-tgm/delegation-processor"
                }
            }
        }
    }
}
