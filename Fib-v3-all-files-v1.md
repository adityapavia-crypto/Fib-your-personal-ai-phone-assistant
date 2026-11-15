# Fib v3 - all files (plain text)

Create each file in GitHub at the exact path in its heading (Add file > Create new file, type the path, paste ONLY the code inside the box, no ``` lines, commit). Start with the files that already exist: replace their whole content.

Total: 24 files.


## 1. `build.gradle.kts`

```kotlin
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20" apply false
}
```


## 2. `gradle.properties`

```
android.useAndroidX=true
org.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8
org.gradle.daemon=false
android.suppressUnsupportedCompileSdk=35
```


## 3. `settings.gradle.kts`

```kotlin
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { google(); mavenCentral() } }
rootProject.name = "Fib"
include(":app")
```


## 4. `.github/workflows/build.yml`

```yaml
name: Build Fib

on:
  push:
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up Java
        uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: "17"

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: "8.9"

      - name: Accept Android licenses
        run: yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses || true

      - name: Write local properties
        run: |
          echo "GEMINI_API_KEY=${{ secrets.GEMINI_API_KEY }}" > local.properties
          echo "OWNER_EMAIL=${{ secrets.OWNER_EMAIL }}" >> local.properties
          echo "PROXY_URL=${{ secrets.PROXY_URL }}" >> local.properties
          echo "PROXY_TOKEN=${{ secrets.PROXY_TOKEN }}" >> local.properties

      - name: Decode upload key (if set)
        run: |
          if [ -n "${{ secrets.KEYSTORE_BASE64 }}" ]; then
            echo "${{ secrets.KEYSTORE_BASE64 }}" | base64 -d > "$GITHUB_WORKSPACE/release.jks"
            echo "KEYSTORE_FILE=$GITHUB_WORKSPACE/release.jks" >> "$GITHUB_ENV"
          fi

      - name: Build debug APK and release bundle
        env:
          KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
          KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
        run: gradle assembleDebug bundleRelease --console=plain

      - name: Upload APK
        uses: actions/upload-artifact@v4
        with:
          name: Fib-apk
          path: app/build/outputs/apk/debug/app-debug.apk

      - name: Upload Play bundle
        uses: actions/upload-artifact@v4
        with:
          name: Fib-aab
          path: app/build/outputs/bundle/release/app-release.aab
          if-no-files-found: ignore
```


## 5. `.github/workflows/keystore.yml`

```yaml
name: Create upload key

on:
  workflow_dispatch:

jobs:
  key:
    runs-on: ubuntu-latest
    steps:
      - name: Set up Java
        uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: "17"

      - name: Generate upload key
        run: |
          PASS=$(openssl rand -hex 12)
          keytool -genkeypair -keystore upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000 -storepass "$PASS" -keypass "$PASS" -dname "CN=Fib, O=FIB, C=FR"
          base64 -w0 upload.jks > keystore_base64.txt
          printf "KEYSTORE_PASSWORD=%s\nKEY_PASSWORD=%s\nKEY_ALIAS=upload\n" "$PASS" "$PASS" > info.txt

      - name: Upload key files
        uses: actions/upload-artifact@v4
        with:
          name: upload-key
          path: |
            keystore_base64.txt
            info.txt
```


## 6. `app/build.gradle.kts`

```kotlin
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val props = Properties().apply { rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) } }
fun prop(k: String) = props.getProperty(k, "")

