$emulatorPath = "C:\Users\me\AppData\Local\Android\Sdk\emulator\emulator.exe"
$adbPath = "C:\Users\me\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$artifactDir = "C:\Users\me\.gemini\antigravity\brain\2dae3cf7-303e-403f-8b2e-27900292a8c7"

$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

# 1. Start emulator
Write-Host "Starting emulator galaxy_s25_2..."
Start-Process $emulatorPath -ArgumentList "@galaxy_s25_2" -WindowStyle Normal

# 2. Wait for ADB detection
Write-Host "Waiting for ADB to detect device..."
& $adbPath wait-for-device

# 3. Wait for Boot
Write-Host "Device detected. Waiting for boot to complete..."
while ($true) {
    $boot = & $adbPath shell getprop sys.boot_completed
    if ($boot -ne $null) {
        $bootVal = $boot.Trim()
        Write-Host "Current boot status: '$bootVal'"
        if ($bootVal -eq "1") {
            Write-Host "Emulator boot completed successfully!"
            break
        }
    }
    Start-Sleep -Seconds 2
}

# 4. Install App via Gradle
Write-Host "Running gradlew installDebug..."
& .\gradlew.bat installDebug

# 5. Launch App
Write-Host "Launching app..."
& $adbPath shell am start -n "com.example.danallacalendar/.MainActivity"

# 6. Wait for app to render
Write-Host "Waiting 10 seconds for app to render..."
Start-Sleep -Seconds 10

# 7. Take Screenshot
Write-Host "Taking screenshot..."
& $adbPath shell screencap -p /sdcard/screenshot.png
& $adbPath pull /sdcard/screenshot.png "$artifactDir\screenshot.png"
Write-Host "Screenshot saved to $artifactDir\screenshot.png"

# 8. Keep PowerShell running so the emulator doesn't close
Write-Host "Keeping session alive to keep emulator running. Press Ctrl+C if you want to abort."
while ($true) {
    Start-Sleep -Seconds 10
}
