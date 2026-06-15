# Add project specific Proguard rules here.
# You can further customize the Proguard configuration in this file.

# Keep Firestore model classes from obfuscation to prevent conflicting case sensitivity R8 crash
-keep class com.example.danallacalendar.estimate.Estimate { *; }
-keep class com.example.danallacalendar.estimate.EstimatePdf { *; }
-keep class com.example.danallacalendar.estimate.** { *; }

# Google API Client
-keep class com.google.api.** { *; }
-keep class com.google.gson.** { *; }
-keep class com.google.android.gms.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn com.google.api.**
-dontwarn com.google.common.**

# NetHttpTransport
-keep class com.google.api.client.http.** { *; }
-keep class com.google.api.client.json.** { *; }
-keep class com.google.api.client.googleapis.** { *; }
