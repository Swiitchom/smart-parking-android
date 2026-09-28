package om.swiitch.smartparking

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

data class ParkingSpot(
    val status: String = "غير متصل",
    val occupied: Boolean = false,
    val alert: Boolean = false
)

data class EventItem(
    val id: String = "",
    val message: String = "",
    val type: String = "",
    val parking: String = "",
    val timestamp: Long = 0L
)

data class ParkingUiState(
    val p1: ParkingSpot = ParkingSpot(),
    val p2: ParkingSpot = ParkingSpot(),
    val p3: ParkingSpot = ParkingSpot(),
    val bookingActive: Boolean = false,
    val bookingName: String = "",
    val bookingPlate: String = "",
    val bookingUid: String = "",
    val bookingArrival: String = "",
    val bookingDurationMinutes: Int = 0,
    val bookingEndTime: String = "",
    val bookingCostOMR: Double = 0.0,
    val systemOnline: Boolean = false,
    val lastSeen: Long = 0L,
    val lastEvent: String = "بانتظار الاتصال",
    val events: List<EventItem> = emptyList(),
    val configured: Boolean = BuildConfig.FIREBASE_DATABASE_URL.isNotBlank()
)

class FirebaseParkingRepository {
    private val root: DatabaseReference? =
        if (BuildConfig.FIREBASE_DATABASE_URL.isNotBlank()) {
            FirebaseDatabase.getInstance(BuildConfig.FIREBASE_DATABASE_URL).reference
        } else null

    fun observe(onChange: (ParkingUiState) -> Unit) {
        val ref = root ?: run {
            onChange(ParkingUiState(configured = false))
            return
        }

        var current = ParkingUiState(configured = true)

        fun emit() {
            onChange(current)
        }

        ref.child("parking").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                fun spot(code: String): ParkingSpot {
                    val s = snapshot.child(code)
                    return ParkingSpot(
                        status = s.child("status").getValue(String::class.java) ?: "متاح",
                        occupied = s.child("occupied").getValue(Boolean::class.java) ?: false,
                        alert = s.child("alert").getValue(Boolean::class.java) ?: false
                    )
                }

                current = current.copy(
                    p1 = spot("P1"),
                    p2 = spot("P2"),
                    p3 = spot("P3"),
                    configured = true
                )
                emit()
            }

