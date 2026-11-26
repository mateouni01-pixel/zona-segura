param([string]$Servidor = 'MateoMS', [string]$ApiCsharp = '')

# Ejecutar con PowerShell 7. Las bases temporales se crean y eliminan aquí.
# No se imprime ninguna contraseña, clave JWT o token de sesión.
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$springRoot = Join-Path $projectRoot 'backend-spring'
if ([string]::IsNullOrWhiteSpace($ApiCsharp)) {
    $ApiCsharp = @((Join-Path $projectRoot '..\Zona-Segura-Api'), (Join-Path $projectRoot '..\SPRINT 2\ZonaSegura.Api')) |
        Where-Object { Test-Path -LiteralPath (Join-Path $_ 'ZonaSegura.Api.csproj') } | Select-Object -First 1
}
if (-not $ApiCsharp -or -not (Test-Path -LiteralPath (Join-Path $ApiCsharp 'ZonaSegura.Api.csproj'))) {
    throw 'Indica -ApiCsharp con la carpeta que contiene ZonaSegura.Api.csproj.'
}
$csharpRoot = (Resolve-Path -LiteralPath $ApiCsharp).Path
$logRoot = Join-Path $PSScriptRoot 'resultados'
New-Item -ItemType Directory -Path $logRoot -Force | Out-Null
$testSuffix = [Guid]::NewGuid().ToString('N').Substring(0, 10)
$springDb = "ZonaSeguraAuthPrueba_Spring_$testSuffix"
$csharpDb = "ZonaSeguraAuthPrueba_Csharp_$testSuffix"
$createdDatabases = [System.Collections.Generic.List[string]]::new()
$processes = [System.Collections.Generic.List[System.Diagnostics.Process]]::new()
$results = [System.Collections.Generic.List[object]]::new()
$originalEnvironment = @{}
foreach ($key in @('ZONA_JWT_SECRET','SPRING_DATASOURCE_URL','ConnectionStrings__ZonaSegura')) {
    $originalEnvironment[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
}

function Sql-Connection([string]$Database) {
    $builder = [System.Data.SqlClient.SqlConnectionStringBuilder]::new()
    $builder['Data Source'] = $Servidor
    $builder['Initial Catalog'] = $Database
    $builder['Integrated Security'] = $true
    $builder['Encrypt'] = $true
    $builder['TrustServerCertificate'] = $true
    $builder['Connect Timeout'] = 10
    return $builder.ConnectionString
}
function Invoke-Sql([string]$Database, [string]$Sql) {
    $connection = [System.Data.SqlClient.SqlConnection]::new((Sql-Connection $Database))
    try {
        $connection.Open()
        $command = $connection.CreateCommand()
        $command.CommandTimeout = 60
        $command.CommandText = $Sql
        [void]$command.ExecuteNonQuery()
    } finally { $connection.Dispose() }
}
function Request([string]$Base, [string]$Method, [string]$Path, $Body = $null, [string]$Token = '') {
    $parameters = @{ Uri = "$Base$Path"; Method = $Method; SkipHttpErrorCheck = $true; NoProxy = $true; TimeoutSec = 10 }
    if ($null -ne $Body) { $parameters.Body = $Body | ConvertTo-Json -Compress; $parameters.ContentType = 'application/json; charset=utf-8' }
    if ($Token) { $parameters.Headers = @{ Authorization = "Bearer $Token" } }
    return Invoke-WebRequest @parameters
}
function Expect([string]$Api, [string]$Name, $Response, [int]$Status) {
    if ([int]$Response.StatusCode -ne $Status) { throw "$Api / $Name : esperado $Status, recibido $($Response.StatusCode)" }
    $results.Add([pscustomobject]@{ api = $Api; prueba = $Name; resultado = 'OK'; http = $Status })
    Write-Host "$Api / $Name : OK ($Status)"
}
function Wait-Api([string]$Base, [System.Diagnostics.Process]$Process) {
    for ($attempt = 0; $attempt -lt 90; $attempt++) {
        if ($Process.HasExited) { throw "El servidor $Base terminó antes de iniciar. Revisar sus logs." }
        try { if ((Request $Base 'GET' '/api/auth/me').StatusCode -eq 401) { return } } catch { }
        Start-Sleep -Milliseconds 500
    }
    throw "El servidor $Base no inició a tiempo. Revisar sus logs."
}
function Signed-TestToken([string]$Issuer, [string]$Subject, [long]$Expires, [string]$Audience = 'zona-segura-android') {
    function Base64Url([byte[]]$Bytes) { return [Convert]::ToBase64String($Bytes).TrimEnd('=').Replace('+','-').Replace('/','_') }
    $header = Base64Url ([Text.Encoding]::UTF8.GetBytes('{"alg":"HS256","typ":"JWT"}'))
    $payload = @{ iss = $Issuer; sub = $Subject; aud = $Audience; exp = $Expires; iat = $Expires - 3600; roles = @('CIUDADANO') } | ConvertTo-Json -Compress
    $unsigned = "$header.$(Base64Url ([Text.Encoding]::UTF8.GetBytes($payload)))"
    $hmac = [Security.Cryptography.HMACSHA256]::new([Convert]::FromBase64String($env:ZONA_JWT_SECRET))
    try { return "$unsigned.$(Base64Url ($hmac.ComputeHash([Text.Encoding]::ASCII.GetBytes($unsigned))))" }
    finally { $hmac.Dispose() }
}

function Add-MapFixtures([string]$Api, [string]$Database, [int]$Author, [int]$TypeA, [int]$TypeB) {
    if ($Api -eq 'Spring') {
        $locationTable='UbicacionesGPS'; $locationId='id_ubicacion'; $authorColumn='id_ciudadano'; $typeColumn='id_tipo'; $reportLocation='id_ubicacion'; $dateColumn='fecha_hora'
    } elseif ($Api -eq 'Csharp') {
        $locationTable='UbicacionesGps'; $locationId='Id'; $authorColumn='CiudadanoId'; $typeColumn='TipoIncidenteId'; $reportLocation='UbicacionGpsId'; $dateColumn='FechaRegistroUtc'
    } else { throw 'API de pruebas desconocida.' }
    $fixtures = @"
SET XACT_ABORT ON; BEGIN TRANSACTION;
DECLARE @fixtures TABLE(n int PRIMARY KEY, lat decimal(9,6), lng decimal(9,6), estado nvarchar(30), edad int, tipo int);
INSERT @fixtures VALUES
(1,-17.783300,-63.182100,N'VALIDADO',2,$TypeA),(2,-17.783300,-63.182100,N'VALIDADO',2,$TypeA),
(3,-17.783300,-63.182100,N'VALIDADO',2,$TypeA),(4,-17.783300,-63.182100,N'VALIDADO',2,$TypeA),
(5,-17.783300,-63.182100,N'VALIDADO',2,$TypeB),
(6,-17.783300,-63.177100,N'EN_ATENCION',2,$TypeA),(7,-17.783300,-63.177100,N'VALIDADO',2,$TypeA),(8,-17.783300,-63.177100,N'ATENDIDO',2,$TypeA),
(9,-17.772100,-63.182100,N'ATENDIDO',2,$TypeA),
(10,-17.783300,-63.182100,N'PENDIENTE_VALIDACION',2,$TypeA),(11,-17.783300,-63.182100,N'RECHAZADO',2,$TypeA),
(12,-17.783300,-63.182100,N'VALIDADO',20,$TypeA),(13,-17.783300,-63.182100,N'VALIDADO',-1,$TypeA),
(14,-17.783300,-63.160000,N'VALIDADO',2,$TypeA);
DECLARE @n int=1, @location int, @lat decimal(9,6), @lng decimal(9,6), @estado nvarchar(30), @edad int, @tipo int;
WHILE @n<=14
BEGIN
 SELECT @lat=lat,@lng=lng,@estado=estado,@edad=edad,@tipo=tipo FROM @fixtures WHERE n=@n;
 INSERT dbo.$locationTable(latitud,longitud) VALUES(@lat,@lng);
 SET @location=CONVERT(int,SCOPE_IDENTITY());
 INSERT dbo.Reportes($authorColumn,$typeColumn,$reportLocation,descripcion,estado,$dateColumn)
 VALUES($Author,@tipo,@location,N'[MAPA-CONTRATO] Datos ficticios de prueba.',@estado,DATEADD(day,-@edad,SYSUTCDATETIME()));
 SET @n=@n+1;
END;
COMMIT TRANSACTION;
"@
    Invoke-Sql $Database $fixtures
}

function Test-Verificacion($Api, $AuthorSession, $ReportInput) {
    $base=$Api.url; $name=$Api.name; $voters=@()
    foreach($n in 1..4){
        $email="verificador$n@example.invalid"
        Expect $name "registrar vecino $n" (Request $base 'POST' '/api/auth/register' @{nombre="Vecino $n";correo=$email;password='prueba-segura-123'}) 201
        $login=Request $base 'POST' '/api/auth/login' @{correo=$email;password='prueba-segura-123'}
        Expect $name "login vecino $n" $login 200
        $voters+=($login.Content|ConvertFrom-Json)
    }
    $new=Request $base 'POST' '/api/reportes' $ReportInput $AuthorSession.token
    Expect $name 'crear incidente para votación' $new 201
    $reportId=($new.Content|ConvertFrom-Json).id
    $vote=@{confirma=$true;latitud=$ReportInput.latitud;longitud=$ReportInput.longitud}
    Expect $name 'voto requiere sesión' (Request $base 'POST' "/api/reportes/$reportId/votos" $vote) 401
    Expect $name 'autor no vota su reporte' (Request $base 'POST' "/api/reportes/$reportId/votos" $vote $AuthorSession.token) 403
    Expect $name 'voto fuera de 500 m' (Request $base 'POST' "/api/reportes/$reportId/votos" @{confirma=$true;latitud=0;longitud=0} $voters[0].token) 403
    Expect $name 'voto exige resultado' (Request $base 'POST' "/api/reportes/$reportId/votos" @{latitud=$vote.latitud;longitud=$vote.longitud} $voters[0].token) 400
    $nearPath="/api/reportes/cercanos?lat=$($vote.latitud)&lng=$($vote.longitud)&radio=500"
    $near=Request $base 'GET' $nearPath $null $voters[0].token
    Expect $name 'vecino consulta pendientes cercanos' $near 200
    if(-not (@($near.Content|ConvertFrom-Json)|Where-Object id -EQ $reportId)){throw 'No aparece el reporte cercano.'}
    if($near.Content -match 'id_ciudadano|ciudadanoId|correo|nombreAutor'){throw 'La consulta cercana expone al autor.'}
    foreach($n in 0..1){
        $response=Request $base 'POST' "/api/reportes/$reportId/votos" $vote $voters[$n].token
        Expect $name "confirmación $($n+1) mantiene pendiente" $response 200
        $data=$response.Content|ConvertFrom-Json
        if($data.estado -ne 'PENDIENTE_VALIDACION' -or $data.confirmaciones -ne ($n+1)){throw 'Se validó antes de tres confirmaciones.'}
    }
    Expect $name 'rechazar voto duplicado' (Request $base 'POST' "/api/reportes/$reportId/votos" $vote $voters[0].token) 409
    $third=Request $base 'POST' "/api/reportes/$reportId/votos" $vote $voters[2].token
    Expect $name 'tercer ciudadano valida' $third 200
    $data=$third.Content|ConvertFrom-Json
    if($data.estado -ne 'VALIDADO' -or $data.confirmaciones -ne 3 -or $data.origenValidacion -ne 'COMUNITARIA'){throw 'No se aplicó el umbral de tres.'}
    Expect $name 'no admite cuarto voto tras validación' (Request $base 'POST' "/api/reportes/$reportId/votos" $vote $voters[3].token) 409
    $parallelReport=Request $base 'POST' '/api/reportes' $ReportInput $AuthorSession.token
    $parallelId=($parallelReport.Content|ConvertFrom-Json).id
    function Concurrent-Votes([string]$Path,[string]$TokenA,[string]$TokenB){
        $barrier=[System.Threading.Barrier]::new(2)
        $jobs=@()
        try{
            foreach($token in @($TokenA,$TokenB)){
                $jobs+=Start-ThreadJob -ScriptBlock {
                    param($gate,$url,$json,$bearer)
                    [void]$gate.SignalAndWait([TimeSpan]::FromSeconds(20))
                    $response=Invoke-WebRequest -Uri $url -Method POST -ContentType 'application/json' -Body $json -Headers @{Authorization="Bearer $bearer"} -SkipHttpErrorCheck -NoProxy -TimeoutSec 20
                    [int]$response.StatusCode
                } -ArgumentList $barrier,"$base$Path",($vote|ConvertTo-Json -Compress),$token
            }
            $statuses=@($jobs | Receive-Job -Wait -ErrorAction Stop | Sort-Object)
            if(($statuses -join ',') -ne '200,409'){throw "Concurrencia incorrecta: $($statuses -join ',')"}
        }finally{
            $jobs|Remove-Job -Force -ErrorAction SilentlyContinue
            $barrier.Dispose()
        }
    }
    Concurrent-Votes "/api/reportes/$parallelId/votos" $voters[0].token $voters[0].token
    $results.Add([pscustomobject]@{api=$name;prueba='voto simultáneo duplicado produce 200 y 409';resultado='OK';http=200})
    Expect $name 'segundo voto antes de concurrencia' (Request $base 'POST' "/api/reportes/$parallelId/votos" $vote $voters[1].token) 200
    Concurrent-Votes "/api/reportes/$parallelId/votos" $voters[2].token $voters[3].token
    $results.Add([pscustomobject]@{api=$name;prueba='tercero y cuarto simultáneos solo aceptan uno';resultado='OK';http=200})
    $parallelState=Request $base 'GET' "/api/reportes/$parallelId" $null $AuthorSession.token
    if(($parallelState.Content|ConvertFrom-Json).estado -ne 'VALIDADO'){throw 'La concurrencia no validó el incidente.'}
    Expect $name 'cercanos requiere coordenadas' (Request $base 'GET' '/api/reportes/cercanos?radio=500' $null $voters[0].token) 400
    $negativeReport=Request $base 'POST' '/api/reportes' $ReportInput $AuthorSession.token
    $negativeId=($negativeReport.Content|ConvertFrom-Json).id
    foreach($n in 0..2){Expect $name "voto negativo $n" (Request $base 'POST' "/api/reportes/$negativeId/votos" @{confirma=$false;latitud=$vote.latitud;longitud=$vote.longitud} $voters[$n].token) 200}
    $pending=Request $base 'GET' "/api/reportes/$negativeId" $null $AuthorSession.token
    if(($pending.Content|ConvertFrom-Json).estado -ne 'PENDIENTE_VALIDACION'){throw 'Los votos negativos rechazaron automáticamente un reporte.'}
    $official=Request $base 'POST' '/api/auth/register' @{nombre='Administrador revisor';correo='revisor@example.invalid';password='prueba-segura-123'}
    Expect $name 'crear cuenta para perfil administrativo de prueba' $official 201
    $officialId=($official.Content|ConvertFrom-Json).idUsuario
    $beforePromotion=Request $base 'POST' '/api/auth/login' @{correo='revisor@example.invalid';password='prueba-segura-123'}
    $ownReport=Request $base 'POST' '/api/reportes' $ReportInput ($beforePromotion.Content|ConvertFrom-Json).token
    $ownId=($ownReport.Content|ConvertFrom-Json).id
    if($name -eq 'Spring'){
        Invoke-Sql $Api.database "DELETE dbo.UsuarioRoles WHERE id_usuario=$officialId; INSERT dbo.UsuarioRoles SELECT $officialId,id_rol FROM dbo.Roles WHERE nombre=N'ADMINISTRADOR'; INSERT dbo.Administradores(id_usuario,nivel_acceso) VALUES($officialId,N'GESTION');"
    }else{Invoke-Sql $Api.database "UPDATE dbo.Usuarios SET Rol=N'ADMINISTRADOR' WHERE Id=$officialId"}
    $officialLogin=Request $base 'POST' '/api/auth/login' @{correo='revisor@example.invalid';password='prueba-segura-123'}
    Expect $name 'login del administrador revisor' $officialLogin 200
    $officialToken=($officialLogin.Content|ConvertFrom-Json).token
    Expect $name 'perfil administrativo' (Request $base 'GET' '/api/admin/perfil' $null $officialToken) 200
    Expect $name 'ciudadano no accede a verificación institucional' (Request $base 'GET' '/api/admin/reportes-verificacion' $null $voters[0].token) 403
    Expect $name 'bandeja administrativa de revisión' (Request $base 'GET' '/api/admin/reportes-verificacion' $null $officialToken) 200
    $evidence=@{confirma=$true;motivo='Revisión administrativa de prueba confirma el incidente en el lugar.'}
    Expect $name 'administrador no verifica un reporte propio' (Request $base 'POST' "/api/admin/reportes/$ownId/verificacion" $evidence $officialToken) 403
    Expect $name 'ciudadano no puede verificar institucionalmente' (Request $base 'POST' "/api/admin/reportes/$negativeId/verificacion" $evidence $voters[0].token) 403
    Expect $name 'motivo obligatorio' (Request $base 'POST' "/api/admin/reportes/$negativeId/verificacion" @{confirma=$true;motivo=''} $officialToken) 400
    $verified=Request $base 'POST' "/api/admin/reportes/$negativeId/verificacion" $evidence $officialToken
    Expect $name 'administrador confirma sin tres votos positivos' $verified 200
    if(($verified.Content|ConvertFrom-Json).estado -ne 'VALIDADO' -or ($verified.Content|ConvertFrom-Json).origenValidacion -ne 'INSTITUCIONAL'){throw 'No se guardó la confirmación institucional.'}
    Expect $name 'no sobrescribir verificación institucional' (Request $base 'POST' "/api/admin/reportes/$negativeId/verificacion" $evidence $officialToken) 409
    $rejectedReport=Request $base 'POST' '/api/reportes' $ReportInput $AuthorSession.token
    $rejectedId=($rejectedReport.Content|ConvertFrom-Json).id
    $rejected=Request $base 'POST' "/api/admin/reportes/$rejectedId/verificacion" @{confirma=$false;motivo='Revisión administrativa de prueba identifica información incorrecta.'} $officialToken
    Expect $name 'rechazo institucional fundamentado' $rejected 200
    if(($rejected.Content|ConvertFrom-Json).estado -ne 'RECHAZADO'){throw 'No se registró el rechazo.'}
    Expect $name 'no reabrir rechazado mediante voto' (Request $base 'POST' "/api/reportes/$rejectedId/votos" $vote $voters[0].token) 409
    if($name -eq 'Spring'){
        Invoke-Sql $Api.database "IF NOT EXISTS(SELECT 1 FROM dbo.VerificacionesInstitucionales WHERE id_reporte=$negativeId AND id_personal=$officialId AND confirma=1 AND LEN(motivo)>=10 AND fecha_hora IS NOT NULL) THROW 51000,'Falta auditoría institucional',1;"
        Invoke-Sql $Api.database "DELETE dbo.UsuarioRoles WHERE id_usuario=$officialId"
    }else{
        Invoke-Sql $Api.database "IF NOT EXISTS(SELECT 1 FROM dbo.VerificacionesInstitucionales WHERE ReporteId=$negativeId AND PersonalId=$officialId AND Confirma=1 AND LEN(Motivo)>=10 AND FechaUtc IS NOT NULL) THROW 51000,'Falta auditoría institucional',1;"
        Expect $name 'conservar reporte votado al borrar' (Request $base 'DELETE' "/api/reportes/$reportId" $null $AuthorSession.token) 409
        Expect $name 'conservar evidencia al editar reporte' (Request $base 'PUT' "/api/reportes/$reportId" $ReportInput $AuthorSession.token) 409
        Invoke-Sql $Api.database "UPDATE dbo.Usuarios SET Rol=N'CIUDADANO' WHERE Id=$officialId"
    }
    $removed=Request $base 'GET' '/api/admin/reportes-verificacion' $null $officialToken
    if([int]$removed.StatusCode -notin 401,403){throw 'Un rol retirado conserva permiso institucional.'}
    $results.Add([pscustomobject]@{api=$name;prueba='auditoría institucional y revocación de rol';resultado='OK';http=[int]$removed.StatusCode})
}

try {
    foreach ($database in @($springDb, $csharpDb)) {
        if ($database -notmatch '^ZonaSeguraAuthPrueba_(Spring|Csharp)_[a-f0-9]{10}$') { throw 'Nombre de base de pruebas inválido.' }
        Invoke-Sql 'master' "CREATE DATABASE [$database]"
        $createdDatabases.Add($database)
    }
    $schema = Get-Content -LiteralPath (Join-Path $projectRoot 'zona_segura.sql') -Raw
    $fixtureSchema = '-- SPRINT 1:' + ($schema -split '-- SPRINT 1:', 2)[1]
    foreach ($batch in [regex]::Split($fixtureSchema, '(?m)^\s*GO\s*$')) {
        if ($batch.Trim()) { Invoke-Sql $springDb $batch }
    }
    $catalogSchema = Get-Content -LiteralPath (Join-Path $springRoot 'sql\sprint2-reportes.sql') -Raw
    foreach ($batch in [regex]::Split($catalogSchema, '(?m)^\s*GO\s*$')) {
        if ($batch.Trim()) { Invoke-Sql $springDb $batch }
    }
    Push-Location $csharpRoot
    try {
        & dotnet build --no-restore
        if ($LASTEXITCODE) { throw 'Falló la compilación C#.' }
        & dotnet ef database update --no-build --connection (Sql-Connection $csharpDb)
        if ($LASTEXITCODE) { throw 'Falló la migración C#.' }
    } finally { Pop-Location }

    $keyBytes = [byte[]]::new(32)
    [Security.Cryptography.RandomNumberGenerator]::Fill($keyBytes)
    $env:ZONA_JWT_SECRET = [Convert]::ToBase64String($keyBytes)
    $env:SPRING_DATASOURCE_URL = "jdbc:sqlserver://$Servidor;databaseName=$springDb;integratedSecurity=true;authenticationScheme=NativeAuthentication;encrypt=true;trustServerCertificate=true"
    $env:ConnectionStrings__ZonaSegura = Sql-Connection $csharpDb
    & dotnet (Join-Path $csharpRoot 'bin\Debug\net10.0\ZonaSegura.Api.dll') --seed
    if ($LASTEXITCODE) { throw 'Falló la carga del catálogo C#.' }
    $springJar = Get-ChildItem -LiteralPath (Join-Path $springRoot 'build\libs') -Filter '*.jar' |
        Where-Object { $_.Name -notlike '*-plain.jar' } | Select-Object -First 1 -ExpandProperty FullName
    if (-not $springJar) { throw 'Compilar primero Spring con gradlew.bat bootJar.' }
    $java = if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
        Join-Path $env:JAVA_HOME 'bin\java.exe'
    } else { 'C:\Program Files\Android\Android Studio\jbr\bin\java.exe' }
    if (-not (Test-Path -LiteralPath $java)) { throw 'Configura JAVA_HOME con un JDK 21 o superior.' }
    $springProcess = Start-Process -FilePath $java -ArgumentList @('"-Djava.library.path=' + $springRoot + '\auth"', '-jar', '"' + $springJar + '"', '--server.port=18080') -WorkingDirectory $springRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $logRoot 'spring-out.log') -RedirectStandardError (Join-Path $logRoot 'spring-error.log')
    $processes.Add($springProcess)
    $dll = Join-Path $csharpRoot 'bin\Debug\net10.0\ZonaSegura.Api.dll'
    $csharpProcess = Start-Process -FilePath (Get-Command dotnet).Source -ArgumentList @('"' + $dll + '"', '--urls', 'http://127.0.0.1:15080') -WorkingDirectory $csharpRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $logRoot 'csharp-out.log') -RedirectStandardError (Join-Path $logRoot 'csharp-error.log')
    $processes.Add($csharpProcess)
    Wait-Api 'http://127.0.0.1:18080' $springProcess
    Wait-Api 'http://127.0.0.1:15080' $csharpProcess
    $sessions = @{}
    foreach ($api in @(
        @{ name = 'Spring'; url = 'http://127.0.0.1:18080'; database = $springDb; issuer = 'zona-segura-api' },
        @{ name = 'Csharp'; url = 'http://127.0.0.1:15080'; database = $csharpDb; issuer = 'zona-segura-csharp' }
    )) {
        $base = $api.url; $name = $api.name
        Expect $name 'sesión sin token' (Request $base 'GET' '/api/auth/me') 401
        Expect $name 'correo inválido' (Request $base 'POST' '/api/auth/register' @{ nombre='Prueba'; correo='no-correo'; password='prueba-segura-123' }) 400
        Expect $name 'contraseña corta' (Request $base 'POST' '/api/auth/register' @{ nombre='Prueba'; correo='contrato@example.invalid'; password='corta' }) 400
        Expect $name 'contraseña UTF-8 mayor de 72 bytes' (Request $base 'POST' '/api/auth/register' @{ nombre='Prueba'; correo='contrato@example.invalid'; password=('á' * 37) }) 400
        $register = Request $base 'POST' '/api/auth/register' @{ nombre=' Ciudadano contrato '; correo=' CONTRATO@EXAMPLE.INVALID '; password='prueba-segura-123'; rol='ADMINISTRADOR'; roles=@('ADMINISTRADOR') }
        Expect $name 'registro ciudadano y normalización' $register 201
        $id = [int]($register.Content | ConvertFrom-Json).idUsuario
        if ($id -le 0) { throw "$name no devolvió idUsuario." }
        Expect $name 'correo duplicado' (Request $base 'POST' '/api/auth/register' @{ nombre='Otra persona'; correo='contrato@example.invalid'; password='prueba-segura-123' }) 409
        Expect $name 'login incorrecto' (Request $base 'POST' '/api/auth/login' @{ correo='contrato@example.invalid'; password='clave-incorrecta' }) 401
        $login = Request $base 'POST' '/api/auth/login' @{ correo=' CONTRATO@example.invalid '; password='prueba-segura-123' }
        Expect $name 'login válido' $login 200
        $session = $login.Content | ConvertFrom-Json
        if ($session.idUsuario -ne $id -or $session.ciudadanoId -ne $id -or $session.roles.Count -ne 1 -or $session.roles[0] -ne 'CIUDADANO' -or -not $session.token) { throw "$name devolvió una identidad o rol incorrectos." }
        $sessions[$name] = $session.token
        $me = Request $base 'GET' '/api/auth/me' $null $session.token
        Expect $name 'sesión con ciudadano real' $me 200
        $sessionProfile = $me.Content | ConvertFrom-Json
        if ($sessionProfile.idUsuario -ne $id -or $sessionProfile.ciudadanoId -ne $id -or $sessionProfile.nombre -ne 'Ciudadano contrato' -or $sessionProfile.roles[0] -ne 'CIUDADANO') { throw "$name devolvió un perfil incorrecto." }
        Expect $name 'reportes sin sesión' (Request $base 'GET' '/api/reportes') 401
        $typesResponse = Request $base 'GET' '/api/tipos-incidente' $null $session.token
        Expect $name 'catálogo autenticado' $typesResponse 200
        $types = @($typesResponse.Content | ConvertFrom-Json)
        $activeType = $types | Where-Object activo | Select-Object -First 1
        if (-not $activeType) { throw "$name no tiene categorías activas." }
        Expect $name 'consulta de categoría' (Request $base 'GET' "/api/tipos-incidente/$($activeType.id)" $null $session.token) 200
        $reportInput = @{ descripcion='[CONTRATO] Incidente creado con identidad de la sesión.'; tipoIncidenteId=[int]$activeType.id; latitud=-17.783300; longitud=-63.182100 }
        Expect $name 'crear sin sesión' (Request $base 'POST' '/api/reportes' $reportInput) 401
        $invalidInput = $reportInput.Clone(); $invalidInput.descripcion = 'corto'
        Expect $name 'descripción inválida' (Request $base 'POST' '/api/reportes' $invalidInput $session.token) 400
        $invalidInput = $reportInput.Clone(); $invalidInput.Remove('latitud')
        Expect $name 'GPS obligatorio' (Request $base 'POST' '/api/reportes' $invalidInput $session.token) 400
        $invalidInput = $reportInput.Clone(); $invalidInput.latitud = 91
        Expect $name 'GPS fuera de rango' (Request $base 'POST' '/api/reportes' $invalidInput $session.token) 400
        $invalidInput = $reportInput.Clone(); $invalidInput.ciudadanoId = $id + 1000
        Expect $name 'rechazo de autor falsificado' (Request $base 'POST' '/api/reportes' $invalidInput $session.token) 400
        $invalidInput = $reportInput.Clone(); $invalidInput.tipoIncidenteId = 2147483647
        Expect $name 'categoría inexistente' (Request $base 'POST' '/api/reportes' $invalidInput $session.token) 400
        $typeSql = if ($name -eq 'Spring') { "UPDATE dbo.TiposIncidente SET activo=0 WHERE id_tipo=$($activeType.id)" } else { "UPDATE dbo.TiposIncidente SET Activo=0 WHERE Id=$($activeType.id)" }
        Invoke-Sql $api.database $typeSql
        Expect $name 'categoría inactiva' (Request $base 'POST' '/api/reportes' $reportInput $session.token) 400
        Invoke-Sql $api.database ($typeSql.Replace('activo=0','activo=1').Replace('Activo=0','Activo=1'))
        $createdResponse = Request $base 'POST' '/api/reportes' $reportInput $session.token
        Expect $name 'crear reporte real' $createdResponse 201
        $createdReport = $createdResponse.Content | ConvertFrom-Json
        if ($createdReport.id -le 0 -or $createdReport.ciudadanoId -ne $id -or $createdReport.estado -ne 'PENDIENTE_VALIDACION' -or [decimal]$createdReport.latitud -ne [decimal]$reportInput.latitud -or $createdReport.descripcion -ne $reportInput.descripcion) { throw "$name no guardó los datos correctos." }
        if (-not $createdResponse.Headers.Location) { throw "$name no devolvió Location." }
        Expect $name 'consultar reporte creado' (Request $base 'GET' "/api/reportes/$($createdReport.id)" $null $session.token) 200
        $history = Request $base 'GET' '/api/reportes' $null $session.token
        Expect $name 'historial del ciudadano' $history 200
        if (-not (@($history.Content | ConvertFrom-Json) | Where-Object id -EQ $createdReport.id)) { throw "$name no incluye el reporte en su historial." }
        $secondRegistration = Request $base 'POST' '/api/auth/register' @{ nombre='Segundo ciudadano'; correo='segundo@example.invalid'; password='prueba-segura-123' }
        Expect $name 'segunda cuenta de prueba' $secondRegistration 201
        $secondLogin = Request $base 'POST' '/api/auth/login' @{ correo='segundo@example.invalid'; password='prueba-segura-123' }
        Expect $name 'sesión de segunda cuenta' $secondLogin 200
        $secondToken = ($secondLogin.Content | ConvertFrom-Json).token
        Expect $name 'otra cuenta no puede consultar el reporte' (Request $base 'GET' "/api/reportes/$($createdReport.id)" $null $secondToken) 404
        $otherHistory = Request $base 'GET' '/api/reportes' $null $secondToken
        Expect $name 'historial separado por cuenta' $otherHistory 200
        if (@($otherHistory.Content | ConvertFrom-Json).Count -ne 0) { throw "$name expuso reportes de otros ciudadanos." }
        if ($name -eq 'Csharp') {
            Expect $name 'otra cuenta no puede modificar el reporte' (Request $base 'PUT' "/api/reportes/$($createdReport.id)" $reportInput $secondToken) 404
            Expect $name 'otra cuenta no puede eliminar el reporte' (Request $base 'DELETE' "/api/reportes/$($createdReport.id)" $null $secondToken) 404
        }
        $otherType = $types | Where-Object { $_.activo -and $_.id -ne $activeType.id } | Select-Object -First 1
        if(-not $otherType) { throw "$name requiere dos categorías para probar el mapa." }
        $secondId=[int]($secondRegistration.Content | ConvertFrom-Json).idUsuario
        Add-MapFixtures $name $api.database $secondId ([int]$activeType.id) ([int]$otherType.id)
        $mapPath='/api/mapa/resumen?sur=-17.79&oeste=-63.19&norte=-17.76&este=-63.16&dias=7'
        Expect $name 'mapa sin sesión' (Request $base 'GET' $mapPath) 401
        $mapResponse=Request $base 'GET' $mapPath $null $session.token
        Expect $name 'mapa de incidentes confirmados' $mapResponse 200
        $mapSummary=$mapResponse.Content | ConvertFrom-Json
        if($mapSummary.totalReportes -ne 9 -or @($mapSummary.zonas).Count -ne 3 -or $mapSummary.umbrales.medio -ne 3 -or $mapSummary.umbrales.alto -ne 5) { throw "$name no agrupó correctamente las zonas o contó estados/fechas excluidos." }
        $zoneLevels=@{}; foreach($zone in $mapSummary.zonas){$zoneLevels[$zone.nivel]=[int]$zone.cantidad}
        if($zoneLevels.BAJO -ne 1 -or $zoneLevels.MEDIO -ne 3 -or $zoneLevels.ALTO -ne 5){throw "$name no aplica los umbrales aprobados."}
        if($mapResponse.Content -match '"(ciudadanoId|correo|descripcion|latitud|longitud|reporteId|password)"\s*:'){throw "$name expone datos individuales en el mapa."}
        foreach($zone in $mapSummary.zonas){
            if(@($zone.poligono).Count -ne 5){throw "$name no devuelve un polígono cerrado."}
            foreach($point in $zone.poligono){foreach($coordinate in $point){if([Math]::Abs($coordinate*200-[Math]::Round($coordinate*200)) -gt 0.000001){throw "$name devuelve coordenadas fuera de la cuadrícula agregada."}}}
        }
        $zonesResponse=Request $base 'GET' ($mapPath.Replace('/resumen?','/zonas?')) $null $session.token
        Expect $name 'contrato de zonas compatible' $zonesResponse 200
        if(@($zonesResponse.Content | ConvertFrom-Json).Count -ne 3){throw "$name cambió el contrato de zonas."}
        $filtered=Request $base 'GET' ($mapPath+"&tipoIncidenteId=$($activeType.id)") $null $session.token
        Expect $name 'mapa por tipo de incidente' $filtered 200
        $filteredSummary=$filtered.Content | ConvertFrom-Json
        if($filteredSummary.totalReportes -ne 8 -or @($filteredSummary.zonas | Where-Object nivel -EQ 'ALTO').Count -ne 0){throw "$name no recalculó el riesgo después del filtro."}
        $shortPeriod=Request $base 'GET' ($mapPath.Replace('dias=7','dias=1')) $null $session.token
        Expect $name 'mapa sin datos recientes' $shortPeriod 200
        if(($shortPeriod.Content | ConvertFrom-Json).totalReportes -ne 0){throw "$name incluye incidentes fuera del período."}
        $longPeriod=Request $base 'GET' ($mapPath.Replace('dias=7','dias=30')) $null $session.token
        Expect $name 'mapa amplía el período' $longPeriod 200
        if(($longPeriod.Content | ConvertFrom-Json).totalReportes -ne 10){throw "$name no incluye los incidentes del período ampliado."}
        $partialCell=Request $base 'GET' '/api/mapa/resumen?sur=-17.784&oeste=-63.184&norte=-17.782&este=-63.181&dias=7' $null $session.token
        Expect $name 'conteo estable al desplazar dentro de una zona' $partialCell 200
        if(($partialCell.Content | ConvertFrom-Json).totalReportes -ne 5){throw "$name recorta el conteo de una celda al desplazar el mapa."}
        Expect $name 'mapa rechaza límites incompletos' (Request $base 'GET' '/api/mapa/resumen?sur=-17.8' $null $session.token) 400
        Expect $name 'mapa rechaza límites invertidos' (Request $base 'GET' '/api/mapa/resumen?sur=-17.7&oeste=-63.2&norte=-17.8&este=-63.1' $null $session.token) 400
        Expect $name 'mapa rechaza área demasiado amplia' (Request $base 'GET' '/api/mapa/resumen?sur=-20&oeste=-65&norte=-16&este=-61' $null $session.token) 400
        Expect $name 'mapa rechaza NaN' (Request $base 'GET' '/api/mapa/resumen?sur=NaN&oeste=-63.2&norte=-17.7&este=-63.1' $null $session.token) 400
        Expect $name 'mapa rechaza período inválido' (Request $base 'GET' ($mapPath.Replace('dias=7','dias=91')) $null $session.token) 400
        Expect $name 'mapa rechaza categoría inválida' (Request $base 'GET' ($mapPath+'&tipoIncidenteId=0') $null $session.token) 400
        Test-Verificacion $api $session $reportInput
        Expect $name 'token malformado' (Request $base 'GET' '/api/auth/me' $null 'token-invalido') 401
        $parts = $session.token.Split('.')
        $replacement = if ($parts[2][0] -eq 'A') { 'B' } else { 'A' }
        $tampered = $parts[0] + '.' + $parts[1] + '.' + $replacement + $parts[2].Substring(1)
        Expect $name 'firma alterada' (Request $base 'GET' '/api/auth/me' $null $tampered) 401
        $expired = Signed-TestToken $api.issuer ([string]$id) ([DateTimeOffset]::UtcNow.ToUnixTimeSeconds() - 120)
        Expect $name 'token vencido' (Request $base 'GET' '/api/auth/me' $null $expired) 401
        $wrongAudience = Signed-TestToken $api.issuer ([string]$id) ([DateTimeOffset]::UtcNow.ToUnixTimeSeconds() + 3600) 'otra-aplicacion'
        Expect $name 'audiencia incorrecta' (Request $base 'GET' '/api/auth/me' $null $wrongAudience) 401
        $wrongSubject = Signed-TestToken $api.issuer 'identificador-invalido' ([DateTimeOffset]::UtcNow.ToUnixTimeSeconds() + 3600)
        Expect $name 'identificador inválido' (Request $base 'GET' '/api/auth/me' $null $wrongSubject) 401
        $disableSql = if ($name -eq 'Spring') { "UPDATE dbo.Usuarios SET activo=0 WHERE id_usuario=$id" } else { "UPDATE dbo.Usuarios SET Activo=0 WHERE Id=$id" }
        Invoke-Sql $api.database $disableSql
        Expect $name 'cuenta desactivada con token vigente' (Request $base 'GET' '/api/auth/me' $null $session.token) 401
        Invoke-Sql $api.database ($disableSql.Replace('activo=0','activo=1').Replace('Activo=0','Activo=1'))
        Expect $name 'cuenta reactivada' (Request $base 'GET' '/api/auth/me' $null $session.token) 200
    }
    Expect 'Csharp' 'rechazar token de Spring' (Request 'http://127.0.0.1:15080' 'GET' '/api/auth/me' $null $sessions.Spring) 401
    Expect 'Spring' 'rechazar token de Csharp' (Request 'http://127.0.0.1:18080' 'GET' '/api/auth/me' $null $sessions.Csharp) 401
    $results | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $logRoot 'contrato-auth.json') -Encoding utf8
    Write-Host "$($results.Count) comprobaciones HTTP correctas."
} finally {
    foreach ($process in $processes) { if (-not $process.HasExited) { Stop-Process -Id $process.Id -ErrorAction SilentlyContinue } }
    foreach ($key in $originalEnvironment.Keys) { [Environment]::SetEnvironmentVariable($key, $originalEnvironment[$key], 'Process') }
    foreach ($database in $createdDatabases) {
        if ($database -notmatch '^ZonaSeguraAuthPrueba_(Spring|Csharp)_[a-f0-9]{10}$') { throw 'Limpieza rechazada: nombre ajeno a las bases de prueba.' }
        Invoke-Sql 'master' "ALTER DATABASE [$database] SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE [$database]"
    }
}
