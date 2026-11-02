param([switch]$VerifyDevice)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$signingDir = Join-Path $env:USERPROFILE '.ceye'
$keyFile = Join-Path $signingDir 'release.jks'
$credentialFile = Join-Path $signingDir 'signing.credential.xml'
$javaRoot = 'D:\Workspace\05_Software\Installers\android studio\jbr'
if (!$env:JAVA_HOME) { $env:JAVA_HOME = $javaRoot }
if (!$env:ANDROID_HOME) { $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
New-Item -ItemType Directory -Path $signingDir -Force | Out-Null
if (!(Test-Path -LiteralPath $credentialFile)) {
    if (Test-Path -LiteralPath $keyFile) { throw 'Existing signing key has no matching credential. Restore the original credential rather than replacing this key.' }
    $bytes = New-Object byte[] 32
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    $secure = ConvertTo-SecureString ([Convert]::ToBase64String($bytes)) -AsPlainText -Force
    $credential = New-Object Management.Automation.PSCredential('ceye',$secure)
    $credential | Export-Clixml -LiteralPath $credentialFile
}
$credential = Import-Clixml -LiteralPath $credentialFile
try {
    $env:CEYE_KEYSTORE = $keyFile
    $env:CEYE_STORE_PASSWORD = $credential.GetNetworkCredential().Password
    $env:CEYE_KEY_PASSWORD = $env:CEYE_STORE_PASSWORD
    if (!(Test-Path -LiteralPath $keyFile)) {
        & "$env:JAVA_HOME\bin\keytool.exe" -genkeypair -keystore $keyFile -alias ceye -storepass:env CEYE_STORE_PASSWORD -keypass:env CEYE_KEY_PASSWORD -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=CeYe, O=SgRpg' -noprompt
        if ($LASTEXITCODE) { throw 'Signing key generation failed' }
    }
    Push-Location $root
    try { $tasks = @(':app:testDebugUnitTest', ':app:lintDebug', ':app:assembleRelease'); if ($VerifyDevice) { $tasks += ':app:connectedDebugAndroidTest' }; & "$root\gradlew.bat" @tasks --console=plain; if ($LASTEXITCODE) { throw 'Release build failed' } } finally { Pop-Location }
} finally {
    Remove-Item Env:CEYE_KEYSTORE,Env:CEYE_STORE_PASSWORD,Env:CEYE_KEY_PASSWORD -ErrorAction SilentlyContinue
}
