package com.example.fitwell.features.leagues

import com.example.fitwell.data.local.LeagueChallengeEntity
import com.example.fitwell.data.local.LeagueDao
import com.example.fitwell.data.local.LeagueEntity
import com.example.fitwell.data.local.LeagueMemberEntity
import com.example.fitwell.data.local.LeagueScoreEntity
import com.example.fitwell.data.local.LeagueScoreMode
import com.example.fitwell.util.startOfDay
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import kotlin.math.max

/**
 * Invite-only fitness leagues.
 * Private by design: join via code/link only. Users control which stats are shared.
 * Scoring is configurable; anti-manipulation is light (local consistency checks).
 * Participation is optional; beginners can use improvement-based scoring.
 */
class LeagueRepository(private val dao: LeagueDao) {

    fun observeLeagues(): Flow<List<LeagueEntity>> = dao.observeLeagues()
    fun observeLeague(id: Long) = dao.observeLeague(id)
    fun observeMembers(leagueId: Long) = dao.observeMembers(leagueId)
    fun observeChallenges(leagueId: Long) = dao.observeChallenges(leagueId)
    fun observeScores(challengeId: Long) = dao.observeScores(challengeId)

    suspend fun createLeague(
        name: String,
        creatorUserId: String,
        creatorDisplayName: String,
        scoreMode: LeagueScoreMode
    ): Long {
        val code = generateInviteCode()
        val leagueId = dao.insertLeague(
            LeagueEntity(
                name = name.trim().ifBlank { "My League" },
                inviteCode = code,
                createdByUserId = creatorUserId,
                scoreMode = scoreMode
            )
        )
        dao.upsertMember(
            LeagueMemberEntity(
                leagueId = leagueId,
                memberUserId = creatorUserId,
                displayName = creatorDisplayName
            )
        )
        // Current week challenge
        val weekStart = startOfWeek(System.currentTimeMillis())
        dao.insertChallenge(
            LeagueChallengeEntity(
                leagueId = leagueId,
                title = "Week of ${java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(java.util.Date(weekStart))}",
                weekStartMs = weekStart,
                weekEndMs = weekStart + 7 * 24 * 60 * 60 * 1000L - 1,
                scoreMode = scoreMode
            )
        )
        return leagueId
    }

    suspend fun joinByCode(
        code: String,
        userId: String,
        displayName: String
    ): Result<Long> {
        val league = dao.findByCode(code.trim().uppercase())
            ?: return Result.failure(IllegalArgumentException("Invalid invite code"))
        if (!league.isActive) return Result.failure(IllegalStateException("League is closed"))
        dao.upsertMember(
            LeagueMemberEntity(
                leagueId = league.leagueId,
                memberUserId = userId,
                displayName = displayName
            )
        )
        return Result.success(league.leagueId)
    }

    suspend fun leave(leagueId: Long, userId: String) {
        dao.removeMember(leagueId, userId)
    }

    suspend fun updateSharePrefs(
        member: LeagueMemberEntity,
        shareSteps: Boolean,
        shareWorkouts: Boolean,
        sharePulse: Boolean
    ) {
        dao.upsertMember(
            member.copy(
                shareSteps = shareSteps,
                shareWorkouts = shareWorkouts,
                sharePulse = sharePulse
            )
        )
    }

    suspend fun blockMember(memberId: Long) = dao.blockMember(memberId)

    suspend fun reportPlaceholder(memberUserId: String, reason: String) {
        // Hook for remote moderation backend; local flag only for now.
    }

    /**
     * Update weekly score. [rawValue] meaning depends on mode:
     * CONSISTENCY: active days (0–7), WORKOUTS: count, STEPS: step total / 1000, IMPROVEMENT: % vs baseline.
     * Soft cap to limit obvious inflation.
     */
    suspend fun updateScore(
        challengeId: Long,
        memberUserId: String,
        mode: LeagueScoreMode,
        rawValue: Int,
        detail: String = ""
    ) {
        val points = when (mode) {
            LeagueScoreMode.CONSISTENCY -> rawValue.coerceIn(0, 7) * 10
            LeagueScoreMode.WORKOUTS -> rawValue.coerceIn(0, 20) * 5
            LeagueScoreMode.STEPS -> (rawValue / 1000).coerceIn(0, 100)
            LeagueScoreMode.IMPROVEMENT -> rawValue.coerceIn(0, 50)
        }
        dao.upsertScore(
            LeagueScoreEntity(
                challengeId = challengeId,
                memberUserId = memberUserId,
                points = points,
                detail = detail.ifBlank { "$mode: $rawValue" },
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deactivateLeague(leagueId: Long) = dao.deactivate(leagueId)

    private fun generateInviteCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..8).map { alphabet.random() }.joinToString("")
    }

    private fun startOfWeek(ms: Long): Long {
        val cal = java.util.Calendar.getInstance().apply {
            timeInMillis = startOfDay(ms)
            firstDayOfWeek = java.util.Calendar.MONDAY
            set(java.util.Calendar.DAY_OF_WEEK, firstDayOfWeek)
        }
        return cal.timeInMillis
    }
}
