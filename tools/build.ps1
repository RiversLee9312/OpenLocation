param([string[]]$Tasks = @(':simulation-core:test', ':app:assembleDebug', ':probe-app:assembleDebug', ':probe-app:assembleDebugAndroidTest'))
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$env:TEMP = Join-Path $projectRoot '.build-tmp'
$env:TMP = $env:TEMP
New-Item -ItemType Directory -Path $env:TEMP -Force | Out-Null
# Keep build transforms and dependency metadata on the workspace drive.
$cachedGradle = Join-Path $env:USERPROFILE '.gradle\wrapper\dists\gradle-8.13-bin\5xuhj0ry160q40clulazy9h7d\gradle-8.13\bin\gradle.bat'
$gradleLauncher = if (Test-Path -LiteralPath $cachedGradle) { $cachedGradle } else { Join-Path $projectRoot 'gradlew.bat' }
& $gradleLauncher -g (Join-Path $projectRoot '.gradle-home') @Tasks --console=plain '-Dorg.gradle.internal.http.connectionTimeout=15000' '-Dorg.gradle.internal.http.socketTimeout=20000'
exit $LASTEXITCODE
