$ErrorActionPreference = 'Stop'
$backendRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$javaHome = 'C:\Program Files\Android\Android Studio\jbr'

$adbPath = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
if (Test-Path -LiteralPath $adbPath) {
    $connectedDevices = @(& $adbPath devices | ForEach-Object {
        if ($_ -match '^(\S+)\s+device\s*$') { $matches[1] }
    })
    if ($connectedDevices.Count -eq 1) {
        & $adbPath -s $connectedDevices[0] reverse tcp:8080 tcp:8080 | Out-Null
        if ($LASTEXITCODE -eq 0) {
            Write-Host 'Conexion USB configurada: el celular puede acceder al backend por el puerto 8080.'
        } else {
            Write-Warning 'No se pudo configurar ADB. Revisa el cable y la autorizacion de depuracion USB.'
        }
    } elseif ($connectedDevices.Count -gt 1) {
        Write-Warning 'Hay varios dispositivos. Configura el elegido con adb -s SERIAL reverse tcp:8080 tcp:8080.'
    } else {
        Write-Host 'No hay un dispositivo ADB autorizado. Al conectarlo, vuelve a abrir este lanzador para configurar el puerto USB.'
    }
}

if (Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue) {
    try {
        $backendStatus = Invoke-RestMethod -Uri 'http://127.0.0.1:8080/api/conexion' -TimeoutSec 5
        if ($backendStatus.estado -eq 'conectado' -and $backendStatus.base -eq 'ZonaSegura') {
            Write-Host 'Spring Boot ya esta encendido y conectado a ZonaSegura. Ya puedes probar la app.'
            exit 0
        }
    } catch { }
    Write-Host 'El puerto 8080 esta ocupado, pero no se pudo confirmar la conexion a ZonaSegura. Revisa la ventana del backend.'
    exit 1
}

if (-not (Test-Path -LiteralPath (Join-Path $javaHome 'bin\java.exe'))) {
    Write-Error 'No se encontró Java en Android Studio.'
    exit 1
}
if (-not (Test-Path -LiteralPath (Join-Path $backendRoot 'auth\mssql-jdbc_auth-13.6.0.x64.dll'))) {
    Write-Error 'Falta la DLL de autenticación SQL Server en la carpeta auth.'
    exit 1
}

$env:JAVA_HOME = $javaHome
$env:Path = "$javaHome\bin;$backendRoot\auth;$env:Path"

if ([string]::IsNullOrWhiteSpace($env:ZONA_JWT_SECRET)) {
    $bytes = New-Object byte[] 32
    $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $generator.GetBytes($bytes)
    } finally {
        $generator.Dispose()
    }
    $env:ZONA_JWT_SECRET = [Convert]::ToBase64String($bytes)
    Write-Host 'Clave JWT de prueba generada. Debes iniciar sesion nuevamente despues de reiniciar el backend.'
}

Set-Location -LiteralPath $backendRoot
& (Join-Path $backendRoot 'gradlew.bat') bootRun
exit $LASTEXITCODE
