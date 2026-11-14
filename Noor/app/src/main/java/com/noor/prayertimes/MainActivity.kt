package com.noor.prayertimes

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.DateComponents
import com.batoulapps.adhan.Madhab
import com.batoulapps.adhan.Prayer
import com.batoulapps.adhan.PrayerTimes
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.delay

private val Navy = Color(0xFF0B1425)
private val Panel = Color(0xFF15233A)
private val Gold = Color(0xFFE6C477)
private val Muted = Color(0xFFB7C1D1)
private val White = Color(0xFFF7F5EF)

class MainActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createChannels()
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        PrayerScheduler.schedule(this)
        setContent { NoorApp(this) }
    }

    private fun createChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("adhan_playback", "Adhan", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Prayer-time Adhan playback"
            setSound(null, null)
        })
        manager.createNotificationChannel(NotificationChannel("prayer_reminders", "Prayer reminders", NotificationManager.IMPORTANCE_HIGH))
    }
}

data class PrayerItem(val name: String, val time: Date, val prayer: Prayer)

object PrayerCalculator {
    fun today(context: Context): List<PrayerItem> {
        val prefs = context.getSharedPreferences("noor", Context.MODE_PRIVATE)
        val lat = prefs.getString("lat", "30.0444")?.toDoubleOrNull() ?: 30.0444
        val lon = prefs.getString("lon", "31.2357")?.toDoubleOrNull() ?: 31.2357
        val coordinates = Coordinates(lat, lon)
        val c = Calendar.getInstance()
        val components = DateComponents(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
        val params = CalculationMethod.EGYPTIAN.parameters
        params.madhab = Madhab.SHAFI
        val times = PrayerTimes(coordinates, components, params)
        return listOf(
            PrayerItem("الفجر", times.fajr, Prayer.FAJR),
            PrayerItem("الظهر", times.dhuhr, Prayer.DHUHR),
            PrayerItem("العصر", times.asr, Prayer.ASR),
            PrayerItem("المغرب", times.maghrib, Prayer.MAGHRIB),
            PrayerItem("العشاء", times.isha, Prayer.ISHA)
        )
    }
}

object PrayerScheduler {
    fun schedule(context: Context) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val prefs = context.getSharedPreferences("noor", Context.MODE_PRIVATE)
        cancelScheduled(context)
        if (!prefs.getBoolean("adhan_enabled", true)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !manager.canScheduleExactAlarms()) return
        val savedIds = mutableSetOf<Int>()
        for (dayOffset in 0..6) {
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, dayOffset) }
            val date = DateComponents(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
            val lat = prefs.getString("lat", "30.0444")?.toDoubleOrNull() ?: 30.0444
            val lon = prefs.getString("lon", "31.2357")?.toDoubleOrNull() ?: 31.2357
            val params = CalculationMethod.EGYPTIAN.parameters
            params.madhab = Madhab.SHAFI
            val pt = PrayerTimes(Coordinates(lat, lon), date, params)
            val entries = listOf(
                Triple("الفجر", pt.fajr, R.raw.fajr_adhan),
                Triple("الظهر", pt.dhuhr, R.raw.adhan),
                Triple("العصر", pt.asr, R.raw.adhan),
                Triple("المغرب", pt.maghrib, R.raw.adhan),
                Triple("العشاء", pt.isha, R.raw.adhan)
            )
            entries.forEach { (name, time, sound) ->
                if (time.after(Date())) {
                    val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
                        putExtra("prayer_name", name)
                        putExtra("sound", sound)
                    }
                    val request = (time.time xor (time.time ushr 32)).toInt()
                    val pi = PendingIntent.getBroadcast(context, request, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time.time, pi)
                    savedIds.add(request)
                }
            }
        }
        prefs.edit().putStringSet("alarm_ids", savedIds.map { it.toString() }.toSet()).apply()
    }

    private fun cancelScheduled(context: Context) {
        val prefs = context.getSharedPreferences("noor", Context.MODE_PRIVATE)
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        prefs.getStringSet("alarm_ids", emptySet())?.forEach { value ->
            val request = value.toIntOrNull() ?: return@forEach
            val pi = PendingIntent.getBroadcast(context, request, Intent(context, PrayerAlarmReceiver::class.java), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
            if (pi != null) manager.cancel(pi)
        }
        prefs.edit().remove("alarm_ids").apply()
    }
}

class PrayerAlarmReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val name = intent.getStringExtra("prayer_name") ?: "الصلاة"
        val sound = intent.getIntExtra("sound", R.raw.adhan)
        val service = Intent(context, AdhanPlaybackService::class.java).apply {
            putExtra("prayer_name", name)
            putExtra("sound", sound)
        }
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(service) else context.startService(service)
        PrayerScheduler.schedule(context)
    }
}

class BootReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) { PrayerScheduler.schedule(context) }
}

class AdhanPlaybackService : android.app.Service() {
    private var player: android.media.MediaPlayer? = null
    override fun onBind(intent: Intent?): android.os.IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val name = intent?.getStringExtra("prayer_name") ?: "حان الآن موعد الصلاة"
        val sound = intent?.getIntExtra("sound", R.raw.adhan) ?: R.raw.adhan
        val notification = androidx.core.app.NotificationCompat.Builder(this, "adhan_playback")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("حان الآن موعد صلاة $name")
            .setContentText("نور - الأذان")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .build()
        startForeground(42, notification)
        try {
            player?.release()
            val created = android.media.MediaPlayer.create(this, sound)
            if (created == null) {
                stopSelf()
            } else {
                created.setOnCompletionListener { stopSelf() }
                created.setOnErrorListener { _, _, _ -> stopSelf(); true }
                created.start()
                player = created
            }
        } catch (e: Exception) { stopSelf() }
        return START_NOT_STICKY
    }
    override fun onDestroy() { player?.release(); player = null; stopForeground(STOP_FOREGROUND_REMOVE); super.onDestroy() }
}

private data class Dhikr(val text: String, val count: Int, val note: String = "")
private val morningAzkar = listOf(
    Dhikr("أصبحنا وأصبح الملك لله، والحمد لله، لا إله إلا الله وحده لا شريك له، له الملك وله الحمد وهو على كل شيء قدير. رب أسألك خير ما في هذا اليوم وخير ما بعده، وأعوذ بك من شر ما في هذا اليوم وشر ما بعده. رب أعوذ بك من الكسل وسوء الكبر، رب أعوذ بك من عذاب في النار وعذاب في القبر.", 1),
    Dhikr("اللهم بك أصبحنا وبك أمسينا وبك نحيا وبك نموت وإليك النشور.", 1),
    Dhikr("رضيت بالله ربًا، وبالإسلام دينًا، وبمحمد ﷺ نبيًا.", 3),
    Dhikr("سبحان الله وبحمده.", 100),
    Dhikr("أستغفر الله وأتوب إليه.", 100),
    Dhikr("اللهم صل وسلم على نبينا محمد.", 10)
)
private val eveningAzkar = listOf(
    Dhikr("أمسينا وأمسى الملك لله، والحمد لله، لا إله إلا الله وحده لا شريك له، له الملك وله الحمد وهو على كل شيء قدير. رب أسألك خير ما في هذه الليلة وخير ما بعدها، وأعوذ بك من شر هذه الليلة وشر ما بعدها.", 1),
    Dhikr("اللهم بك أمسينا وبك أصبحنا وبك نحيا وبك نموت وإليك المصير.", 1),
    Dhikr("رضيت بالله ربًا، وبالإسلام دينًا، وبمحمد ﷺ نبيًا.", 3),
    Dhikr("سبحان الله وبحمده.", 100),
    Dhikr("أستغفر الله وأتوب إليه.", 100),
    Dhikr("اللهم صل وسلم على نبينا محمد.", 10)
)

@Composable
fun NoorApp(context: Context) {
    var tab by remember { mutableStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); refresh++ } }
    MaterialTheme(colorScheme = darkColorScheme(primary = Gold, background = Navy, surface = Panel, onBackground = White, onSurface = White)) {
        Scaffold(containerColor = Navy, bottomBar = {
            NavigationBar(containerColor = Panel, contentColor = White) {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Default.AccessTime, null) }, label = { Text("الصلاة") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Default.AutoAwesome, null) }, label = { Text("الأذكار") })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("الإعدادات") })
            }
        }) { padding ->
            when (tab) {
                0 -> PrayerScreen(context, Modifier.padding(padding), refresh)
                1 -> AzkarScreen(Modifier.padding(padding))
                else -> SettingsScreen(context, Modifier.padding(padding))
            }
        }
    }
}