android {
    namespace = "com.fib.jarvis"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.fib.jarvis"
        minSdk = 26; targetSdk = 35
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "1.0"
        buildConfigField("String", "GEMINI_API_KEY", "\"${prop("GEMINI_API_KEY")}\"")
        buildConfigField("String", "OWNER_EMAIL", "\"${prop("OWNER_EMAIL")}\"")
        buildConfigField("String", "PROXY_URL", "\"${prop("PROXY_URL")}\"")
        buildConfigField("String", "PROXY_TOKEN", "\"${prop("PROXY_TOKEN")}\"")
    }
    signingConfigs {
        create("release") {
            val ks = System.getenv("KEYSTORE_FILE")
            if (!ks.isNullOrEmpty()) {
                storeFile = file(ks)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    lint { checkReleaseBuilds = false; abortOnError = false }
    buildTypes {
        release {
            isMinifyEnabled = false
            if (!System.getenv("KEYSTORE_FILE").isNullOrEmpty()) signingConfig = signingConfigs.getByName("release")
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.android.billingclient:billing-ktx:7.1.1")
    implementation("com.google.android.gms:play-services-ads:23.3.0")
    implementation("com.google.android.ump:user-messaging-platform:3.0.0")
}
```


## 7. `app/src/main/AndroidManifest.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET"/>
    <uses-permission android:name="android.permission.RECORD_AUDIO"/>
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE"/>
    <uses-permission android:name="android.permission.READ_CONTACTS"/>
    <uses-permission android:name="android.permission.CALL_PHONE"/>
    <uses-permission android:name="android.permission.SEND_SMS"/>
    <uses-permission android:name="android.permission.CAMERA"/>
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
    <uses-permission android:name="android.permission.SET_ALARM"/>
    <uses-permission android:name="com.google.android.gms.permission.AD_ID"/>

    <queries>
        <intent><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent>
    </queries>

    <application
        android:label="@string/app_name"
        android:icon="@android:drawable/ic_btn_speak_now"
        android:supportsRtl="true"
        android:localeConfig="@xml/locales_config"
        android:theme="@style/Theme.AppCompat.DayNight.NoActionBar">

        <meta-data android:name="com.google.android.gms.ads.APPLICATION_ID"
            android:value="ca-app-pub-3940256099942544~3347511713"/>

        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
        </activity>

        <service android:name=".ListenService" android:exported="false"
            android:foregroundServiceType="microphone"/>
        <receiver android:name=".KillReceiver" android:exported="false"/>
    </application>
</manifest>
```


## 8. `app/src/main/java/com/fib/jarvis/Ai.kt`

```kotlin
package com.fib.jarvis

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

object Ai {
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun http(url: String, body: String? = null): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 15000
        c.setRequestProperty("User-Agent", "FibAssistant/1.0 (Android)")
        if (BuildConfig.PROXY_URL.isNotEmpty() && url == BuildConfig.PROXY_URL) c.setRequestProperty("x-app-token", BuildConfig.PROXY_TOKEN)
        else if (url.contains("generativelanguage")) c.setRequestProperty("x-goog-api-key", BuildConfig.GEMINI_API_KEY)
        if (body != null) {
            c.requestMethod = "POST"; c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body.toByteArray()) }
        }
        return (if (c.responseCode < 400) c.inputStream else c.errorStream).bufferedReader().use { it.readText() }
    }

    suspend fun answer(ctx: Context, q: String): String = withContext(Dispatchers.IO) {
        val lang = Locale.getDefault().language.ifEmpty { "en" }
        try {
            if (FibState.isPremium(ctx) && (BuildConfig.PROXY_URL.isNotEmpty() || BuildConfig.GEMINI_API_KEY.isNotEmpty())) gemini(ctx, q, lang)
            else wikipedia(ctx, q, lang)
        } catch (e: Exception) { ctx.getString(R.string.nothing) }
    }

    private fun wikipedia(ctx: Context, q: String, lang: String): String {
        val base = "https://$lang.wikipedia.org"
        val s = JSONArray(http("$base/w/api.php?action=opensearch&limit=1&format=json&search=${enc(q)}"))
        val title = s.getJSONArray(1).optString(0)
        if (title.isEmpty()) return ctx.getString(R.string.nothing)
        val r = JSONObject(http("$base/api/rest_v1/page/summary/${enc(title)}"))
        return r.optString("extract", ctx.getString(R.string.nothing))
    }

    private fun gemini(ctx: Context, q: String, lang: String): String {
        val contents = JSONArray()
        FibState.history(ctx).forEach {
            contents.put(JSONObject().put("role", if (it.user) "user" else "model")
                .put("parts", JSONArray().put(JSONObject().put("text", it.text))))
        }
        contents.put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", q))))
        val body = JSONObject().put("contents", contents).put("systemInstruction",
            JSONObject().put("parts", JSONArray().put(JSONObject().put("text",
                "You are Fib, a fast, helpful JARVIS-style voice assistant. Be concise. Reply in the language with code '$lang'."))))
        val url = if (BuildConfig.PROXY_URL.isNotEmpty()) BuildConfig.PROXY_URL
            else "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"
        return JSONObject(http(url, body.toString())).getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
    }
}
```


## 9. `app/src/main/java/com/fib/jarvis/Billing.kt`

```kotlin
package com.fib.jarvis

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*

class Billing(private val ctx: Context, private val onChange: (Boolean) -> Unit) : PurchasesUpdatedListener {
    private val client = BillingClient.newBuilder(ctx).setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()).build()

    fun connect() = client.startConnection(object : BillingClientStateListener {
        override fun onBillingSetupFinished(r: BillingResult) { refresh() }
        override fun onBillingServiceDisconnected() {}
    })

    fun buy(a: Activity) {
        val q = QueryProductDetailsParams.newBuilder().setProductList(listOf(
            QueryProductDetailsParams.Product.newBuilder().setProductId("fib_premium_monthly")
                .setProductType(BillingClient.ProductType.SUBS).build())).build()
        client.queryProductDetailsAsync(q) { _, list ->
            val d = list.firstOrNull() ?: return@queryProductDetailsAsync
            val token = d.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: return@queryProductDetailsAsync
            val p = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).setOfferToken(token).build()
            client.launchBillingFlow(a, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(p)).build())
        }
    }

    override fun onPurchasesUpdated(r: BillingResult, p: MutableList<Purchase>?) {
        p?.forEach { if (!it.isAcknowledged) client.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder().setPurchaseToken(it.purchaseToken).build()) {} }
        refresh()
    }

    fun refresh() = client.queryPurchasesAsync(
        QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()) { _, l ->
        val on = l.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        FibState.setPremium(ctx, on); onChange(on)
    }
}
```


## 10. `app/src/main/java/com/fib/jarvis/Brain.kt`

```kotlin
package com.fib.jarvis

import android.content.Context

object Brain {
    /** One entry point for typed and spoken input. */
    suspend fun reply(c: Context, text: String): String {
        FibState.remember(c, Msg(true, text))
        val r = Router.handle(c, text) ?: Ai.answer(c, text)
        FibState.remember(c, Msg(false, r))
        return r
    }
}
```


## 11. `app/src/main/java/com/fib/jarvis/FibState.kt`

```kotlin
package com.fib.jarvis

import android.content.*
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject

data class Msg(val user: Boolean, val text: String)

object FibState {
    private fun p(c: Context) = c.getSharedPreferences("fib", Context.MODE_PRIVATE)

    fun isKilled(c: Context) = p(c).getBoolean("killed", false)
    fun isOwner(c: Context) = p(c).getBoolean("owner", false)
    fun setOwner(c: Context, v: Boolean) = p(c).edit().putBoolean("owner", v).apply()
    fun isPremium(c: Context) = p(c).getBoolean("premium", false) || isOwner(c)
    fun setPremium(c: Context, v: Boolean) = p(c).edit().putBoolean("premium", v).apply()
    fun wasStoppedBySystem(c: Context) = p(c).getBoolean("stopped", false)
    fun setStoppedBySystem(c: Context, v: Boolean) = p(c).edit().putBoolean("stopped", v).apply()

    /** Kill Fib: persists, stops the listening service. Stays off (even after restart) until enabled again. */
    fun setKilled(c: Context, v: Boolean) {
        p(c).edit().putBoolean("killed", v).apply()
        if (v) c.stopService(Intent(c, ListenService::class.java))
        else startListening(c)
    }

