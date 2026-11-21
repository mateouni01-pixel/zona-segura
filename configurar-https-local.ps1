param()
$ErrorActionPreference='Stop'
$codeRoot=Split-Path -Parent $MyInvocation.MyCommand.Path
$privateRoot=Join-Path $codeRoot '.https-local'
$publicRoot=Join-Path $codeRoot 'Certificados'
$androidRoot=Join-Path $codeRoot 'app\src\debug\res\raw'
$keytool=Join-Path 'C:\Program Files\Android\Android Studio\jbr' 'bin\keytool.exe'
if(-not(Test-Path -LiteralPath $keytool)){throw 'Falta keytool de Android Studio.'}
New-Item -ItemType Directory -Path $privateRoot,$publicRoot,$androidRoot -Force | Out-Null
$owner=[System.Security.Principal.WindowsIdentity]::GetCurrent().User.Value
& icacls.exe $privateRoot /inheritance:r /grant:r "*${owner}:(OI)(CI)F" '*S-1-5-18:(OI)(CI)F' | Out-Null
if($LASTEXITCODE -ne 0){throw 'No se pudo restringir el acceso a las claves locales.'}
$configPath=Join-Path $privateRoot 'entorno.json'
$caStore=Join-Path $privateRoot 'ca.p12'
$serverStore=Join-Path $privateRoot 'servidor.p12'
$caCertificate=Join-Path $privateRoot 'ca.crt'
$serverRequest=Join-Path $privateRoot 'servidor.csr'
$serverCertificate=Join-Path $privateRoot 'servidor.crt'
function Invoke-Keytool([string[]]$Arguments){& $keytool @Arguments;if($LASTEXITCODE -ne 0){throw 'No se pudo preparar el certificado HTTPS local.'}}
$previousPassword=$env:ZONA_TLS_PASSWORD
try{
    if(Test-Path -LiteralPath $configPath){
        $config=Get-Content -LiteralPath $configPath -Raw | ConvertFrom-Json
        $env:ZONA_TLS_PASSWORD=$config.password
        if(-not(Test-Path -LiteralPath $serverStore) -or -not(Test-Path -LiteralPath $caCertificate)){throw 'La configuración HTTPS local está incompleta.'}
    }else{
        if((Test-Path -LiteralPath $caStore) -or (Test-Path -LiteralPath $serverStore)){throw 'Existen claves locales sin configuración. Conserva sus archivos y revisa la preparación anterior.'}
        $bytes=New-Object byte[] 32
        $random=[System.Security.Cryptography.RandomNumberGenerator]::Create()
        try{$random.GetBytes($bytes)}finally{$random.Dispose()}
        $env:ZONA_TLS_PASSWORD=[Convert]::ToBase64String($bytes)
        Invoke-Keytool @('-genkeypair','-alias','zona-local-ca','-keystore',$caStore,'-storetype','PKCS12','-storepass:env','ZONA_TLS_PASSWORD','-keyalg','RSA','-keysize','3072','-validity','3650','-dname','CN=Zona Segura Desarrollo Local CA','-ext','bc:c=ca:true','-ext','ku:c=keyCertSign,cRLSign','-noprompt')
        Invoke-Keytool @('-exportcert','-rfc','-alias','zona-local-ca','-keystore',$caStore,'-storepass:env','ZONA_TLS_PASSWORD','-file',$caCertificate)
        Invoke-Keytool @('-genkeypair','-alias','servidor','-keystore',$serverStore,'-storetype','PKCS12','-storepass:env','ZONA_TLS_PASSWORD','-keyalg','RSA','-keysize','3072','-validity','365','-dname','CN=localhost','-ext','SAN=dns:localhost,ip:127.0.0.1','-ext','EKU=serverAuth','-noprompt')
        Invoke-Keytool @('-certreq','-alias','servidor','-keystore',$serverStore,'-storepass:env','ZONA_TLS_PASSWORD','-file',$serverRequest)
        Invoke-Keytool @('-gencert','-alias','zona-local-ca','-keystore',$caStore,'-storepass:env','ZONA_TLS_PASSWORD','-infile',$serverRequest,'-outfile',$serverCertificate,'-rfc','-validity','365','-ext','SAN=dns:localhost,ip:127.0.0.1','-ext','KU=digitalSignature,keyEncipherment','-ext','EKU=serverAuth')
        Invoke-Keytool @('-importcert','-alias','zona-local-ca','-keystore',$serverStore,'-storepass:env','ZONA_TLS_PASSWORD','-file',$caCertificate,'-noprompt')
        Invoke-Keytool @('-importcert','-alias','servidor','-keystore',$serverStore,'-storepass:env','ZONA_TLS_PASSWORD','-file',$serverCertificate,'-noprompt')
        [ordered]@{keystore=$serverStore;password=$env:ZONA_TLS_PASSWORD;ca=$caCertificate}|ConvertTo-Json|Set-Content -LiteralPath $configPath -Encoding UTF8
    }
    Copy-Item -LiteralPath $caCertificate -Destination (Join-Path $publicRoot 'ca-desarrollo.crt') -Force
    Copy-Item -LiteralPath $caCertificate -Destination (Join-Path $androidRoot 'zona_local_ca.crt') -Force
    Write-Host 'HTTPS local preparado. Claves privadas restringidas en .https-local; CA pública copiada a Android debug y Certificados.'
    Write-Host 'No se cambió el almacén de confianza de Windows ni se inició ningún servidor.'
}finally{$env:ZONA_TLS_PASSWORD=$previousPassword}
