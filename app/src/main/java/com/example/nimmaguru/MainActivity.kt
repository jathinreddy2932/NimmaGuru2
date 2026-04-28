package com.nimmaguru.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.nimmaguru.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppPrefs.init(applicationContext)

        setContent {
            NimmaGuruApp()
        }
    }
}

// --- Data Models (Firestore Optimized) ---

data class GuruProfile(
    val uid: String = "",
    val name: String = "",
    val village: String = "",
    val skills: String = "",
    val freeHours: String = "",
    val appreciationsCount: Int = 0
)

data class Session(
    val id: String = "",
    val guruUid: String = "",
    val guruName: String = "",
    val subject: String = "",
    val time: String = "",
    val medium: String = "",
    val location: String = "Samudaya Bhavana",
    val description: String = "",
    val meetingLink: String = "",
    val timestamp: Long = 0L
)

data class ThankYouNote(
    val id: String = "",
    val guruUid: String = "",
    val sessionSubject: String = "",
    val studentName: String = "",
    val message: String = "",
    val timestamp: Long = 0L
)

// --- Simple Localized Translation Engine (English / Kannada) ---

object Str {
    fun t(en: String, kn: String): String {
        return if (AppState.selectedLanguage == "Kannada") kn else en
    }

    fun dyn(text: String): String {
        if (AppState.selectedLanguage != "Kannada") return text
        return when (text.trim().lowercase()) {
            "math", "mathematics" -> "ಗಣಿತ"
            "science" -> "ವಿಜ್ಞಾನ"
            "carpentry" -> "ಮರಗೆಲಸ"
            "values" -> "ಮೌಲ್ಯಗಳು"
            "english" -> "ಇಂಗ್ಲಿಷ್"
            "physics" -> "ಭೌತಶಾಸ್ತ್ರ"
            "online" -> "ಆನ್‌ಲೈನ್"
            "samudaya bhavana" -> "ಸಮುದಾಯ ಭವನ"
            else -> text
        }
    }

    fun num(text: String): String {
        if (AppState.selectedLanguage != "Kannada") return text
        val kannadaDigits = arrayOf('೦', '೧', '೨', '೩', '೪', '೫', '೬', '೭', '೮', '೯')
        return text
            .map { if (it.isDigit()) kannadaDigits[it - '0'] else it }
            .joinToString("")
            .replace("am", " ಬೆಳಿಗ್ಗೆ", ignoreCase = true)
            .replace("pm", " ಸಂಜೆ", ignoreCase = true)
            .trim()
    }
}

// --- App Preferences ---

object AppPrefs {
    private const val PREFS_NAME = "nimma_guru_prefs"
    private const val KEY_ROLE = "role"
    private lateinit var prefs: android.content.SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    fun saveRole(role: String) { prefs.edit().putString(KEY_ROLE, role).apply() }
    fun getRole(): String = prefs.getString(KEY_ROLE, "") ?: ""
    fun clear() { prefs.edit().clear().apply() }
}

// --- Global App State & Firestore Handlers ---

object AppState {
    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance().apply {
        firestoreSettings = com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(false)
            .build()
    }

    var currentUserName by mutableStateOf("")
    var currentUserRole by mutableStateOf("")
    var firebaseUser by mutableStateOf<FirebaseUser?>(null)
    var isAuthReady by mutableStateOf(false)
    var currentGuruProfile by mutableStateOf<GuruProfile?>(null)

    var selectedLanguage by mutableStateOf("English")

    val sessions = mutableStateListOf<Session>()
    var isLoadingSessions by mutableStateOf(false)
    
    val topGurus = mutableStateListOf<GuruProfile>()
    val thankYouNotes = mutableStateListOf<ThankYouNote>()

    private var authListener: FirebaseAuth.AuthStateListener? = null
    private var sessionsRegistration: ListenerRegistration? = null
    private var wallOfFameRegistration: ListenerRegistration? = null
    private var notesRegistration: ListenerRegistration? = null

