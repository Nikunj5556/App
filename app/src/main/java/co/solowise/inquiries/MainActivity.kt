package co.solowise.inquiries

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Navy = Color(0xFF101B35)
private val Paper = Color(0xFFF7F2E8)
private val Gold = Color(0xFFC79A45)
private val Muted = Color(0xFF778092)
private val Green = Color(0xFF24785D)

data class Inquiry(val id: String, val name: String, val phone: String, val email: String,
                   val matter: String, val time: String, val created: String)

class MainActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createChannel()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { MaterialTheme(colorScheme = lightColorScheme(primary = Navy, background = Paper, surface = Color.White)) { Dashboard(this) } }
    }
    private fun createChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel("inquiries", "New inquiries", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Alerts when a new website inquiry arrives"
        })
    }
    fun notifyInquiry(item: Inquiry) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val n = NotificationCompat.Builder(this, "inquiries").setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("New website inquiry").setContentText("${item.name} · ${item.matter}")
            .setStyle(NotificationCompat.BigTextStyle().bigText("${item.name}\n${item.phone}\n${item.matter}"))
            .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).build()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(item.id.hashCode(), n)
    }
    fun call(phone: String) { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone.filter { it.isDigit() || it == '+' }}"))) }
    fun whatsapp(phone: String) {
        val digits = phone.filter(Char::isDigit)
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits")))
    }
}

