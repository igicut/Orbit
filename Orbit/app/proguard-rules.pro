# ProGuard pravila projekta

# Za WebView sa JS odkomentarisi i upisi klasu interfejsa
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Odkomentarisi za brojeve linija u stack trace-u
#-keepattributes SourceFile,LineNumberTable

# Uz brojeve linija, sakriva ime izvornog fajla
#-renamesourcefileattribute SourceFile