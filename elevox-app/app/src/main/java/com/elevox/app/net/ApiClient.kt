package com.elevox.app.net

import com.elevox.app.BuildConfig
import com.elevox.app.R
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.tls.HandshakeCertificates
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.Response
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit

data class DadosRequest(
	val currentFloor: Int,
	val targetFloor: Int
)

data class ElevatorStatus(
	val currentFloor: Int,
	val status: String,
	val lastUpdate: Long
)

interface DadosApi {
	@POST("dados")
	suspend fun send(@Body body: DadosRequest): Response<Unit>
}

interface StatusApi {
	@GET("status")
	suspend fun getStatus(): Response<ElevatorStatus>
}

object ApiClient {
	private val baseUrl: String = "https://${BuildConfig.ESP32_HOST}/"

	private fun computePinFromRaw(): String? {
		return try {
			val cf = CertificateFactory.getInstance("X.509")
			val certInput = com.elevox.app.AppContext.get().resources.openRawResource(R.raw.esp)
			certInput.use { input ->
				val cert = cf.generateCertificate(input) as X509Certificate
				CertificatePinner.pin(cert)
			}
		} catch (_: Throwable) {
			null
		}
	}

	private fun trustedClient(builder: OkHttpClient.Builder): OkHttpClient.Builder {
		return try {
			val cf = CertificateFactory.getInstance("X.509")
			// Raw resource name is the filename without extension: esp.crt -> R.raw.esp
			val certInput = com.elevox.app.AppContext.get().resources.openRawResource(R.raw.esp)
			certInput.use { input ->
				val cert = cf.generateCertificate(input) as X509Certificate
				val certificates = HandshakeCertificates.Builder()
					.addTrustedCertificate(cert)
					.build()
				builder.sslSocketFactory(certificates.sslSocketFactory(), certificates.trustManager)
			}
			builder
		} catch (t: Throwable) {
			builder // fallback: use default trust if raw not present
		}
	}

	private val client: OkHttpClient by lazy {
		var builder = OkHttpClient.Builder()
			.connectTimeout(8, TimeUnit.SECONDS)
			.readTimeout(15, TimeUnit.SECONDS)
			.writeTimeout(15, TimeUnit.SECONDS)

		computePinFromRaw()?.let { pin ->
			val pinner = CertificatePinner.Builder()
				.add(BuildConfig.ESP32_HOST, pin)
				.build()
			builder = builder.certificatePinner(pinner)
		}

		if (BuildConfig.DEBUG) {
			builder.addInterceptor(
				HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
			)
			// DEV-ONLY: relax hostname verification for self-signed certs without SANs
			builder.hostnameVerifier { hostname, _ -> hostname == BuildConfig.ESP32_HOST }
		}

		trustedClient(builder).build()
	}

	private val moshi: Moshi by lazy {
		Moshi.Builder()
			.add(KotlinJsonAdapterFactory())
			.build()
	}

	private val retrofit: Retrofit by lazy {
		Retrofit.Builder()
			.baseUrl(baseUrl)
			.addConverterFactory(MoshiConverterFactory.create(moshi))
			.client(client)
			.build()
	}

	val dadosApi: DadosApi by lazy { retrofit.create(DadosApi::class.java) }
	val statusApi: StatusApi by lazy { retrofit.create(StatusApi::class.java) }
}

