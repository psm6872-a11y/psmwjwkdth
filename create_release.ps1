$token = "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"
$repo = "psm6872-a11y/psmwjwkdth"
$tag = "v1.0.29"
$apkPath = "c:\Users\me\Documents\danalla\psm\app\build\outputs\apk\debug\app-debug.apk"

$headers = @{
    "Authorization" = "token $token"
    "Accept"        = "application/vnd.github.v3+json"
    "User-Agent"    = "PowerShell"
}

# Create new release
Write-Output "Creating release $tag ..."
$body = '{"tag_name":"' + $tag + '","name":"' + $tag + '","body":"v1.0.29: HTTP 411 Length Required 및 특수문자/한글 경로 오류 수정을 통한 원격 공유 연동 기능 정상화","draft":false,"prerelease":false}'
$bodyBytes = [System.Text.Encoding]::UTF8.GetBytes($body)

$release = Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/releases" -Method Post -Headers $headers -Body $bodyBytes -ContentType "application/json; charset=utf-8"
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
