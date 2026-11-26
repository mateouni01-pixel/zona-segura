# Panel institucional Zona Segura

## Selector de interfaces para todos los roles (25/11/2026)

La web admite ahora Ciudadano, Personal de Alcaldía, Administrador y Unidad Operativa. Después del login se abre `/Cuenta/Interfaces` con una tarjeta por rol asignado; la persona elige cuál usar. **Cambiar interfaz** vuelve al selector sin cerrar sesión. Las 15 combinaciones se resuelven filtrando cuatro estrategias, conservando menús propios y comprobando los roles vigentes con `/api/auth/me` al mostrar y elegir.

Se incorporaron las vistas ciudadanas (mis reportes, crear reporte con GPS y mapa) y operativas (bandeja, avisos y atención) consumiendo la API existente. Se conserva autorización por rol en MVC y en la API. La cuenta operativa necesita pertenecer a una unidad. No requiere otra API ni cambios de base. El código usa Strategy y un registro; detalle en `../docs/INTERFACES-POR-ROL.md`. Las secciones anteriores describen la evolución previa; ya no se da prioridad automática al administrador ni se rechaza el acceso web de ciudadanos y unidades.

## Eliminar cuentas (25/11/2026)

Administración ofrece **Usuarios y permisos > Eliminar** y **Administrar cuenta > Eliminar cuenta**. La pantalla identifica la cuenta y exige motivo de 10–2000 caracteres y escribir su ID. Solo el POST confirmado envía `DELETE /api/admin/usuarios/{id}` a la API compartida; abrir la pantalla no borra datos. El éxito vuelve al directorio con confirmación.

El borrado es definitivo para cuentas sin actividad vinculada. Se protegen la cuenta propia y el último administrador activo. Si hay reportes, votos, sanciones, decisiones, órdenes o actividad institucional, se muestra la restricción y se conserva el historial; sigue disponible desactivar el acceso. La auditoría de creación y eliminación se conserva aunque la cuenta desaparezca. Spring necesita `sprint3-eliminacion-cuentas.sql`, ya aplicado localmente con respaldo. Esta funcionalidad no está en la API C# alternativa.

El panel compila. La nueva suite MVC con API simulada no pudo ejecutarse porque Windows bloqueó la DLL por Control de aplicaciones (`0x800711C7`); no se modificó esa política. Las pruebas de Spring y SQL Server sí se ejecutaron. Detalles: `../pruebas/panel-admin/VERIFICACION-ELIMINACION-2026-11-25.md`. Recompilar y reiniciar el proyecto web en Visual Studio para cargar las nuevas vistas.

## Conexión compartida en desarrollo (25/11/2026)

Al ejecutar desde Visual Studio con el perfil incluido (`Development`), la web consulta `http://127.0.0.1:8080/`, la misma API iniciada con `backend-spring/iniciar-wifi.cmd` que Android alcanza mediante la IP Wi-Fi de la PC. La web puede seguir abierta en HTTPS; su conexión a la API se realiza desde el servidor MVC. Cambiar de red solo requiere actualizar la dirección de Android, porque la conexión local de MVC no depende de la IP Wi-Fi.

Reiniciar el panel después de cambiar su dirección de API, ya que se toma al arrancar. No basta con recargar el navegador. El lanzador HTTPS explícito `iniciar-https-local.ps1 -Componente Panel` sigue seleccionando `https://localhost:8443/` y requiere la API iniciada con `-Componente Spring`. La configuración fuera de desarrollo conserva HTTPS.

Diagnóstico comprobado: `/api/conexion` en 8080 devolvió HTTP 200 y base `ZonaSegura`; no había servicio escuchando en 8443. Esa diferencia provocaba **No se pudo conectar con la API** al ingresar.

## Interfaces según el rol (25/11/2026)

La misma web ofrece dos espacios de trabajo. La entrada `/` consulta la sesión y dirige al panel correspondiente; sin sesión abre el acceso institucional. El formulario presenta ambos perfiles y toma los roles de la API, sin un selector que conceda permisos.