    fun startListening(c: Context) {
        if (isKilled(c)) return
        if (c.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        ContextCompat.startForegroundService(c, Intent(c, ListenService::class.java))
    }

    fun norm(s: String) = s.lowercase().replace(Regex("[^\\p{L}\\p{N} ]"), " ").replace(Regex("\\s+"), " ").trim()
    fun isKillPhrase(s: String) = norm(s).contains("kill fib") || norm(s).contains("kill fip")

    // ---- memory: Free = last 10 messages, Premium = last 500 ----
    fun remember(c: Context, m: Msg) {
        val arr = JSONArray(p(c).getString("mem", "[]"))
        arr.put(JSONObject().put("u", m.user).put("t", m.text))
        val keep = 500
        val out = JSONArray()
        for (i in maxOf(0, arr.length() - keep) until arr.length()) out.put(arr.get(i))
        p(c).edit().putString("mem", out.toString()).apply()
    }
    fun history(c: Context, limit: Int = if (isPremium(c)) 500 else 10): List<Msg> {
        val arr = JSONArray(p(c).getString("mem", "[]"))
        return (maxOf(0, arr.length() - limit) until arr.length()).map {
            val o = arr.getJSONObject(it); Msg(o.getBoolean("u"), o.getString("t"))
        }
    }
    fun clearMemory(c: Context) = p(c).edit().remove("mem").apply()
}
```


## 12. `app/src/main/java/com/fib/jarvis/ListenService.kt`

```kotlin
package com.fib.jarvis

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.os.*
import android.speech.*
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.util.Locale

class ListenService : Service() {
    private var sr: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var awakeUntil = 0L
    private val h = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val wake = Regex("\\bhey,? ?(fib|fip|fibb|phib)\\b")

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        if (FibState.isKilled(this)) { stopSelf(); return START_NOT_STICKY }
        FibState.setStoppedBySystem(this, false)
        val ch = "fib"
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(ch, "Fib", NotificationManager.IMPORTANCE_LOW))
        val kill = PendingIntent.getBroadcast(this, 0, Intent(this, KillReceiver::class.java).setPackage(packageName), PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, ch).setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Fib").setContentText(getString(R.string.listening)).setOngoing(true)
            .setContentIntent(open).addAction(0, getString(R.string.kill), kill).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(1, n)
        if (tts == null) tts = TextToSpeech(this) { tts?.language = Locale.getDefault() }
        listen()
        return START_STICKY
    }

    private fun listen() {
        if (FibState.isKilled(this)) return
        sr?.destroy()
        sr = SpeechRecognizer.createSpeechRecognizer(this).also { r ->
            r.setRecognitionListener(object : RecognitionListener {
                override fun onResults(b: Bundle?) {
                    b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { process(it) }
                    h.postDelayed({ listen() }, 250)
                }
                override fun onError(e: Int) { h.postDelayed({ listen() }, 1000) }
                override fun onReadyForSpeech(p: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(v: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(b: Bundle?) {}
                override fun onEvent(t: Int, b: Bundle?) {}
            })
            r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag()))
        }
    }

    private fun process(text: String) {
        val said = text.trim()
        val t = FibState.norm(said)
        if (FibState.isKillPhrase(t)) { FibState.setKilled(this, true); return }
        val m = wake.find(t)
        if (m != null) {
            val cmd = Regex("(?i)^.*?hey,?\\s*(?:fib|fip|fibb|phib)\\b[\\s,.:;!-]*").replace(said, "").trim()
            if (cmd.isEmpty()) { awakeUntil = System.currentTimeMillis() + 8000; say("...") } else run(cmd)
        } else if (System.currentTimeMillis() < awakeUntil) {
            awakeUntil = 0; run(said)
        }
    }

    private fun run(cmd: String) = scope.launch { say(Brain.reply(this@ListenService, cmd)) }

    private fun say(s: String) { tts?.speak(s.take(600), TextToSpeech.QUEUE_FLUSH, null, "fib") }

    override fun onDestroy() {
        sr?.destroy(); tts?.shutdown(); scope.cancel()
        if (!FibState.isKilled(this)) {   // stopped by the system, not by the user → ask to whitelist
            FibState.setStoppedBySystem(this, true)
            val open = PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            getSystemService(NotificationManager::class.java).notify(2,
                NotificationCompat.Builder(this, "fib").setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentText(getString(R.string.wl_stopped)).setContentIntent(open).setAutoCancel(true).build())
        }
        super.onDestroy()
    }
}

class KillReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) = FibState.setKilled(c, true)
}
```


## 13. `app/src/main/java/com/fib/jarvis/MainActivity.kt`

```kotlin
package com.fib.jarvis

import android.Manifest
import android.accounts.AccountManager
import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.os.LocaleListCompat
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var billing: Billing
    private val premium = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        premium.value = FibState.isPremium(this)
        billing = Billing(this) { premium.value = FibState.isPremium(this) }.also { it.connect() }

        val ci = UserMessagingPlatform.getConsentInformation(this)
        if (ci.canRequestAds()) MobileAds.initialize(this) {}
        ci.requestConsentInfoUpdate(this, ConsentRequestParameters.Builder().build(), {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(this) {
                if (ci.canRequestAds()) MobileAds.initialize(this) {}
            }
        }, { })

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF38BDF8))) {
                App(this, premium.value) { billing.buy(this) }
            }
        }
    }
}

private val LANGS = listOf("en" to "English", "zh" to "中文", "hi" to "हिन्दी", "es" to "Español",
    "fr" to "Français", "ar" to "العربية", "it" to "Italiano")

private fun steps(): List<Pair<String, Int>> = buildList {
    add(Manifest.permission.RECORD_AUDIO to R.string.perm_mic)
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS to R.string.perm_notif)
    add(Manifest.permission.READ_CONTACTS to R.string.perm_contacts)
    add(Manifest.permission.CALL_PHONE to R.string.perm_phone)
    add(Manifest.permission.SEND_SMS to R.string.perm_sms)
    add(Manifest.permission.CAMERA to R.string.perm_camera)
    add(Manifest.permission.ACCESS_FINE_LOCATION to R.string.perm_location)
}

