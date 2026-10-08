plugins {
    id("nitonfx.java-conventions")
    id("nitonfx.test-conventions")
}

description = "End to end check that annotationProcessor(...) produces usable delegators."

dependencies {
    compileOnly(project(":delegation-annotations"))
    annotationProcessor(project(":delegation-processor"))
}