| Permisos de la cuenta | Inicio | Interfaz y tareas |
| --- | --- | --- |
| `ADMINISTRADOR` | `/Admin/Inicio` | Inicio con accesos a usuarios y permisos, creación de cuentas, validación de reportes, moderación y auditoría. |
| `PERSONAL_ALCALDIA` | `/Panel` | Mapa, reportes, recursos, unidades y órdenes de intervención. |
| Ambos roles | `/Admin/Inicio` | Enlace **Cambiar a Alcaldía / Administración**; cada espacio muestra su propio menú, colores y encabezado. |

Los enlaces, la marca y el indicador de página actual conservan el contexto del panel visitado. `/Admin` sigue siendo el directorio de usuarios. Las rutas mantienen autorización por rol; un acceso sin permisos dirige a `/Cuenta/AccesoDenegado`, que responde 403 y permite volver al panel. Las cuentas ciudadanas u operativas sin un rol institucional continúan usando Android.

Alcaldía consulta los reportes pendientes, pero no muestra enlaces a la antigua validación municipal. Tanto la tabla inicial como su actualización JavaScript indican **En espera de validación**; los reportes validados permiten crear una orden.

Validación de este cambio: compilación C#/Razor de ambas copias sin advertencias ni errores, sintaxis JavaScript correcta y **48 comprobaciones MVC aprobadas** con API simulada, autenticación por cookies y sesión. Incluyen navegación por rol, cuentas con ambos roles, denegación por URL directa, rechazo de perfiles y caducidad/cierre de sesión. No se probaron cuentas reales, SQL Server ni navegación visual. Detalle: `../pruebas/panel-admin/VERIFICACION-PANELES-2026-11-25.md`.

Recompilar y reiniciar el panel para ver los cambios. No requiere migraciones ni cambios de permisos en la base de datos.

## Corrección de roles de validación manual (25/11/2026)

Según la corrección del caso de uso, **Administrador/moderador** (`ADMINISTRADOR`) revisa, valida o rechaza manualmente reportes desde **Administración > Validar reportes**, ruta `/Admin/Verificar`. Personal de Alcaldía conserva mapa, recursos, unidades y órdenes; no puede realizar la revisión manual. Las tres confirmaciones ciudadanas siguen validando automáticamente, por decisión expresa del usuario.

La web y Android usan `GET /api/admin/reportes-verificacion` y `POST /api/admin/reportes/{id}/verificacion`. Ambas APIs comprueban el administrador vigente y conservan motivo, responsable y fecha. Las rutas antiguas `/api/alcaldia/.../verificacion` permanecen como alias administrativos y también rechazan a Personal de Alcaldía. Se prohíbe revisar el reporte propio y sobrescribir una decisión existente.

Spring necesita `backend-spring/sql/sprint3-validacion-administrativa.sql` en bases existentes para que el responsable histórico `id_personal` referencie `Usuarios`, sin obligar a crear un perfil municipal. Se aplicó a `MateoMS/ZonaSegura` después de un respaldo verificado, conservando los 3 reportes y las 3 revisiones existentes. La API C# ya referencia `Usuarios` y no necesita migración para este cambio. Reiniciar los procesos con la compilación actual.

## Panel de administración: creación de usuarios (25/11/2026)

Una cuenta con `ADMINISTRADOR` ingresa por el mismo formulario institucional y se dirige a `/Admin/Inicio`. En **Usuarios y permisos > Crear usuario** puede registrar nombre, correo, contraseña inicial, confirmación, uno o varios roles y motivo de creación. Puede crear otros administradores, ciudadanos, personal de Alcaldía y unidades operativas. Una cuenta administrativa que también necesite el mapa debe tener además `PERSONAL_ALCALDIA`.

La creación utiliza `POST /api/admin/usuarios` del Spring Boot actualizado de `backend-spring`. La API comprueba el administrador vigente, guarda la contraseña con BCrypt y registra responsable, motivo y roles en auditoría. Cuenta, especializaciones, permisos y auditoría se guardan en una transacción. No requiere tablas nuevas sobre el esquema de administración existente. El registro público continúa creando únicamente ciudadanos. La API C# alternativa no incorpora este nuevo endpoint.