    fun startAuthListener() {
        if (authListener != null) return
        authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            firebaseUser = firebaseAuth.currentUser
            isAuthReady = true

            if (firebaseAuth.currentUser != null) {
                currentUserName = firebaseAuth.currentUser?.email?.substringBefore("@") ?: "User"
                currentUserRole = AppPrefs.getRole()
                if (currentUserRole == "guru") fetchGuruProfile(firebaseAuth.currentUser!!.uid)
            } else {
                currentUserName = ""
                currentGuruProfile = null
                currentUserRole = if (AppPrefs.getRole() == "student") "student" else ""
            }
        }
        auth.addAuthStateListener(authListener!!)
    }

    fun stopAuthListener() {
        authListener?.let { auth.removeAuthStateListener(it) }
        authListener = null
    }

    private fun fetchGuruProfile(uid: String) {
        firestore.collection("users").document(uid).addSnapshotListener { snap, _ ->
            if (snap != null && snap.exists()) {
                currentGuruProfile = snap.toObject(GuruProfile::class.java)
            } else {
                currentGuruProfile = null // Profile incomplete
            }
        }
    }

    fun attachSessionsListener() {
        sessionsRegistration?.remove()
        isLoadingSessions = true
        sessionsRegistration = firestore.collection("sessions")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                sessions.clear()
                snapshot?.documents?.forEach { doc ->
                    doc.toObject(Session::class.java)?.let { sessions.add(it) }
                }
                isLoadingSessions = false
            }
    }

    fun detachSessionsListener() {
        sessionsRegistration?.remove()
        sessionsRegistration = null
    }

    fun attachWallOfFameListener() {
        wallOfFameRegistration?.remove()
        wallOfFameRegistration = firestore.collection("users")
            .orderBy("appreciationsCount", Query.Direction.DESCENDING)
            .limit(10)
            .addSnapshotListener { snapshot, _ ->
                topGurus.clear()
                snapshot?.documents?.forEach { doc ->
                    doc.toObject(GuruProfile::class.java)?.let { topGurus.add(it) }
                }
            }
        
        notesRegistration?.remove()
        notesRegistration = firestore.collection("appreciations")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(20)
            .addSnapshotListener { snapshot, _ ->
                thankYouNotes.clear()
                snapshot?.documents?.forEach { doc ->
                    doc.toObject(ThankYouNote::class.java)?.let { thankYouNotes.add(it) }
                }
            }
    }

    fun saveGuruProfile(profile: GuruProfile, onDone: () -> Unit, onError: (String) -> Unit) {
        firestore.collection("users").document(profile.uid).set(profile)
            .addOnSuccessListener { onDone() }
            .addOnFailureListener { e -> onError(e.message ?: "Unknown Error") }
    }

    fun saveSession(session: Session, onDone: () -> Unit, onError: (String) -> Unit) {
        firestore.collection("sessions").document(session.id).set(session)
            .addOnSuccessListener { onDone() }
            .addOnFailureListener { e -> onError(e.message ?: "Unknown Error") }
    }

    fun deleteSession(sessionId: String, onDone: () -> Unit) {
        firestore.collection("sessions").document(sessionId).delete().addOnSuccessListener { onDone() }
    }

    fun sendAppreciation(guruUid: String, subject: String, text: String, onDone: () -> Unit, onError: (String) -> Unit = {}) {
        val noteId = UUID.randomUUID().toString()
        val note = ThankYouNote(id = noteId, guruUid = guruUid, sessionSubject = subject, studentName = "Student", message = text, timestamp = System.currentTimeMillis())
        
        // Save the appreciation note first
        firestore.collection("appreciations").document(noteId).set(note)
            .addOnSuccessListener {
                // Then increment the guru's counter using set+merge (works even if doc doesn't exist)
                val guruRef = firestore.collection("users").document(guruUid)
                guruRef.set(
                    mapOf("appreciationsCount" to FieldValue.increment(1)),
                    com.google.firebase.firestore.SetOptions.merge()
                ).addOnSuccessListener { onDone() }
                 .addOnFailureListener { e ->
                     // Note was saved but counter failed — still call onDone
                     onDone()
                 }
            }
            .addOnFailureListener { e -> onError(e.message ?: "Failed to send appreciation") }
    }

    fun login(email: String, pass: String, role: String, onDone: () -> Unit, onError: (String) -> Unit) {
        auth.signInWithEmailAndPassword(email, pass).addOnSuccessListener {
            AppPrefs.saveRole(role)
            currentUserRole = role
            if (role == "guru" && auth.currentUser != null) {
                fetchGuruProfile(auth.currentUser!!.uid)
            }
            onDone()
        }.addOnFailureListener {
            // Fallback: If login fails (user doesn't exist), try to register them automatically
            auth.createUserWithEmailAndPassword(email, pass).addOnSuccessListener {
                AppPrefs.saveRole(role)
                currentUserRole = role
                onDone()
            }.addOnFailureListener { e2 -> 
                onError(e2.message ?: "Invalid credentials or email format.") 
            }
        }
    }

    fun logout() {
        detachSessionsListener()
        wallOfFameRegistration?.remove()
        notesRegistration?.remove()
        AppPrefs.clear()
        auth.signOut()
    }
}

// --- Shared UI Components ---

@Composable
fun PremiumButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isSecondary: Boolean = false,
    icon: @Composable (() -> Unit)? = null
) {
    val buttonHeight = 56.dp
    val buttonShape = RoundedCornerShape(16.dp)

    if (isSecondary) {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier
                .fillMaxWidth()
                .height(buttonHeight),
            shape = buttonShape,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
        ) {
            if (icon != null) {
                icon()
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    } else {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier
                .fillMaxWidth()
                .height(buttonHeight),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            ),
            shape = buttonShape,
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 0.dp)
        ) {
            if (icon != null) {
                icon()
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}


@Composable
fun AvatarCircle(name: String, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 48.dp) {
    val initial = name.take(1).uppercase()
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(initial, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.4).sp)
    }
}

