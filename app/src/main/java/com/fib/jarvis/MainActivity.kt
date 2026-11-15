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