Recompilar y reiniciar Spring y el panel para cargar este cambio; una API anterior en HTTP:8080 no sustituye al Spring HTTPS:8443 configurado en el panel. Desde `04_Codigo/zona-segura`, el backend puede iniciarse sin cambiar permanentemente la política de PowerShell:

```powershell
powershell.exe -NoProfile -ExecutionPolicy RemoteSigned -File .\iniciar-https-local.ps1 -Componente Spring
```

Después, ejecutar `ZonaSegura.Panel.csproj` desde Visual Studio. La ruta de administración será `https://localhost:58874/Admin` con el perfil incluido, o `https://localhost:5091/Admin` con el lanzador HTTPS. Hace falta una cuenta previamente autorizada como administrador para crear las siguientes; esta actualización no concede permisos a una cuenta existente automáticamente.

Validación: compilación C#/Razor, pruebas unitarias del servicio Spring y comprobaciones HTTP del panel con identidad y API simuladas. Las pruebas del panel verifican autorización, antifalsificación, roles, confirmación y límite UTF-8 de contraseña, errores de correo duplicado y redirección al directorio. No se crearon usuarios reales ni se realizaron pruebas de creación contra SQL Server. Ver `../pruebas/panel-admin/README.md`.

## Estado vigente y HTTPS local

Incluye acceso municipal y administrativo separado, usuarios/roles, moderación, sanciones y levantamiento con auditoría. Los permisos reales se verifican en las APIs, con protección de autoanulación y último administrador. Una sanción suspende acceso, sin modificar reputación. El mapa consulta al desplazar/zoom con debounce y cancelación, actualiza tabla, siete indicadores y zonas críticas; los errores muestran datos no disponibles. Puede superponer la cobertura de los doce distritos.

Las migraciones de operaciones y administración se aplicaron a ambas bases locales con respaldo previo. Este panel no concede permisos por elegir un rol al ingresar: usar una cuenta que ya tenga ADMINISTRADOR o PERSONAL_ALCALDIA. La API C# admite un rol por cuenta; Spring conserva varios si corresponden.

La URL predeterminada de la API ahora es `https://localhost:8443/`. En Development, `Api:DevelopmentCaPath` apunta al certificado público `../Certificados/ca-desarrollo.crt`; el cliente solo admite esa CA para HTTPS sobre loopback, manteniendo validación de nombre y fecha. No se desactiva la comprobación de certificados ni se exponen Bearer al navegador. Fuera de Development se exige HTTPS y cookies seguras.

En la actualización local se inició el panel recompilado en `https://localhost:58874` con el certificado de desarrollo ASP.NET del equipo y Spring en `https://localhost:8443`. Se observaron sus mensajes de arranque y puertos; no se inició sesión ni se ejecutaron recorridos funcionales. El cambio de proceso invalida las sesiones MVC en memoria: volver a ingresar. La API HTTP que pueda estar abierta en otra copia del proyecto no se utiliza desde esta configuración.

Desde `04_Codigo`, preparar `configurar-https-local.ps1`, iniciar `iniciar-https-local.ps1 -Componente Spring` y en otra consola `iniciar-https-local.ps1 -Componente Panel`. Este último utiliza `https://localhost:5091` y certificado local. Para que el navegador confíe en él, importar **manualmente**, si se desea usar ese certificado en el navegador, únicamente la CA pública creada para este equipo desde `Certificados/ca-desarrollo.crt` al almacén personal de autoridades raíz. La preparación no realiza esa importación. Alternativamente, iniciar el panel con el certificado de desarrollo ASP.NET ya configurado en el equipo; la confianza de su conexión a Spring continúa acotada al cliente MVC.

Los apartados posteriores contienen instrucciones y resultados de las entregas anteriores. Sus referencias a HTTP:8080 y migraciones pendientes son históricas; para desarrollo HTTP explícito usar `Api__BaseUrl=http://127.0.0.1:8080/` con un Spring actualizado. No se ejecutaron pruebas funcionales autenticadas ni navegador o emulador en este bloque.

