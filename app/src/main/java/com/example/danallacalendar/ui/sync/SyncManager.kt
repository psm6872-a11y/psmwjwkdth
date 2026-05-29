package com.example.danallacalendar.ui.sync

import android.util.Log
import com.example.danallacalendar.data.Event
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

enum class SyncRole {
    NONE, HOST, CLIENT, SIMULATION
}

enum class SyncPermission {
    NONE, READ_ONLY, FULL_ACCESS
}

data class SyncPeer(
    val id: String,
    val name: String,
    val permission: SyncPermission
)

class SyncManager(
    private val onEventReceived: suspend (Event) -> Unit,
    private val onEventDeleted: suspend (String) -> Unit,
    private val getCurrentEventsProvider: suspend () -> List<Event>
) {
    private val tag = "SyncManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val APP_KEY = "danallacalendar2026"
    private val syncMutex = Mutex()
    private var pollJob: Job? = null
    
    // States
    private val _role = MutableStateFlow(SyncRole.NONE)
    val role = _role.asStateFlow()

    private val _permission = MutableStateFlow(SyncPermission.NONE)
    val permission = _permission.asStateFlow()

    private val _inviteCode = MutableStateFlow("") // Used as Room ID
    val inviteCode = _inviteCode.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected = _isConnected.asStateFlow()

    private val _connectedPeers = MutableStateFlow<List<SyncPeer>>(emptyList())
    val connectedPeers = _connectedPeers.asStateFlow()

    private val _syncLogs = MutableStateFlow<List<String>>(emptyList())
    val syncLogs = _syncLogs.asStateFlow()

    // Temporary/simulation support (to prevent compile errors)
    private val _simGuestConnected = MutableStateFlow(false)
    val simGuestConnected = _simGuestConnected.asStateFlow()
    private val _simGuestEvents = MutableStateFlow<List<Event>>(emptyList())
    val simGuestEvents = _simGuestEvents.asStateFlow()
    private val _simGuestPermission = MutableStateFlow(SyncPermission.NONE)
    val simGuestPermission = _simGuestPermission.asStateFlow()
    private val _simGuestInviteCode = MutableStateFlow("")
    val simGuestInviteCode = _simGuestInviteCode.asStateFlow()

    private var lastSyncedPasteId = ""
    private var isSyncingDb = false

    fun log(message: String) {
        Log.d(tag, message)
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        _syncLogs.value = listOf("[$timestamp] $message") + _syncLogs.value.take(49)
    }

    // --- Remote Sync Actions ---

    // Create Room (Host)
    fun startHosting(perm: SyncPermission) {
        stopAll()
        _role.value = SyncRole.HOST
        _permission.value = SyncPermission.FULL_ACCESS // Creator always has full access
        
        // Generate random 6-digit Room ID
        val generatedRoomId = (100000..999999).random().toString()
        _inviteCode.value = generatedRoomId
        
        log("원격 공유방 생성 완료. 방 번호: $generatedRoomId")
        _isConnected.value = true

        scope.launch(Dispatchers.IO) {
            // Save selected guest permission in ROOM_{roomId}_PERM
            updateRoomValue("PERM_$generatedRoomId", perm.name)

            // Register host member in MEMBERS_{roomId}
            registerMember(generatedRoomId, "방장", SyncPermission.FULL_ACCESS)

            try {
                val currentEvents = getCurrentEventsProvider()
                val serialized = serializeEvents(currentEvents)
                val pasteId = uploadToPasteRs(serialized)
                if (pasteId.isNotEmpty()) {
                    val ok = updateRoomValue(generatedRoomId, pasteId)
                    if (ok) {
                        lastSyncedPasteId = pasteId
                        log("캘린더 데이터를 서버에 업로드했습니다. 동기화 대기 중...")
                    }
                }
            } catch (e: Exception) {
                log("초기 업로드 실패: ${e.localizedMessage}")
            }
            
            // Start Polling
            startPolling(generatedRoomId)
        }
    }

    // Join Room (Client)
    fun joinHost(ip: String, code: String, deviceName: String) {
        stopAll()
        _role.value = SyncRole.CLIENT
        _inviteCode.value = code
        log("원격 공유방 연결 중... 방 번호: $code")

        scope.launch(Dispatchers.IO) {
            val pasteId = getRoomValue(code)
            if (pasteId.isEmpty() || pasteId == "null") {
                log("오류: 공유방 번호($code)를 찾을 수 없습니다.")
                _role.value = SyncRole.NONE
                _isConnected.value = false
                return@launch
            }

            // Fetch room guest permission
            val permName = getRoomValue("PERM_$code")
            val assignedPerm = try {
                SyncPermission.valueOf(permName)
            } catch (e: Exception) {
                SyncPermission.READ_ONLY
            }
            _permission.value = assignedPerm

            val cleanDeviceName = if (deviceName.isBlank()) "참여자" else deviceName
            log("공유방 확인됨 (권한: ${if (assignedPerm == SyncPermission.READ_ONLY) "읽기 전용" else "모든 권한"}). 연결 중...")
            
            // Register client member
            registerMember(code, cleanDeviceName, assignedPerm)

            _isConnected.value = true

            // Initial Sync
            syncWithPasteId(pasteId)
            
            // Start Polling
            startPolling(code)
        }
    }

    fun stopAll() {
        val currentRoom = _inviteCode.value
        val currentRole = _role.value
        
        pollJob?.cancel()
        pollJob = null
        _role.value = SyncRole.NONE
        _permission.value = SyncPermission.NONE
        _inviteCode.value = ""
        _isConnected.value = false
        _connectedPeers.value = emptyList()
        lastSyncedPasteId = ""
        log("원격 동기화가 중단되었습니다.")

        if (currentRoom.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                if (currentRole == SyncRole.HOST) {
                    // Host clears the room keys
                    updateRoomValue(currentRoom, "null")
                    updateRoomValue("PERM_$currentRoom", "null")
                    updateRoomValue("MEMBERS_$currentRoom", "null")
                } else {
                    // Client removes themselves from the member list
                    unregisterMember(currentRoom, "참여자")
                }
            }
        }
    }

    // --- Member List Coordination Helpers ---
    private fun registerMember(roomId: String, name: String, perm: SyncPermission) {
        val current = getRoomValue("MEMBERS_$roomId")
        // Strip everything except Korean, English letters, and numbers to be safe in URL paths
        val cleanName = name.replace(Regex("[^a-zA-Z0-9가-힣]"), "")
        val newItem = "${cleanName}_${perm.name}"
        val updated = if (current.isEmpty() || current == "null") {
            newItem
        } else {
            val list = current.split(",")
            if (list.any { it.startsWith("${cleanName}_") }) {
                current
            } else {
                "$current,$newItem"
            }
        }
        updateRoomValue("MEMBERS_$roomId", updated)
    }

    private fun unregisterMember(roomId: String, name: String) {
        val current = getRoomValue("MEMBERS_$roomId")
        if (current.isNotEmpty() && current != "null") {
            val cleanName = name.replace(Regex("[^a-zA-Z0-9가-힣]"), "")
            val list = current.split(",")
            val filtered = list.filter { !it.startsWith("${cleanName}_") }
            updateRoomValue("MEMBERS_$roomId", filtered.joinToString(","))
        }
    }

    private fun pollMembers(roomId: String) {
        val raw = getRoomValue("MEMBERS_$roomId")
        if (raw.isNotEmpty() && raw != "null") {
            val list = ArrayList<SyncPeer>()
            val items = raw.split(",")
            for (item in items) {
                val parts = item.split("_")
                if (parts.size == 2) {
                    val name = parts[0]
                    val perm = try {
                        SyncPermission.valueOf(parts[1])
                    } catch (e: Exception) {
                        SyncPermission.READ_ONLY
                    }
                    // Format display names cleanly
                    val displayName = if (name == "방장") "방장 (나)" else name
                    list.add(SyncPeer(displayName, displayName, perm))
                }
            }
            _connectedPeers.value = list
        }
    }

    // --- Background Polling ---
    private fun startPolling(roomCode: String) {
        pollJob?.cancel()
        pollJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                // Poll Member List and Calendar Data
                try {
                    pollMembers(roomCode)
                    
                    val pasteId = getRoomValue(roomCode)
                    if (pasteId.isNotEmpty() && pasteId != "null" && pasteId != lastSyncedPasteId) {
                        log("서버에서 새 일정을 확인했습니다. 동기화 중...")
                        syncWithPasteId(pasteId)
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Polling error", e)
                }
                delay(5000) // Poll every 5 seconds
            }
        }
    }

    private suspend fun syncWithPasteId(pasteId: String) {
        syncMutex.withLock {
            try {
                isSyncingDb = true
                val content = downloadFromPasteRs(pasteId)
                if (content.isNotEmpty()) {
                    val remoteEvents = deserializeEvents(content)
                    val localEvents = getCurrentEventsProvider()

                    // 1. Delete local events that aren't present in remote list
                    for (local in localEvents) {
                        if (local.syncId != null && remoteEvents.none { it.syncId == local.syncId }) {
                            onEventDeleted(local.syncId)
                        }
                    }

                    // 2. Insert or update remote events locally
                    for (remote in remoteEvents) {
                        onEventReceived(remote)
                    }

                    lastSyncedPasteId = pasteId
                    log("동기화 완료. ${remoteEvents.size}개의 일정을 로드했습니다.")
                }
            } catch (e: Exception) {
                log("동기화 실패: ${e.localizedMessage}")
            } finally {
                isSyncingDb = false
            }
        }
    }

    // --- Outward Updates Broadcast ---
    fun broadcastLocalUpdate(event: Event) {
        if (isSyncingDb) return // Ignore if triggered by sync downloader
        if (!event.isSynced) return
        if (_permission.value == SyncPermission.READ_ONLY) {
            log("동기화 거부: 읽기 전용 권한이므로 로컬 변경 사항이 업로드되지 않습니다.")
            return
        }
        triggerUploadAndSync()
    }

    fun broadcastLocalDelete(syncId: String) {
        if (isSyncingDb) return // Ignore if triggered by sync downloader
        if (_permission.value == SyncPermission.READ_ONLY) {
            log("동기화 거부: 읽기 전용 권한이므로 로컬 삭제가 업로드되지 않습니다.")
            return
        }
        triggerUploadAndSync()
    }

    private fun triggerUploadAndSync() {
        val roomCode = _inviteCode.value
        if (roomCode.isEmpty()) return

        scope.launch(Dispatchers.IO) {
            syncMutex.withLock {
                try {
                    val currentEvents = getCurrentEventsProvider()
                    val serialized = serializeEvents(currentEvents)
                    val pasteId = uploadToPasteRs(serialized)
                    if (pasteId.isNotEmpty()) {
                        val ok = updateRoomValue(roomCode, pasteId)
                        if (ok) {
                            lastSyncedPasteId = pasteId
                            log("로컬 변경 사항을 서버에 동기화했습니다.")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Local upload failed", e)
                }
            }
        }
    }

    // --- HTTP Helper Functions ---

    private fun uploadToPasteRs(content: String): String {
        return try {
            val url = URL("https://paste.rs")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "text/plain; charset=UTF-8")
            
            val writer = OutputStreamWriter(conn.outputStream, "UTF-8")
            writer.write(content)
            writer.flush()
            writer.close()
            
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8"))
                val responseUrl = reader.readLine()?.trim() ?: ""
                reader.close()
                // Extacts the paste ID from "https://paste.rs/XXXXX"
                responseUrl.substringAfterLast("/")
            } else {
                ""
            }
        } catch (e: Exception) {
            Log.e(tag, "paste.rs upload failed", e)
            ""
        }
    }

    private fun downloadFromPasteRs(pasteId: String): String {
        return try {
            val url = URL("https://paste.rs/$pasteId")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8"))
                val content = reader.readText()
                reader.close()
                content
            } else {
                ""
            }
        } catch (e: Exception) {
            Log.e(tag, "paste.rs download failed", e)
            ""
        }
    }

    private fun updateRoomValue(roomId: String, value: String): Boolean {
        return try {
            val encodedValue = java.net.URLEncoder.encode(value, "UTF-8")
            val url = URL("https://keyvalue.immanuel.co/api/KeyVal/UpdateValue/$APP_KEY/$roomId/$encodedValue")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Length", "0")
            conn.responseCode == HttpURLConnection.HTTP_OK
        } catch (e: Exception) {
            Log.e(tag, "keyvalue update failed", e)
            false
        }
    }

    private fun getRoomValue(roomId: String): String {
        return try {
            val url = URL("https://keyvalue.immanuel.co/api/KeyVal/GetValue/$APP_KEY/$roomId")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8"))
                val raw = reader.readLine()?.trim() ?: ""
                reader.close()
                // The service might return value surrounded by quotes
                val clean = raw.replace("\"", "")
                java.net.URLDecoder.decode(clean, "UTF-8")
            } else {
                ""
            }
        } catch (e: Exception) {
            Log.e(tag, "keyvalue get failed", e)
            ""
        }
    }

    // --- Event Serializer Helpers ---

    private fun serializeEvents(events: List<Event>): String {
        val sb = java.lang.StringBuilder()
        for (e in events) {
            sb.append("${e.title}\t${e.startMillis}\t${e.endMillis}\t${e.isAllDay}\t${e.location}\t${e.notes}\t${e.repeatType}\t${e.reminderMinutes}\t${e.calendarId}\t${e.syncId}\t${e.isSynced}\n")
        }
        return sb.toString()
    }

    private fun deserializeEvents(str: String): List<Event> {
        val list = ArrayList<Event>()
        if (str.isBlank()) return list
        val lines = str.split("\n")
        for (line in lines) {
            if (line.isBlank()) continue
            val token = line.split("\t")
            if (token.size < 11) continue
            list.add(
                Event(
                    title = token[0],
                    startMillis = token[1].toLong(),
                    endMillis = token[2].toLong(),
                    isAllDay = token[3].toBoolean(),
                    location = token[4],
                    notes = token[5],
                    repeatType = token[6],
                    reminderMinutes = token[7].toInt(),
                    calendarId = token[8].toInt(),
                    syncId = token[9],
                    isSynced = token[10].toBoolean()
                )
            )
        }
        return list
    }

    // Stub/Simulation simulation methods to avoid compilation issues in case they're called
    fun startSimulation(permission: SyncPermission) {}
    fun simulateGuestConnect(code: String, name: String): Boolean = false
    fun simulateGuestDisconnect() {}
    fun addSimulatedEventFromGuest(title: String, startMillis: Long, endMillis: Long, isAllDay: Boolean, calendarId: Int) {}
    fun deleteSimulatedEventFromGuest(syncId: String) {}
}
