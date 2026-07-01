package com.autotimelykorzina

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.entities.Plugin
import com.discord.stores.StoreStream
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

@AliucordPlugin
class AutoTimelyKorzina : Plugin() {

    companion object {
        const val CHANNEL_ID = "atk_notify_channel"
        const val NOTIFICATION_ID = 4242
        const val INTERVAL_HOURS = 12L
        const val MESSAGE_DELAY_MS = 2000L
    }

    private var scheduler: ScheduledExecutorService? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun start(context: Context) {
        createNotificationChannel(context)
        startScheduler(context)
        logger.info("[AutoTimelyKorzina] Запущен!")
    }

    override fun stop(context: Context) {
        scheduler?.shutdownNow()
        scheduler = null
        logger.info("[AutoTimelyKorzina] Остановлен.")
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AutoTimelyKorzina",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Уведомления плагина AutoTimelyKorzina"
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun startScheduler(context: Context) {
        scheduler = Executors.newSingleThreadScheduledExecutor()
        scheduler?.scheduleAtFixedRate(
            { runTask(context) },
            0L,
            INTERVAL_HOURS,
            TimeUnit.HOURS
        )
    }

    private fun runTask(context: Context) {
        try {
            val korzinaChannels = findKorzinaChannels()
            logger.info("[AutoTimelyKorzina] Найдено каналов: ${korzinaChannels.size}")

            korzinaChannels.forEachIndexed { index, channelId ->
                mainHandler.postDelayed({
                    sendMessage(channelId)
                }, index * MESSAGE_DELAY_MS)
            }

            val totalDelay = korzinaChannels.size * MESSAGE_DELAY_MS + 1000L
            mainHandler.postDelayed({ sendNotification(context) }, totalDelay)

        } catch (e: Exception) {
            logger.error("[AutoTimelyKorzina] Ошибка в runTask", e)
        }
    }

    private fun findKorzinaChannels(): List<Long> {
        val result = mutableListOf<Long>()
        try {
            val guildChannels = StoreStream.getChannels().guildChannelsMap ?: return result
            for ((_, channels) in guildChannels) {
                for ((_, channel) in channels) {
                    val name = channel.name ?: continue
                    if (name.contains("корзина", ignoreCase = true)) {
                        result.add(channel.id)
                        logger.info("[AutoTimelyKorzina] Канал: ${channel.name} (${channel.id})")
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("[AutoTimelyKorzina] Ошибка поиска каналов", e)
        }
        return result
    }

    private fun sendMessage(channelId: Long) {
        try {
            com.aliucord.api.MessageAPI.sendMessage(channelId, "/timely")
            logger.info("[AutoTimelyKorzina] Отправлено /timely в $channelId")
        } catch (e: Exception) {
            logger.error("[AutoTimelyKorzina] Ошибка отправки в $channelId", e)
        }
    }

    private fun sendNotification(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("AutoTimelyKorzina")
            .setContentText("Прошло 12 часов. Откройте Aliuco
