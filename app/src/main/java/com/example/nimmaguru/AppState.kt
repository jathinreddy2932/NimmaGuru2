package com.nimmaguru.app

import android.content.Context
import androidx.compose.runtime.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.util.UUID

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