@Composable
private fun Dashboard(activity: MainActivity) {
    var inquiries by remember { mutableStateOf<List<Inquiry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("All") }
    var selected by remember { mutableStateOf<Inquiry?>(null) }
    var lastIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    suspend fun refresh(first: Boolean = false) {
        try {
            val rows = fetchInquiries()
            if (!first) rows.filter { it.id !in lastIds }.forEach { activity.notifyInquiry(it) }
            lastIds = rows.map { it.id }.toSet()
            inquiries = rows
            error = null
        } catch (e: Exception) { error = "Could not load inquiries. Check connection and Supabase access." }
        loading = false
    }
    LaunchedEffect(Unit) {
        refresh(true)
        while (true) { delay(30_000); refresh() }
    }
    val visible = inquiries.filter {
        (filter == "All" || it.matter.equals(filter, true)) &&
        (query.isBlank() || it.name.contains(query, true) || it.phone.contains(query, true) || it.email.contains(query, true))
    }
    Surface(Modifier.fillMaxSize(), color = Paper) {
        Column {
            Row(Modifier.fillMaxWidth().background(Navy).padding(start=22.dp,end=22.dp,top=24.dp,bottom=24.dp), verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("SOLOWISE  /  CLIENT DESK", color=Gold, fontSize=11.sp, fontWeight=FontWeight.Bold, letterSpacing=1.8.sp)
                    Spacer(Modifier.height(9.dp))
                    Text("Inquiries", color=Color.White, fontSize=30.sp, fontWeight=FontWeight.SemiBold)
                    Text("Advocate Sharad Kumar Saxena", color=Color(0xFFD3D8E2), fontSize=13.sp)
                }
                Box(Modifier.size(46.dp).background(Color(0xFF263654), CircleShape), contentAlignment=Alignment.Center) {
                    Icon(Icons.Default.Gavel, null, tint=Gold, modifier=Modifier.size(25.dp))
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal=18.dp).offset(y=(-1).dp), horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                StatCard("TOTAL", inquiries.size.toString(), Modifier.weight(1f))
                StatCard("TODAY", inquiries.count { it.created.take(10) == java.time.LocalDate.now().toString() }.toString(), Modifier.weight(1f))
                StatCard("NEW", inquiries.size.toString(), Modifier.weight(1f))
            }
            OutlinedTextField(query, { query = it }, modifier=Modifier.fillMaxWidth().padding(horizontal=18.dp, vertical=14.dp),
                placeholder={ Text("Search name, phone or email…", fontSize=14.sp) }, leadingIcon={ Icon(Icons.Default.Search, null) },
                singleLine=true, shape=RoundedCornerShape(16.dp), colors=OutlinedTextFieldDefaults.colors(unfocusedBorderColor=Color(0xFFE1DACA), focusedBorderColor=Gold))
            Row(Modifier.padding(horizontal=18.dp), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf("All", "criminal", "marriage", "civil").forEach { item ->
                    FilterChip(selected=filter==item, onClick={filter=item}, label={Text(if(item=="All") "All matters" else item.replaceFirstChar { it.uppercase() }, fontSize=12.sp)},
                        colors=FilterChipDefaults.filterChipColors(selectedContainerColor=Navy, selectedLabelColor=Color.White))
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal=22.dp, vertical=16.dp), verticalAlignment=Alignment.CenterVertically) {
                Text("RECENT REQUESTS", fontSize=11.sp, color=Muted, fontWeight=FontWeight.Bold, letterSpacing=1.4.sp, modifier=Modifier.weight(1f))
                Text("Auto-refresh · 30 sec", fontSize=11.sp, color=Muted)
            }
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment=Alignment.Center) { CircularProgressIndicator(color=Gold) }
                error != null && inquiries.isEmpty() -> EmptyState("Connection needed", error!!)
                visible.isEmpty() -> EmptyState("All caught up", "No inquiries match this view yet.")
                else -> LazyColumn(contentPadding=PaddingValues(start=18.dp,end=18.dp,bottom=24.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    items(visible, key={it.id}) { inquiry ->
                        InquiryCard(inquiry, onClick={selected=inquiry}, onCall={activity.call(inquiry.phone)}, onWhatsApp={activity.whatsapp(inquiry.phone)})
                    }
                }
            }
        }
    }
    if (selected != null) {
        val i = selected!!
        AlertDialog(onDismissRequest={selected=null}, containerColor=Paper,
            title={ Text(i.name, fontWeight=FontWeight.SemiBold, color=Navy) },
            text={ Column(verticalArrangement=Arrangement.spacedBy(9.dp)) {
                DetailLine("PHONE", i.phone); DetailLine("EMAIL", i.email.ifBlank { "Not provided" })
                DetailLine("MATTER", i.matter); DetailLine("RECEIVED", i.created)
                Text("Initial enquiry only. Avoid sharing sensitive case documents through this app.", fontSize=12.sp, color=Muted)
            } },
            confirmButton={ TextButton(onClick={activity.whatsapp(i.phone)}) { Icon(Icons.Default.Chat, null); Spacer(Modifier.width(6.dp)); Text("WhatsApp") } },
            dismissButton={ TextButton(onClick={activity.call(i.phone)}) { Icon(Icons.Default.Call, null); Spacer(Modifier.width(6.dp)); Text("Call") } }
        )
    }
}

