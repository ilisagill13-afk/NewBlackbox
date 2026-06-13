# Keep WebView JS interface callbacks
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
