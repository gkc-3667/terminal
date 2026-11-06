plugins {
    id("com.android.application")
    id("com.chaquo.python")
}

android {
    namespace = "com.gkc3667.terminal"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.gkc3667.terminal"
        minSdk = 24
        targetSdk = 28
        versionCode = 2
        versionName = "2.0"
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }
}

dependencies {
    implementation("com.github.mwiede:jsch:0.2.20")
    implementation("androidx.webkit:webkit:1.9.0")
}

chaquopy {
    defaultConfig {
        version = "3.11"
        pip {
            install("discord.py==2.5.2")
        }
    }
}
