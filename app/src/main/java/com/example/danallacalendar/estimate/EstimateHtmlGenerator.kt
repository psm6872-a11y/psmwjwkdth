package com.example.danallacalendar.estimate

import android.content.Context

object EstimateHtmlGenerator {

    fun generateEstimateHtml(context: Context, estimate: Estimate): String {
        val template = context.assets.open("estimate_template.html").use { inputStream ->
            inputStream.bufferedReader().use { it.readText() }
        }

        // Helper to format currency
        fun formatCurrency(valStr: String): String {
            val clean = valStr.replace("₩", "").replace("만원", "").trim()
            if (clean.isEmpty()) return ""
            return "₩ $clean 만원"
        }

        // Replace basic fields
        var html = template
            .replace("{{departure}}", estimate.departure)
            .replace("{{destination}}", estimate.destination)
            .replace("{{estimateDate}}", estimate.estimateDate)
            .replace("{{moveDate}}", estimate.moveDate)
            .replace("{{phoneNumber}}", estimate.phoneNumber)
            .replace("{{moveInfo}}", estimate.moveInfo.ifBlank { estimate.moveType })
            .replace("{{startTime}}", estimate.startTime)
            .replace("{{customerName}}", estimate.customerName)
            .replace("{{memo}}", estimate.memo)
            .replace("{{totalVolume}}", if (estimate.totalVolume.isNotEmpty()) "${estimate.totalVolume} 톤" else "")
            .replace("{{workers}}", if (estimate.workersM.isNotEmpty() || estimate.workersF.isNotEmpty()) "남${estimate.workersM} 여${estimate.workersF}" else "")
            .replace("{{ladderStart}}", if (estimate.laddersStartFloor.isNotEmpty() || estimate.laddersStartCost.isNotEmpty()) "${estimate.laddersStartFloor}층 / ${estimate.laddersStartCost}만원" else "")
            .replace("{{ladderEnd}}", if (estimate.laddersEndFloor.isNotEmpty() || estimate.laddersEndCost.isNotEmpty()) "${estimate.laddersEndFloor}층 / ${estimate.laddersEndCost}만원" else "")
            .replace("{{extraTruck}}", formatCurrency(estimate.extraTruck))
            .replace("{{moveCost}}", formatCurrency(estimate.moveCost))
            .replace("{{optionCost}}", formatCurrency(estimate.optionCost))
            .replace("{{totalCost}}", formatCurrency(estimate.totalCost))
            .replace("{{deposit}}", formatCurrency(estimate.deposit))
            .replace("{{balance}}", formatCurrency(estimate.balance))

        // Replace room columns
        val columns = listOf("안방", "작은방1", "작은방2", "입구방", "거실", "주방", "그외")
        columns.forEach { col ->
            val itemsMap = estimate.roomItems[col] ?: emptyMap()
            val formatted = itemsMap.entries.joinToString("\n") { (item, count) ->
                if (count > 1) "$item x$count" else item
            }
            html = html.replace("{{room_$col}}", formatted)
        }

        // Replace disposal columns (제자리, 1층, 폐기)
        val disposalTypes = listOf("제자리", "1층", "폐기")
        disposalTypes.forEach { type ->
            val itemsList = mutableListOf<String>()
            estimate.roomItems.forEach { (_, itemsMap) ->
                itemsMap.forEach { (item, count) ->
                    if (item.contains("($type)")) {
                        val cleanItem = item.replace("($type)", "").trim()
                        itemsList.add(if (count > 1) "$cleanItem x$count" else cleanItem)
                    }
                }
            }
            html = html.replace("{{room_$type}}", itemsList.joinToString("\n"))
        }

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
            it.contains("TV") && it.contains("벽걸이") && !it.contains("85\"") &&
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
