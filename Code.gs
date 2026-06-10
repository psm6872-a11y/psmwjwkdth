function doPost(e) {
  Logger.log("doPost 진입");
  var data = JSON.parse(e.postData.contents);
  
  // 월별 폴더 생성
  var rootFolderName = "다날라 견적";
  var monthFolderName = Utilities.formatDate(new Date(), "GMT+9", "yyyy년 MM월");
  
  var rootFolder = getOrCreateFolder(rootFolderName, DriveApp.getRootFolder());
  var monthFolder = getOrCreateFolder(monthFolderName, rootFolder);
  
  // 템플릿 스프레드시트 복사
  var templateSS = SpreadsheetApp.openById("1BDM_cWNaFm19fAbLSAbh-N1u11geMuUi7gXz8kZ6-v0");
  var templateFile = DriveApp.getFileById(templateSS.getId());
  
  // 파일 이름 생성 (날짜 + 순번)
  var dateStr = data.estimateDate || Utilities.formatDate(new Date(), "GMT+9", "yyyy-MM-dd");
  var fileName = getNewFileName(monthFolder, dateStr);
  
  // 파일 복사 후 월별 폴더로 이동
  var newFile = templateFile.makeCopy(fileName, monthFolder);
  var newSS = SpreadsheetApp.openById(newFile.getId());
  var newSheet = newSS.getSheetByName(data.moveType);
  
  // 데이터 채워넣기 (기존 로직 동일)
  newSheet.getRange("B6").setValue(String(data.departure));
  newSheet.getRange("B7").setValue(String(data.destination));
  newSheet.getRange("J6").setValue(String(data.laddersStartFloor));
  newSheet.getRange("J7").setValue(String(data.laddersEndFloor));
  newSheet.getRange("B8").setValue(String(data.visitDate));  // 방문날짜
  newSheet.getRange("C8").setValue(String(data.estimateDate));
  newSheet.getRange("F8").setValue(String(data.moveDate));
  newSheet.getRange("J8").setValue(String(data.startTime));
  newSheet.getRange("B9").setValue(String(data.moveInfo || "포장이사"));
  newSheet.getRange("G9").setValue(String(data.phoneNumber));
  newSheet.getRange("A38").setValue(String(data.memo));
  newSheet.getRange("G37").setValue(data.totalVolume + "톤");
  newSheet.getRange("J37").setValue(formatWonMan(data.moveCost));
  newSheet.getRange("G38").setValue("남" + data.workersM + " 여" + data.workersF);
  newSheet.getRange("G39").setValue(formatWonMan(data.extraTruck));
  newSheet.getRange("J39").setValue(formatWonMan(data.totalCost));
  newSheet.getRange("G40").setValue(data.laddersStartFloor + "층 / " + data.laddersStartCost + "만원");
  newSheet.getRange("G41").setValue(data.laddersEndFloor + "층 / " + data.laddersEndCost + "만원");
  newSheet.getRange("J40").setValue(formatWonMan(data.deposit));
  newSheet.getRange("J41").setValue(formatWonMan(data.balance));
  newSheet.getRange("G46").setValue(data.customerName);
  
  // 옵션비용
  var optionCostVal = parseFloat(data.optionCost) || 0;
  newSheet.getRange("J38").setValue(formatWonMan(data.optionCost));
  newSheet.getRange("I38").setValue(optionCostVal < 0 ? "할인" : "옵션비용");
  
  // 물품 내역
  newSheet.getRange("A13").setValue(data.roomItems?.["안방"] || "");
  newSheet.getRange("A13").setBackground(null);
  newSheet.getRange("B13").setValue(data.roomItems?.["작은방1"] || "");
  newSheet.getRange("C13").setValue(data.roomItems?.["작은방2"] || "");
  newSheet.getRange("D13").setValue(data.roomItems?.["입구방"] || "");
  newSheet.getRange("E13").setValue(data.roomItems?.["거실"] || "");
  newSheet.getRange("F13").setValue(data.roomItems?.["주방"] || "");
  newSheet.getRange("G13").setValue(data.roomItems?.["그외"] || "");
  
  // 제외 옵션
  // roomItems에서 제외 항목 추출
  var excludeItems = {};
  Object.keys(data.roomItems || {}).forEach(function(room) {
    var items = data.roomItems[room];
    if (items) {
      items.split("\n").forEach(function(item) {
        if (item.includes("(폐기)")) excludeItems["폐기"] = (excludeItems["폐기"] || "") + item.replace("(폐기)", "").trim() + "\n";
        if (item.includes("(제자리)")) excludeItems["제자리"] = (excludeItems["제자리"] || "") + item.replace("(제자리)", "").trim() + "\n";
        if (item.includes("(1층)")) excludeItems["1층"] = (excludeItems["1층"] || "") + item.replace("(1층)", "").trim() + "\n";
      });
    }
  });
  newSheet.getRange("K13").setValue(excludeItems["폐기"] || "");
  newSheet.getRange("I13").setValue(excludeItems["제자리"] || "");
  newSheet.getRange("J13").setValue(excludeItems["1층"] || "");
  
  // 특수 항목
  // 전체 roomItems에서 특수 항목 추출
  var allItems = Object.values(data.roomItems || {}).join("\n");
  
  // 장농 분해형
  var jangItem = allItems.split("\n").filter(function(i) { return i.includes("장농") && i.includes("분해형"); }).join("\n");
  newSheet.getRange("J25").setValue(jangItem);
  
  // 세탁기/건조기 일체형
  var washItem = allItems.split("\n").filter(function(i) { return (i.includes("세탁기") || i.includes("건조기")) && i.includes("일체형"); }).join("\n");
  newSheet.getRange("F25").setValue(washItem);
  
  // 에어컨
  var airconItem = allItems.split("\n").filter(function(i) { return i.includes("에어컨"); }).join("\n");
  newSheet.getRange("B25").setValue(airconItem);
  
  // TV 벽걸이 (85"이상 제외)
  var tvWallItem = allItems.split("\n").filter(function(i) {
    return i.includes("TV") && i.includes("벽걸이") && !i.includes("85\"") && !i.includes("(폐기)") && !i.includes("(제자리)") && !i.includes("(1층)");
  }).join("\n");
  newSheet.getRange("B26").setValue(tvWallItem);

  // TV 85"이상 (공통)
  var tv85Item = allItems.split("\n").filter(function(i) {
    return i.includes("TV") && i.includes("85\"") && !i.includes("(폐기)") && !i.includes("(제자리)") && !i.includes("(1층)");
  }).join("\n");
  newSheet.getRange("F27").setValue(tv85Item);
  
  // 식기세척기 매립형
  var dishItem = allItems.split("\n").filter(function(i) { return i.includes("식기세척기") && i.includes("매립형"); }).join("\n");
  newSheet.getRange("F26").setValue(dishItem);
  
  // 저장된 파일 URL 반환
  var fileUrl = "https://docs.google.com/spreadsheets/d/" + newFile.getId();
  
  return ContentService.createTextOutput(JSON.stringify({
    status: "success", 
    sheetName: fileName,
    fileUrl: fileUrl
  })).setMimeType(ContentService.MimeType.JSON);
}

function getOrCreateFolder(folderName, parentFolder) {
  var folders = parentFolder.getFoldersByName(folderName);
  if (folders.hasNext()) {
    return folders.next();
  }
  return parentFolder.createFolder(folderName);
}

function getNewFileName(folder, dateStr) {
  var index = 1;
  while (folder.getFilesByName(dateStr + "(" + index + ")").hasNext()) {
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

function formatWonMan(val) {
  if (val === undefined || val === null || String(val).trim() === "") {
    return "";
  }
  var s = String(val).trim();
  // 중복 방지
  if (s.indexOf("₩") === 0) {
    s = s.substring(1);
  }
  if (s.endsWith("만원")) {
    s = s.substring(0, s.length - 2);
  }
  return "₩" + s + "만원";
}
