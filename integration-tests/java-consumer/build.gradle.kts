plugins {
    id("nitonfx.java-conventions")
    id("nitonfx.test-conventions")
}

description = "End to end check that annotationProcessor(...) produces usable delegators."

dependencies {
    implementation(project(":annotations"))
    annotationProcessor(project(":processor"))
}
