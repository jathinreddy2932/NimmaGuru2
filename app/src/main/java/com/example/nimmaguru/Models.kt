package com.nimmaguru.app

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
