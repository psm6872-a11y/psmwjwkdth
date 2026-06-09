function doPost(e) {
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
  var newSheet = newSS.getSheetByName("포장이사");
  
  // 데이터 채워넣기 (기존 로직 동일)
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
  
  // 옵션비용
  var optionCostVal = parseFloat(data.optionCost) || 0;
  newSheet.getRange("J38").setValue(data.optionCost);
  newSheet.getRange("I38").setValue(optionCostVal < 0 ? "할인" : "옵션비용");
  
  // 물품 내역
  newSheet.getRange("A13").setValue(data.roomItems?.["안방"] || "");
  newSheet.getRange("B13").setValue(data.roomItems?.["작은방1"] || "");
  newSheet.getRange("C13").setValue(data.roomItems?.["작은방2"] || "");
  newSheet.getRange("D13").setValue(data.roomItems?.["입구방"] || "");
  newSheet.getRange("E13").setValue(data.roomItems?.["거실"] || "");
  newSheet.getRange("F13").setValue(data.roomItems?.["주방"] || "");
  newSheet.getRange("G13").setValue(data.roomItems?.["그외"] || "");
  
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
