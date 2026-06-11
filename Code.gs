function doPost(e) {
  Logger.log("doPost 진입");
  var data = JSON.parse(e.postData.contents);
  
  // 월별 폴더 생성
  var rootFolderName = "다날라 견적";
  var monthFolderName = Utilities.formatDate(new Date(), "GMT+9", "yyyy년 MM월");
  
  var rootFolder = getOrCreateFolder(rootFolderName, DriveApp.getRootFolder());
  var monthFolder = getOrCreateFolder(monthFolderName, rootFolder);
  
  // 템플릿 스프레드시트 열기
  var templateSS = SpreadsheetApp.openById("1BDM_cWNaFm19fAbLSAbh-N1u11geMuUi7gXz8kZ6-v0");
  var templateFile = DriveApp.getFileById(templateSS.getId());
  
  // 월별 파일 이름 (예: "2026년 06월")
  var spreadsheetName = monthFolderName;
  
  // 월별 파일이 해당 폴더에 존재하는지 확인
  var files = monthFolder.getFilesByName(spreadsheetName);
  var destSS;
  var isNewFile = false;
  if (files.hasNext()) {
    var file = files.next();
    destSS = SpreadsheetApp.openById(file.getId());
  } else {
    // 월별 파일이 없으면 템플릿 복사해서 생성
    var newFile = templateFile.makeCopy(spreadsheetName, monthFolder);
    destSS = SpreadsheetApp.openById(newFile.getId());
    isNewFile = true;
  }
  
  // 시트 이름 생성 (날짜 + 순번, 예: 06-10(1))
  var dateStr = data.estimateDate || Utilities.formatDate(new Date(), "GMT+9", "yyyy-MM-dd");
  var dateParts = dateStr.split("-");
  var monthDayStr = "견적";
  if (dateParts.length >= 3) {
    monthDayStr = dateParts[1] + "-" + dateParts[2];
  } else {
    monthDayStr = Utilities.formatDate(new Date(), "GMT+9", "MM-dd");
  }
  
  var index = 1;
  while (destSS.getSheetByName(monthDayStr + "(" + index + ")") !== null) {
    index++;
  }
  var sheetName = monthDayStr + "(" + index + ")";
  
  // 템플릿 스프레드시트에서 data.moveType에 해당하는 시트를 복사해서 추가
  var templateSheet = templateSS.getSheetByName(data.moveType);
  if (!templateSheet) {
    // 예외 상황
    templateSheet = templateSS.getSheets()[0];
  }
  var newSheet = templateSheet.copyTo(destSS);
  newSheet.setName(sheetName);
  newSheet.showSheet();
  
  // 새로 생성된 파일인 경우 기존 템플릿 복사 시 같이 들어온 불필요한 기본 탭들을 삭제
  if (isNewFile) {
    var sheets = destSS.getSheets();
    sheets.forEach(function(sh) {
      if (sh.getName() !== sheetName) {
        try {
          destSS.deleteSheet(sh);
        } catch(err) {
          Logger.log("기본 시트 삭제 에러: " + err.message);
        }
      }
    });
  }
  
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
  newSheet.getRange("J37").setNumberFormat("@").setValue(formatWonMan(data.moveCost));
  newSheet.getRange("G38").setValue("남" + data.workersM + " 여" + data.workersF);
  newSheet.getRange("G39").setValue(formatWonMan(data.extraTruck));
  newSheet.getRange("J39").setNumberFormat("@").setValue(formatWonMan(data.totalCost));
  newSheet.getRange("G40").setValue(data.laddersStartFloor + "층 / " + data.laddersStartCost + "만원");
  newSheet.getRange("G41").setValue(data.laddersEndFloor + "층 / " + data.laddersEndCost + "만원");
  newSheet.getRange("J40").setNumberFormat("@").setValue(formatWonMan(data.deposit));
  newSheet.getRange("J41").setNumberFormat("@").setValue(formatWonMan(data.balance));
  newSheet.getRange("G46").setValue(data.customerName);
  
  // 옵션비용
  var optionCostVal = parseFloat(data.optionCost) || 0;
  newSheet.getRange("J38").setNumberFormat("@").setValue(formatWonMan(data.optionCost));
  newSheet.getRange("I38").setValue(optionCostVal < 0 ? "할인" : "옵션비용");
  // 물품 내역
  newSheet.getRange("A13:G13").setBackground("#ffffff");
  newSheet.getRange("A13").setValue(data.roomItems?.["안방"] || "");
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
  
  // 저장된 파일 URL 반환 (해당 시트로 바로 이동하는 gid 쿼리 추가)
  var fileUrl = "https://docs.google.com/spreadsheets/d/" + destSS.getId() + "/edit#gid=" + newSheet.getSheetId();
  
  // PDF 생성 및 base64 인코딩 & 구글 드라이브 영구 저장
  var pdfBase64 = "";
  var pdfFileId = "";
  try {
    var pdfUrl = "https://docs.google.com/spreadsheets/d/" + destSS.getId() + "/export?exportFormat=pdf&format=pdf&gid=" + newSheet.getSheetId();
    var pdfResponse = UrlFetchApp.fetch(pdfUrl, {
      headers: {
        'Authorization': 'Bearer ' + ScriptApp.getOAuthToken(),
        'MuteHttpExceptions': true
      }
    });
    var pdfBlob = pdfResponse.getBlob();
    pdfBase64 = Utilities.base64Encode(pdfBlob.getBytes());
    
    // 구글 드라이브 영구 저장
    var pdfRootFolderName = "다날라 견적서pdf";
    var pdfRootFolder = getOrCreateFolder(pdfRootFolderName, DriveApp.getRootFolder());
    var pdfMonthFolder = getOrCreateFolder(monthFolderName, pdfRootFolder);
    
    var pdfFileName = sheetName + (data.customerName ? "_" + data.customerName : "") + ".pdf";
    var pdfFile = pdfMonthFolder.createFile(pdfBlob);
    pdfFile.setName(pdfFileName);
    
    // 링크가 있는 모든 사용자에게 뷰어 권한 허용
    pdfFile.setSharing(DriveApp.Access.ANYONE_WITH_LINK, DriveApp.Permission.VIEW);
    pdfFileId = pdfFile.getId();
  } catch (pdfErr) {
    Logger.log("PDF 생성 및 저장 중 에러 발생: " + pdfErr.message);
  }

  // 디버깅 정보 수집
  var debugInfo = {};
  try {
    debugInfo.b9_merged = newSheet.getRange("B9").isPartOfMerge();
    debugInfo.b9_val = String(newSheet.getRange("B9").getValue());
    debugInfo.a9_val = String(newSheet.getRange("A9").getValue());
    debugInfo.c9_val = String(newSheet.getRange("C9").getValue());
    debugInfo.b10_val = String(newSheet.getRange("B10").getValue());
    debugInfo.b8_val = String(newSheet.getRange("B8").getValue());
  } catch (debugErr) {
    debugInfo.error = debugErr.message;
  }
  
  return ContentService.createTextOutput(JSON.stringify({
    status: "success", 
    sheetName: sheetName,
    fileUrl: fileUrl,
    pdfBase64: pdfBase64,
    pdfFileId: pdfFileId,
    debugInfo: debugInfo
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
