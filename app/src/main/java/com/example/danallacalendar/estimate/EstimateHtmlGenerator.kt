package com.example.danallacalendar.estimate

import android.content.Context
import android.util.Base64
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.gms.tasks.Tasks
import com.example.danallacalendar.data.local.UserPreferences

object EstimateHtmlGenerator {

    fun generateEstimateHtml(context: Context, estimate: Estimate): String {
        val templateName = when (estimate.moveType) {
            "보관이사" -> "storage_estimate_template.html"
            "사무실이사" -> "office_estimate_template.html"
            else -> "estimate_template.html"
        }
        val template = context.assets.open(templateName).use { inputStream ->
            inputStream.bufferedReader().use { it.readText() }
        }

        // UserPreferences에서 방코드(roomCode) 획득
        val userPreferences = UserPreferences(context)
        val roomCode = userPreferences.getLastRoomCode()

        var companyName = "다날라 익스프레스"
        var licenseNumber = ""
        var ceoNickname = ""
        var companyPhone = ""
        var ceoName = ""
        var bizNumber = ""
        var bankAccount = ""
        var logoBase64 = ""
        var stampBase64 = ""

        if (roomCode.isNotEmpty()) {
            try {
                // Tasks.await가 메인 스레드에서 불릴 때의 IllegalStateException을 피하기 위해 
                // 백그라운드 싱글 스레드 Executor를 생성하여 Tasks.await를 그 위에서 실행시키고 메인 스레드에서 future.get()으로 대기합니다.
                val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
                val future = executor.submit(java.util.concurrent.Callable {
                    val firestore = FirebaseFirestore.getInstance()
                    val task = firestore.collection("company_info").document(roomCode).get()
                    Tasks.await(task)
                })
                val documentSnapshot = future.get()
                executor.shutdown()
                
                if (documentSnapshot != null && documentSnapshot.exists()) {
                    companyName = documentSnapshot.getString("companyName")?.ifBlank { "다날라 익스프레스" } ?: "다날라 익스프레스"
                    licenseNumber = documentSnapshot.getString("licenseNumber") ?: ""
                    ceoNickname = documentSnapshot.getString("ceoNickname") ?: ""
                    companyPhone = documentSnapshot.getString("companyPhone") ?: ""
                    ceoName = documentSnapshot.getString("ceoName") ?: ""
                    bizNumber = documentSnapshot.getString("bizNumber") ?: ""
                    bankAccount = documentSnapshot.getString("bankAccount") ?: ""
                    logoBase64 = documentSnapshot.getString("logoBase64") ?: ""
                    stampBase64 = documentSnapshot.getString("stampBase64") ?: ""
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // assets 파일 -> Base64 헬퍼 함수
        fun assetToBase64(assetName: String): String {
            return try {
                context.assets.open(assetName).use { inputStream ->
                    val bytes = inputStream.readBytes()
                    val base64Encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    "data:image/png;base64,$base64Encoded"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                ""
            }
        }

        // 로고 이미지 처리 (미지정 시 1x1 투명 GIF)
        val logoFinal = if (logoBase64.isNotEmpty()) logoBase64 else "data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7"

        // 도장 이미지 처리 (미지정 시 blank_stamp.png)
        val stampFinal = if (stampBase64.isNotEmpty()) stampBase64 else assetToBase64("blank_stamp.png")

        // Helper to format currency
        fun formatCurrency(valStr: String): String {
            val clean = valStr.replace("₩", "").replace("만원", "").trim()
            if (clean.isEmpty()) return ""
            return "₩ $clean 만원"
        }

        val optionCostVal = estimate.optionCost.toLongOrNull() ?: 0L
        val optionLabel = if (optionCostVal < 0) "할인" else "옵션비용"

        // Replace basic fields
        var html = template
            .replace("{{companyName}}", companyName)
            .replace("{{licenseNumber}}", licenseNumber)
            .replace("{{ceoNickname}}", ceoNickname)
            .replace("{{companyPhone}}", companyPhone)
            .replace("{{ceoName}}", ceoName)
            .replace("{{bizNumber}}", bizNumber)
            .replace("{{bankAccount}}", bankAccount)
            .replace("{{logoBase64}}", logoFinal)
            .replace("{{stampBase64}}", stampFinal)
            .replace("{{departure}}", estimate.departure.replace("|", " "))
            .replace("{{destination}}", estimate.destination.replace("|", " "))

            .replace("{{departureFloorType}}", estimate.departureFloorType)
            .replace("{{destinationFloorType}}", estimate.destinationFloorType)
            .replace("{{estimateDate}}", estimate.estimateDate)
            .replace("{{moveDate}}", estimate.moveDate)
            .replace("{{phoneNumber}}", estimate.phoneNumber)
            .replace("{{moveInfo}}", estimate.moveInfo.ifBlank { estimate.moveType })
            .replace("{{startTime}}", estimate.startTime)
            .replace("{{outDate}}", if (estimate.outDate == "미정") "" else estimate.outDate)
            .replace("{{customerName}}", estimate.customerName)
            .replace("{{memo}}", estimate.memo.replace("\n", "<br>"))
            .replace("{{totalVolume}}", if (estimate.totalVolume.isNotEmpty()) "${estimate.totalVolume} 톤" else "")
            .replace("{{workers}}", if (estimate.workersM.isNotEmpty() || estimate.workersF.isNotEmpty()) "남${estimate.workersM} 여${estimate.workersF}" else "")
            .replace("{{ladderStart}}", if (estimate.laddersStartFloor.isNotEmpty() || estimate.laddersStartCost.isNotEmpty()) "${estimate.laddersStartFloor}층 / ${estimate.laddersStartCost}만원" else "")
            .replace("{{ladderEnd}}", if (estimate.laddersEndFloor.isNotEmpty() || estimate.laddersEndCost.isNotEmpty()) "${estimate.laddersEndFloor}층 / ${estimate.laddersEndCost}만원" else "")
            .replace("{{extraTruck}}", formatCurrency(estimate.extraTruck))
            .replace("{{storageCost}}", formatCurrency(estimate.storageCost))
            .replace("{{moveCost}}", formatCurrency(estimate.moveCost))
            .replace("{{moveCostOut}}", formatCurrency(estimate.moveCostOut))
            .replace("{{optionCost}}", formatCurrency(estimate.optionCost))
            .replace("{{totalCost}}", formatCurrency(estimate.totalCost))
            .replace("{{deposit}}", formatCurrency(estimate.deposit))
            .replace("{{balance}}", formatCurrency(estimate.balance))
            .replace("{{balanceOut}}", formatCurrency(estimate.balanceOut))
            .replace("옵션비용", optionLabel)

        // Replace room columns
        if (estimate.moveType == "사무실이사") {
            val candidates = listOf("대표실", "사무실", "회의실", "기타")
            val presentKeys = estimate.roomItems.keys.filter { it != "제외" && it != "이동하지않음" }
            val displaySpaces = mutableListOf<String>()
            
            candidates.forEach { cand ->
                if (cand in presentKeys) {
                    displaySpaces.add(cand)
                }
            }
            presentKeys.forEach { key ->
                if (key !in displaySpaces) {
                    displaySpaces.add(key)
                }
            }
            candidates.forEach { cand ->
                if (displaySpaces.size < 4 && cand !in displaySpaces) {
                    displaySpaces.add(cand)
                }
            }
            while (displaySpaces.size < 4) {
                displaySpaces.add("")
            }
            
            for (i in 0 until 4) {
                val spaceName = displaySpaces[i]
                html = html.replace("{{header_${i + 1}}}", spaceName)
                
                val itemsMap = if (spaceName.isNotEmpty()) estimate.roomItems[spaceName] ?: emptyMap() else emptyMap()
                val formatted = itemsMap.entries.joinToString("\n") { (item, count) ->
                    if (count > 1) "$item x$count" else item
                }
                html = html.replace("{{room_col_${i + 1}}}", formatted)

                val vol = if (spaceName.isNotEmpty()) estimate.roomVolumes[spaceName] ?: "" else ""
                val formattedVol = if (vol.isNotEmpty()) "${vol}t" else ""
                html = html.replace("{{volume_col_${i + 1}}}", formattedVol)
            }
        } else {
            val columns = listOf("안방", "작은방1", "작은방2", "입구방", "거실", "주방", "그외")
            columns.forEach { col ->
                val itemsMap = estimate.roomItems[col] ?: emptyMap()
                val formatted = itemsMap.entries.joinToString("\n") { (item, count) ->
                    if (count > 1) "$item x$count" else item
                }
                html = html.replace("{{room_$col}}", formatted)
            }
        }

        // Replace combined disposal column (제자리, 1층, 폐기)
        val combinedList = mutableListOf<String>()
        val disposalTypes = listOf("제자리", "1층", "폐기")
        
        estimate.roomItems.forEach { (_, itemsMap) ->
            itemsMap.forEach { (item, count) ->
                disposalTypes.forEach { type ->
                    if (item.contains("($type)")) {
                        val cleanItem = item.replace("($type)", "").trim()
                        val formattedItem = "$cleanItem-$type"
                        combinedList.add(if (count > 1) "$formattedItem x$count" else formattedItem)
                    }
                }
            }
        }
        
        html = html.replace("{{room_이동하지않음}}", combinedList.joinToString("\n"))

        // Replace special appliances/furniture in options table
        val allItemsList = estimate.roomItems.values.flatMap { it.keys }

        // 에어컨
        val aircon = allItemsList.filter { it.contains("에어컨") }.joinToString("\n")
        html = html.replace("{{opt_에어컨}}", aircon)

        // 일체형세탁기
        val washer = allItemsList.filter { (it.contains("세탁기") || it.contains("건조기")) && it.contains("일체형") }.joinToString("\n")
        html = html.replace("{{opt_일체형세탁기}}", washer)

        // 분해장농
        val wardrobe = allItemsList.filter { it.contains("장농") && it.contains("분해형") }.joinToString("\n")
        html = html.replace("{{opt_분해장농}}", wardrobe)

        // 벽걸이TV
        val tvWall = allItemsList.filter {
            it.contains("TV") && it.contains("벽걸이") &&
                    !it.contains("(폐기)") && !it.contains("(제자리)") && !it.contains("(1층)")
        }.joinToString("\n")
        html = html.replace("{{opt_벽걸이TV}}", tvWall)

        // 식기세척기
        val dishwasher = allItemsList.filter { it.contains("식기세척기") && it.contains("매립형") }.joinToString("\n")
        html = html.replace("{{opt_식기세척기}}", dishwasher)

        // 시스템행거
        val hanger = allItemsList.filter { it.contains("시스템행거") }.joinToString("\n")
        html = html.replace("{{opt_시스템행거}}", hanger)

        return html
    }
}