@Composable private fun StatCard(label: String, value: String, modifier: Modifier) {
    Card(modifier, shape=RoundedCornerShape(16.dp), colors=CardDefaults.cardColors(containerColor=Color.White), elevation=CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(13.dp)) {
            Text(label, color=Muted, fontSize=10.sp, fontWeight=FontWeight.Bold, letterSpacing=1.sp)
            Spacer(Modifier.height(5.dp)); Text(value, color=Navy, fontSize=25.sp, fontWeight=FontWeight.SemiBold)
        }
    }
}
@Composable private fun InquiryCard(i: Inquiry, onClick: ()->Unit, onCall: ()->Unit, onWhatsApp: ()->Unit) {
    Card(shape=RoundedCornerShape(18.dp), colors=CardDefaults.cardColors(containerColor=Color.White), elevation=CardDefaults.cardElevation(1.dp),
        modifier=Modifier.fillMaxWidth().clickable { onClick() }) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).background(Color(0xFFF0E7D5), CircleShape), contentAlignment=Alignment.Center) {
                    Text(i.name.trim().firstOrNull()?.uppercase() ?: "?", color=Navy, fontWeight=FontWeight.Bold, fontSize=19.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(i.name.ifBlank { "Website visitor" }, color=Navy, fontWeight=FontWeight.SemiBold, fontSize=17.sp, maxLines=1, overflow=TextOverflow.Ellipsis)
                    Text(i.phone, color=Muted, fontSize=13.sp)
                }
                Text(i.created.takeLast(8).take(5), color=Muted, fontSize=11.sp)
            }
            Spacer(Modifier.height(13.dp))
            Row(verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.background(Color(0xFFF6EEDB), RoundedCornerShape(7.dp)).padding(horizontal=10.dp, vertical=6.dp)) {
                    Text(i.matter, color=Color(0xFF76551F), fontSize=12.sp, fontWeight=FontWeight.Medium)
                }
                Spacer(Modifier.width(8.dp))
                Text(i.email.ifBlank { "Email not provided" }, color=Muted, fontSize=12.sp, maxLines=1, overflow=TextOverflow.Ellipsis, modifier=Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                Button(onClick=onWhatsApp, modifier=Modifier.weight(1f).height(42.dp), shape=RoundedCornerShape(11.dp), colors=ButtonDefaults.buttonColors(containerColor=Green)) {
                    Icon(Icons.Default.Chat, null, modifier=Modifier.size(17.dp)); Spacer(Modifier.width(7.dp)); Text("WhatsApp", fontSize=13.sp)
                }
                OutlinedButton(onClick=onCall, modifier=Modifier.weight(1f).height(42.dp), shape=RoundedCornerShape(11.dp), colors=ButtonDefaults.outlinedButtonColors(contentColor=Navy)) {
                    Icon(Icons.Default.Call, null, modifier=Modifier.size(17.dp)); Spacer(Modifier.width(7.dp)); Text("Call", fontSize=13.sp)
                }
            }
        }
    }
}
@Composable private fun DetailLine(label: String, value: String) {
    Column { Text(label, color=Muted, fontSize=10.sp, fontWeight=FontWeight.Bold, letterSpacing=1.sp); Text(value, color=Navy, fontSize=14.sp) }
}
@Composable private fun EmptyState(title: String, message: String) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment=Alignment.CenterHorizontally) {
        Icon(Icons.Default.Inbox, null, tint=Gold, modifier=Modifier.size(42.dp))
        Spacer(Modifier.height(12.dp)); Text(title, color=Navy, fontWeight=FontWeight.SemiBold, fontSize=18.sp)
        Spacer(Modifier.height(5.dp)); Text(message, color=Muted, fontSize=13.sp)
    }
}

private suspend fun fetchInquiries(): List<Inquiry> = withContext(Dispatchers.IO) {
    // Public anon key from the existing website. Replace with a restricted, server-issued credential before production.
    val supabaseUrl = "https://sdsfgqhebohsjcpqxaei.supabase.co"
    val anonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InNkc2ZncWhlYm9oc2pjcHF4YWVpIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODg3NjUwMTUsImV4cCI6MjEwNDM0MTAxNX0.Xh4vJlR9fzBSdUhwptPc6tcPCeKBiAzkXkzh-Z675Nk"
    val request = Request.Builder().url("$supabaseUrl/rest/v1/inquiries?select=id,name,phone,email,metadata,created_at&client_id=eq.sharad-kumar-saxena&order=created_at.desc&limit=100")
        .addHeader("apikey", anonKey).addHeader("Authorization", "Bearer $anonKey").build()
    OkHttpClient().newCall(request).execute().use { response ->
        if (!response.isSuccessful) error("Supabase returned ${response.code}")
        val arr = JSONArray(response.body?.string() ?: "[]")
        (0 until arr.length()).map { n ->
            val o = arr.getJSONObject(n); val m = o.optJSONObject("metadata")
            Inquiry(o.optString("id", "row-$n"), o.optString("name",""), o.optString("phone",""), o.optString("email",""),
                when(m?.optString("matter_type","") ?: "") { "criminal"->"Criminal"; "marriage"->"Marriage"; "civil"->"Civil"; else->"General" },
                m?.optString("preferred_time","") ?: "", o.optString("created_at",""))
        }
    }
}
