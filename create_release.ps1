$token = ""
$repo = "psm6872-a11y/psmwjwkdth"
$tag = "v1.0.24"
$apkPath = "c:\Users\me\Documents\danalla\psm\app\build\outputs\apk\debug\app-debug.apk"

$headers = @{
    "Authorization" = "token $token"
    "Accept"        = "application/vnd.github.v3+json"
    "User-Agent"    = "PowerShell"
}

# Create new release
Write-Output "Creating release $tag ..."
$body = @{
    tag_name = $tag
    name     = $tag
    body     = "v1.0.24: 최근 통화 목록 중복 제거, 전화번호 포맷팅(하이픈 추가), 이름 미지정 번호 레이아웃 개선"
    draft    = $false
    prerelease = $false
} | ConvertTo-Json

$release = Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/releases" -Method Post -Headers $headers -Body $body -ContentType "application/json"
Write-Output "Release ID: $($release.id)"

# Upload APK
$uploadUrl = $release.upload_url -replace '\{.*\}', ''
$uploadUrl = "${uploadUrl}?name=app-debug.apk"

Write-Output "Uploading APK..."
$uploadHeaders = @{
    "Authorization" = "token $token"
    "Accept"        = "application/vnd.github.v3+json"
    "User-Agent"    = "PowerShell"
}
$uploadResponse = Invoke-RestMethod -Uri $uploadUrl -Method Post -Headers $uploadHeaders -InFile $apkPath -ContentType "application/vnd.android.package-archive"
Write-Output "APK uploaded: $($uploadResponse.browser_download_url)"