            override fun onCancelled(error: DatabaseError) {
                current = current.copy(lastEvent = "parking: ${error.message}")
                emit()
            }
        })

        ref.child("booking").child("P3").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                current = current.copy(
                    bookingActive = snapshot.child("active").getValue(Boolean::class.java) ?: false,
                    bookingName = snapshot.child("name").getValue(String::class.java) ?: "",
                    bookingPlate = snapshot.child("plate").getValue(String::class.java) ?: "",
                    bookingUid = snapshot.child("uid").getValue(String::class.java) ?: "",
                    bookingArrival = snapshot.child("arrivalTime").getValue(String::class.java) ?: "",
                    bookingDurationMinutes = snapshot.child("durationMinutes").getValue(Int::class.java) ?: 0,
                    bookingEndTime = snapshot.child("endTime").getValue(String::class.java) ?: "",
                    bookingCostOMR = snapshot.child("costOMR").getValue(Double::class.java) ?: 0.0
                )
                emit()
            }

            override fun onCancelled(error: DatabaseError) {
                current = current.copy(lastEvent = "booking: ${error.message}")
                emit()
            }
        })

        ref.child("system").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                current = current.copy(
                    systemOnline = snapshot.child("online").getValue(Boolean::class.java) ?: false,
                    lastSeen = snapshot.child("lastSeen").getValue(Long::class.java) ?: 0L,
                    lastEvent = snapshot.child("lastEvent").getValue(String::class.java)
                        ?: current.lastEvent
                )
                emit()
            }

            override fun onCancelled(error: DatabaseError) {
                current = current.copy(lastEvent = "system: ${error.message}")
                emit()
            }
        })

        ref.child("events").limitToLast(30).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val items = snapshot.children.mapNotNull { e ->
                    val message = e.child("message").getValue(String::class.java)
                        ?: return@mapNotNull null

                    EventItem(
                        id = e.key ?: "",
                        message = message,
                        type = e.child("type").getValue(String::class.java) ?: "info",
                        parking = e.child("parking").getValue(String::class.java) ?: "",
                        timestamp = e.child("timestamp").getValue(Long::class.java) ?: 0L
                    )
                }.sortedByDescending { it.timestamp }

                current = current.copy(events = items)
                emit()
            }

            override fun onCancelled(error: DatabaseError) {
                current = current.copy(lastEvent = "events: ${error.message}")
                emit()
            }
        })
    }

    fun saveBooking(
        name: String,
        plate: String,
        uid: String,
        arrivalTime: String,
        durationMinutes: Int,
        endTime: String,
        costOMR: Double,
        done: (Boolean, String) -> Unit
    ) {
        val ref = root ?: run {
            done(false, "Realtime Database غير مهيأة")
            return
        }

        val cleanUid = uid.trim()
            .uppercase()
            .replace(":", " ")
            .replace("-", " ")
            .replace(Regex("\\s+"), " ")

        val data: Map<String, Any> = mapOf(
            "active" to true,
            "name" to name.trim(),
            "plate" to plate.trim(),
            "uid" to cleanUid,
            "arrivalTime" to arrivalTime,
            "durationMinutes" to durationMinutes,
            "endTime" to endTime,
            "costOMR" to costOMR,
            "createdAt" to ServerValue.TIMESTAMP
        )

        ref.child("booking").child("P3").setValue(data)
            .addOnSuccessListener {
                ref.child("system").child("lastEvent").setValue("P3 Booking Created")
                addEvent("تم إنشاء حجز P3 • " + arrivalTime + " • " + durationMinutes + " دقيقة", "booking", "P3")
                done(true, "تم تأكيد الحجز")
            }
            .addOnFailureListener {
                done(false, it.message ?: "تعذر الحجز")
            }
    }

    fun clearBooking(done: (Boolean, String) -> Unit) {
        val ref = root ?: run {
            done(false, "Realtime Database غير مهيأة")
            return
        }

        val data = mapOf(
            "active" to false,
            "name" to "",
            "plate" to "",
            "uid" to "",
            "arrivalTime" to "",
            "durationMinutes" to 0,
            "endTime" to "",
            "costOMR" to 0.0
        )

        ref.child("booking").child("P3").setValue(data)
            .addOnSuccessListener {
                ref.child("system").child("lastEvent").setValue("P3 Booking Cleared")
                addEvent("تم إلغاء حجز P3", "booking", "P3")
                done(true, "تم إلغاء الحجز")
            }
            .addOnFailureListener {
                done(false, it.message ?: "تعذر الإلغاء")
            }
    }

    private fun addEvent(message: String, type: String, parking: String) {
        val ref = root ?: return
        val data: Map<String, Any> = mapOf(
            "message" to message,
            "type" to type,
            "parking" to parking,
            "timestamp" to ServerValue.TIMESTAMP
        )
        ref.child("events").push().setValue(data)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var showSplash by remember { mutableStateOf(true) }

            LaunchedEffect(Unit) {
                delay(1400)
                showSplash = false
            }

            val prefs = remember { getSharedPreferences("smart_parking_settings", MODE_PRIVATE) }
            var darkMode by remember { mutableStateOf(prefs.getBoolean("dark_mode", false)) }
            var isArabic by remember { mutableStateOf(prefs.getBoolean("arabic", true)) }

            val scheme = if (darkMode) {
                darkColorScheme(
                    primary = Color(0xFF75D8FF),
                    secondary = Color(0xFF7CE7D4),
                    background = Color(0xFF07111F),
                    surface = Color(0xFF0F1C2C),
                    surfaceVariant = Color(0xFF18283A),
                    error = Color(0xFFFF6B6B)
                )
            } else {
                lightColorScheme(
                    primary = Color(0xFF0B57D0),
                    secondary = Color(0xFF00796B),
                    background = Color(0xFFF4F7FB),
                    surface = Color(0xFFFFFFFF),
                    surfaceVariant = Color(0xFFEAF0F7),
                    error = Color(0xFFD93025)
                )
            }

            CompositionLocalProvider(
                LocalLayoutDirection provides if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr
            ) {
                MaterialTheme(colorScheme = scheme) {
                    if (showSplash) {
                        SchoolOmanSplash(isArabic = isArabic)
                    } else {
                        SmartParkingApp(
                            darkMode = darkMode,
                            isArabic = isArabic,
                            onDarkModeChange = {
                                darkMode = it
                                prefs.edit().putBoolean("dark_mode", it).apply()
                            },
                            onLanguageChange = {
                                isArabic = it
                                prefs.edit().putBoolean("arabic", it).apply()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SmartParkingApp(
    darkMode: Boolean,
    isArabic: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    onLanguageChange: (Boolean) -> Unit
) {
    val repo = remember { FirebaseParkingRepository() }
    var state by remember { mutableStateOf(ParkingUiState()) }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var snackbarMessage by remember { mutableStateOf("") }
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        repo.observe { state = it }
        while (true) {
            now = System.currentTimeMillis()
            delay(5000)
        }
    }

    LaunchedEffect(snackbarMessage) {
        if (snackbarMessage.isNotBlank()) {
            snackbarHost.showSnackbar(snackbarMessage)
            snackbarMessage = ""
        }
    }

    val deviceOnline = state.systemOnline && state.lastSeen > 0 &&
        (now - state.lastSeen) < 120000

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = { AppHeader(isArabic = isArabic, online = deviceOnline) },
        bottomBar = {
            AppBottomBar(
                selected = selectedTab,
                isArabic = isArabic,
                onSelect = { selectedTab = it }
            )
        }
    ) { padding ->
        when (selectedTab) {
            0 -> DashboardScreen(Modifier.padding(padding), state, isArabic, deviceOnline)
            1 -> BookingScreen(
                Modifier.padding(padding),
                state,
                isArabic,
                onSave = { name, plate, uid, arrival, duration, endTime, cost ->
                    repo.saveBooking(name, plate, uid, arrival, duration, endTime, cost) { _, msg ->
                        snackbarMessage = msg
                    }
                },
                onClear = {
                    repo.clearBooking { _, msg -> snackbarMessage = msg }
                }
            )
            2 -> AlertsScreen(Modifier.padding(padding), state, isArabic)
            3 -> EventsScreen(Modifier.padding(padding), state.events, isArabic)
            else -> SettingsScreen(
                Modifier.padding(padding),
                isArabic,
                darkMode,
                state.configured,
                deviceOnline,
                onDarkModeChange,
                onLanguageChange
            )
        }
    }
}

@Composable
fun AppHeader(isArabic: Boolean, online: Boolean) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(42.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.LocalParking, null, tint = Color.White, modifier = Modifier.size(27.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    tr(isArabic, "مواقف مدارس عمان الذكية", "SCHOOL OMAN PARKING SMART"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    tr(isArabic, "نظام ذكي لإدارة مواقف المدارس", "Smart school parking management"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
            ConnectionPill(online, isArabic)
        }
    }
}

@Composable
fun ConnectionPill(online: Boolean, isArabic: Boolean) {
    val color = if (online) Color(0xFF159447) else Color(0xFFD93025)
    Row(
        modifier = Modifier.background(color.copy(alpha = 0.12f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(
            if (online) tr(isArabic, "متصل", "Online") else tr(isArabic, "غير متصل", "Offline"),
            color = color, fontWeight = FontWeight.SemiBold, fontSize = 12.sp
        )
    }
}

@Composable
fun AppBottomBar(selected: Int, isArabic: Boolean, onSelect: (Int) -> Unit) {
    val items = listOf(
        Triple(Icons.Filled.Home, "الرئيسية", "Home"),
        Triple(Icons.Filled.EventAvailable, "الحجز", "Booking"),
        Triple(Icons.Filled.Notifications, "التنبيهات", "Alerts"),
        Triple(Icons.Filled.History, "السجل", "History"),
        Triple(Icons.Filled.Settings, "الإعدادات", "Settings")
    )

    NavigationBar {
        items.forEachIndexed { index, item ->
            NavigationBarItem(
                selected = selected == index,
                onClick = { onSelect(index) },
                icon = { Icon(item.first, null) },
                label = { Text(tr(isArabic, item.second, item.third), fontSize = 10.sp) }
            )
        }
    }
}

@Composable
fun DashboardScreen(
    modifier: Modifier,
    state: ParkingUiState,
    isArabic: Boolean,
    online: Boolean
) {
    val spots = listOf(state.p1, state.p2, state.p3)
    val occupied = spots.count { it.occupied }
    val alerts = spots.count { it.alert }
    val available = spots.count {
        !it.occupied && !it.alert && !it.status.contains("محجوز") && !it.status.contains("Booked", true)
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(tr(isArabic, "لوحة التحكم", "Dashboard"),
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            tr(
                isArabic,
                "متابعة مواقف الحافلات والحجوزات والتنبيهات بشكل مباشر",
                "Live school-bus parking, booking and alert monitoring"
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryCard(Modifier.weight(1f), tr(isArabic, "متاح", "Available"), available.toString(), Color(0xFF159447))
            SummaryCard(Modifier.weight(1f), tr(isArabic, "مشغول", "Occupied"), occupied.toString(), Color(0xFFE67E22))
            SummaryCard(Modifier.weight(1f), tr(isArabic, "تنبيه", "Alerts"), alerts.toString(), Color(0xFFD93025))
        }

        ParkingSpotCard("P1", tr(isArabic, "باص الأول", "Bus 1"), state.p1)
        ParkingSpotCard("P2", tr(isArabic, "باص الثاني", "Bus 2"), state.p2)
        ParkingSpotCard("P3", tr(isArabic, "موقف الحجز", "Booking bay"), state.p3)

        Card(shape = RoundedCornerShape(20.dp)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Bolt, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(tr(isArabic, "آخر حدث", "Last event"), fontWeight = FontWeight.Bold)
                    Text(state.lastEvent, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (!online) {
            AssistChip(
                onClick = {},
                label = { Text(tr(isArabic, "تحقق من اتصال ESP32 بالإنترنت", "Check ESP32 internet connection")) },
                leadingIcon = { Icon(Icons.Filled.CloudOff, null) }
            )
        }
    }
}

@Composable
fun SummaryCard(modifier: Modifier, label: String, value: String, color: Color) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.10f))
    ) {
        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = color)
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ParkingSpotCard(code: String, title: String, spot: ParkingSpot) {
    val statusColor = when {
        spot.alert -> Color(0xFFD93025)
        spot.occupied -> Color(0xFFE67E22)
        spot.status.contains("محجوز") || spot.status.contains("Booked", true) -> Color(0xFFF2A000)
        spot.status.contains("قبول") || spot.status.contains("Accepted", true) -> Color(0xFF0B57D0)
        else -> Color(0xFF159447)
    }
    val animated by animateColorAsState(statusColor, label = "status")

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = animated.copy(alpha = 0.10f))
    ) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(54.dp).background(animated, RoundedCornerShape(17.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(code, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(Modifier.height(4.dp))
                Text(spot.status, color = animated, fontWeight = FontWeight.SemiBold)
            }
            Icon(
                when {
                    spot.alert -> Icons.Filled.Warning
                    spot.occupied -> Icons.Filled.DirectionsCar
                    else -> Icons.Filled.CheckCircle
                },
                null,
                tint = animated
            )
        }
    }
}

@Composable
fun BookingScreen(
    modifier: Modifier,
    state: ParkingUiState,
    isArabic: Boolean,
    onSave: (String, String, String, String, Int, String, Double) -> Unit,
    onClear: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var plate by rememberSaveable { mutableStateOf("") }
    var uid by rememberSaveable { mutableStateOf("") }
    var arrivalTime by rememberSaveable { mutableStateOf(defaultArrivalTime()) }
    var durationMinutes by rememberSaveable { mutableIntStateOf(30) }
    var showDurationMenu by remember { mutableStateOf(false) }

    val durationOptions = listOf(30, 60, 90, 120)
    val endTime = remember(arrivalTime, durationMinutes) {
        calculateEndTime(arrivalTime, durationMinutes)
    }
    val cost = remember(durationMinutes) {
        (durationMinutes / 30.0) * 0.500
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            tr(isArabic, "حجز الموقف P3", "P3 Booking"),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            tr(
                isArabic,
                "أدخل بيانات المركبة ووقت الوصول، وسيتم حساب نهاية الحجز والتكلفة تلقائيًا.",
                "Enter vehicle and arrival details. End time and estimated cost are calculated automatically."
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (state.bookingActive) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFF2A000).copy(alpha = 0.10f)
                )
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(13.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.EventAvailable, null, tint = Color(0xFFF2A000))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                tr(isArabic, "حجز P3 فعال", "P3 booking active"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                tr(isArabic, "موقف مخصص حتى نهاية الحجز", "Reserved until booking end time"),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider()

                    BookingInfo(Icons.Filled.Person, tr(isArabic, "الاسم", "Name"), state.bookingName)
                    BookingInfo(Icons.Filled.DirectionsCar, tr(isArabic, "رقم المركبة", "Plate"), state.bookingPlate)
                    BookingInfo(Icons.Filled.CreditCard, "UID", state.bookingUid)
                    BookingInfo(Icons.Filled.AccessTime, tr(isArabic, "وقت الوصول", "Arrival"), state.bookingArrival)
                    BookingInfo(
                        Icons.Filled.Timer,
                        tr(isArabic, "مدة الحجز", "Duration"),
                        if (state.bookingDurationMinutes > 0)
                            "${state.bookingDurationMinutes} ${tr(isArabic, "دقيقة", "min")}"
                        else "-"
                    )
                    BookingInfo(Icons.Filled.Schedule, tr(isArabic, "ينتهي", "Ends"), state.bookingEndTime)
                    BookingInfo(
                        Icons.Filled.Payments,
                        tr(isArabic, "التكلفة التقديرية", "Estimated cost"),
                        if (state.bookingCostOMR > 0)
                            String.format(Locale.US, "%.3f OMR", state.bookingCostOMR)
                        else "0.000 OMR"
                    )

                    Spacer(Modifier.height(4.dp))

                    Button(
                        onClick = onClear,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Filled.Delete, null)
                        Spacer(Modifier.width(8.dp))
                        Text(tr(isArabic, "إلغاء الحجز", "Cancel booking"))
                    }
                }
            }
        } else {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(
                    Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(tr(isArabic, "اسم صاحب الحجز", "Driver name")) },
                        leadingIcon = { Icon(Icons.Filled.Person, null) },
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = plate,
                        onValueChange = { plate = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(tr(isArabic, "رقم المركبة", "Vehicle plate")) },
                        leadingIcon = { Icon(Icons.Filled.DirectionsCar, null) },
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = uid,
                        onValueChange = { uid = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("UID") },
                        placeholder = { Text("12 34 56 78") },
                        leadingIcon = { Icon(Icons.Filled.CreditCard, null) },
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = arrivalTime,
                        onValueChange = { value ->
                            if (value.length <= 5) arrivalTime = value
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(tr(isArabic, "وقت الوصول", "Arrival time")) },
                        placeholder = { Text("07:30") },
                        leadingIcon = { Icon(Icons.Filled.AccessTime, null) },
                        supportingText = {
                            Text(tr(isArabic, "الصيغة 24 ساعة، مثال 07:30", "24-hour format, e.g. 07:30"))
                        },
                        singleLine = true
                    )

                    Box {
                        OutlinedButton(
                            onClick = { showDurationMenu = true },
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        ) {
                            Icon(Icons.Filled.Timer, null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${tr(isArabic, "مدة الحجز", "Duration")}: " +
                                    "$durationMinutes ${tr(isArabic, "دقيقة", "min")}"
                            )
                        }

                        DropdownMenu(
                            expanded = showDurationMenu,
                            onDismissRequest = { showDurationMenu = false }
                        ) {
                            durationOptions.forEach { minutes ->
                                DropdownMenuItem(
                                    text = {
                                        Text("$minutes ${tr(isArabic, "دقيقة", "min")}")
                                    },
                                    onClick = {
                                        durationMinutes = minutes
                                        showDurationMenu = false
                                    }
                                )
                            }
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)
                        ),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(15.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            BookingInfo(
                                Icons.Filled.Schedule,
                                tr(isArabic, "وقت انتهاء الحجز", "Booking ends"),
                                endTime
                            )
                            BookingInfo(
                                Icons.Filled.Payments,
                                tr(isArabic, "التكلفة التقديرية", "Estimated cost"),
                                String.format(Locale.US, "%.3f OMR", cost)
                            )
                        }
                    }

                    Button(
                        onClick = {
                            onSave(name, plate, uid, arrivalTime, durationMinutes, endTime, cost)
                            name = ""
                            plate = ""
                            uid = ""
                        },
                        enabled = name.isNotBlank() &&
                            plate.isNotBlank() &&
                            uid.isNotBlank() &&
                            isValidTime(arrivalTime),
                        modifier = Modifier.fillMaxWidth().height(54.dp)
                    ) {
                        Icon(Icons.Filled.CheckCircle, null)
                        Spacer(Modifier.width(8.dp))
                        Text(tr(isArabic, "تأكيد الحجز", "Confirm booking"))
                    }
                }
            }
        }
    }
}

