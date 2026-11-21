# Panel municipal Zona Segura

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
- Revisión municipal con motivo obligatorio; confirmación y rechazo auditados por la API.
- Registro y edición de recursos, unidades y asociación de cuentas operativas ya autorizadas.
- Creación de órdenes con tipo, instrucciones, recurso e identificador de solicitud para evitar duplicados.
- Seguimiento de órdenes y avisos. El cierre continúa en la bandeja de la unidad operativa; el municipio no se adjudica ese permiso.
- Consulta por área visible: al desplazar o ampliar el mapa se consultan de nuevo mapa, tabla, zonas e indicadores mediante el servidor MVC. Las solicitudes se agrupan y cancelan para que una respuesta anterior no reemplace la vista actual.
- Indicadores de reportes totales, pendientes, validados, en atención, atendidos, rechazados y zonas altas, más las doce celdas de mayor concentración. Las celdas del borde conservan su conteo completo; la tabla y los contadores de reportes usan coordenadas dentro del área visible.
- Capa opcional de límites territoriales obtenida de la API. Si no puede descargarse, el mapa de incidentes continúa funcionando.
- Edición de permisos y acceso con motivo obligatorio; suspensión de cuentas por una sanción sobre un reporte rechazado y levantamiento explícito de sanciones. La API impide sancionar dos veces el mismo hecho y protege al último administrador activo. Las decisiones conservan responsable, fecha y motivo.

El índice positivo/total es informativo y no sustituye el umbral de tres confirmaciones ciudadanas. Los filtros por índice y por estado son independientes. No se publica la identidad del ciudadano; las coordenadas precisas del panel se entregan únicamente a personal municipal autorizado.

Las páginas administrativas requieren ADMINISTRADOR. La verificación institucional, las coordenadas del mapa y la gestión municipal requieren PERSONAL_ALCALDIA. Una cuenta con ambos permisos obtiene ambos menús; una cuenta administrativa no recibe permisos municipales automáticamente. Las cuentas se registran por el mecanismo existente y un administrador autorizado les asigna los roles necesarios; el panel no contiene una contraseña ni una elevación inicial de permisos. Los permisos recién concedidos requieren iniciar sesión nuevamente para renovar las credenciales.

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

Abrir `http://localhost:5090`. Es necesario que la API elegida esté recompilada, en ejecución y tenga instaladas las migraciones de verificación y operaciones descritas en la guía del proyecto Android. El endpoint `GET /api/alcaldia/panel` se agregó a ambas APIs sin cambiar el esquema SQL. El panel por sí solo no crea cuentas institucionales ni eleva roles.

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