private fun nextMissing(a: Activity, s: List<Pair<String, Int>>, from: Int): Int {
    var i = from
    while (i < s.size && a.checkSelfPermission(s[i].first) == android.content.pm.PackageManager.PERMISSION_GRANTED) i++
    return i
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(act: Activity, premium: Boolean, onBuy: () -> Unit) {
    val s = remember { steps() }
    var i by remember { mutableIntStateOf(nextMissing(act, s, 0)) }
    var wlAsked by remember { mutableStateOf(false) }
    var killed by remember { mutableStateOf(FibState.isKilled(act)) }
    val msgs = remember { mutableStateListOf<Msg>().apply { addAll(FibState.history(act)) } }
    var input by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    val greeting = stringResource(R.string.greeting)

    var ownerOn by remember { mutableStateOf(FibState.isOwner(act)) }
    val isPrem = premium || ownerOn
    val acct = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val name = r.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
        if (name != null && BuildConfig.OWNER_EMAIL.isNotEmpty() && name.equals(BuildConfig.OWNER_EMAIL, true)) {
            FibState.setOwner(act, true); ownerOn = true
            Toast.makeText(act, "Premium unlocked", Toast.LENGTH_SHORT).show()
        } else if (name != null) Toast.makeText(act, "Not the owner account", Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(Unit) { while (true) { killed = FibState.isKilled(act); delay(700) } }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        i = nextMissing(act, s, i + 1)
    }
    if (i < s.size) {
        AlertDialog(
            onDismissRequest = {},
            text = { Text(act.getString(s[i].second, act.getString(R.string.kill_hint))) },
            confirmButton = { TextButton({ launcher.launch(s[i].first) }) { Text(stringResource(R.string.allow)) } },
            dismissButton = { TextButton({ i = nextMissing(act, s, i + 1) }) { Text(stringResource(R.string.not_now)) } })
    } else if (!wlAsked && (Whitelist.isBlocked(act) || FibState.wasStoppedBySystem(act))) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.wl_title)) },
            text = { Text(act.getString(R.string.wl_msg, act.getString(R.string.kill_hint))) },
            confirmButton = { TextButton({ wlAsked = true; Whitelist.request(act); Whitelist.openAutostart(act) }) { Text(stringResource(R.string.allow)) } },
            dismissButton = { TextButton({ wlAsked = true }) { Text(stringResource(R.string.not_now)) } })
    } else {
        LaunchedEffect(Unit) { FibState.startListening(act) }
    }

    fun send(text: String) {
        if (text.isBlank()) return
        msgs.add(Msg(true, text)); input = ""
        if (FibState.isKillPhrase(text)) { FibState.setKilled(act, true); killed = true; msgs.add(Msg(false, act.getString(R.string.killed))); return }
        scope.launch { msgs.add(Msg(false, Brain.reply(act, text))); list.animateScrollToItem(msgs.size) }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Fib") }, actions = {
                TextButton(onClick = { if (!isPrem) onBuy() }) {
                    Text(stringResource(if (isPrem) R.string.premium_on else R.string.premium)) }
                Box {
                    TextButton({ menu = true }) { Text("🌐") }
                    DropdownMenu(menu, { menu = false }) {
                        LANGS.forEach { (code, name) ->
                            DropdownMenuItem(text = { Text(name) }, onClick = {
                                menu = false
                                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(code))
                            })
                        }
                        DropdownMenuItem(text = { Text("Owner sign-in") }, onClick = {
                            menu = false
                            acct.launch(AccountManager.newChooseAccountIntent(null, null, arrayOf("com.google"), null, null, null, null))
                        })
                    }
                }
            })
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                if (!isPrem) AdBanner()
                if (killed) {
                    Row(Modifier.fillMaxWidth().background(Color(0xFF7F1D1D), RoundedCornerShape(12.dp)).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.killed), Modifier.weight(1f))
                        Button({ FibState.setKilled(act, false); killed = false }) { Text(stringResource(R.string.enable)) }
                    }
                } else Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(input, { input = it }, Modifier.weight(1f), shape = RoundedCornerShape(24.dp),
                        placeholder = { Text(stringResource(R.string.hint)) }, singleLine = true)
                    Spacer(Modifier.width(8.dp))
                    Button({ send(input) }) { Text("➤") }
                }
            }
        }
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize().padding(horizontal = 16.dp), state = list,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Bubble(Msg(false, greeting)) }
            items(msgs) { Bubble(it) }
        }
    }
}

@Composable
fun Bubble(m: Msg) {
    Box(Modifier.fillMaxWidth(), contentAlignment = if (m.user) Alignment.CenterEnd else Alignment.CenterStart) {
        Surface(shape = RoundedCornerShape(16.dp), color = if (m.user) Color(0xFF1E3A8A) else Color(0xFF1A2233),
            modifier = Modifier.widthIn(max = 320.dp)) { Text(m.text, Modifier.padding(12.dp)) }
    }
}

@Composable
fun AdBanner() {
    AndroidView(modifier = Modifier.fillMaxWidth(), factory = { ctx ->
        AdView(ctx).apply {
            setAdSize(AdSize.BANNER)
            adUnitId = "ca-app-pub-3940256099942544/6300978111"
            loadAd(AdRequest.Builder().build())
        }
    })
}
```


## 14. `app/src/main/java/com/fib/jarvis/Router.kt`

```kotlin
package com.fib.jarvis

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.telecom.TelecomManager
import androidx.core.app.NotificationCompat

object Router {
    private const val NEW = Intent.FLAG_ACTIVITY_NEW_TASK
    private val I = RegexOption.IGNORE_CASE

