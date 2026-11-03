package dev.chayeon.core.engine

import dev.chayeon.core.model.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/** Sealed generic Success resolves its payload through Any; both transports share this closed allowlist. */
val EngineJson = Json {
    encodeDefaults = true
    serializersModule = SerializersModule {
        polymorphic(Any::class) {
            subclass(Session::class)
            subclass(CommandReceipt::class)
            subclass(MyProfile::class)
            subclass(ParticipationSettings::class)
            subclass(DiscoveryState::class)
            subclass(ConnectionRequest::class)
            subclass(ConnectionGreeting::class)
            subclass(Connection::class)
            subclass(AfterRequest::class)
            subclass(AfterConnection::class)
            subclass(AfterMessage::class)
            subclass(Block::class)
            subclass(Report::class)
            subclass(SyncSnapshot::class)
        }
    }
}
