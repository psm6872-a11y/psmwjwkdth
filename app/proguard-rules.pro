# Add project specific Proguard rules here.
# You can further customize the Proguard configuration in this file.

# Keep Firestore model classes from obfuscation to prevent conflicting case sensitivity R8 crash
-keep class com.example.danallacalendar.estimate.Estimate { *; }
-keep class com.example.danallacalendar.estimate.EstimatePdf { *; }
-keep class com.example.danallacalendar.estimate.** { *; }
