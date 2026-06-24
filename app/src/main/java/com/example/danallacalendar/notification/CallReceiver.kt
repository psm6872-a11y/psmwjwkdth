package com.example.danallacalendar.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.example.danallacalendar.data.CalendarDatabase
import com.example.danallacalendar.data.Event
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            if (state == TelephonyManager.EXTRA_STATE_RINGING) {
                val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                if (!incomingNumber.isNullOrBlank()) {
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            handleIncomingCall(context, incomingNumber)
                        } catch (e: Exception) {
                            android.util.Log.e("CallReceiver", "Error processing incoming call", e)
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }
        }
    }

    private suspend fun handleIncomingCall(context: Context, incomingNumber: String) {
        val incomingClean = normalizePhoneNumber(incomingNumber)
        if (incomingClean.isEmpty()) return

        val db = CalendarDatabase.getDatabase(context, CoroutineScope(Dispatchers.IO))

        // Check Blacklist First (Highest Priority)
        val blacklistItems = try {
            db.blacklistDao().getAllList()
        } catch (e: Exception) {
            emptyList()
        }
        val matchedBlacklist = blacklistItems.find { 
            normalizePhoneNumber(it.phoneNumber) == incomingClean
        }
        if (matchedBlacklist != null) {
            val reason = matchedBlacklist.reason.ifBlank { "사유 없음" }
            val deepLink = "danallacalendar://blacklist"
            NotificationHelper.showCallScanNotification(
                context,
                "[B/L] 경고 - 블랙리스트 번호",
                "사유: $reason | ${formatPhoneNumberForDetail(incomingClean)}",
                deepLink
            )
            return
        }

        val allEstimates = try {
            db.estimatePdfDao().getAllPdfsList()
        } catch (e: Exception) {
            emptyList()
        }

        val allEvents = try {
            db.eventDao().getAllEventsList()
        } catch (e: Exception) {
            emptyList()
        }

        // Filter matching estimates
        val matchingEstimates = allEstimates.filter { 
            normalizePhoneNumber(it.phoneNumber) == incomingClean
        }

        // 1st Priority: Contract-completed (계약완료)
        // Estimate matches, and there is at least one linked event with isAllDay == true
        val contractCompletedMatch = matchingEstimates.find { estimate ->
            allEvents.any { event -> 
                (event.linkedEstimateId == estimate.estimateId || 
                 event.linkedEstimateId == estimate.id.toString()) && 
                event.isAllDay
            }
        }

        if (contractCompletedMatch != null) {
            val customerName = contractCompletedMatch.customerName.ifBlank { "고객" }
            val departure = contractCompletedMatch.departure.split("|").firstOrNull()?.trim() ?: ""
            val body = if (departure.isNotBlank()) "계약완료 - $departure 이사 예정" else "계약완료 이사 예정"
            val deepLink = "danallacalendar://estimate?highlightId=${contractCompletedMatch.estimateId}"
            NotificationHelper.showCallScanNotification(
                context,
                "[계약완료] $customerName",
                body,
                deepLink
            )
            return
        }

        // 2nd Priority: Visit-completed or General Estimate (방문완료 / 일반견적)
        // Estimate matches, and either has linked events with isAllDay == false, or no linked events
        val generalEstimateMatch = matchingEstimates.firstOrNull()
        if (generalEstimateMatch != null) {
            val customerName = generalEstimateMatch.customerName.ifBlank { "고객" }
            val departure = generalEstimateMatch.departure.split("|").firstOrNull()?.trim() ?: ""
            
            // Check if there is any linked event (which would mean visit completed/scheduled)
            val hasLinkedEvent = allEvents.any { event ->
                event.linkedEstimateId == generalEstimateMatch.estimateId || 
                event.linkedEstimateId == generalEstimateMatch.id.toString()
            }
            
            val titleTag = if (hasLinkedEvent) "[방문완료]" else "[견적서]"
            val body = if (departure.isNotBlank()) "$departure 견적 문의" else "견적 문의"
            val deepLink = "danallacalendar://estimate?highlightId=${generalEstimateMatch.estimateId}"
            NotificationHelper.showCallScanNotification(
                context,
                "$titleTag $customerName",
                body,
                deepLink
            )
            return
        }

        // 3rd Priority: Schedule cards (Calendar Events)
        // Event title, notes, or location contains the phone number
        val matchedEvent = allEvents.find { event ->
            eventMatchesPhoneNumber(event, incomingClean)
        }

        if (matchedEvent != null) {
            val cleanTitle = matchedEvent.title.replace("\n", " ").trim()
            val deepLink = "danallacalendar://event?id=${matchedEvent.id}"
            NotificationHelper.showCallScanNotification(
                context,
                "[일정] $cleanTitle",
                "일정 상세 페이지로 연결",
                deepLink
            )
            return
        }
    }

    private fun normalizePhoneNumber(number: String): String {
        val clean = number.replace(Regex("[^0-9]"), "")
        return if (clean.startsWith("82")) {
            "0" + clean.substring(2)
        } else {
            clean
        }
    }

    private fun formatPhoneNumberForDetail(number: String): String {
        return when {
            number.length == 11 ->
                "${number.substring(0, 3)}-" +
                "${number.substring(3, 7)}-" +
                "${number.substring(7)}"
            number.length == 10 ->
                "${number.substring(0, 3)}-" +
                "${number.substring(3, 6)}-" +
                "${number.substring(6)}"
            else -> number
        }
    }

    private fun extractPhoneNumbers(text: String): List<String> {
        val regex = Regex("""\d[- \d]{7,15}\d""")
        return regex.findAll(text).map { match ->
            normalizePhoneNumber(match.value)
        }.filter { it.length in 9..11 && it.startsWith("0") }.toList()
    }

    private fun eventMatchesPhoneNumber(event: Event, incomingClean: String): Boolean {
        // Check notes
        if (normalizePhoneNumber(event.notes) == incomingClean) return true
        if (event.notes.split("|||").any { normalizePhoneNumber(it) == incomingClean }) return true
        
        // Check title
        if (normalizePhoneNumber(event.title) == incomingClean) return true
        if (extractPhoneNumbers(event.title).contains(incomingClean)) return true

        // Check location
        if (normalizePhoneNumber(event.location) == incomingClean) return true
        if (event.location.split("|||").any { normalizePhoneNumber(it) == incomingClean }) return true
        if (extractPhoneNumbers(event.location).contains(incomingClean)) return true

        return false
    }
}
