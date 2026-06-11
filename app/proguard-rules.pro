# Argon2 (JNI): conserva las clases con métodos nativos y sus nombres
-keep class org.signal.argon2.** { *; }

# ZXing
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**
