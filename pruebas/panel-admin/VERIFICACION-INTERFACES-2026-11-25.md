# Selector de interfaces por rol — 25/11/2026

Resultado final: **151 comprobaciones MVC aprobadas**, compilación C#/Razor de ambas copias sin errores ni advertencias y **7 pruebas Android aprobadas** con APK debug generado. No se modificaron cuentas, roles ni datos reales.

## Web

El verificador ejecuta las vistas y controladores reales con cookies y sesión ASP.NET, API simulada y puerto temporal en loopback. Comprueba las 15 combinaciones no vacías de los cuatro roles: login al selector, opciones exactas, elección de cada rol y permanencia de la elección al entrar en `/`. También verifica el rechazo de un rol no asignado, revocación con el selector abierto, antifalsificación y separación de menús. El perfil se consulta mediante `/api/auth/me`.

Los recorridos nuevos incluyen ciudadano con creación de reporte y clave idempotente sin autor manipulable, mapa ciudadano, restricción del acceso operativo, bandeja de la unidad y registro de atención. Una orden ausente de la bandeja no puede abrirse. Se mantienen comprobaciones de creación/eliminación de cuentas, revisión administrativa y caducidad/cierre de sesión.

Se detectó y corrigió la interpretación regional de las coordenadas GPS: el binder específico usa punto decimal y valida límites, sin cambiar el idioma o formato de los demás formularios. El recorrido final de creación terminó en 302 al directorio ciudadano y envió el UUID idempotente al endpoint de reportes.

Comando ejecutado desde la raíz del repositorio:

```powershell
dotnet build pruebas/panel-admin/PanelAdmin.Checks.csproj --no-restore -p:UseAppHost=false
dotnet run --project pruebas/panel-admin/PanelAdmin.Checks.csproj --no-build --no-launch-profile -p:UseAppHost=false
```

Esta ejecución final sí terminó con código 0. Las limitaciones de Control de aplicaciones descritas en la verificación de eliminación corresponden a la versión anterior; no se cambió ni desactivó la política de Windows.

## Android

Comando: `gradlew.bat :app:testDebugUnitTest :app:assembleDebug --offline`, correcto. JUnit: 3 pruebas de `InterfazUsuarioTest`, 3 de `LoginValidatorTest` y 1 de `ExampleUnitTest`, sin fallos ni errores. La primera prueba del catálogo recorre las 15 combinaciones; las otras cubren Ciudadano + Alcaldía, duplicados, roles desconocidos y listas vacías. Se emplean colecciones compatibles con el mínimo Android del proyecto en el código de producción.

APK: `app/build/outputs/apk/debug/app-debug.apk`. No se instaló ni se ejecutó en teléfono o emulador. El modo sin conexión, los permisos de ubicación y el cambio de pila de actividades requieren todavía comprobación en dispositivo.

Actualización de colores solicitada después: Ciudadano conserva su paleta, Alcaldía usa azul y Administración morado. Los temas incluyen selector, paneles y pantallas internas, también con la configuración nocturna del teléfono. Se repitieron `:app:testDebugUnitTest :app:assembleDebug --offline`: compilación correcta y las 7 pruebas aprobadas. Sin comprobación visual en dispositivo.

## Alcance

La API Spring existente suministra roles y autorización; no se añadieron endpoints, migraciones ni dependencias. Las comprobaciones web simulan las respuestas, de modo que no certifican un ingreso con credenciales reales ni operaciones persistidas en SQL Server. No se hizo revisión visual en navegador. Para usarlo, recompilar/reiniciar MVC e instalar el APK actualizado. Diseño y uso: [INTERFACES-POR-ROL.md](../../docs/INTERFACES-POR-ROL.md).
