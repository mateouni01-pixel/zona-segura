param([switch]$Actualizar, [switch]$SinAdb)

$ErrorActionPreference = 'Stop'
$backendRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$javaHome = 'C:\Program Files\Android\Android Studio\jbr'

$adbPath = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
if (-not $SinAdb -and (Test-Path -LiteralPath $adbPath)) {
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

$listeners = @(Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue)
if ($listeners.Count -gt 0 -and $Actualizar) {
    $processIds = @($listeners | Select-Object -ExpandProperty OwningProcess -Unique)
    if ($processIds.Count -ne 1) { throw 'No se pudo identificar un unico backend en el puerto 8080.' }
    $runningBackend = Get-CimInstance Win32_Process -Filter "ProcessId=$($processIds[0])"
    if ($runningBackend.Name -ne 'java.exe' -or [string]::IsNullOrWhiteSpace($runningBackend.CommandLine) -or
        $runningBackend.CommandLine.IndexOf($backendRoot, [StringComparison]::OrdinalIgnoreCase) -lt 0) {
        throw 'El puerto 8080 pertenece a otro proceso. No se detuvo ningun programa.'
    }
    Write-Host 'Reiniciando exclusivamente el backend de esta carpeta para cargar los cambios.'
    Stop-Process -Id $processIds[0] -ErrorAction Stop
    Wait-Process -Id $processIds[0] -Timeout 15 -ErrorAction SilentlyContinue
} elseif ($listeners.Count -gt 0) {
    try {
        $backendStatus = Invoke-RestMethod -Uri 'http://127.0.0.1:8080/api/conexion' -TimeoutSec 5
        if ($backendStatus.estado -eq 'conectado' -and $backendStatus.base -eq 'ZonaSegura') {
            if ($backendStatus.panelMunicipal -eq $true -and $backendStatus.administracion -eq $true) {
                Write-Host 'Spring Boot ya esta encendido y admite el panel municipal y la administracion.'
                exit 0
            }
            Write-Warning 'El backend encendido es anterior a la administracion actual. Ejecuta iniciar-backend.cmd -Actualizar para cargar la version actual.'
            exit 1
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