    private val SMS = Regex(
        "^(?:send (?:a )?(?:text message|text|message|sms) to|text|sms|message|env[ií]a (?:un )?(?:mensaje|sms) a|envoie (?:un )?(?:message|sms) [aà]|manda (?:un )?(?:messaggio|sms) a|invia (?:un )?(?:messaggio|sms) a)\\s+(.+?)\\s+(?:saying|that says|say|diciendo|que diga|disant|en disant|dicendo|che dice)\\s+(.+)$", I)
    private val CALL = Regex("^(?:call|phone|ring|llama a|llama|appelle|appeler|chiama)\\s+(.+)$", I)

    fun handle(c: Context, raw: String): String? {
        val said = raw.trim().trimEnd('.', '!', '?')
        val t = FibState.norm(raw)

        SMS.find(said)?.let { return sms(c, it.groupValues[1].trim(), it.groupValues[2].trim()) }
        CALL.find(said)?.let { return call(c, it.groupValues[1].trim()) }

        if (Regex("flashlight|torch|linterna|lampe de poche|lampe torche|torcia|手电筒|टॉर्च|مصباح").containsMatchIn(t)) {
            val off = Regex("\\boff\\b|apaga|éteins|spegni|关|बंद|اطفئ").containsMatchIn(t)
            return try {
                val m = c.getSystemService(CameraManager::class.java)
                m.setTorchMode(m.cameraIdList[0], !off); "OK"
            } catch (e: Exception) { null }
        }
        if (Regex("alarm|timer|alarma|réveil|minuteur|sveglia|闹钟|अलार्म|منبه").containsMatchIn(t)) {
            launch(c, Intent(AlarmClock.ACTION_SHOW_ALARMS)); return "OK"
        }
        Regex("^(?:navigate to|directions to|navega a|navigue vers|naviga verso|naviga a)\\s+(.+)$").find(t)?.let {
            launch(c, Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(it.groupValues[1])))); return "OK"
        }
        Regex("^(?:open|abre|ouvre|apri)\\s+(.+)$").find(t)?.let { return openApp(c, it.groupValues[1]) }
        Regex("^(?:google|search for|busca|cherche|cerca)\\s+(.+)$").find(t)?.let {
            launch(c, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(it.groupValues[1])))); return "OK"
        }
        return null
    }

    // ---------- contacts ----------
    private fun lookup(c: Context, name: String): Pair<String, String>? {
        val digits = name.filter { it.isDigit() || it == '+' }
        if (digits.length >= 5 && name.count { it.isLetter() } == 0) return digits to digits
        if (c.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        var res: Pair<String, String>? = null
        c.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?", arrayOf("%$name%"),
            "${ContactsContract.CommonDataKinds.Phone.IS_PRIMARY} DESC"
        )?.use { if (it.moveToFirst()) res = it.getString(0) to it.getString(1).replace(" ", "").replace("-", "") }
        return res
    }

    // ---------- call (works from background via Telecom) ----------
    private fun call(c: Context, name: String): String {
        val (shown, number) = lookup(c, name) ?: return c.getString(R.string.no_contact, name)
        if (c.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            try {
                c.getSystemService(TelecomManager::class.java).placeCall(Uri.fromParts("tel", number, null), Bundle())
                return c.getString(R.string.calling, shown)
            } catch (e: Exception) { /* fall through to dialer */ }
        }
        launch(c, Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
        return c.getString(R.string.calling, shown)
    }

    // ---------- SMS ----------
    private fun sms(c: Context, name: String, body: String): String {
        val (shown, number) = lookup(c, name) ?: return c.getString(R.string.no_contact, name)
        if (c.checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
            try {
                val sm = if (Build.VERSION.SDK_INT >= 31) c.getSystemService(SmsManager::class.java) else @Suppress("DEPRECATION") SmsManager.getDefault()
                sm.sendMultipartTextMessage(number, null, sm.divideMessage(body), null, null)
                return c.getString(R.string.sms_sent, shown)
            } catch (e: Exception) { }
        }
        launch(c, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).putExtra("sms_body", body))
        return c.getString(R.string.sms_opened, shown)
    }

    private fun openApp(c: Context, label: String): String {
        val pm = c.packageManager
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        val hit = apps.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(label) } ?: return c.getString(R.string.nothing)
        val i = pm.getLaunchIntentForPackage(hit.activityInfo.packageName) ?: return c.getString(R.string.nothing)
        launch(c, i); return "OK"
    }

    /** Android may block screens started from the background, so also post a tap-to-open notification. */
    private fun launch(c: Context, i: Intent) {
        i.addFlags(NEW)
        try { c.startActivity(i) } catch (_: Exception) {}
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("fib_action", "Fib actions", NotificationManager.IMPORTANCE_HIGH))
        val pi = PendingIntent.getActivity(c, System.currentTimeMillis().toInt(), i, PendingIntent.FLAG_IMMUTABLE)
        nm.notify(3, NotificationCompat.Builder(c, "fib_action").setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Fib").setContentText(c.getString(R.string.tap_to_open))
            .setContentIntent(pi).setAutoCancel(true).setTimeoutAfter(30_000).build())
    }
}
```


## 15. `app/src/main/java/com/fib/jarvis/Whitelist.kt`

```kotlin
package com.fib.jarvis

import android.app.Activity
import android.content.*
import android.os.PowerManager
import android.provider.Settings

object Whitelist {
    fun isBlocked(c: Context) =
        !c.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(c.packageName)

    fun request(a: Activity) {
        try { a.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } catch (e: Exception) { }
    }

