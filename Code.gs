function doPost(e) {
  var ss = SpreadsheetApp.openById("1BDM_cWNaFm19fAbLSAbh-N1u11geMuUi7gXz8kZ6-v0");
  var templateSheet = ss.getSheetByName("포장이사");
  var data = JSON.parse(e.postData.contents);
  
  // 날짜별 시트 이름 생성 (2026-06-09(1), (2)...)
  var dateStr = data.estimateDate || Utilities.formatDate(new Date(), "GMT+9", "yyyy-MM-dd");
  var sheetName = getNewSheetName(ss, dateStr);
  
  // 포장이사 시트 복사
  var newSheet = templateSheet.copyTo(ss);
  newSheet.setName(sheetName);
  
  // 데이터 채워넣기
  newSheet.getRange("B6").setValue(data.departure);
  newSheet.getRange("B7").setValue(data.destination);
  newSheet.getRange("J6").setValue(data.laddersStartFloor);
  newSheet.getRange("J7").setValue(data.laddersEndFloor);
  newSheet.getRange("C8").setValue(data.estimateDate);
  newSheet.getRange("F8").setValue(data.moveDate);
  newSheet.getRange("J8").setValue(data.startTime);
  newSheet.getRange("C9").setValue(data.moveType);
  newSheet.getRange("G9").setValue(data.phoneNumber);
  newSheet.getRange("A38").setValue(data.memo);
  newSheet.getRange("G37").setValue(data.totalVolume + "톤");
  newSheet.getRange("J37").setValue(data.moveCost);
  newSheet.getRange("G38").setValue("남" + data.workersM + " 여" + data.workersF);
  newSheet.getRange("G39").setValue(data.extraTruck);
  newSheet.getRange("J39").setValue(data.totalCost);
  newSheet.getRange("G40").setValue(data.laddersStartFloor + "층 / " + data.laddersStartCost + "만원");
  newSheet.getRange("G41").setValue(data.laddersEndFloor + "층 / " + data.laddersEndCost + "만원");
  newSheet.getRange("J40").setValue(data.deposit);
  newSheet.getRange("J41").setValue(data.balance);
  newSheet.getRange("G46").setValue(data.customerName);
  
  // 옵션비용 - 음수면 I38을 "할인"으로 변경
  var optionCostVal = parseFloat(data.optionCost) || 0;
  newSheet.getRange("J38").setValue(data.optionCost);
  newSheet.getRange("I38").setValue(optionCostVal < 0 ? "할인" : "옵션비용");
  
  // 물품 내역 (줄바꿈으로 각 셀에 입력)
  newSheet.getRange("A13").setValue(data.roomItems?.["안방"] || "");
  newSheet.getRange("B13").setValue(data.roomItems?.["작은방1"] || "");
  newSheet.getRange("C13").setValue(data.roomItems?.["작은방2"] || "");
  newSheet.getRange("D13").setValue(data.roomItems?.["입구방"] || "");
  newSheet.getRange("E13").setValue(data.roomItems?.["거실"] || "");
  newSheet.getRange("F13").setValue(data.roomItems?.["주방"] || "");
  newSheet.getRange("G13").setValue(data.roomItems?.["그외"] || "");
  
  return ContentService.createTextOutput(JSON.stringify({status: "success", sheetName: sheetName}))
    .setMimeType(ContentService.MimeType.JSON);
}

function getNewSheetName(ss, dateStr) {
  var index = 1;
  while (ss.getSheetByName(dateStr + "(" + index + ")")) {
    index++;
  }
  return dateStr + "(" + index + ")";
}

function deleteTestSheets() {
  var ss = SpreadsheetApp.openById("1BDM_cWNaFm19fAbLSAbh-N1u11geMuUi7gXz8kZ6-v0");
  var sheets = ss.getSheets();
  sheets.forEach(function(sheet) {
    if (sheet.getName() !== "포장이사") {
      ss.deleteSheet(sheet);
    }
  });
}
