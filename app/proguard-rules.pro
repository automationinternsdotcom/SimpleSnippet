# SimpleSnippet ProGuard/R8 rules.
#
# Gson deserializes the config data classes reflectively from the JSON blob in
# SharedPreferences. Without these rules, R8 renames or strips their fields and
# release builds silently load empty configs — no crash, no log, snippets just
# disappear. Keep the whole data package.

-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

-keep class com.simplesnippet.app.data.** { *; }
