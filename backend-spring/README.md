# Zona Segura - API principal Spring Boot

## Eliminación definitiva de cuentas (25/11/2026)

`DELETE /api/admin/usuarios/{id}` recibe `motivo` (10–2000 caracteres) y `confirmacionId` (debe coincidir con la ruta). Solo un administrador vigente puede ejecutarlo. Devuelve 204 tras borrar físicamente la cuenta, roles, pertenencia a unidad y especializaciones; las credenciales desaparecen y los JWT anteriores pierden acceso por `CuentaVigenteFilter`. No se puede eliminar la cuenta propia ni el último administrador activo.

Se bloquea con 409 la eliminación si existen reportes, votos, reputación histórica, verificaciones, órdenes, lecturas de avisos, moderación, sanciones o acciones administrativas realizadas por la cuenta. Los registros de auditoría que la tienen como objetivo se conservan con su ID original. La operación y su motivo se guardan como `USUARIO_ELIMINADO` en la misma transacción; ante error se revierte todo. Se mantienen las FK de actividad y la del responsable de auditoría.

Las bases existentes necesitan `sql/sprint3-eliminacion-cuentas.sql` después de administración y con respaldo previo. Retira únicamente la FK del objetivo histórico de auditoría, conservando sus IDs y filas. Aplicado a `MateoMS/ZonaSegura` tras BACKUP COPY_ONLY con CHECKSUM y RESTORE VERIFYONLY; se conservaron 5 usuarios y 1 auditoría. Respaldo: `C:\Program Files\Microsoft SQL Server\MSSQL17.MSSQLSERVER\MSSQL\Backup\ZonaSegura_antes_eliminacion_cuentas_20261125_1930.bak`. El esquema inicial y el script de administración ya usan el ID histórico sin FK.

La interfaz está en **Usuarios y permisos > Eliminar** y en **Administrar cuenta > Eliminar cuenta**. Requiere motivo y escribir el ID antes del POST MVC, que envía el DELETE a esta misma API compartida por web y Android. No se implementó una pantalla nueva de eliminación en Android ni en la API C# alternativa.

Las pruebas de eliminación incluyen servicios, seguridad HTTP con JWT y SQL Server con cuentas temporales para los cuatro roles y rollback. Ver [verificación](../pruebas/panel-admin/VERIFICACION-ELIMINACION-2026-11-25.md). No se eliminó ninguna cuenta real.

## Corrección del bloqueo administrativo persistente (25/11/2026)

El mensaje **Otra operación administrativa está en curso. Reintenta.** también se producía sin concurrencia: el controlador JDBC usa transacciones implícitas y `sp_getapplock` era la primera consulta de la operación. SQL Server devolvía `-999` porque aún no había una transacción SQL activa. Se reprodujo con el mismo controlador y base, sin modificar datos; al consultar el perfil antes del bloqueo, el resultado fue `0` y `@@TRANCOUNT=1`.

`mutation` ahora consulta primero el perfil administrativo, adquiere el bloqueo global, bloquea al responsable y comprueba nuevamente sus permisos. Se conserva ese orden para evitar cambios con permisos revocados mientras se esperaba. Las cinco operaciones comparten la corrección: crear/editar usuarios, rechazar reportes, sancionar y levantar sanciones. Los resultados `-1/-2/-3` conservan la respuesta 409; `-999` o un resultado ausente se registran como error técnico y devuelven 500, sin atribuirlos a otra operación.

Validación: **40 pruebas aprobadas, sin fallos ni omisiones** (25 de administración, 2 de integración JDBC/SQL Server, 8 de verificación y 5 de seguridad HTTP), más `bootJar` correcto. Las pruebas SQL reproducen el error original, recorren las cinco entradas con datos deliberadamente inválidos antes de escribir, comprueban el bloqueo dentro de una sola transacción y su liberación tras rollback. No alteran cuentas, roles ni auditoría. No sustituyen la prueba del guardado solicitado por el usuario desde la web.

Las pruebas SQL son optativas. Desde esta carpeta, con SQL Server y la DLL JDBC local disponibles:

