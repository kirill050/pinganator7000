package dev.pinganator.ping

import dev.pinganator.data.model.PingTarget

object DefaultTargets {
    val ALL = listOf(
        PingTarget(
            id = "yandex_dns",
            label = "Яндекс DNS",
            address = "77.88.8.8",
            checkMode = PingTarget.CheckMode.TCP,
            port = 53,
            subtitle = "77.88.8.8"
        ),
        PingTarget(
            id = "ya_ru",
            label = "ya.ru",
            address = "https://ya.ru",
            checkMode = PingTarget.CheckMode.HTTPS_HEAD,
            subtitle = "ya.ru"
        ),
        PingTarget(
            id = "google_dns",
            label = "Google DNS",
            address = "8.8.8.8",
            checkMode = PingTarget.CheckMode.TCP,
            port = 53,
            subtitle = "8.8.8.8"
        ),
        PingTarget(
            id = "telegram",
            label = "Telegram",
            address = "https://api.telegram.org",
            checkMode = PingTarget.CheckMode.HTTPS_HEAD,
            subtitle = "api.telegram.org"
        )
    )

    fun getEnabled(disabledIds: Set<String>): List<PingTarget> =
        ALL.filter { it.id !in disabledIds }
}
