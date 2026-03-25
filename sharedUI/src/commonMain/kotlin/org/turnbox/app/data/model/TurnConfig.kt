package org.turnbox.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class TurnConfig(
    val enabled: Boolean = false,
    val peer: String = "",
    val link: String = "",
    val user: String = "",
    val pass: String = "",
    val threads: Int = 8,
    val udp: Boolean = true,
    val noDtls: Boolean = false,
    val listen: String = "127.0.0.1:9000"
)
