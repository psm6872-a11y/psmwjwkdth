package com.example.danallacalendar.ui.sync

import android.util.Log
import kotlin.coroutines.coroutineContext
import com.example.danallacalendar.data.Event
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

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
    
    // States
    private val _role = MutableStateFlow(SyncRole.NONE)
    val role = _role.asStateFlow()

    private val _permission = MutableStateFlow(SyncPermission.NONE)
    val permission = _permission.asStateFlow()

    private val _inviteCode = MutableStateFlow("")
    val inviteCode = _inviteCode.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected = _isConnected.asStateFlow()

    private val _connectedPeers = MutableStateFlow<List<SyncPeer>>(emptyList())
    val connectedPeers = _connectedPeers.asStateFlow()

    private val _syncLogs = MutableStateFlow<List<String>>(emptyList())
    val syncLogs = _syncLogs.asStateFlow()

    // Real Networking
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var serverJob: Job? = null
    private var clientJob: Job? = null
    private val clientHandlers = ConcurrentHashMap<String, SocketHandler>()

    // Simulation States
    private val _simGuestConnected = MutableStateFlow(false)
    val simGuestConnected = _simGuestConnected.asStateFlow()

    private val _simGuestEvents = MutableStateFlow<List<Event>>(emptyList())
    val simGuestEvents = _simGuestEvents.asStateFlow()

    private val _simGuestPermission = MutableStateFlow(SyncPermission.NONE)
    val simGuestPermission = _simGuestPermission.asStateFlow()

    private val _simGuestInviteCode = MutableStateFlow("")
    val simGuestInviteCode = _simGuestInviteCode.asStateFlow()

    fun log(message: String) {
        Log.d(tag, message)
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        _syncLogs.value = listOf("[$timestamp] $message") + _syncLogs.value.take(49)
    }

    // --- Invite Code Helpers ---
    fun generateInviteCode(permission: SyncPermission): String {
        val uuidPart = UUID.randomUUID().toString().substring(0, 4).uppercase()
        val permPart = if (permission == SyncPermission.READ_ONLY) "READ" else "WRITE"
        val code = "ROOM-$uuidPart-$permPart"
        if (_role.value == SyncRole.HOST) {
            _inviteCode.value = code
        } else if (_role.value == SyncRole.SIMULATION) {
            _simGuestInviteCode.value = code
        }
        return code
    }

    // --- P2P Network Sync (Host Server) ---
    fun startHosting(perm: SyncPermission) {
        stopAll()
        _role.value = SyncRole.HOST
        _permission.value = SyncPermission.FULL_ACCESS // Host always has full access
        val code = generateInviteCode(perm)
        log("Started Sync Host. Invite Code: $code")
        
        serverJob = scope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(9090)
                log("Server started on port 9090. Waiting for connections...")
                _isConnected.value = true
                
                while (isActive) {
                    val socket = serverSocket?.accept() ?: break
                    log("New connection request from ${socket.remoteSocketAddress}")
                    launch(Dispatchers.IO) {
                        handleClientConnection(socket)
                    }
                }
            } catch (e: Exception) {
                log("Host server error: ${e.localizedMessage}")
            }
        }
    }

    private suspend fun handleClientConnection(socket: Socket) {
        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), "UTF-8"))
        val writer = PrintWriter(socket.getOutputStream().writer(), true)
        
        try {
            // Read Handshake
            val handshake = reader.readLine() ?: return
            val parts = handshake.split("|")
            if (parts.size < 3 || parts[0] != "CONNECT") {
                writer.println("ERROR|Invalid Handshake")
                socket.close()
                return
            }

            val clientCode = parts[1]
            val clientName = parts[2]
            
            // Validate code
            if (clientCode != _inviteCode.value) {
                writer.println("ERROR|Invite code mismatch")
                socket.close()
                return
            }

            // Determine guest permission from code
            val clientPermission = if (clientCode.endsWith("READ")) {
                SyncPermission.READ_ONLY
            } else {
                SyncPermission.FULL_ACCESS
            }

            val peerId = UUID.randomUUID().toString()
            val peer = SyncPeer(peerId, clientName, clientPermission)
            
            // Accept connection
            writer.println("CONNECT_ACK|${clientPermission.name}|Shared Calendar")
            log("Accepted Guest '$clientName' with permission: ${clientPermission.name}")
            
            // Add peer
            _connectedPeers.value = _connectedPeers.value + peer
            
            // Send existing events
            val currentEvents = getCurrentEventsProvider()
            val syncedEvents = currentEvents.filter { it.isSynced }
            for (event in syncedEvents) {
                writer.println("ADD_EVENT|${serializeEvent(event)}")
            }
            log("Synced ${syncedEvents.size} existing events to '$clientName'")

            val handler = SocketHandler(socket, reader, writer)
            clientHandlers[peerId] = handler

            // Start reading client updates
            while (coroutineContext.isActive) {
                val msg = reader.readLine() ?: break
                handleIncomingMessage(msg, clientPermission, peerId)
            }
        } catch (e: Exception) {
            log("Error with client connection: ${e.localizedMessage}")
        } finally {
            socket.close()
            // Cleanup peer
            val peer = _connectedPeers.value.find { clientHandlers[it.id]?.socket == socket }
            if (peer != null) {
                _connectedPeers.value = _connectedPeers.value - peer
                clientHandlers.remove(peer.id)
                log("Guest '${peer.name}' disconnected.")
            }
        }
    }

    // --- P2P Network Sync (Client Join) ---
    fun joinHost(ip: String, code: String, deviceName: String) {
        stopAll()
        _role.value = SyncRole.CLIENT
        _inviteCode.value = code
        log("Connecting to Host at $ip with code $code...")

        clientJob = scope.launch(Dispatchers.IO) {
            try {
                val socket = Socket(ip, 9090)
                clientSocket = socket
                val writer = PrintWriter(socket.getOutputStream().writer(), true)
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), "UTF-8"))
                
                // Handshake
                writer.println("CONNECT|$code|$deviceName")
                val response = reader.readLine()
                if (response == null) {
                    log("Host closed connection during handshake.")
                    socket.close()
                    return@launch
                }

                val parts = response.split("|")
                if (parts[0] == "CONNECT_ACK") {
                    val perm = SyncPermission.valueOf(parts[1])
                    _permission.value = perm
                    _isConnected.value = true
                    log("Connected to Shared Calendar! Permission: ${perm.name}")

                    // Listen for server broadcast updates
                    while (isActive) {
                        val msg = reader.readLine() ?: break
                        handleIncomingMessage(msg, SyncPermission.FULL_ACCESS, "HOST")
                    }
                } else {
                    log("Connection Rejected: ${response.substringAfter('|')}")
                    socket.close()
                }
            } catch (e: Exception) {
                log("Connection error: ${e.localizedMessage}")
                _isConnected.value = false
                _role.value = SyncRole.NONE
            }
        }
    }

    private suspend fun handleIncomingMessage(msg: String, senderPermission: SyncPermission, peerId: String) {
        try {
            val parts = msg.split("|")
            if (parts.size < 2) return
            
            val cmd = parts[0]
            val payload = parts.subList(1, parts.size).joinToString("|")
            
            when (cmd) {
                "ADD_EVENT" -> {
                    // Check write permission of sender
                    if (senderPermission == SyncPermission.READ_ONLY) {
                        log("Warning: Rejected insert request from Read-Only Peer $peerId")
                        return
                    }
                    val event = deserializeEvent(payload)
                    onEventReceived(event)
                    log("Synced Event Added/Updated: '${event.title}'")
                    
                    // Host rebroadcasts to all other clients
                    if (_role.value == SyncRole.HOST) {
                        broadcastToClients(msg, peerId)
                    }
                }
                "DELETE_EVENT" -> {
                    if (senderPermission == SyncPermission.READ_ONLY) {
                        log("Warning: Rejected delete request from Read-Only Peer $peerId")
                        return
                    }
                    val syncId = payload
                    onEventDeleted(syncId)
                    log("Synced Event Deleted (SyncId: $syncId)")

                    if (_role.value == SyncRole.HOST) {
                        broadcastToClients(msg, peerId)
                    }
                }
            }
        } catch (e: Exception) {
            log("Error handling incoming message: ${e.localizedMessage}")
        }
    }

    private fun broadcastToClients(msg: String, excludePeerId: String) {
        for ((id, handler) in clientHandlers) {
            if (id != excludePeerId) {
                try {
                    handler.writer.println(msg)
                } catch (e: Exception) {
                    // Ignore, handler cleanup will occur on connection failure
                }
            }
        }
    }

    // --- Outward Updates Broadcast ---
    fun broadcastLocalUpdate(event: Event) {
        if (!event.isSynced) return
        val serialized = serializeEvent(event)
        val msg = "ADD_EVENT|$serialized"

        if (_role.value == SyncRole.HOST) {
            broadcastToClients(msg, "")
            log("Broadcasted event update to all clients: '${event.title}'")
        } else if (_role.value == SyncRole.CLIENT) {
            scope.launch(Dispatchers.IO) {
                try {
                    val writer = clientSocket?.getOutputStream()?.writer()
                    if (writer != null) {
                        PrintWriter(writer, true).println(msg)
                        log("Sent update to Host: '${event.title}'")
                    }
                } catch (e: Exception) {
                    log("Failed to send update to Host: ${e.localizedMessage}")
                }
            }
        } else if (_role.value == SyncRole.SIMULATION) {
            // Mirror database state in simulated client
            if (_simGuestConnected.value) {
                val list = _simGuestEvents.value.filter { it.syncId != event.syncId } + event
                _simGuestEvents.value = list
                log("[SIM] Host updated event: '${event.title}', synced to Guest.")
            }
        }
    }

    fun broadcastLocalDelete(syncId: String) {
        val msg = "DELETE_EVENT|$syncId"
        if (_role.value == SyncRole.HOST) {
            broadcastToClients(msg, "")
            log("Broadcasted deletion of Event (SyncId: $syncId) to clients.")
        } else if (_role.value == SyncRole.CLIENT) {
            scope.launch(Dispatchers.IO) {
                try {
                    val writer = clientSocket?.getOutputStream()?.writer()
                    if (writer != null) {
                        PrintWriter(writer, true).println(msg)
                        log("Sent delete request to Host for SyncId: $syncId")
                    }
                } catch (e: Exception) {
                    log("Failed to send deletion request: ${e.localizedMessage}")
                }
            }
        } else if (_role.value == SyncRole.SIMULATION) {
            if (_simGuestConnected.value) {
                _simGuestEvents.value = _simGuestEvents.value.filter { it.syncId != syncId }
                log("[SIM] Host deleted event: SyncId $syncId, synced to Guest.")
            }
        }
    }

    // --- In-Memory Split-Screen Simulation Control ---
    fun startSimulation(permission: SyncPermission) {
        stopAll()
        _role.value = SyncRole.SIMULATION
        _permission.value = SyncPermission.FULL_ACCESS
        
        _simGuestPermission.value = permission
        val code = generateInviteCode(permission)
        log("Started Local Sync Simulation. Room Code: $code")
    }

    fun simulateGuestConnect(code: String, deviceName: String): Boolean {
        if (_role.value != SyncRole.SIMULATION) return false
        if (code != _simGuestInviteCode.value) {
            log("[SIM] Guest connection failed: Invite Code mismatch.")
            return false
        }
        
        _simGuestConnected.value = true
        log("[SIM] Guest '$deviceName' joined with permission: ${_simGuestPermission.value.name}")
        
        // Initial sync of host events to simulated guest
        scope.launch {
            val currentEvents = getCurrentEventsProvider().filter { it.isSynced }
            _simGuestEvents.value = currentEvents
            log("[SIM] Initial sync complete. synced ${currentEvents.size} events to Guest.")
        }
        return true
    }

    fun simulateGuestDisconnect() {
        _simGuestConnected.value = false
        _simGuestEvents.value = emptyList()
        log("[SIM] Guest disconnected.")
    }

    fun addSimulatedEventFromGuest(title: String, startMillis: Long, endMillis: Long, isAllDay: Boolean, calendarId: Int) {
        if (_simGuestPermission.value == SyncPermission.READ_ONLY) {
            log("[SIM] Blocked simulated Guest write: Read-Only permission.")
            return
        }

        val event = Event(
            title = title,
            startMillis = startMillis,
            endMillis = endMillis,
            isAllDay = isAllDay,
            calendarId = calendarId,
            syncId = UUID.randomUUID().toString(),
            isSynced = true
        )

        // Insert into host database so they sync
        scope.launch {
            onEventReceived(event)
            // Mirror in guest list
            _simGuestEvents.value = _simGuestEvents.value + event
            log("[SIM] Guest created event: '$title'. Synced to Host in real-time!")
        }
    }

    fun deleteSimulatedEventFromGuest(syncId: String) {
        if (_simGuestPermission.value == SyncPermission.READ_ONLY) {
            log("[SIM] Blocked simulated Guest delete: Read-Only permission.")
            return
        }

        scope.launch {
            onEventDeleted(syncId)
            _simGuestEvents.value = _simGuestEvents.value.filter { it.syncId != syncId }
            log("[SIM] Guest deleted event (SyncId: $syncId). Synced to Host!")
        }
    }

    // --- Cleanup & Reset ---
    fun stopAll() {
        serverJob?.cancel()
        clientJob?.cancel()
        
        try {
            serverSocket?.close()
            clientSocket?.close()
        } catch (e: Exception) {}
        
        for (handler in clientHandlers.values) {
            try {
                handler.socket.close()
            } catch (e: Exception) {}
        }
        
        clientHandlers.clear()
        
        _role.value = SyncRole.NONE
        _permission.value = SyncPermission.NONE
        _inviteCode.value = ""
        _isConnected.value = false
        _connectedPeers.value = emptyList()
        
        // Simulation reset
        _simGuestConnected.value = false
        _simGuestEvents.value = emptyList()
        _simGuestPermission.value = SyncPermission.NONE
        _simGuestInviteCode.value = ""
        
        log("Synchronization engine stopped/reset.")
    }

    // --- Simple Serializer Helpers ---
    private fun serializeEvent(e: Event): String {
        return "${e.title}\t${e.startMillis}\t${e.endMillis}\t${e.isAllDay}\t${e.location}\t${e.notes}\t${e.repeatType}\t${e.reminderMinutes}\t${e.calendarId}\t${e.syncId}\t${e.isSynced}"
    }

    private fun deserializeEvent(str: String): Event {
        val token = str.split("\t")
        return Event(
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
    }

    private class SocketHandler(
        val socket: Socket,
        val reader: BufferedReader,
        val writer: PrintWriter
    )
}
