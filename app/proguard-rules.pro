# The platform and Vehicle SDK discover API implementations reflectively.
-keep class ai.pleos.playground.vehicle.** { *; }
-dontwarn android.car.**
# Pleos TTS: only the ON_DEVICE engine is used. Its optional login library (server mode) is excluded
# in data:voice, so its references are expected to be missing. The SDK talks to a platform service;
# keep it unshrunk like the Vehicle SDK rather than depend on R8 seeing every entry point.
-keep class ai.pleos.playground.tts.** { *; }
-dontwarn ai.pleos.playground.security.**
