# Paneles institucionales según el rol

Verificado el 25/11/2026. La web abre un espacio de Administración o Alcaldía según los permisos devueltos por la API. Ambas copias del código del panel están sincronizadas; cada README conserva sus rutas locales.

## Resultados

- Panel principal: `dotnet build 04_Codigo/ZonaSegura.Panel/ZonaSegura.Panel.csproj --no-restore -p:UseAppHost=false -p:BaseOutputPath=bin/roles/ -v minimal`, correcto sin advertencias ni errores. La salida separada evita reemplazar archivos de un proceso abierto.
- Panel del repositorio y verificador: `dotnet build pruebas/panel-admin/PanelAdmin.Checks.csproj --no-restore -p:UseAppHost=false -v minimal`, correcto sin advertencias ni errores.
- `node --check 04_Codigo/ZonaSegura.Panel/wwwroot/js/mapa.js`, correcto.
- `dotnet run --project pruebas/panel-admin/PanelAdmin.Checks.csproj --no-build --no-launch-profile -p:UseAppHost=false`, **48 comprobaciones aprobadas**. El servidor temporal se cerró al terminar.

La primera ejecución quedó bloqueada por la restricción de sockets de la red aislada (`SocketException 10013`). Con el permiso del ejecutor para conectar a loopback, la suite pudo ejecutarse. Se corrigió una expectativa de URL del verificador: el enlace de asignación usa `/Panel/Asignar/11` porque `id` forma parte de la ruta MVC. La ejecución final completa terminó con código 0.

## Cobertura

Se mantienen las 20 comprobaciones anteriores de administración de cuentas y validación: roles, acceso anónimo/municipal, antifalsificación, contraseñas, correo duplicado, envío de roles, confirmación en el directorio y revisión administrativa.

Las 28 comprobaciones nuevas cubren:

- Acceso común y redirección de `/` al login o al inicio correspondiente.
- Autenticación mediante `CuentaController`, cookies reales de ASP.NET y sesión MVC, con respuesta de API simulada.
- Inicio, marca, menú e indicador de página propios de Administración; interfaz propia de Alcaldía.
- Denegación por URL directa en ambos sentidos y pantalla 403 con regreso al panel.
- Reportes pendientes sin enlaces obsoletos de validación municipal; creación de orden para reportes validados.
- Cuenta con ambos roles, prioridad administrativa al ingresar, cambio en ambos sentidos y contexto municipal en recursos.
- Rechazo del acceso institucional de ciudadanos y unidades operativas sin rol de Administración o Alcaldía.
- Perfil rechazado por la API sin dejar una sesión autorizada.
- Respuesta 401 de la API, caducidad de la sesión en ambos paneles y cierre de sesión.

## Límites

Se usó una API simulada, sin credenciales ni datos reales. No se inició sesión contra Spring o SQL Server, no se modificaron permisos ni se aplicaron migraciones. No se hizo revisión visual en navegador. Los procesos que el usuario tuviera abiertos no se reiniciaron; para cargar las nuevas interfaces hay que recompilar y reiniciar el panel.
