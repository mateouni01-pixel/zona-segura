param([ValidateSet('Spring','Panel','ApiCsharp')][string]$Componente='Spring',[switch]$Actualizar)
$ErrorActionPreference='Stop'
$codeRoot=Split-Path -Parent $MyInvocation.MyCommand.Path
$configPath=Join-Path $codeRoot '.https-local\entorno.json'
if(-not(Test-Path -LiteralPath $configPath)){& (Join-Path $codeRoot 'configurar-https-local.ps1')}
$config=Get-Content -LiteralPath $configPath -Raw | ConvertFrom-Json
$env:ASPNETCORE_ENVIRONMENT='Development'
$env:ASPNETCORE_Kestrel__Certificates__Default__Path=$config.keystore
$env:ASPNETCORE_Kestrel__Certificates__Default__Password=$config.password
$env:ZONA_TLS_KEYSTORE=([Uri]$config.keystore).AbsoluteUri
$env:ZONA_TLS_PASSWORD=$config.password
if ($Componente -eq 'Spring' -and [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
    $env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
}
if([string]::IsNullOrWhiteSpace($env:ZONA_JWT_SECRET)){
    $bytes=New-Object byte[] 32;$random=[System.Security.Cryptography.RandomNumberGenerator]::Create()
    try{$random.GetBytes($bytes)}finally{$random.Dispose()}
    $env:ZONA_JWT_SECRET=[Convert]::ToBase64String($bytes)
}
$targetPort=switch($Componente){'Spring'{8443}'Panel'{5091}'ApiCsharp'{5443}}
$projectRoot=switch($Componente){'Spring'{Join-Path $codeRoot 'backend-spring'}'Panel'{Join-Path $codeRoot 'ZonaSegura.Panel'}'ApiCsharp'{Join-Path $codeRoot 'SPRINT 2\ZonaSegura.Api'}}
$listeners=@(Get-NetTCPConnection -LocalPort $targetPort -State Listen -ErrorAction SilentlyContinue)
if($listeners.Count -gt 0){
    if(-not $Actualizar){throw "El puerto $targetPort ya está ocupado. Usa -Actualizar para reiniciar únicamente este proyecto."}
    $owners=@($listeners|Select-Object -ExpandProperty OwningProcess -Unique)
    if($owners.Count -ne 1){throw 'No se pudo identificar un único proceso.'}
    $running=Get-CimInstance Win32_Process -Filter "ProcessId=$($owners[0])"
    $owned=$running -and -not[string]::IsNullOrWhiteSpace($running.CommandLine) -and $running.CommandLine.IndexOf($projectRoot,[StringComparison]::OrdinalIgnoreCase)-ge 0
    $validName=if($Componente -eq 'Spring'){$running.Name -eq 'java.exe'}else{$running.Name -in @('dotnet.exe','ZonaSegura.Panel.exe','ZonaSegura.Api.exe')}
    if(-not $owned -or -not $validName){throw 'El puerto pertenece a otro programa. No se detuvo ningún proceso.'}
    Stop-Process -Id $owners[0] -ErrorAction Stop
    Wait-Process -Id $owners[0] -Timeout 15 -ErrorAction SilentlyContinue
}
Set-Location -LiteralPath $projectRoot
switch($Componente){
    'Spring'{
        $env:SPRING_PROFILES_ACTIVE='https'
        $env:Path="$env:JAVA_HOME\bin;$projectRoot\auth;$env:Path"
        Write-Host 'API Spring HTTPS: https://localhost:8443. Las sesiones anteriores pueden requerir un nuevo ingreso.'
        & (Join-Path $projectRoot 'gradlew.bat') bootRun
    }
    'Panel'{
        if([string]::IsNullOrWhiteSpace($env:Api__BaseUrl)){$env:Api__BaseUrl='https://localhost:8443/'}
        $env:Api__DevelopmentCaPath=$config.ca
        Write-Host 'Panel HTTPS: https://localhost:5091. El navegador necesita confiar en la CA pública local.'
        & dotnet run --no-launch-profile --urls 'https://localhost:5091'
    }
    'ApiCsharp'{Write-Host 'API C# independiente HTTPS: https://localhost:5443.';& dotnet run --no-launch-profile --urls 'https://localhost:5443'}
}
exit $LASTEXITCODE