@Composable
fun VoiceFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = Color.White,
        shape = CircleShape,
        modifier = modifier
            .size(72.dp)
            .shadow(8.dp, CircleShape)
    ) {
        Icon(Icons.Default.Mic, contentDescription = "Voice Search", modifier = Modifier.size(36.dp))
    }
}


// --- Main Application Navigation ---

@Composable
fun NimmaGuruApp() {
    val navController = rememberNavController()
    DisposableEffect(Unit) {
        AppState.startAuthListener()
        onDispose { AppState.stopAuthListener() }
    }

    LaunchedEffect(AppState.isAuthReady, AppState.firebaseUser, AppState.currentUserRole) {
        if (!AppState.isAuthReady) return@LaunchedEffect
        val dest = if (AppState.firebaseUser != null) {
            if (AppState.currentUserRole == "guru") "guru_dashboard" else "student_dashboard"
        } else if (AppPrefs.getRole() == "student" || AppState.currentUserRole == "student") {
            "student_dashboard"
        } else "role_select"

        navController.navigate(dest) { popUpTo(0); launchSingleTop = true }
    }

    NimmaGuruTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            if (!AppState.isAuthReady) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            } else {
                NavHost(navController, "role_select") {
                    composable("role_select") { RoleSelectionScreen(navController) }
                    composable("login/guru") { LoginScreen(navController, "guru") }
                    composable("guru_dashboard") { GuruDashboardScreen(navController) }
                    composable("student_dashboard") { StudentDashboardScreen(navController) }
                    composable("add_session") { AddSessionScreen(navController) }
                    composable("edit_profile") { EditProfileScreen(navController) }
                    composable("wall_of_fame") { WallOfFameScreen(navController) }
                }
            }
        }
    }
}

// --- 1. Role Selection & Login ---

@Composable
fun RoleSelectionScreen(navController: NavHostController) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(1000)) + slideInVertically(initialOffsetY = { 50 }),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.School, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(20.dp))
                Text(Str.t("Nimma-Guru", "ನಿಮ್ಮ-ಗುರು"), style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
                Text(Str.t("Connecting Wisdom with Youth", "ಜ್ಞಾನ ಮತ್ತು ಯುವಕರ ಸೇತುವೆ"), style = MaterialTheme.typography.titleMedium, color = HeritageGold)
                
                Spacer(Modifier.height(56.dp))
                
                Card(
                    modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        AvatarCircle(name = "G", size = 64.dp)
                        Spacer(Modifier.height(16.dp))
                        Text(Str.t("Teaching Professionals", "ನಿವೃತ್ತ ವೃತ್ತಿಪರರು"), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(20.dp))
                        PremiumButton(
                            onClick = { navController.navigate("login/guru") },
                            text = Str.t("Login as Mentor", "ಗುರುವಾಗಿ ಲಾಗಿನ್ ಮಾಡಿ")
                        )
                    }
                }
                
                Spacer(Modifier.height(24.dp))
                
                Card(
                    modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = HeritageSoftTeal)
                ) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Person, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(16.dp))
                        Text(Str.t("Students / Learners", "ವಿದ್ಯಾರ್ಥಿಗಳು / ಕಲಿಯುವವರು"), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(20.dp))
                        PremiumButton(
                            onClick = { 
                                AppPrefs.saveRole("student")
                                AppState.currentUserRole = "student"
                                navController.navigate("student_dashboard") 
                            },
                            text = Str.t("Login as Student", "ವಿದ್ಯಾರ್ಥಿಯಾಗಿ ಲಾಗಿನ್ ಮಾಡಿ"),
                            isSecondary = true
                        )
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(navController: NavHostController, role: String) {
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoggingIn by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                Str.t("Welcome Back", "ಮತ್ತೆ ಸ್ವಾಗತ"),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))
            Text(
                Str.t("Login to continue your journey", "ನಿಮ್ಮ ಪ್ರಯಾಣವನ್ನು ಮುಂದುವರಿಸಲು ಲಾಗಿನ್ ಮಾಡಿ"),
                style = MaterialTheme.typography.titleMedium,
                color = HeritageGold
            )
            Spacer(Modifier.height(56.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(Str.t("Email", "ಇಮೇಲ್")) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Default.Email, null, tint = MaterialTheme.colorScheme.primary) },
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                )
            )
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(Str.t("Password", "ಪಾಸ್‌ವರ್ಡ್")) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary) },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(imageVector = image, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                )
            )
            Spacer(Modifier.height(48.dp))

            PremiumButton(
                onClick = {
                    isLoggingIn = true
                    AppState.login(email.trim(), password.trim(), role,
                        onDone = { isLoggingIn = false },
                        onError = { msg -> isLoggingIn = false; Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
                    )
                },
                text = if (isLoggingIn) Str.t("Logging in...", "ಲಾಗಿನ್ ಆಗುತ್ತಿದೆ...") else Str.t("Login", "ಲಾಗಿನ್ ಮಾಡಿ"),
                enabled = !isLoggingIn
            )
        }
    }
}


