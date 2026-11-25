package com.example.di

import androidx.room.Room
import com.example.data.local.AppDatabase
import com.example.data.parser.M3uParser
import com.example.data.remote.XtreamService
import com.example.data.repository.IptvRepository
import com.example.ui.screens.home.HomeViewModel
import com.example.ui.screens.player.PlayerViewModel
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

val appModule = module {
    single {
        OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "VLC/3.0.18 LibVLC/3.0.18")
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    single {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    single {
        Room.databaseBuilder(
            androidContext(),
            AppDatabase::class.java,
            "aura_iptv.db"
        ).fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    single { M3uParser() }
    single { XtreamService(get(), get()) }
    single { IptvRepository(get(), get(), get(), get()) }

    viewModel { HomeViewModel(get()) }
    viewModel { PlayerViewModel(androidContext(), get()) }
}
