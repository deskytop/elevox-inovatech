import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties

plugins {
	alias(libs.plugins.android.application)
	alias(libs.plugins.kotlin.android)
}

android {
	namespace = "com.elevox.app"
	compileSdk = 34

	defaultConfig {
		applicationId = "com.elevox.app"
		minSdk = 24
		targetSdk = 34
		versionCode = 1
		versionName = "1.0"

		// Read ESP32_HOST from local.properties and fail if missing
		val props = gradleLocalProperties(rootDir, providers)
		val host = props.getProperty("ESP32_HOST")
			?: throw GradleException("Missing ESP32_HOST in local.properties. Copy local.properties.example and set ESP32_HOST")
		buildConfigField("String", "ESP32_HOST", "\"$host\"")

	}

	buildTypes {
		release {
			isMinifyEnabled = false
			proguardFiles(
				getDefaultProguardFile("proguard-android-optimize.txt"),
				"proguard-rules.pro"
			)
		}
	}

	buildFeatures {
		compose = true
		buildConfig = true
	}

	composeOptions {
		kotlinCompilerExtensionVersion = "1.5.14"
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}

	packaging {
		resources {
			excludes += "/META-INF/{AL2.0,LGPL2.1}"
		}
	}
}

kotlin {
	jvmToolchain(17)
}

dependencies {
	implementation(platform(libs.androidx.compose.bom))
	implementation(libs.androidx.core.ktx)
	implementation(libs.androidx.activity.compose)
	implementation(libs.androidx.lifecycle.runtime.ktx)
	implementation(libs.androidx.lifecycle.viewmodel.compose)
	implementation(libs.androidx.compose.ui)
	implementation(libs.androidx.compose.material3)
	implementation(libs.material)
	debugImplementation(libs.androidx.compose.ui.tooling)

	implementation(libs.coroutines.android)
	implementation(libs.okhttp)
	implementation(libs.okhttp.tls)
	debugImplementation(libs.okhttp.logging)
	implementation(libs.retrofit)
	implementation(libs.retrofit.moshi)
	implementation(libs.moshi.kotlin)
}
