# kotlinx.serialization: @Serializable 클래스의 직렬화 정보를 유지합니다.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# 앱의 @Serializable DTO들
-keep,includedescriptorclasses class com.hellstation.**$$serializer { *; }
-keepclassmembers class com.hellstation.** {
    *** Companion;
}
-keepclasseswithmembers class com.hellstation.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**

# Retrofit
-keepattributes Signature, Exceptions
-dontwarn retrofit2.**
