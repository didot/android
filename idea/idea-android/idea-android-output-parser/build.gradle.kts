
apply { plugin("kotlin") }

dependencies {
    compile(project(":compiler:util"))
    compile(ideaSdkCoreDeps("intellij-core"))
    compile(ideaPluginDeps("gradle-api", plugin = "gradle"))
    compile(ideaPluginDeps("android", "android-common", "sdk-common", plugin = "android"))
    compile(ideaSdkDeps("android-base-common"))
}

sourceSets {
    "main" { projectDefault() }
    "test" {}
}

