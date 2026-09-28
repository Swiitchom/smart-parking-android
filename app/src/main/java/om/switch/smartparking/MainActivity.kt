package om.switch.smartparking

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLayoutDirection
import com.google.firebase.database.*

data class ParkingState(
    val p1: String = "غير متصل",
    val p2: String = "غير متصل",
    val p3: String = "غير متصل",
    val lastEvent: String = "بانتظار الاتصال",
    val bookingActive: Boolean = false,
    val bookingName: String = "",
    val bookingPlate: String = "",
    val bookingUid: String = "",
    val configured: Boolean = BuildConfig.FIREBASE_DATABASE_URL.isNotBlank()
)

class FirebaseParkingRepository {
    private val root: DatabaseReference? =
        if (BuildConfig.FIREBASE_DATABASE_URL.isNotBlank())
            FirebaseDatabase.getInstance(BuildConfig.FIREBASE_DATABASE_URL).reference
        else null

    fun observe(onChange: (ParkingState) -> Unit) {
        val ref = root ?: run {
            onChange(ParkingState(configured = false))
            return
        }

        ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val booking = snapshot.child("booking").child("P3")

                onChange(
                    ParkingState(
                        p1 = snapshot.child("parking").child("P1").child("status")
                            .getValue(String::class.java) ?: "متاح",
                        p2 = snapshot.child("parking").child("P2").child("status")
                            .getValue(String::class.java) ?: "متاح",
                        p3 = snapshot.child("parking").child("P3").child("status")
                            .getValue(String::class.java) ?: "متاح",
                        lastEvent = snapshot.child("system").child("lastEvent")
                            .getValue(String::class.java) ?: "لا توجد أحداث",
                        bookingActive = booking.child("active")
                            .getValue(Boolean::class.java) ?: false,
                        bookingName = booking.child("name")
                            .getValue(String::class.java) ?: "",
                        bookingPlate = booking.child("plate")
                            .getValue(String::class.java) ?: "",
                        bookingUid = booking.child("uid")
                            .getValue(String::class.java) ?: "",
                        configured = true
                    )
                )
            }

            override fun onCancelled(error: DatabaseError) {
                onChange(
                    ParkingState(
                        lastEvent = "Firebase: ${error.message}",
                        configured = true
                    )
                )
            }
        })
    }

    fun saveBooking(
        name: String,
        plate: String,
        uid: String,
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

        val data = mapOf(
            "active" to true,
            "name" to name.trim(),
            "plate" to plate.trim(),
            "uid" to cleanUid
        )

        ref.child("booking").child("P3").setValue(data)
            .addOnSuccessListener {
                ref.child("system").child("lastEvent")
                    .setValue("P3 Booking Created")
                done(true, "تم الحجز")
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
            "uid" to ""
        )

        ref.child("booking").child("P3").setValue(data)
            .addOnSuccessListener {
                ref.child("system").child("lastEvent")
                    .setValue("P3 Booking Cleared")
                done(true, "تم إلغاء الحجز")
            }
            .addOnFailureListener {
                done(false, it.message ?: "تعذر الإلغاء")
            }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalLayoutDirection provides LayoutDirection.Rtl
                ) {
                    SmartParkingScreen()
                }
            }
        }
    }
}

@Composable
fun SmartParkingScreen() {
    val repo = remember { FirebaseParkingRepository() }
    var state by remember { mutableStateOf(ParkingState()) }
    var showBooking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        repo.observe { state = it }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("المواقف الذكية") }
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            if (!state.configured) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Firebase Realtime Database غير مكتملة",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }

            ParkingCard("P1", "باص الأول", state.p1)
            ParkingCard("P2", "باص الثاني", state.p2)
            ParkingCard("P3", "موقف الحجز", state.p3)

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "حجز P3",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(Modifier.height(8.dp))

                    if (state.bookingActive) {
                        Text("الاسم: ${state.bookingName}")
                        Text("رقم المركبة: ${state.bookingPlate}")
                        Text("UID: ${state.bookingUid}")

                        Spacer(Modifier.height(12.dp))

                        Button(
                            onClick = {
                                repo.clearBooking { _, msg ->
                                    message = msg
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("إلغاء الحجز")
                        }
                    } else {
                        Button(
                            onClick = { showBooking = true },
                            enabled = state.configured,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("حجز الموقف الثالث")
                        }
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "آخر حدث",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(state.lastEvent)
                }
            }

            if (message.isNotBlank()) {
                Text(message)
            }
        }
    }

    if (showBooking) {
        BookingDialog(
            onDismiss = { showBooking = false },
            onSave = { name, plate, uid ->
                repo.saveBooking(name, plate, uid) { ok, msg ->
                    message = msg
                    if (ok) showBooking = false
                }
            }
        )
    }
}

@Composable
fun ParkingCard(
    code: String,
    title: String,
    status: String
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "$code - $title",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(6.dp))
            Text(status)
        }
    }
}

@Composable
fun BookingDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var plate by remember { mutableStateOf("") }
    var uid by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("حجز P3") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("الاسم") },
                    singleLine = true
                )

                OutlinedTextField(
                    value = plate,
                    onValueChange = { plate = it },
                    label = { Text("رقم المركبة") },
                    singleLine = true
                )

                OutlinedTextField(
                    value = uid,
                    onValueChange = { uid = it },
                    label = { Text("UID البطاقة") },
                    placeholder = { Text("12 34 56 78") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, plate, uid) },
                enabled = name.isNotBlank() &&
                    plate.isNotBlank() &&
                    uid.isNotBlank()
            ) {
                Text("تأكيد الحجز")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}
