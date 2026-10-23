param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$SdkPath = $env:ANDROID_HOME
)
$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
if (-not $JavaHome) {
    foreach ($candidate in @('C:\Program Files\Java\jdk-17', 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot')) {
        if (Test-Path -LiteralPath (Join-Path $candidate 'bin\java.exe')) { $JavaHome = $candidate; break }
    }
}
if (-not $JavaHome -or -not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin\java.exe'))) {
    throw 'Set JAVA_HOME or pass -JavaHome with a JDK 17+ installation.'
}
$env:JAVA_HOME = $JavaHome
if ($SdkPath) {
    $escapedSdk = $SdkPath.Replace('\', '\\').Replace(':', '\:')
    [IO.File]::WriteAllText((Join-Path $projectRoot 'local.properties'), "sdk.dir=$escapedSdk`n", [Text.Encoding]::ASCII)
}
if (-not (Test-Path -LiteralPath (Join-Path $projectRoot 'local.properties'))) {
    throw 'Pass -SdkPath with an Android SDK containing platforms;android-35 and build-tools;35.0.0.'
}
$signingDir = Join-Path $projectRoot '.signing'
New-Item -ItemType Directory -Path $signingDir -Force | Out-Null
$signingProperties = Join-Path $signingDir 'signing.properties'
$keyPath = Join-Path $signingDir 'pet-release.jks'
if (-not (Test-Path -LiteralPath $signingProperties)) {
    if (Test-Path -LiteralPath $keyPath) { throw 'A signing key already exists. Restore its signing.properties before building.' }
    $storePassword = [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')
    $env:PET_ANDROID_SIGN_PASSWORD = $storePassword
    try {
        & (Join-Path $JavaHome 'bin\keytool.exe') -genkeypair -noprompt -keystore $keyPath -storetype JKS `
            -alias pet -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=DesktopPet Android, O=Local Build' `
            -storepass:env PET_ANDROID_SIGN_PASSWORD -keypass:env PET_ANDROID_SIGN_PASSWORD
        if ($LASTEXITCODE -ne 0) { throw 'Could not generate the local signing key.' }
        [IO.File]::WriteAllText($signingProperties,
            "storePassword=$storePassword`nkeyAlias=pet`nkeyPassword=$storePassword`n", [Text.Encoding]::ASCII)
    } finally { Remove-Item Env:\PET_ANDROID_SIGN_PASSWORD -ErrorAction SilentlyContinue }
}
Push-Location $projectRoot
try {
    $petOsVersions = @('8','10','11','12')
    $petGradleTasks = @()
    foreach ($petOsVersion in $petOsVersions) {
        $petGradleTasks += ":app:testAndroid${petOsVersion}DebugUnitTest"
        $petGradleTasks += ":app:lintAndroid${petOsVersion}Release"
        $petGradleTasks += ":app:assembleAndroid${petOsVersion}Release"
    }
    & '.\gradlew.bat' --no-daemon @petGradleTasks
    if ($LASTEXITCODE -ne 0) { throw 'Android build or verification failed.' }
    $dist = Join-Path $projectRoot 'dist'
    New-Item -ItemType Directory -Path $dist -Force | Out-Null
    $petSumLines = @()
    foreach ($petOsVersion in $petOsVersions) {
        $petApkName = "Pet_Android${petOsVersion}_v1.0.0.apk"
        $petApkPath = Join-Path $dist $petApkName
        $petBuiltApk = Join-Path $projectRoot "app\build\outputs\apk\android${petOsVersion}\release\app-android${petOsVersion}-release.apk"
        Copy-Item -LiteralPath $petBuiltApk -Destination $petApkPath -Force
        $petDigest = (Get-FileHash -LiteralPath $petApkPath -Algorithm SHA256).Hash.ToLowerInvariant()
        $petSumLines += "$petDigest  $petApkName"
        Write-Host "APK: $petApkPath"
    }
    [IO.File]::WriteAllText((Join-Path $dist 'SHA256SUMS.txt'), ($petSumLines -join "`n") + "`n", [Text.Encoding]::ASCII)
} finally { Pop-Location }