Panel ASP.NET Core MVC con .NET 10, controladores, modelos de vista, vistas Razor, Tag Helpers, validación de formularios y Bootstrap, siguiendo la estructura de las prácticas locales EntradasMvc y ExploradorContenidoAleatorio. Usa Leaflet para el mapa. Las prácticas originales se consultaron y no se modificaron.

## Relación con la planificación

El Excel recibido prescribe HTML, CSS, JavaScript, Bootstrap y Leaflet para el panel web, consumiendo la misma API REST Spring Boot que Android. ASP.NET Core MVC se incorpora como estructura del panel: las vistas generan HTML y conservan Bootstrap y Leaflet. No sustituye la API Spring ni conecta el navegador directamente a SQL Server. La API C# alternativa también admite el mismo contrato. Esta decisión debe incorporarse al apartado tecnológico del Excel si el equipo la adopta formalmente; el archivo recibido no se editó.

## Funciones

- Acceso institucional por correo y contraseña, comprobando el rol municipal y el perfil en la API.
- Acceso de Administración con menú propio: usuarios y permisos, moderación y auditoría. El inicio de sesión comprueba los roles vigentes en la API; no se elige ni se concede un rol desde el formulario de acceso.
- Token Bearer guardado en la sesión del servidor; el navegador recibe una cookie HTTP-only, no el token de la API.
- Mapa de calles, marcadores de incidentes y celdas de riesgo; opción de ocultar la capa de riesgo.
- Filtros compartidos para mapa y tabla: fecha desde/hasta UTC inclusive, categoría, estado e índice mínimo de veracidad. Máximo 366 días por consulta. Las zonas cuentan solo VALIDADO, EN_ATENCION y ATENDIDO; los pendientes o rechazados pueden consultarse como marcadores institucionales y no contribuyen al color.
- Revisión administrativa con motivo obligatorio; confirmación y rechazo auditados por la API.
- Registro y edición de recursos, unidades y asociación de cuentas operativas ya autorizadas.
- Creación de órdenes con tipo, instrucciones, recurso e identificador de solicitud para evitar duplicados.
- Seguimiento de órdenes y avisos. El cierre continúa en la bandeja de la unidad operativa; el municipio no se adjudica ese permiso.
- Consulta por área visible: al desplazar o ampliar el mapa se consultan de nuevo mapa, tabla, zonas e indicadores mediante el servidor MVC. Las solicitudes se agrupan y cancelan para que una respuesta anterior no reemplace la vista actual.
- Indicadores de reportes totales, pendientes, validados, en atención, atendidos, rechazados y zonas altas, más las doce celdas de mayor concentración. Las celdas del borde conservan su conteo completo; la tabla y los contadores de reportes usan coordenadas dentro del área visible.
- Capa opcional de límites territoriales obtenida de la API. Si no puede descargarse, el mapa de incidentes continúa funcionando.
- Edición de permisos y acceso con motivo obligatorio; suspensión de cuentas por una sanción sobre un reporte rechazado y levantamiento explícito de sanciones. La API impide sancionar dos veces el mismo hecho y protege al último administrador activo. Las decisiones conservan responsable, fecha y motivo.

El índice positivo/total es informativo y no sustituye el umbral de tres confirmaciones ciudadanas. Los filtros por índice y por estado son independientes. No se publica la identidad del ciudadano; las coordenadas precisas del panel se entregan únicamente a personal municipal autorizado.

Las páginas administrativas y la validación manual requieren ADMINISTRADOR. Las coordenadas del mapa y la gestión municipal requieren PERSONAL_ALCALDIA. Una cuenta con ambos permisos obtiene ambos menús; una cuenta administrativa no recibe permisos municipales automáticamente. Las cuentas se registran por el mecanismo existente y un administrador autorizado les asigna los roles necesarios; el panel no contiene una contraseña ni una elevación inicial de permisos. Los permisos recién concedidos requieren iniciar sesión nuevamente para renovar las credenciales.

