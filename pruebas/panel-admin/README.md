# Comprobación del panel administrativo

**Estado actual:** 178 comprobaciones MVC aprobadas, incluidas 27 de guías de uso. Comprueban contenido por rol, búsqueda sin acentos, autorización, contexto de la interfaz y consultas de ayuda sin llamadas a la API. Detalles en [GUIAS-DE-USO.md](../../docs/GUIAS-DE-USO.md). Los recuentos siguientes corresponden a etapas anteriores.

**Verificación vigente:** [151 comprobaciones MVC aprobadas y selector de interfaces](VERIFICACION-INTERFACES-2026-11-25.md). La suite actual cubre los cuatro roles y sus 15 combinaciones, además de los flujos administrativos anteriores. El comando de ejecución indicado más abajo sigue siendo válido. Los resultados y bloqueos de las secciones siguientes son históricos.

La funcionalidad nueva de eliminación y sus límites de verificación están en [VERIFICACION-ELIMINACION-2026-11-25.md](VERIFICACION-ELIMINACION-2026-11-25.md). El verificador incluye ahora 57 comprobaciones, pero la ejecución actual está bloqueada por Control de aplicaciones de Windows. Las 48 comprobaciones aprobadas citadas a continuación corresponden al bloque anterior de interfaces por rol.

Última verificación: **48 comprobaciones aprobadas**, descritas en [VERIFICACION-PANELES-2026-11-25.md](VERIFICACION-PANELES-2026-11-25.md). La [verificación anterior](VERIFICACION-2026-11-25.md) conserva las limitaciones observadas en la ejecución previa.

Desde la raíz `04_Codigo/zona-segura`:

```powershell
dotnet run --project .\pruebas\panel-admin\PanelAdmin.Checks.csproj --no-launch-profile
```

La comprobación inicia un servidor MVC temporal en un puerto libre de loopback. Usa las vistas y controladores reales del panel, con identidad y API simuladas exclusivamente en este proyecto de pruebas. Comprueba el rechazo a anónimos y personal municipal, acceso administrativo, formulario con roles, antifalsificación, rol obligatorio, contraseñas distintas, límite UTF-8, correo duplicado, ausencia de contraseñas en HTML, envío al endpoint administrativo y redirección/confirmación.

El servidor se cierra al terminar. No requiere credenciales, no modifica cuentas y no se conecta a SQL Server. No valida TLS, las credenciales reales de Spring ni su persistencia de datos.

El controlador real de acceso se prueba además con cookies y sesión MVC: raíz y login según rol, inicio administrativo, interfaz municipal, navegación y cambio entre paneles con ambos roles, bloqueo por URL directa, pantalla 403, rechazo de ciudadanos/unidades sin permisos institucionales, verificación del perfil de la API y caducidad/cierre de sesión. La autenticación por cabecera pertenece exclusivamente al verificador; el panel utiliza las cookies creadas después de consultar la API.

También comprueba que `/Admin/Verificar` pertenezca al administrador, que Personal de Alcaldía no acceda a esa pantalla ni a la antigua `/Panel/Verificar`, que el menú apunte a Administración y que el envío use `/api/admin/reportes/{id}/verificacion`. Una decisión omitida no se interpreta como rechazo.

Pruebas complementarias del servicio Spring (desde `backend-spring`):

```powershell
.\gradlew.bat test --offline --tests '*AdministracionServiceTests'
```

Usan JdbcTemplate y el perfil de autenticación simulados, con BCrypt real. Comprueban los cuatro roles, combinación de roles, denegación de privilegios, credenciales inválidas, auditoría sin contraseña, correo duplicado, datos inválidos y que el registro público conserve solo el rol ciudadano. No sustituyen una prueba transaccional contra SQL Server.
