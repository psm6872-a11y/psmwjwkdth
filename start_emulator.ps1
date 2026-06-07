$emulatorPath = "C:\Users\me\AppData\Local\Android\Sdk\emulator\emulator.exe"
$adbPath = "C:\Users\me\AppData\Local\Android\Sdk\platform-tools\adb.exe"

Write-Host "Starting emulator galaxy_s25_2..."
Start-Process $emulatorPath -ArgumentList "@galaxy_s25_2" -WindowStyle Normal

Write-Host "Waiting for ADB to detect device..."
& $adbPath wait-for-device

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