// --- 2. Edit Profile for Active Aging (Big & Simple UI) ---

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditProfileScreen(navController: NavHostController) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(AppState.currentGuruProfile?.name ?: "") }
    var village by remember { mutableStateOf(AppState.currentGuruProfile?.village ?: "") }
    var skills by remember { mutableStateOf(AppState.currentGuruProfile?.skills ?: "") }
    var freeHours by remember { mutableStateOf(AppState.currentGuruProfile?.freeHours ?: "") }
    var isSaving by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { 
            TopAppBar(
                title = { Text(Str.t("My Mentor Profile", "ನನ್ನ ಪ್ರೊಫೈಲ್")) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            ) 
        }
    ) { padding ->
        Column(
            Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(24.dp)
                .fillMaxSize(), 
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(Str.t("Help students in your village find you!", "ವಿದ್ಯಾರ್ಥಿಗಳಿಗೆ ಸಹಾಯ ಮಾಡಿ!"), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            
            OutlinedTextField(
                value = name, 
                onValueChange = { name=it }, 
                label = { Text(Str.t("Your Full Name", "ನಿಮ್ಮ ಪೂರ್ಣ ಹೆಸರು")) }, 
                modifier = Modifier.fillMaxWidth(), 
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.outlinedTextFieldColors(focusedBorderColor = MaterialTheme.colorScheme.primary)
            )
            OutlinedTextField(
                value = village, 
                onValueChange = { village=it }, 
                label = { Text(Str.t("Your Village or Area", "ನಿಮ್ಮ ಗ್ರಾಮ ಅಥವಾ ಪ್ರದೇಶ")) }, 
                modifier = Modifier.fillMaxWidth(), 
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.outlinedTextFieldColors(focusedBorderColor = MaterialTheme.colorScheme.primary)
            )
            OutlinedTextField(
                value = skills, 
                onValueChange = { skills=it }, 
                label = { Text(Str.t("Skills (e.g., Math, Carpentry)", "ಕೌಶಲ್ಯಗಳು (ಉದಾ: ಗಣಿತ, ಮರಗೆಲಸ)")) }, 
                modifier = Modifier.fillMaxWidth(), 
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.outlinedTextFieldColors(focusedBorderColor = MaterialTheme.colorScheme.primary)
            )
            OutlinedTextField(
                value = freeHours, 
                onValueChange = { freeHours=it }, 
                label = { Text(Str.t("Free Hours (e.g., 5 PM - 6 PM)", "ಬಿಡುವಿನ ವೇಳೆ (ಉದಾ: 5 PM - 6 PM)")) }, 
                modifier = Modifier.fillMaxWidth(), 
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.outlinedTextFieldColors(focusedBorderColor = MaterialTheme.colorScheme.primary)
            )

            Spacer(Modifier.weight(1f))
            PremiumButton(
                onClick = {
                    isSaving = true
                    val p = GuruProfile(uid = AppState.auth.currentUser!!.uid, name = name, village = village, skills = skills, freeHours = freeHours, appreciationsCount = AppState.currentGuruProfile?.appreciationsCount ?: 0)
                    AppState.saveGuruProfile(p, 
                        onDone = { 
                            isSaving = false
                            navController.popBackStack() 
                        },
                        onError = { 
                            isSaving = false
                            Toast.makeText(context, "Error: $it", Toast.LENGTH_LONG).show() 
                        }
                    )
                },
                text = if (isSaving) Str.t("Saving...", "ಉಳಿಸಲಾಗುತ್ತಿದೆ...") else Str.t("Save Profile", "ಉಳಿಸಿ"),
                enabled = !isSaving
            )
        }
    }
}