@Composable
fun BookingInfo(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value.ifBlank { "-" }, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun AlertsScreen(modifier: Modifier, state: ParkingUiState, isArabic: Boolean) {
    val alerts = listOf("P1" to state.p1, "P2" to state.p2, "P3" to state.p3)
        .filter { it.second.alert }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(tr(isArabic, "التنبيهات", "Alerts"),
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        if (alerts.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF159447).copy(alpha = 0.10f)),
                shape = RoundedCornerShape(22.dp)
            ) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Verified, null, tint = Color(0xFF159447))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(tr(isArabic, "كل شيء طبيعي", "All clear"), fontWeight = FontWeight.Bold)
                        Text(tr(isArabic, "لا توجد تنبيهات نشطة حاليًا", "No active alerts"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            alerts.forEach { (code, spot) ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.10f)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("$code • ${spot.status}", fontWeight = FontWeight.Bold)
                            Text(tr(isArabic, "يتطلب الانتباه", "Needs attention"), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EventsScreen(modifier: Modifier, events: List<EventItem>, isArabic: Boolean) {
    var filter by rememberSaveable { mutableStateOf("all") }

    val filters = listOf(
        "all" to tr(isArabic, "الكل", "All"),
        "alert" to tr(isArabic, "تنبيهات", "Alerts"),
        "booking" to tr(isArabic, "حجوزات", "Bookings"),
        "movement" to tr(isArabic, "دخول/خروج", "Entry/Exit")
    )

    val filtered = events.filter {
        when (filter) {
            "alert" -> it.type == "alert"
            "booking" -> it.type == "booking"
            "movement" -> it.type == "entry" || it.type == "exit"
            else -> true
        }
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            tr(isArabic, "سجل النظام", "System Log"),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            tr(
                isArabic,
                "سجل موحد للحجوزات والتنبيهات وحركة المركبات.",
                "Unified log for bookings, alerts and vehicle movement."
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filters.forEach { (key, label) ->
                FilterChip(
                    selected = filter == key,
                    onClick = { filter = key },
                    label = { Text(label, fontSize = 11.sp) }
                )
            }
        }

        if (filtered.isEmpty()) {
            Card(shape = RoundedCornerShape(18.dp)) {
                Text(
                    tr(isArabic, "لا توجد أحداث ضمن هذا التصنيف.", "No events in this category."),
                    modifier = Modifier.padding(18.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            filtered.forEach { event ->
                val eventColor = when (event.type) {
                    "alert" -> MaterialTheme.colorScheme.error
                    "booking" -> Color(0xFFF2A000)
                    "entry" -> Color(0xFF159447)
                    "exit" -> Color(0xFF0B57D0)
                    else -> MaterialTheme.colorScheme.primary
                }

                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = eventColor.copy(alpha = 0.07f)
                    )
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val icon = when (event.type) {
                            "alert" -> Icons.Filled.Warning
                            "entry" -> Icons.Filled.Login
                            "exit" -> Icons.Filled.Logout
                            "booking" -> Icons.Filled.EventAvailable
                            else -> Icons.Filled.Info
                        }

                        Box(
                            Modifier.size(40.dp).background(
                                eventColor.copy(alpha = 0.14f),
                                CircleShape
                            ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(icon, null, tint = eventColor)
                        }

                        Spacer(Modifier.width(12.dp))

                        Column(Modifier.weight(1f)) {
                            Text(event.message, fontWeight = FontWeight.SemiBold)
                            Text(
                                listOf(event.parking, formatTime(event.timestamp))
                                    .filter { it.isNotBlank() }
                                    .joinToString(" • "),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    modifier: Modifier,
    isArabic: Boolean,
    darkMode: Boolean,
    configured: Boolean,
    online: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    onLanguageChange: (Boolean) -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(tr(isArabic, "الإعدادات", "Settings"),
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        SettingSwitch(Icons.Filled.DarkMode, tr(isArabic, "الوضع الليلي", "Dark mode"),
            darkMode, onDarkModeChange)
        SettingSwitch(Icons.Filled.Language, tr(isArabic, "الواجهة العربية", "Arabic interface"),
            isArabic, onLanguageChange)

        Card(shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                BookingInfo(Icons.Filled.CloudDone, "Firebase",
                    if (configured) tr(isArabic, "مهيأ", "Configured") else tr(isArabic, "غير مهيأ", "Not configured"))
                BookingInfo(Icons.Filled.Router, "ESP32",
                    if (online) tr(isArabic, "متصل", "Online") else tr(isArabic, "غير متصل", "Offline"))
                BookingInfo(
                    Icons.Filled.School,
                    tr(isArabic, "الهوية", "Identity"),
                    tr(isArabic, "مواقف مدارس عمان الذكية", "SCHOOL OMAN PARKING SMART")
                )
                BookingInfo(Icons.Filled.Info, tr(isArabic, "إصدار التطبيق", "App version"), "4.0.0")
            }
        }
    }
}

@Composable
fun SettingSwitch(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
fun SchoolOmanSplash(isArabic: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier
                    .size(112.dp)
                    .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(32.dp)),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.School,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(42.dp)
                    )
                    Icon(
                        Icons.Filled.DirectionsBus,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(42.dp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                if (isArabic) "مواقف مدارس عمان الذكية" else "SCHOOL OMAN PARKING SMART",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(8.dp))

            Text(
                if (isArabic) "نظام ذكي وآمن لمواقف المدارس" else "Smart & safe school parking",
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 14.sp
            )

            Spacer(Modifier.height(26.dp))

            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 3.dp,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

fun defaultArrivalTime(): String {
    val format = SimpleDateFormat("HH:mm", Locale.US)
    return format.format(Date())
}

fun isValidTime(value: String): Boolean {
    return try {
        val format = SimpleDateFormat("HH:mm", Locale.US)
        format.isLenient = false
        format.parse(value)
        true
    } catch (_: Exception) {
        false
    }
}

fun calculateEndTime(arrival: String, durationMinutes: Int): String {
    return try {
        val format = SimpleDateFormat("HH:mm", Locale.US)
        format.isLenient = false
        val date = format.parse(arrival) ?: return "-"
        val calendar = Calendar.getInstance()
        calendar.time = date
        calendar.add(Calendar.MINUTE, durationMinutes)
        format.format(calendar.time)
    } catch (_: Exception) {
        "-"
    }
}

fun tr(arabic: Boolean, ar: String, en: String): String = if (arabic) ar else en

fun formatTime(timestamp: Long): String {
    if (timestamp <= 0) return ""
    return SimpleDateFormat("dd/MM • HH:mm", Locale.getDefault()).format(Date(timestamp))
}
