[CmdletBinding()]
param(
    [string] $JavaHome,
    [switch] $Offline,
    [switch] $IncludeRelease
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot

if (-not $JavaHome) {
    # Usar el mismo runtime que Android Studio evita diferencias con el IDE.
    $studioRuntime = Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'
    if (Test-Path -LiteralPath (Join-Path $studioRuntime 'bin\java.exe')) {
        $JavaHome = $studioRuntime
    } elseif ($env:JAVA_HOME) {
        $JavaHome = $env:JAVA_HOME
    } else {
        $javaCommand = Get-Command java.exe -ErrorAction Stop
        $JavaHome = Split-Path -Parent (Split-Path -Parent $javaCommand.Source)
    }
}

$JavaHome = (Resolve-Path -LiteralPath $JavaHome).Path
if (-not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin\javac.exe'))) {
    throw "JavaHome debe señalar a un JDK: $JavaHome"
}

$previousEnvironment = @{
    JAVA_HOME = $env:JAVA_HOME
    GRADLE_USER_HOME = $env:GRADLE_USER_HOME
    ANDROID_USER_HOME = $env:ANDROID_USER_HOME
}

$gradleArguments = @(
    '--no-daemon', '--no-watch-fs', '--max-workers=2', '--console=plain', '--continue',
    ':app:assembleDebug', ':app:testDebugUnitTest', '--rerun', ':app:lintDebug'
)
if ($Offline) { $gradleArguments += '--offline' }
if ($IncludeRelease) { $gradleArguments += ':app:assembleRelease' }

Push-Location -LiteralPath $projectRoot
try {
    $env:JAVA_HOME = $JavaHome
    $env:GRADLE_USER_HOME = Join-Path $projectRoot '.gradle-user-home'
    $env:ANDROID_USER_HOME = Join-Path $projectRoot '.android-user-home'
    Write-Host "JDK: $JavaHome"
    & (Join-Path $projectRoot 'gradlew.bat') @gradleArguments
    if ($LASTEXITCODE -ne 0) {
        throw "La verificación de Android falló (Gradle: $LASTEXITCODE)."
    }
} finally {
    $env:JAVA_HOME = $previousEnvironment.JAVA_HOME
    $env:GRADLE_USER_HOME = $previousEnvironment.GRADLE_USER_HOME
    $env:ANDROID_USER_HOME = $previousEnvironment.ANDROID_USER_HOME
    Pop-Location
}
