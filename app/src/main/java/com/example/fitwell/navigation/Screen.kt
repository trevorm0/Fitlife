package com.example.fitwell.navigation

/** Central route table. Detail/edit screens live outside the bottom bar, which is hidden on them. */
object Screen {
    const val WELCOME = "welcome"
    const val AUTH = "auth"
    const val HOME = "home"
    const val WORKOUTS = "workouts"
    const val HEALTH = "health?tab={tab}"   // tab: 0 Overview, 1 Medication, 2 Appointments
    const val NUTRITION = "nutrition"
    const val PROFILE = "profile"

    const val WORKOUT_DETAILS = "workout/{id}"
    const val SESSION = "session/{id}"
    const val HISTORY = "history"
    const val GOALS = "goals"
    const val SETTINGS = "settings"
    const val HELP = "help"
    const val ABOUT = "about"

    const val MEDICATION_DETAILS = "medication/{id}"
    const val MEDICATION_EDIT = "medication_edit?id={id}"
    const val MEDICATION_HISTORY = "medication_history?id={id}"
    const val APPOINTMENT_DETAILS = "appointment/{id}"
    const val APPOINTMENT_EDIT = "appointment_edit?id={id}"

    // Feature modules
    const val PULSE = "pulse"
    const val STEPS = "steps"
    const val COACH = "coach"
    const val LEAGUES = "leagues"
    const val MEAL_PHOTO = "meal_photo"
    const val ADAPTIVE = "adaptive"

    fun health(tab: Int = 0) = "health?tab=$tab"
    fun workout(id: Long) = "workout/$id"
    fun session(id: Long) = "session/$id"
    fun medication(id: Long) = "medication/$id"
    fun medicationEdit(id: Long = 0) = "medication_edit?id=$id"
    fun medicationHistory(id: Long = -1) = "medication_history?id=$id"
    fun appointment(id: Long) = "appointment/$id"
    fun appointmentEdit(id: Long = 0) = "appointment_edit?id=$id"
}