La API Spring admite varios roles por cuenta y se conservan al editar mediante casillas. La API C# alternativa admite uno y devuelve un error de validación si se solicita una combinación. La suspensión y el levantamiento se validan en el servidor; no se modifica una reputación numérica en este bloque. Los cambios requieren instalar las migraciones de administración del backend elegido.

## Configurar y compilar

Desde esta carpeta:

```powershell
dotnet build
```

`Api:BaseUrl` en appsettings.json apunta a Spring Boot en `http://127.0.0.1:8080/`. Para cambiar sin editar el archivo:

```powershell
$env:Api__BaseUrl = 'http://127.0.0.1:8080/'
```

Para la API C# independiente, utilizar `http://127.0.0.1:5080/` y una cuenta institucional de esa base. Nunca mezclar usuarios, tokens o identificadores entre las bases.

Cuando corresponda ejecutar (no se hizo durante esta implementación):

```powershell
$env:ASPNETCORE_ENVIRONMENT = 'Development'
dotnet run --no-launch-profile --urls 'http://localhost:5090'
```

Abrir `http://localhost:5090`. Es necesario que la API elegida esté recompilada, en ejecución y tenga instaladas las migraciones de verificación y operaciones descritas en la guía del proyecto Android. El endpoint `GET /api/alcaldia/panel` se agregó a ambas APIs sin cambiar el esquema SQL. Crear cuentas y asignar roles requiere una sesión de administrador autorizada por la API.

El cliente usa llamadas desde el servidor MVC; no necesita habilitar CORS para exponer el token en el navegador. Formularios de escritura con protección antifalsificación, solicitudes GET sin efectos de escritura, sesión de 55 minutos y comprobación del rol vigente en el backend. En despliegue real, configurar dominio permitido, HTTPS y persistencia adecuada de sesión y claves de protección de datos. Reiniciar el panel local invalida las sesiones mantenidas en memoria.

Bootstrap y Leaflet están incluidos localmente. Las calles se solicitan a OpenStreetMap con su atribución visible, sin descargas masivas. Se necesita internet para las calles; el panel no almacena mapas offline.

## Verificación de esta entrega

El bloque de administración y actualización del mapa se comprobó mediante compilación C#/Razor y comprobación de sintaxis JavaScript, sin navegar, ejecutar pruebas funcionales ni emuladores. La salida de verificación se genera con `dotnet build --no-restore --output bin/Verificacion` para no reemplazar los archivos de un panel que pudiera estar abierto. Hay que iniciar la compilación actual cuando corresponda usarla. La compilación no certifica los permisos ni las interacciones en ejecución.

La entrega inicial se comprobó mediante compilación, sin iniciar aplicaciones ni ejecutar pruebas funcionales. Los 176 resultados HTTP históricos son anteriores al panel y no lo validan.

Al revisar el error 404 mostrado el 20/11/2026 se comprobó que el backend activo había arrancado antes de compilar el nuevo controlador del panel. Se recompilaron Spring y MVC y se actualizaron los procesos locales. La comprobación del arranque utiliza únicamente el estado público `/api/conexion`; no se ejecutaron pruebas funcionales autenticadas, navegador ni emuladores, y no se aplicaron migraciones ni se realizaron commits o publicaciones en Git. La compilación comprueba C# y Razor, pero no certifica la interacción visual ni los datos de una sesión municipal.

Si reaparece el aviso de versión antigua, desde `../zona-segura/backend-spring` ejecutar:

```powershell
.\iniciar-backend.cmd -Actualizar -SinAdb
```

`-Actualizar` reinicia únicamente el proceso Java identificado como perteneciente a esa carpeta y carga el código actual. `-SinAdb` omite las acciones sobre dispositivos. `/api/conexion` identifica la versión `2.3-panel` con `panelMunicipal: true`. Si se genera otra clave JWT o se reinicia el panel, ingresar nuevamente. Cuando falla la consulta, el panel muestra el error sin presentar contadores en cero ni un mapa vacío como si fueran datos válidos.
