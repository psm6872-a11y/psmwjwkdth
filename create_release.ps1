$token = ""
$repo = "psm6872-a11y/psmwjwkdth"
$tag = "v1.0.20"
$apkPath = "c:\Users\me\Documents\danalla\psm\app\build\outputs\apk\debug\app-debug.apk"

$headers = @{
    "Authorization" = "token $token"
    "Accept"        = "application/vnd.github.v3+json"
    "User-Agent"    = "PowerShell"
}

# Get existing release
Write-Output "Fetching release $tag ..."
$release = Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/releases/tags/$tag" -Method Get -Headers $headers
Write-Output "Release ID: $($release.id)"

# Delete old APK asset if exists
$oldAsset = $release.assets | Where-Object { $_.name -eq "app-debug.apk" }
if ($oldAsset) {
    Write-Output "Deleting old asset ID $($oldAsset.id) ..."
    Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/releases/assets/$($oldAsset.id)" -Method Delete -Headers $headers
    Write-Output "Old asset deleted."
}

# Upload new APK
$uploadUrl = $release.upload_url -replace '\{.*\}', ''
$uploadUrl = "${uploadUrl}?name=app-debug.apk"

Write-Output "Uploading new APK..."
$uploadHeaders = @{
    "Authorization" = "token $token"
    "Accept"        = "application/vnd.github.v3+json"
    "User-Agent"    = "PowerShell"
}
$uploadResponse = Invoke-RestMethod -Uri $uploadUrl -Method Post -Headers $uploadHeaders -InFile $apkPath -ContentType "application/vnd.android.package-archive"
Write-Output "APK uploaded: $($uploadResponse.browser_download_url)"
