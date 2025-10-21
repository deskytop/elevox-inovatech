package com.elevox.app

import android.app.Application

class AppContext : Application() {
	override fun onCreate() {
		super.onCreate()
		instance = this
	}

	companion object {
		private lateinit var instance: AppContext
		fun get(): AppContext = instance
	}
}