@Composable
fun PrayerScreen(context: Context, modifier: Modifier, refresh: Int) {
    val prayers = remember(refresh) { runCatching { PrayerCalculator.today(context) }.getOrDefault(emptyList()) }
    val now = Date()
    val next = prayers.firstOrNull { it.time.after(now) }
    val format = remember { SimpleDateFormat("hh:mm a", Locale("ar")) }
    Column(modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(18.dp))
        Text("☾", color = Gold, fontSize = 42.sp)
        Text("نور", color = Gold, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text("رفيقك اليومي للعبادة", color = Muted, fontSize = 14.sp)
        Spacer(Modifier.height(24.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("الصلاة القادمة", color = Muted)
                Text(next?.name ?: "الفجر", color = Gold, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text(next?.let { format.format(it.time) } ?: "—", color = White, fontSize = 24.sp)
                val remaining = next?.let { (it.time.time - now.time).coerceAtLeast(0L) / 1000L }
                if (remaining != null) Text("متبقي ${remaining / 3600} س ${remaining % 3600 / 60} د", color = Gold, fontSize = 14.sp)
                Text("مواقيت الصلاة - طريقة الحساب المصرية", color = Muted, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(20.dp))
        if (prayers.isEmpty()) Text("تعذر حساب المواقيت. راجع الإعدادات.", color = White)
        prayers.forEach { p ->
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp).background(Panel, RoundedCornerShape(16.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(p.name, color = White, fontSize = 18.sp, modifier = Modifier.weight(1f))
                Text(format.format(p.time), color = Gold, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("يتم تشغيل أذان الفجر المخصص وباقي الصلوات بالأذان الكامل.", color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun AzkarScreen(modifier: Modifier) {
    var evening by remember { mutableStateOf(false) }
    val azkar = if (evening) eveningAzkar else morningAzkar
    val counts = remember(evening) { mutableStateListOf<Int>().apply { azkar.forEach { add(it.count) } } }
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text("الأذكار اليومية", color = Gold, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !evening, onClick = { evening = false }, label = { Text("أذكار الصباح") })
            FilterChip(selected = evening, onClick = { evening = true }, label = { Text("أذكار المساء") })
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            itemsIndexed(azkar) { index, dhikr ->
                Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.End) {
                        Text(dhikr.text, color = White, fontSize = 19.sp, textAlign = TextAlign.End, lineHeight = 32.sp)
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("المتبقي: ${counts[index]} / ${dhikr.count}", color = Muted, modifier = Modifier.weight(1f))
                            OutlinedButton(onClick = { if (counts[index] > 0) counts[index] = counts[index] - 1 }) { Text("تسبيح", color = Gold) }
                            TextButton(onClick = { counts[index] = dhikr.count }) { Text("إعادة", color = Muted) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(context: Context, modifier: Modifier) {
    val prefs = remember { context.getSharedPreferences("noor", Context.MODE_PRIVATE) }
    var lat by remember { mutableStateOf(prefs.getString("lat", "30.0444") ?: "30.0444") }
    var lon by remember { mutableStateOf(prefs.getString("lon", "31.2357") ?: "31.2357") }
    var enabled by remember { mutableStateOf(prefs.getBoolean("adhan_enabled", true)) }
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("الإعدادات", color = Gold, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("الأذان", color = White, fontSize = 18.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("تشغيل الأذان تلقائيًا", color = White, modifier = Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = {
                        enabled = it
                        prefs.edit().putBoolean("adhan_enabled", it).apply()
                        PrayerScheduler.schedule(context)
                    })
                }
                Text("أذان الفجر: الملف الأول المخصص للفجر. باقي الصلوات: ملف الأذان العام.", color = Muted, fontSize = 12.sp)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("الموقع (خط العرض وخط الطول)", color = White)
                OutlinedTextField(value = lat, onValueChange = { lat = it }, label = { Text("Latitude") }, singleLine = true)
                OutlinedTextField(value = lon, onValueChange = { lon = it }, label = { Text("Longitude") }, singleLine = true)
                Text("القيم الافتراضية تقريبية لمدينة الجيزة. غيّرها لموقعك للحصول على مواقيت أدق.", color = Muted, fontSize = 12.sp)
                Button(onClick = {
                    val a = lat.toDoubleOrNull(); val b = lon.toDoubleOrNull()
                    if (a != null && b != null && a in -90.0..90.0 && b in -180.0..180.0) {
                        prefs.edit().putString("lat", a.toString()).putString("lon", b.toString()).apply()
                        PrayerScheduler.schedule(context)
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Navy)) { Text("حفظ الموقع") }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("صلاحية المنبهات الدقيقة", color = White)
                Text("اسمح للمنبهات الدقيقة حتى يعمل الأذان في موعده على إصدارات أندرويد الحديثة.", color = Muted, fontSize = 12.sp)
                Button(onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Navy)) { Text("فتح إعدادات المنبهات") }
            }
        }
    }
}