```powershell
$env:ZONA_SQLSERVER_CHECKS='true'
$env:JAVA_TOOL_OPTIONS='-Djava.library.path="'+(Resolve-Path .\auth).Path+'"'
.\gradlew.bat test --tests '*AdministracionServiceTests' --tests '*AdministracionSqlServerTests' --tests '*VerificacionPermisosTests' --tests '*VerificacionSecurityTests' bootJar --offline
```

Usan `MateoMS/ZonaSegura` por defecto; `ZONA_SQLSERVER_JDBC` permite seleccionar otra conexión con un administrador activo existente. Sin `ZONA_SQLSERVER_CHECKS=true`, las pruebas SQL se omiten. No requiere migraciones. Reiniciar la API para cargar la corrección; si el lanzador genera otra clave JWT, volver a iniciar sesión en web y móvil.

Referencia: Microsoft documenta que los bloqueos con propietario `Transaction` requieren una transacción activa, y que `-999` indica un error de llamada: [sp_getapplock](https://learn.microsoft.com/en-us/sql/relational-databases/system-stored-procedures/sp-getapplock-transact-sql). Las consultas escalares sin tabla no inician transacciones implícitas: [SET IMPLICIT_TRANSACTIONS](https://learn.microsoft.com/en-us/sql/t-sql/statements/set-implicit-transactions-transact-sql).

## Validación manual por Administrador/moderador (25/11/2026)

La revisión manual se asigna a `ADMINISTRADOR`; `PERSONAL_ALCALDIA` no puede consultar la bandeja de revisión ni validar/rechazar manualmente. Se conservan las tres confirmaciones ciudadanas como vía automática. Rutas vigentes: `GET /api/admin/reportes-verificacion` y `POST /api/admin/reportes/{id}/verificacion`, con `confirma` y motivo de 10 a 2000 caracteres. Los alias antiguos bajo `/api/alcaldia` también requieren administrador, incluso si un token anterior todavía contiene ese rol y ya fue retirado en la base.

Aplicar `sql/sprint3-validacion-administrativa.sql` sobre bases existentes, después de respaldarlas. Conserva `id_personal` y todos los datos históricos; cambia únicamente su FK de `PersonalAlcaldia` a `Usuarios`. No concede permisos ni crea cuentas. Se aplicó localmente a `MateoMS/ZonaSegura` con respaldo verificado; permanecen 3 reportes y 3 revisiones. El esquema inicial ya incluye la relación corregida. La revisión queda además en `AuditoriaAdministrativa`; se mantienen las restricciones de reporte propio y decisión única.

Verificación sin SQL: `./gradlew.bat test --offline --tests '*AdministracionServiceTests' --tests '*VerificacionPermisosTests' --tests '*VerificacionSecurityTests'`. La última suite usa los filtros HTTP reales y JWT de prueba para comprobar rutas nuevas y antiguas, denegación municipal y revocación del permiso. Las suites de servicio comprueban que un administrador sin perfil municipal pueda revisar y que tres ciudadanos sigan validando automáticamente.

## Creación administrativa de cuentas (25/11/2026)

`POST /api/admin/usuarios` recibe `nombre`, `correo`, `password`, `roles` y `motivo`. Solo un administrador vigente puede crear cuentas, incluyendo otros administradores. Los roles admitidos son `CIUDADANO`, `PERSONAL_ALCALDIA`, `ADMINISTRADOR` y `UNIDAD_OPERATIVA`, combinables sin duplicados. La cuenta se crea activa; el correo se normaliza y debe ser único; contraseña de al menos 8 caracteres y como máximo 72 bytes UTF-8; motivo de 10 a 2000 caracteres.

Devuelve `201` con la cuenta y sus roles, sin contraseña ni hash. Valida errores con `400`, correo duplicado con `409` y conserva la protección de autenticación/autorización existente. La creación cifra con BCrypt y guarda cuenta, especializaciones, roles y auditoría `USUARIO_CREADO` en una transacción. Usa las tablas existentes; no necesita otra migración. El registro público sigue asignando exclusivamente `CIUDADANO`. El responsable se obtiene del JWT y de su perfil vigente.

El formulario está en el panel MVC, `/Admin/CrearUsuario`. Reiniciar este backend actualizado y el panel antes de usarlo. Pruebas unitarias sin SQL: `./gradlew.bat test --offline --tests '*AdministracionServiceTests'`. Las comprobaciones HTTP del formulario con API simulada están en `../pruebas/panel-admin`.

## Estado vigente: administración, territorio y HTTPS

Implementados directorio y edición de usuarios/roles, moderación con motivo, sanciones de acceso, levantamiento explícito y auditoría. Solo un administrador vigente puede ejecutarlos; se protege al propio y al último administrador y no se sobrescriben verificaciones institucionales. Una sanción exige reporte rechazado, autor distinto y ausencia de sanción anterior para ese hecho; no altera reputación. El levantamiento conserva el historial y restaura el estado previo de acceso. Los cambios administrativos se serializan mediante bloqueo transaccional.

`CuentaVigenteFilter` comprueba cuenta, sanción y roles SQL antes de autorizar cada JWT. El directorio admite búsqueda por ID/nombre/correo de hasta 100 caracteres y limita a 200 coincidencias; la auditoría devuelve hasta 500 registros recientes y admite filtro por usuario. El panel agrega viewport WGS84 e indicadores de total, pendientes, validados, en atención, atendidos, rechazados y zonas altas. Tabla e indicadores se recortan al área exacta; el color considera celdas completas para evitar cambios por recortar un borde.

Nuevas rutas: `GET /api/admin/perfil`, `GET /api/admin/usuarios`, `GET/PUT /api/admin/usuarios/{id}`, `GET /api/moderacion/reportes`, `POST /api/reportes/{id}/rechazo`, `POST /api/reportes/{id}/sancion`, `POST /api/admin/usuarios/{id}/levantar-sancion`, `GET /api/admin/auditoria` y `GET /api/territorio/cobertura`. Escrituras administrativas exigen `motivo` de 10–2000 caracteres; el cambio de usuario recibe `roles` y `activo`. El API obtiene responsables de la sesión. La cobertura admite nuevas coordenadas solo dentro de los doce polígonos urbanos distribuidos en `distritos-urbanos.json`, sin borrar datos históricos.

En este equipo ya se aplicaron `sprint2-operaciones.sql` y `sprint3-administracion.sql` a ZonaSegura, con respaldo previo. Para otra base existente aplicarlos en ese orden, después de verificación; no ejecutar `zona_segura.sql` sobre datos existentes. Los scripts incluyen las opciones SET requeridas por los índices filtrados y se invocan con `sqlcmd -I -b -f 65001`.

HTTPS: desde `04_Codigo`, ejecutar `configurar-https-local.ps1` una vez y luego `iniciar-https-local.ps1 -Componente Spring`. El perfil `https` escucha en `https://localhost:8443`, loopback, TLS 1.2/1.3, clave PKCS12 por entorno. No necesita modificar el almacén de confianza de Windows. El lanzador HTTP anterior permanece para desarrollo; la configuración HTTPS es la utilizada por el APK preparado. El estado público identifica `2.4-administracion`.

Los apartados siguientes conservan detalles de entregas anteriores. Las menciones a migraciones todavía sin aplicar describen esas entregas, no este estado vigente. Compilación y preparación de bases no certifican pruebas funcionales ni los tiempos de los RNF.

## Sincronización y operaciones incorporadas

Se implementaron reportes con Idempotency-Key, unidades y recursos, asignación transaccional, órdenes, bandeja operativa, cierre y avisos de lectura. Aplicar `sql/sprint2-operaciones.sql` en una base existente antes de ejecutar esta versión. El esquema inicial nuevo ya lo incorpora. La nueva migración no se aplicó ni se probó en SQL por indicación del usuario: solo se compiló el backend con `bootJar -x test --offline`. Los resultados HTTP anteriores no certifican estas funciones. El contrato y la instalación están en `../IMPLEMENTACION-PUNTOS-1-A-6.md`.

Backend principal del proyecto, conectado a la base SQL Server `ZonaSegura`. El módulo C# de la otra materia replica el mismo contrato HTTP de autenticación, con usuarios y base independientes.

## Ejecutar

Aplicar el esquema `zona_segura.sql` del proyecto Android en una instalación nueva. Configurar la conexión de SQL Server en `src/main/resources/application.properties` o mediante `SPRING_DATASOURCE_URL`. Para autenticación de Windows se necesita la DLL JDBC disponible en `auth`.

Desde esta carpeta, ejecutar `iniciar-backend.ps1`. Usa el puerto 8080 y genera una clave JWT de desarrollo si `ZONA_JWT_SECRET` no está definida. Si cambia la clave al reiniciar, las sesiones anteriores dejan de ser válidas.

Para cargar cambios cuando ya está encendido: `iniciar-backend.cmd -Actualizar -SinAdb`. El reinicio comprueba que el proceso Java del puerto 8080 pertenece a esta carpeta antes de detenerlo; no afecta otros servidores. `-SinAdb` evita las acciones sobre dispositivos. Sin `-Actualizar`, un servidor antiguo que no informa soporte del panel municipal produce un aviso en vez de darse por actualizado. `/api/conexion` devuelve también `version: "2.3-panel"` y `panelMunicipal: true`.

## Contrato de autenticación

| Método | Ruta | Comportamiento |
| --- | --- | --- |
| POST | `/api/auth/register` | `nombre`, `correo`, `password`; crea Usuario, rol CIUDADANO y Ciudadano en una transacción; responde 201 con `idUsuario` |
| POST | `/api/auth/login` | `correo`, `password`; responde con token Bearer de una hora, identidad, roles y `ciudadanoId` |
| GET | `/api/auth/me` | Requiere Bearer; consulta nombre, roles, estado activo y ciudadano en SQL Server |

Nombre: hasta 150 caracteres. Correo: hasta 254, normalizado a minúsculas y sin espacios exteriores. Contraseña: al menos 8 caracteres y hasta 72 bytes UTF-8; hash BCrypt. Entradas inválidas: 400. Correo duplicado: 409. Credenciales inválidas, cuenta desactivada/eliminada o token inválido: 401.

Un ciudadano usa clave primaria compartida con su usuario en esta base. `/me` comprueba que esa extensión exista antes de devolver una sesión ciudadana; no copia identificadores de otra base. Las cuentas sin perfil ciudadano devuelven `ciudadanoId: 0`.

El JWT exige emisor `zona-segura-api` y audiencia `zona-segura-android`. Los tokens de C# se rechazan aunque ambas APIs se prueben con la misma clave. La sesión devuelve los roles actuales de la base en vez de limitarse a repetir los guardados en el token.

En Android, `backend_base_url` apunta por defecto a este servidor. Registro usa `/register`, no `/registro`.

## Crear y consultar reportes

Para una base existente, ejecutar `sql/sprint2-reportes.sql` sobre `ZonaSegura`. Agrega el estado activo de los tipos y, si el catálogo está vacío, dos categorías iniciales. En una instalación nueva aplicar antes el esquema completo `zona_segura.sql`.

Todas estas rutas requieren Bearer y una cuenta activa:

| Método | Ruta | Resultado |
| --- | --- | --- |
| GET | `/api/tipos-incidente` | Catálogo con `id`, `nombre`, `activo` |
| GET | `/api/tipos-incidente/{id}` | Categoría; 404 si no existe |
| POST | `/api/reportes` | Ciudadano; guarda ubicación y reporte en una transacción; 201 y Location |
| GET | `/api/reportes` | Solo reportes del ciudadano autenticado |
| GET | `/api/reportes/{id}` | Reporte propio; 404 si no existe o pertenece a otra cuenta |

El POST recibe `descripcion` (10 a 2000 caracteres recortados), `tipoIncidenteId`, `latitud` y `longitud`. El tipo debe existir y estar activo; latitud debe estar entre -90 y 90, longitud entre -180 y 180. El servidor toma el autor de la sesión y fija `PENDIENTE_VALIDACION`; si se envía un `ciudadanoId` distinto al de la sesión, devuelve 400. Devuelve los datos guardados y su fecha en UTC.

En Android, **Crear reporte** abre el formulario y **Mis reportes** abre el historial. Votos y verificación municipal están implementados según la versión 2.2. Recursos, moderación general, fotografía y recuperación de contraseña continúan pendientes en este backend. La modificación y eliminación de reportes siguen pendientes en Spring; C# conserva su CRUD con control de propietario y protege de edición o borrado a los reportes que ya tienen votos o verificaciones.

## Validación comunitaria e institucional versión 2.2

Aplicar `sql/sprint2-verificacion.sql` sobre una base existente. Crea únicamente `VerificacionesInstitucionales`; votos usan `ValidacionesComunitarias`, ya incluida en el esquema inicial. El script es aditivo e idempotente y no cambia estados ni votos históricos.

`GET /api/reportes/cercanos?lat=...&lng=...&radio=500` devuelve pendientes de otros autores dentro de 500 m, con contador de positivos, negativos y `yaVoto`, sin identidad ni coordenadas exactas. `POST /api/reportes/{id}/votos` recibe `confirma`, `latitud` y `longitud`. El servidor valida cuenta activa, autor distinto, radio y voto único. Tres positivos de ciudadanos distintos validan el reporte; antes de tres sigue pendiente. Negativos no rechazan automáticamente. El índice informativo es positivos/total y no sustituye el contador. La distancia depende del GPS recibido y no verifica por sí sola la autenticidad física de la posición.

`GET /api/alcaldia/reportes-verificacion` exige rol municipal vigente y registro `PersonalAlcaldia`, y permite revisar pendientes o validados sin comprobación institucional previa. `POST /api/alcaldia/reportes/{id}/verificacion` recibe `confirma` y `motivo` de 10–2000 caracteres. Registra responsable desde la sesión, resultado y fecha UTC; confirma como VALIDADO o rechaza como RECHAZADO. No exige votos ni cercanía; prohíbe reportes propios y sobrescribir decisiones. No asigna recursos ni crea órdenes. La auditoría no se publica en el mapa.

Votos y decisiones bloquean el reporte con `UPDLOCK,HOLDLOCK` dentro de una transacción. Un duplicado o estado cerrado devuelve 409; autor o radio indebido, 403; datos inválidos, 400; inexistente, 404. Se comprueba el rol en SQL además del JWT. Las pruebas del proyecto cubren solicitudes simultáneas y revocación del permiso. No se crean funcionarios ni se elevan roles automáticamente.

## Mapa de riesgo

Aplicar `sql/sprint2-mapa.sql` para agregar el índice del mapa en una base existente. No cambia reportes ni estados. El esquema Android nuevo ya incluye el índice.

`GET /api/mapa/resumen` requiere una cuenta activa (ciudadano, personal municipal o administrador). Acepta `sur`, `oeste`, `norte`, `este` juntos, `dias` de 1 a 90 (7 por defecto) y `tipoIncidenteId` positivo opcional. El área no puede superar 2 grados por lado. Sin límites se usa el área inicial de Santa Cruz. `GET /api/mapa/zonas` devuelve solo el arreglo de zonas para conservar el contrato previo.

SQL agrupa reportes confirmados (VALIDADO, EN_ATENCION, ATENDIDO) por celdas de 0,005 grados, con filtro de fecha de registro UTC y tipo. Excluye pendientes, rechazados y fechas futuras. Consulta celdas completas para mantener estable su conteo cerca de los bordes. Regla aprobada: 1–2 BAJO, 3–4 MEDIO, 5 o más ALTO. Una celda vacía no se genera.

El resumen incluye `zonas`, `totalReportes`, `dias`, `desdeUtc`, `actualizadoUtc`, `tamanoCeldaGrados`, `umbrales`. Cada zona incluye identificador de celda, nombre genérico, nivel, cantidad y polígono cerrado en orden longitud/latitud. Nunca devuelve la identidad, descripción, ID de reporte o ubicación exacta de un incidente.

La implementación C# usa el mismo contrato y regla. El mapa expresa concentración de incidentes confirmados; no es una predicción ni una garantía de seguridad.
