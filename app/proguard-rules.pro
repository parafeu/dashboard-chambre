# kotlinx.serialization: we only use the JsonElement tree API (no @Serializable classes),
# so no extra keep rules are required beyond the library's bundled consumer rules.
-dontwarn org.slf4j.**
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
