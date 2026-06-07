$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

Write-Host "Verifying Java installation..."
java -version

Write-Host "Running gradlew installDebug..."
.\gradlew.bat installDebug
