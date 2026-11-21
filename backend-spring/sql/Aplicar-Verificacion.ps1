param([string]$Servidor='MateoMS',[string]$Base='ZonaSegura')
$ErrorActionPreference='Stop'
$builder=[System.Data.SqlClient.SqlConnectionStringBuilder]::new()
$builder['Data Source']=$Servidor
$builder['Initial Catalog']=$Base
$builder['Integrated Security']=$true
$builder['Encrypt']=$true
$builder['TrustServerCertificate']=$true
$connection=[System.Data.SqlClient.SqlConnection]::new($builder.ConnectionString)
try {
    $connection.Open()
    $count=$connection.CreateCommand()
    $count.CommandText='SELECT COUNT(*) FROM dbo.Reportes'
    $before=[int]$count.ExecuteScalar()
    $script=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'sprint2-verificacion.sql') -Raw
    foreach($batch in [regex]::Split($script,'(?m)^\s*GO\s*$')){
        if($batch.Trim()){
            $command=$connection.CreateCommand();$command.CommandText=$batch
            [void]$command.ExecuteNonQuery();$command.Dispose()
        }
    }
    $after=[int]$count.ExecuteScalar()
    if($before -ne $after){throw 'El número de reportes cambió durante la actualización.'}
    $count.CommandText="SELECT COUNT(*) FROM sys.tables WHERE name='VerificacionesInstitucionales'"
    if([int]$count.ExecuteScalar() -ne 1){throw 'No se creó la tabla de verificación.'}
    Write-Output "Actualización aplicada a $Base. Tabla VerificacionesInstitucionales disponible. Reportes conservados: $after."
} finally { $connection.Dispose() }