// --- 3. Guru Dashboard ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuruDashboardScreen(navController: NavHostController) {
    val uid = AppState.auth.currentUser?.uid.orEmpty()
    val mySessions = AppState.sessions.filter { it.guruUid == uid }

    DisposableEffect(Unit) {
        AppState.attachSessionsListener()
        onDispose { AppState.detachSessionsListener() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Str.t("Mentor Dashboard", "ಮಾರ್ಗದರ್ಶಕ ಡ್ಯಾಶ್‌ಬೋರ್ಡ್")) },
                navigationIcon = { IconButton(onClick = { AppState.logout() }) { Icon(Icons.Default.ExitToApp, null) } },
                actions = { LanguageMenu() },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                    navigationIconContentColor = MaterialTheme.colorScheme.primary,
                    actionIconContentColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 8.dp) {
                NavigationBarItem(
                    selected = true,
                    onClick = { },
                    icon = { Icon(Icons.Default.Home, null) },
                    label = { Text(Str.t("Home", "ಮುಖಪುಟ")) }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate("wall_of_fame") },
                    icon = { Icon(Icons.Default.EmojiEvents, null) },
                    label = { Text(Str.t("Fame", "ಕೀರ್ತಿ")) }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate("edit_profile") },
                    icon = { Icon(Icons.Default.Person, null) },
                    label = { Text(Str.t("Profile", "ಪ್ರೊಫೈಲ್")) }
                )
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (AppState.currentGuruProfile == null || AppState.currentGuruProfile!!.village.isBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp).shadow(4.dp, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(Str.t("Profile Incomplete! Students cannot find you easily.", "ಪ್ರೊಫೈಲ್ ಪೂರ್ಣಗೊಂಡಿಲ್ಲ! ಕಷ್ಟವಾಗಬಹುದು."), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.height(12.dp))
                        PremiumButton(onClick = { navController.navigate("edit_profile") }, text = Str.t("Edit Profile", "ಪ್ರೊಫೈಲ್ ಎಡಿಟ್ ಮಾಡಿ"))
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp).shadow(4.dp, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = HeritageSoftTeal),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AvatarCircle(name = AppState.currentGuruProfile!!.name, size = 64.dp)
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(AppState.currentGuruProfile!!.name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                                Text("📍 ${AppState.currentGuruProfile!!.village}", style = MaterialTheme.typography.bodyMedium)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Favorite, contentDescription = null, tint = HeartRed, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("${AppState.currentGuruProfile!!.appreciationsCount} " + Str.t("Thanks", "ಧನ್ಯವಾದಗಳು"), style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                        IconButton(onClick = { navController.navigate("edit_profile") }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Profile", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    Str.t("My Upcoming Classes", "ನನ್ನ ಮುಂಬರುವ ತರಗತಿಗಳು"),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(onClick = { navController.navigate("add_session") }) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(Str.t("Add New", "ಹೊಸತು ಸೇರಿಸಿ"))
                }
            }

            if (mySessions.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.alpha(0.5f)) {
                        Icon(Icons.Default.DateRange, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(16.dp))
                        Text(Str.t("No classes scheduled", "ಯಾವುದೇ ತರಗತಿಗಳಿಲ್ಲ"), style = MaterialTheme.typography.titleMedium)
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(mySessions) { session ->
                        Card(
                            modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(Modifier.padding(20.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(color = HeritageSoftTeal, shape = RoundedCornerShape(8.dp)) {
                                        Text(Str.dyn(session.subject), modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                                    }
                                    Spacer(Modifier.weight(1f))
                                    IconButton(onClick = { AppState.deleteSession(session.id) {} }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.6f))
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.LocationOn, null, Modifier.size(18.dp), tint = HeritageGold)
                                    Text(" ${Str.dyn(session.location)}", style = MaterialTheme.typography.bodyLarge)
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Schedule, null, Modifier.size(18.dp), tint = HeritageGold)
                                    Text(" ${Str.num(session.time)}", style = MaterialTheme.typography.bodyLarge)
                                }
                                if (session.description.isNotBlank()) {
                                    Spacer(Modifier.height(12.dp))
                                    Text(session.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}


// --- 4. Student Dashboard (Filter + Location Search) ---

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StudentDashboardScreen(navController: NavHostController) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        AppState.attachSessionsListener()
        onDispose { AppState.detachSessionsListener() }
    }

    var locationSearch by remember { mutableStateOf("") }
    val subjects = listOf("All") + AppState.sessions.map { it.subject }.distinct()
    var selectedSubject by remember(subjects) { mutableStateOf(subjects.firstOrNull() ?: "All") }
    var noteDialogSession by remember { mutableStateOf<Session?>(null) } 

    val filtered = AppState.sessions.filter { session ->
        val matchLoc = session.location.contains(locationSearch, ignoreCase = true) || session.guruName.contains(locationSearch, ignoreCase = true)
        val matchSub = (selectedSubject == "All" || session.subject == selectedSubject)
        matchLoc && matchSub
    }

    val quotes = listOf(
        Str.t("Education is the most powerful weapon which you can use to change the world.", "ಶಿಕ್ಷಣವು ಜಗತ್ತನ್ನು ಬದಲಾಯಿಸಲು ನೀವು ಬಳಸಬಹುದಾದ ಅತ್ಯಂತ ಶಕ್ತಿಶಾಲಿ ಅಸ್ತ್ರವಾಗಿದೆ."),
        Str.t("The beautiful thing about learning is that no one can take it away from you.", "ಕಲಿಯುವಿಕೆಯ ಸುಂದರವಾದ ವಿಷಯವೆಂದರೆ ಅದನ್ನು ಯಾರೂ ನಿಮ್ಮಿಂದ ಕಸಿದುಕೊಳ್ಳಲು ಸಾಧ್ಯವಿಲ್ಲ."),
        Str.t("Learn as if you will live forever, live like you will die tomorrow.", "ನೀವು ಶಾಶ್ವತವಾಗಿ ಬದುಕುತ್ತೀರಿ ಎಂಬಂತೆ ಕಲಿಯಿರಿ, ನಾಳೆ ಸಾಯುತ್ತೀರಿ ಎಂಬಂತೆ ಬದುಕಿ.")
    )
    val randomQuote = remember { quotes.random() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Str.t("Nimma-Guru", "ನಿಮ್ಮ-ಗುರು"), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { AppState.logout() }) { Icon(Icons.Default.ExitToApp, null) } },
                actions = { 
                    IconButton(onClick = { navController.navigate("wall_of_fame") }) { Icon(Icons.Default.EmojiEvents, "Wall of Fame", tint = HeritageGold) }
                    LanguageMenu() 
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                    navigationIconContentColor = MaterialTheme.colorScheme.primary,
                    actionIconContentColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 8.dp) {
                NavigationBarItem(
                    selected = true,
                    onClick = { },
                    icon = { Icon(Icons.Default.Home, null) },
                    label = { Text(Str.t("Home", "ಮುಖಪುಟ")) }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate("wall_of_fame") },
                    icon = { Icon(Icons.Default.EmojiEvents, null) },
                    label = { Text(Str.t("Wall", "ಗೋಡೆ")) }
                )
            }
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Greeting
            item {
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text(Str.t("Namaste,", "ನಮಸ್ಕಾರ,"), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Str.t("What would you like to learn?", "ನೀವು ಏನು ಕಲಿಯಲು ಬಯಸುತ್ತೀರಿ?"), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                }
            }

            // Big Primary Card: Ask Guru
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().height(160.dp).shadow(4.dp, RoundedCornerShape(12.dp))
                        .clickable { Toast.makeText(context, "Q&A Feature Coming Soon!", Toast.LENGTH_SHORT).show() },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Column(Modifier.padding(24.dp).fillMaxSize(), verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.QuestionAnswer, null, tint = Color.White, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(Str.t("Ask Guru", "ಗುರುವನ್ನು ಕೇಳಿ"), style = MaterialTheme.typography.titleLarge, color = Color.White)
                        Text(Str.t("Get answers from village experts", "ಗ್ರಾಮದ ತಜ್ಞರಿಂದ ಉತ್ತರಗಳನ್ನು ಪಡೆಯಿರಿ"), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
                    }
                }
            }

            // Two Small Cards Row
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Card(
                        modifier = Modifier.weight(1f).height(140.dp).shadow(4.dp, RoundedCornerShape(12.dp))
                            .clickable { Toast.makeText(context, "Scroll down to see classes", Toast.LENGTH_SHORT).show() },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = HeritageSoftTeal)
                    ) {
                        Column(Modifier.padding(20.dp).fillMaxSize(), verticalArrangement = Arrangement.Center) {
                            Icon(Icons.Default.MenuBook, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.height(12.dp))
                            Text(Str.t("Learn Today", "ಇಂದು ಕಲಿಯಿರಿ"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Card(
                        modifier = Modifier.weight(1f).height(140.dp).shadow(4.dp, RoundedCornerShape(12.dp))
                            .clickable { Toast.makeText(context, "Mentor Directory Coming Soon!", Toast.LENGTH_SHORT).show() },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(Modifier.padding(20.dp).fillMaxSize(), verticalArrangement = Arrangement.Center) {
                            Icon(Icons.Default.GroupAdd, null, tint = HeritageGold, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.height(12.dp))
                            Text(Str.t("Connect Guru", "ಗುರು ಸಂಪರ್ಕ"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // Quote Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = HeritageSoftTeal.copy(alpha = 0.5f))
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Icon(Icons.Default.FormatQuote, null, tint = HeritageGold.copy(alpha = 0.6f), modifier = Modifier.size(32.dp))
                        Text(randomQuote, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary, fontStyle = FontStyle.Italic)
                    }
                }
            }

            item { Divider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)) }

            // Search & Filter (Less prominent now)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = locationSearch, 
                        onValueChange = { locationSearch = it },
                        label = { Text(Str.t("Search Classes / Location", "ತರಗತಿಗಳು / ಸ್ಥಳ ಹುಡುಕಿ")) },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        subjects.forEach { sub ->
                            FilterChip(
                                selected = selectedSubject == sub, 
                                onClick = { selectedSubject = sub }, 
                                label = { Text(if(sub == "All") Str.t("All", "ಎಲ್ಲಾ") else Str.dyn(sub)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
            }

            // Session List
            if (filtered.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().height(150.dp), Alignment.Center) {
                        Text(Str.t("No classes found", "ಯಾವುದೇ ತರಗತಿಗಳು ಇಲ್ಲ"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(filtered) { session ->
                    Card(
                        modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(12.dp)),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AvatarCircle(name = session.guruName, size = 56.dp)
                                Spacer(Modifier.width(16.dp))
                                Column {
                                    Text(session.guruName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Surface(color = HeritageSoftTeal, shape = RoundedCornerShape(6.dp)) {
                                        Text(Str.dyn(session.subject), modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(18.dp), tint = HeritageGold)
                                Spacer(Modifier.width(6.dp))
                                Text(session.location, style = MaterialTheme.typography.bodyLarge)
                                Spacer(Modifier.width(20.dp))
                                Icon(Icons.Default.Schedule, null, modifier = Modifier.size(18.dp), tint = HeritageGold)
                                Spacer(Modifier.width(6.dp))
                                Text(session.time, style = MaterialTheme.typography.bodyLarge)
                            }
                            if (session.description.isNotBlank()) {
                                Spacer(Modifier.height(12.dp))
                                Text(session.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            
                            Spacer(Modifier.height(20.dp))
                            
                            if (session.meetingLink.isNotBlank()) {
                                PremiumButton(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(session.meetingLink))
                                        context.startActivity(intent)
                                    },
                                    text = Str.t("Join Online Class", "ಆನ್‌ಲೈನ್ ತರಗತಿಗೆ ಸೇರಿ"),
                                    icon = { Icon(Icons.Default.Videocam, null) }
                                )
                                Spacer(Modifier.height(12.dp))
                            }
                            
                            PremiumButton(
                                onClick = { noteDialogSession = session },
                                text = Str.t("Appreciate Mentor", "ಧನ್ಯವಾದ ಹೇಳಿ"),
                                icon = { Icon(Icons.Default.Favorite, null) },
                                isSecondary = true
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    // Appreciate Dialog
    if (noteDialogSession != null) {
        var noteText by remember(noteDialogSession) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { noteDialogSession = null },
            title = { 
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Favorite, contentDescription = null, tint = HeartRed)
                    Spacer(Modifier.width(12.dp))
                    Text(Str.t("Thank You Note", "ಧನ್ಯವಾದಗಳು"), color = MaterialTheme.colorScheme.primary) 
                }
            },
            text = { 
                OutlinedTextField(
                    value = noteText, 
                    onValueChange = { noteText=it }, 
                    label = { Text(Str.t("Write a short appreciation message...", "ಮೆಚ್ಚುಗೆಯ ಸಂದೇಶ ಬರೆಯಿರಿ...")) },
                    minLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) 
            },
            confirmButton = {
                PremiumButton(
                    onClick = {
                        if (noteText.isNotBlank()) {
                            AppState.sendAppreciation(noteDialogSession!!.guruUid, noteDialogSession!!.subject, noteText,
                                onDone = {
                                    Toast.makeText(context, "Note sent!", Toast.LENGTH_SHORT).show()
                                    noteDialogSession = null
                                },
                                onError = { msg ->
                                    Toast.makeText(context, "Error: $msg", Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                    },
                    text = Str.t("Send", "ಕಳುಹಿಸಿ"),
                    modifier = Modifier.width(120.dp)
                )
            },
            dismissButton = { TextButton(onClick = { noteDialogSession = null }) { Text(Str.t("Cancel", "ರದ್ದುಮಾಡಿ")) } },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        )
    }
}


// --- 5. Add Session Screen (With Location Setting) ---

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddSessionScreen(navController: NavHostController) {
    val context = LocalContext.current
    var subject by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("Samudaya Bhavana") }
    var time by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var meetingLink by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { 
            TopAppBar(
                title = { Text(Str.t("Host Class", "ತರಗತಿಯನ್ನು ಸೇರಿಸಿ")) }, 
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, null, tint = MaterialTheme.colorScheme.primary) } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            ) 
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(pad)
                .padding(24.dp)
                .fillMaxSize(), 
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Text(Str.t("What will you teach today?", "ಇಂದು ನೀವು ಏನು ಕಲಿಸುತ್ತೀರಿ?"), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = subject, 
                    onValueChange = { subject=it }, 
                    label = { Text(Str.t("Subject/Skill", "ವಿಷಯ/ಕೌಶಲ್ಯ")) }, 
                    modifier = Modifier.fillMaxWidth(), 
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(focusedBorderColor = MaterialTheme.colorScheme.primary)
                )
                FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Math", "Science", "Carpentry", "Values", "English").forEach { 
                        SuggestionChip(
                            onClick = { subject = it }, 
                            label = { Text(it) },
                            shape = RoundedCornerShape(12.dp)
                        ) 
                    }
                }
            }
            item { 
                OutlinedTextField(
                    value = location, 
                    onValueChange = { location=it }, 
                    label = { Text(Str.t("Class Location", "ಸ್ಥಳ")) }, 
                    modifier = Modifier.fillMaxWidth(), 
                    shape = RoundedCornerShape(12.dp), 
                    leadingIcon = { Icon(Icons.Default.LocationOn, null, tint = HeritageGold) },
                    colors = TextFieldDefaults.outlinedTextFieldColors(focusedBorderColor = MaterialTheme.colorScheme.primary)
                ) 
            }
            item { 
                OutlinedTextField(
                    value = meetingLink, 
                    onValueChange = { meetingLink=it }, 
                    label = { Text(Str.t("Meeting Link (Optional)", "ಮೀಟಿಂಗ್ ಲಿಂಕ್ (ಐಚ್ಛಿಕ)")) }, 
                    modifier = Modifier.fillMaxWidth(), 
                    shape = RoundedCornerShape(12.dp), 
                    leadingIcon = { Icon(Icons.Default.Link, null, tint = HeritageGold) },
                    colors = TextFieldDefaults.outlinedTextFieldColors(focusedBorderColor = MaterialTheme.colorScheme.primary)
                ) 
            }
            item { 
                OutlinedTextField(
                    value = time, 
                    onValueChange = { time=it }, 
                    label = { Text(Str.t("Time", "ಸಮಯ")) }, 
                    modifier = Modifier.fillMaxWidth(), 
                    shape = RoundedCornerShape(12.dp), 
                    leadingIcon = { Icon(Icons.Default.Schedule, null, tint = HeritageGold) },
                    colors = TextFieldDefaults.outlinedTextFieldColors(focusedBorderColor = MaterialTheme.colorScheme.primary)
                ) 
            }
            item { 
                OutlinedTextField(
                    value = desc, 
                    onValueChange = { desc=it }, 
                    label = { Text(Str.t("Description", "ವಿವರಣೆ")) }, 
                    modifier = Modifier.fillMaxWidth(), 
                    shape = RoundedCornerShape(12.dp), 
                    minLines = 3,
                    colors = TextFieldDefaults.outlinedTextFieldColors(focusedBorderColor = MaterialTheme.colorScheme.primary)
                ) 
            }
            item {
                Spacer(Modifier.height(16.dp))
                PremiumButton(
                    onClick = {
                        isSaving = true
                        val session = Session(id = UUID.randomUUID().toString(), guruUid = AppState.auth.currentUser!!.uid, guruName = AppState.currentGuruProfile?.name ?: "Guru", subject = subject, location = location, meetingLink = meetingLink, time = time, description = desc, timestamp = System.currentTimeMillis())
                        AppState.saveSession(session, 
                            onDone = { 
                                isSaving = false
                                navController.popBackStack() 
                            },
                            onError = { 
                                isSaving = false
                                Toast.makeText(context, "Error: $it", Toast.LENGTH_LONG).show() 
                            }
                        )
                    },
                    text = if (isSaving) Str.t("Posting...", "ಪೋಸ್ಟ್ ಮಾಡಲಾಗುತ್ತಿದೆ...") else Str.t("Post Session", "ತರಗತಿಯನ್ನು ಪೋಸ್ಟ್ ಮಾಡಿ"),
                    enabled = !isSaving
                )
            }
        }
    }
}


// --- 6. Wall of Fame Screen ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallOfFameScreen(navController: NavHostController) {
    DisposableEffect(Unit) {
        AppState.attachWallOfFameListener()
        onDispose { /* Keep memory light, registration is handled inside */ }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Str.t("Wall of Fame", "ಮೆಚ್ಚುಗೆಯ ಗೋಡೆ")) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize().background(MaterialTheme.colorScheme.background), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            
            item { 
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = HeritageGold, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(Str.t("Our Community's Top Mentors", "ನಮ್ಮ ಸಮುದಾಯದ ಉನ್ನತ ಮಾರ್ಗದರ್ಶಕರು"), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.primary) 
                    Spacer(Modifier.height(16.dp))
                }
            }
            
            if (AppState.topGurus.isNotEmpty()) {
                itemsIndexed(AppState.topGurus) { index, guru ->
                    val isTop3 = index < 3
                    val cardColor = when(index) {
                        0 -> Color(0xFFFFF9C4) // Light Gold
                        1 -> Color(0xFFF5F5F5) // Light Silver
                        2 -> Color(0xFFFFE0B2) // Light Bronze
                        else -> MaterialTheme.colorScheme.surface
                    }
                    val iconColor = when(index) {
                        0 -> HeritageGold
                        1 -> Silver
                        2 -> Bronze
                        else -> MaterialTheme.colorScheme.primary
                    }
                    val border = if (isTop3) BorderStroke(2.dp, iconColor.copy(alpha = 0.5f)) else null

                    Card(
                        modifier = Modifier.fillMaxWidth().shadow(if (isTop3) 8.dp else 2.dp, RoundedCornerShape(12.dp)), 
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = cardColor),
                        border = border
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(iconColor.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                                Text("#${index + 1}", fontWeight = FontWeight.Bold, color = iconColor)
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(guru.name, style = MaterialTheme.typography.titleMedium, fontWeight = if (isTop3) FontWeight.Bold else FontWeight.Medium)
                                Text("📍 ${guru.village}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${guru.appreciationsCount}", style = MaterialTheme.typography.titleLarge, color = iconColor, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Default.Favorite, contentDescription = null, tint = HeartRed, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }

            item { 
                Spacer(Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Divider(Modifier.weight(1f))
                    Text(Str.t("Recent Thank You Notes", "ಇತ್ತೀಚಿನ ವಂದನಾ ಸಂದೇಶಗಳು"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.primary) 
                    Divider(Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
            }

            items(AppState.thankYouNotes) { note ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(), 
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Icon(Icons.Default.FormatQuote, contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), modifier = Modifier.size(32.dp))
                        Text(note.message, style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic, modifier = Modifier.padding(vertical = 8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Text("- ${note.studentName} ", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Text("(${note.sessionSubject})", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }
            
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

// --- Utility Components ---

@Composable
fun LanguageMenu() {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) { Icon(Icons.Default.Language, "Language") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("English") }, onClick = { AppState.selectedLanguage = "English"; expanded = false })
            DropdownMenuItem(text = { Text("ಕನ್ನಡ") }, onClick = { AppState.selectedLanguage = "Kannada"; expanded = false })
        }
    }
}
