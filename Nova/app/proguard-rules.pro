# Keep kotlinx.serialization serializers & @Serializable models (User, Session, SavedPlace, EmergencyContact, Routes)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt

-keep,includedescriptorclasses class com.nova.rider.data.** { *; }
-keep,includedescriptorclasses class com.nova.rider.navigation.** { *; }

-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
-keepclassmembers class *$$serializer {
    static <fields>;
}
-keepclassmembers class * {
    *** Companion;
}

# Credential Manager / Passkeys & Play Services
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
