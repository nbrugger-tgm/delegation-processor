plugins {
    id("nitonfx.java-conventions")
    id("nitonfx.test-conventions")
    id("nitonfx.publish-conventions")
}

description = "javac annotation processor generating delegator interfaces for @Delegator."

publishedLibrary {
    artifactId = "delegation-processor"
}

dependencies {
    // Exposed as `api` so that a single
    //   annotationProcessor("eu.nitonfx.processors:delegation-processor:<v>")
    //   implementation("eu.nitonfx.processors:delegation-processor:<v>")
    // pair brings the @Delegator annotation along.
    api(project(":annotations"))

    implementation(libs.javapoet)

    compileOnly(libs.auto.service.annotations)
    annotationProcessor(libs.auto.service)

    // `compileOnly` dependencies are not inherited by the test compile classpath; javac needs the
    // annotation to read the already-compiled DelegatorProcessor class file.
    testCompileOnly(libs.auto.service.annotations)

    testImplementation(libs.compile.testing)
    testImplementation(libs.javapoet)
}

// compile-testing drives javac in-process and needs access to the compiler internals.
tasks.test {
    jvmArgs(
        "--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
        "--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
        "--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",
        "--add-exports=jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED",
        "--add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
        "--add-exports=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED",
        "--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
        "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
        "--add-opens=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED",
    )
}