    fun openAutostart(a: Activity) {
        val t = listOf(
            "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            "com.samsung.android.lool" to "com.samsung.android.sm.battery.ui.BatteryActivity")
        for ((p, cl) in t) try { a.startActivity(Intent().setComponent(ComponentName(p, cl))); return } catch (_: Exception) {}
    }
}
```


## 16. `app/src/main/res/values-ar/strings.xml`

```xml
<resources>
    <string name="kill_hint">يمكنك دائمًا أن تقول “kill Fib”.</string>
    <string name="perm_mic">يحتاج Fib إلى الميكروفون لسماع “Hey Fib” وأوامرك. %1$s</string>
    <string name="perm_notif">يحتاج Fib إلى الإشعارات لإظهار أنه يستمع. %1$s</string>
    <string name="perm_contacts">يحتاج Fib إلى جهات الاتصال للاتصال بالأسماء. %1$s</string>
    <string name="perm_phone">يحتاج Fib إلى إذن الهاتف لإجراء المكالمات. %1$s</string>
    <string name="perm_camera">يحتاج Fib إلى الكاميرا لالتقاط الصور. %1$s</string>
    <string name="perm_location">يحتاج Fib إلى موقعك للملاحة. %1$s</string>
    <string name="allow">السماح</string>
    <string name="not_now">ليس الآن</string>
    <string name="wl_title">يرجى إضافة Fib إلى القائمة البيضاء</string>
    <string name="wl_msg">قد يمنع هاتفك Fib من الاستماع دائمًا. يرجى إضافة Fib إلى القائمة البيضاء (البطارية: غير مقيّد، والسماح بالتشغيل التلقائي) ليعمل “Hey Fib” دائمًا. %1$s</string>
    <string name="wl_stopped">أوقف هاتفك Fib. اضغط لإضافته إلى القائمة البيضاء.</string>
    <string name="hint">اكتب إلى Fib…</string>
    <string name="greeting">مرحبًا، أنا Fib. قل “Hey Fib” أو اكتب أدناه. مجانًا: بواسطة ويكيبيديا.</string>
    <string name="premium">⭐ بريميوم 10€/شهر</string>
    <string name="premium_on">⭐ بريميوم</string>
    <string name="killed">تم تعطيل Fib.</string>
    <string name="enable">تفعيل Fib</string>
    <string name="listening">يستمع إلى “Hey Fib”</string>
    <string name="kill">إيقاف Fib</string>
    <string name="nothing">لم أجد شيئًا.</string>
    <string name="perm_sms">يحتاج Fib إلى إذن الرسائل القصيرة لإرسال الرسائل نيابةً عنك. %1$s</string>
    <string name="calling">جارٍ الاتصال بـ %1$s</string>
    <string name="sms_sent">تم إرسال رسالة إلى %1$s</string>
    <string name="sms_opened">الرسالة جاهزة لـ %1$s</string>
    <string name="no_contact">لا أجد %1$s في جهات الاتصال</string>
    <string name="tap_to_open">اضغط للفتح</string>
</resources>
```


## 17. `app/src/main/res/values-es/strings.xml`

```xml
<resources>
    <string name="kill_hint">Siempre puedes decir “kill Fib”.</string>
    <string name="perm_mic">Fib necesita el micrófono para oír “Hey Fib” y tus órdenes. %1$s</string>
    <string name="perm_notif">Fib necesita notificaciones para mostrar que está escuchando. %1$s</string>
    <string name="perm_contacts">Fib necesita tus contactos para llamar por nombre. %1$s</string>
    <string name="perm_phone">Fib necesita acceso al teléfono para hacer llamadas. %1$s</string>
    <string name="perm_camera">Fib necesita la cámara para hacer fotos. %1$s</string>
    <string name="perm_location">Fib necesita tu ubicación para navegar. %1$s</string>
    <string name="allow">Permitir</string>
    <string name="not_now">Ahora no</string>
    <string name="wl_title">Añade Fib a la lista blanca</string>
    <string name="wl_msg">Tu teléfono puede impedir que Fib escuche siempre. Añade Fib a la lista blanca (batería: sin restricciones y permitir inicio automático) para que “Hey Fib” funcione siempre. %1$s</string>
    <string name="wl_stopped">Tu teléfono detuvo Fib. Toca para añadirlo a la lista blanca.</string>
    <string name="hint">Escribe a Fib…</string>
    <string name="greeting">Hola, soy Fib. Di “Hey Fib” o escribe abajo. Gratis: con Wikipedia.</string>
    <string name="premium">⭐ Premium 10 €/mes</string>
    <string name="premium_on">⭐ Premium</string>
    <string name="killed">Fib está desactivado.</string>
    <string name="enable">Activar Fib</string>
    <string name="listening">Escuchando “Hey Fib”</string>
    <string name="kill">Apagar Fib</string>
    <string name="nothing">No encontré nada.</string>
    <string name="perm_sms">Fib necesita permiso de SMS para enviar mensajes por ti. %1$s</string>
    <string name="calling">Llamando a %1$s</string>
    <string name="sms_sent">Mensaje enviado a %1$s</string>
    <string name="sms_opened">Mensaje listo para %1$s</string>
    <string name="no_contact">No encuentro a %1$s en tus contactos</string>
    <string name="tap_to_open">Toca para abrir</string>
</resources>
```


## 18. `app/src/main/res/values-fr/strings.xml`

```xml
<resources>
    <string name="kill_hint">Vous pouvez toujours dire « kill Fib ».</string>
    <string name="perm_mic">Fib a besoin du micro pour entendre « Hey Fib » et vos commandes. %1$s</string>
    <string name="perm_notif">Fib a besoin des notifications pour montrer qu’il écoute. %1$s</string>
    <string name="perm_contacts">Fib a besoin de vos contacts pour appeler par nom. %1$s</string>
    <string name="perm_phone">Fib a besoin d’accéder au téléphone pour appeler. %1$s</string>
    <string name="perm_camera">Fib a besoin de l’appareil photo. %1$s</string>
    <string name="perm_location">Fib a besoin de votre position pour la navigation. %1$s</string>
    <string name="allow">Autoriser</string>
    <string name="not_now">Pas maintenant</string>
    <string name="wl_title">Ajoutez Fib à la liste blanche</string>
    <string name="wl_msg">Votre téléphone peut empêcher Fib d’écouter en permanence. Ajoutez Fib à la liste blanche (batterie : sans restriction, et autoriser le démarrage auto) pour que « Hey Fib » fonctionne toujours. %1$s</string>
    <string name="wl_stopped">Votre téléphone a arrêté Fib. Touchez pour l’autoriser.</string>
    <string name="hint">Écrire à Fib…</string>
    <string name="greeting">Salut, je suis Fib. Dites « Hey Fib » ou écrivez ci-dessous. Gratuit : propulsé par Wikipédia.</string>
    <string name="premium">⭐ Premium 10 €/mois</string>
    <string name="premium_on">⭐ Premium</string>
    <string name="killed">Fib est désactivé.</string>
    <string name="enable">Activer Fib</string>
    <string name="listening">À l’écoute de « Hey Fib »</string>
    <string name="kill">Arrêter Fib</string>
    <string name="nothing">Je n’ai rien trouvé.</string>
    <string name="perm_sms">Fib a besoin de l’autorisation SMS pour envoyer des messages à votre place. %1$s</string>
    <string name="calling">J’appelle %1$s</string>
    <string name="sms_sent">Message envoyé à %1$s</string>
    <string name="sms_opened">Message prêt pour %1$s</string>
    <string name="no_contact">Je ne trouve pas %1$s dans vos contacts</string>
    <string name="tap_to_open">Touchez pour ouvrir</string>
</resources>
```


## 19. `app/src/main/res/values-hi/strings.xml`

```xml
<resources>
    <string name="kill_hint">आप हमेशा “kill Fib” कह सकते हैं।</string>
    <string name="perm_mic">“Hey Fib” और आपके आदेश सुनने के लिए Fib को माइक्रोफ़ोन चाहिए। %1$s</string>
    <string name="perm_notif">सुनते समय दिखाने के लिए Fib को नोटिफ़िकेशन चाहिए। %1$s</string>
    <string name="perm_contacts">नाम से कॉल करने के लिए Fib को संपर्क चाहिए। %1$s</string>
    <string name="perm_phone">कॉल करने के लिए Fib को फ़ोन की अनुमति चाहिए। %1$s</string>
    <string name="perm_camera">फ़ोटो के लिए Fib को कैमरा चाहिए। %1$s</string>
    <string name="perm_location">नेविगेशन के लिए Fib को लोकेशन चाहिए। %1$s</string>
    <string name="allow">अनुमति दें</string>
    <string name="not_now">अभी नहीं</string>
    <string name="wl_title">कृपया Fib को व्हाइटलिस्ट करें</string>
    <string name="wl_msg">आपका फ़ोन Fib को हमेशा सुनने से रोक सकता है। कृपया Fib को व्हाइटलिस्ट करें (बैटरी: अप्रतिबंधित, और ऑटो-स्टार्ट की अनुमति दें) ताकि “Hey Fib” हमेशा काम करे। %1$s</string>
    <string name="wl_stopped">आपके फ़ोन ने Fib को रोक दिया। व्हाइटलिस्ट करने के लिए टैप करें।</string>
    <string name="hint">Fib को संदेश भेजें…</string>
    <string name="greeting">नमस्ते, मैं Fib हूँ। “Hey Fib” कहें या नीचे लिखें। फ्री: विकिपीडिया से।</string>
    <string name="premium">⭐ प्रीमियम €10/माह</string>
    <string name="premium_on">⭐ प्रीमियम</string>
    <string name="killed">Fib बंद है।</string>
    <string name="enable">Fib चालू करें</string>
    <string name="listening">“Hey Fib” सुन रहा है</string>
    <string name="kill">Fib बंद करें</string>
    <string name="nothing">कुछ नहीं मिला।</string>
    <string name="perm_sms">आपकी ओर से संदेश भेजने के लिए Fib को SMS अनुमति चाहिए। %1$s</string>
    <string name="calling">%1$s को कॉल कर रहा हूँ</string>
    <string name="sms_sent">%1$s को संदेश भेज दिया</string>
    <string name="sms_opened">%1$s के लिए संदेश तैयार है</string>
    <string name="no_contact">आपके संपर्कों में %1$s नहीं मिला</string>
    <string name="tap_to_open">खोलने के लिए टैप करें</string>
</resources>
```


## 20. `app/src/main/res/values-it/strings.xml`

```xml
<resources>
    <string name="kill_hint">Puoi sempre dire “kill Fib”.</string>
    <string name="perm_mic">Fib ha bisogno del microfono per sentire “Hey Fib” e i tuoi comandi. %1$s</string>
    <string name="perm_notif">Fib ha bisogno delle notifiche per mostrare che sta ascoltando. %1$s</string>
    <string name="perm_contacts">Fib ha bisogno dei contatti per chiamare per nome. %1$s</string>
    <string name="perm_phone">Fib ha bisogno del telefono per fare chiamate. %1$s</string>
    <string name="perm_camera">Fib ha bisogno della fotocamera. %1$s</string>
    <string name="perm_location">Fib ha bisogno della posizione per la navigazione. %1$s</string>
    <string name="allow">Consenti</string>
    <string name="not_now">Non ora</string>
    <string name="wl_title">Aggiungi Fib alla whitelist</string>
    <string name="wl_msg">Il telefono potrebbe impedire a Fib di ascoltare sempre. Aggiungi Fib alla whitelist (batteria: senza restrizioni e avvio automatico consentito) così “Hey Fib” funziona sempre. %1$s</string>
    <string name="wl_stopped">Il telefono ha fermato Fib. Tocca per aggiungerlo alla whitelist.</string>
    <string name="hint">Scrivi a Fib…</string>
    <string name="greeting">Ciao, sono Fib. Di’ “Hey Fib” o scrivi qui sotto. Gratis: con Wikipedia.</string>
    <string name="premium">⭐ Premium 10 €/mese</string>
    <string name="premium_on">⭐ Premium</string>
    <string name="killed">Fib è disattivato.</string>
    <string name="enable">Attiva Fib</string>
    <string name="listening">In ascolto di “Hey Fib”</string>
    <string name="kill">Spegni Fib</string>
    <string name="nothing">Non ho trovato nulla.</string>
    <string name="perm_sms">Fib ha bisogno del permesso SMS per inviare messaggi al posto tuo. %1$s</string>
    <string name="calling">Chiamo %1$s</string>
    <string name="sms_sent">Messaggio inviato a %1$s</string>
    <string name="sms_opened">Messaggio pronto per %1$s</string>
    <string name="no_contact">Non trovo %1$s nei tuoi contatti</string>
    <string name="tap_to_open">Tocca per aprire</string>
</resources>
```


## 21. `app/src/main/res/values-zh/strings.xml`

```xml
<resources>
    <string name="kill_hint">你随时可以说“kill Fib”。</string>
    <string name="perm_mic">Fib需要麦克风来听“Hey Fib”和你的指令。%1$s</string>
    <string name="perm_notif">Fib需要通知权限来显示正在聆听。%1$s</string>
    <string name="perm_contacts">Fib需要通讯录来按姓名打电话。%1$s</string>
    <string name="perm_phone">Fib需要电话权限来拨打电话。%1$s</string>
    <string name="perm_camera">Fib需要相机权限来拍照。%1$s</string>
    <string name="perm_location">Fib需要位置权限来导航。%1$s</string>
    <string name="allow">允许</string>
    <string name="not_now">暂不</string>
    <string name="wl_title">请将Fib加入白名单</string>
    <string name="wl_msg">你的手机可能会阻止Fib持续聆听。请将Fib加入白名单（电池：无限制，并允许自启动），这样“Hey Fib”才能一直有效。%1$s</string>
    <string name="wl_stopped">手机已停止Fib。点按以加入白名单。</string>
    <string name="hint">给Fib发消息…</string>
    <string name="greeting">你好，我是Fib。说“Hey Fib”或在下方输入。免费版由维基百科驱动。</string>
    <string name="premium">⭐ 高级版 €10/月</string>
    <string name="premium_on">⭐ 高级版</string>
    <string name="killed">Fib已停用。</string>
    <string name="enable">启用Fib</string>
    <string name="listening">正在聆听“Hey Fib”</string>
    <string name="kill">关闭Fib</string>
    <string name="nothing">没有找到相关内容。</string>
    <string name="perm_sms">Fib需要短信权限来替你发送短信。%1$s</string>
    <string name="calling">正在呼叫%1$s</string>
    <string name="sms_sent">已向%1$s发送短信</string>
    <string name="sms_opened">给%1$s的短信已准备好</string>
    <string name="no_contact">通讯录中找不到%1$s</string>
    <string name="tap_to_open">点按打开</string>
</resources>
```


## 22. `app/src/main/res/values/strings.xml`

```xml
<resources>
    <string name="app_name">Fib</string>
    <string name="kill_hint">You can always say “kill Fib”.</string>
    <string name="perm_mic">Fib needs the microphone to hear “Hey Fib” and your commands. %1$s</string>
    <string name="perm_notif">Fib needs notifications to show that it is listening. %1$s</string>
    <string name="perm_contacts">Fib needs your contacts to call people by name. %1$s</string>
    <string name="perm_phone">Fib needs phone access to place calls. %1$s</string>
    <string name="perm_camera">Fib needs the camera for photos. %1$s</string>
    <string name="perm_location">Fib needs your location for navigation. %1$s</string>
    <string name="allow">Allow</string>
    <string name="not_now">Not now</string>
    <string name="wl_title">Please whitelist Fib</string>
    <string name="wl_msg">Your phone may block Fib from always listening. Please whitelist Fib (battery: Unrestricted, and allow auto-start) so “Hey Fib” always works. %1$s</string>
    <string name="wl_stopped">Your phone stopped Fib. Tap to whitelist it.</string>
    <string name="hint">Message Fib…</string>
    <string name="greeting">Hi, I’m Fib. Say “Hey Fib” or type below. Free: powered by Wikipedia.</string>
    <string name="premium">⭐ Premium €10/mo</string>
    <string name="premium_on">⭐ Premium</string>
    <string name="killed">Fib is disabled.</string>
    <string name="enable">Enable Fib</string>
    <string name="listening">Listening for “Hey Fib”</string>
    <string name="kill">Kill Fib</string>
    <string name="nothing">I couldn’t find anything for that.</string>
    <string name="perm_sms">Fib needs SMS permission to send text messages for you. %1$s</string>
    <string name="calling">Calling %1$s</string>
    <string name="sms_sent">Message sent to %1$s</string>
    <string name="sms_opened">Message ready for %1$s</string>
    <string name="no_contact">I can’t find %1$s in your contacts</string>
    <string name="tap_to_open">Tap to open</string>
</resources>
```


## 23. `app/src/main/res/xml/locales_config.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<locale-config xmlns:android="http://schemas.android.com/apk/res/android">
    <locale android:name="en"/><locale android:name="zh"/><locale android:name="hi"/>
    <locale android:name="es"/><locale android:name="fr"/><locale android:name="ar"/><locale android:name="it"/>
</locale-config>
```


## 24. `proxy/worker.js`

```javascript
// Cloudflare Worker: keeps the Gemini key off the phone.
// Secrets to set on the Worker: GEMINI_API_KEY and APP_TOKEN.
export default {
  async fetch(req, env) {
    if (req.method !== "POST") return new Response("ok");
    if (req.headers.get("x-app-token") !== env.APP_TOKEN) return new Response("forbidden", { status: 403 });
    const r = await fetch(
      "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent",
      { method: "POST",
        headers: { "Content-Type": "application/json", "x-goog-api-key": env.GEMINI_API_KEY },
        body: await req.text() });
    return new Response(await r.text(), { status: r.status, headers: { "Content-Type": "application/json" } });
  }
};
```
