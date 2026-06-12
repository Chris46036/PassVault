# Argon2 (JNI): conserva las clases con métodos nativos y sus nombres
-keep class com.lambdapioneer.argon2kt.** { *; }

# ZXing
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**
