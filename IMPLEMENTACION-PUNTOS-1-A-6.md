# Implementación local de los puntos 1 al 6

**Actualización posterior del 20/11/2026:** las migraciones de operaciones y administración ya se aplicaron en las dos bases locales con respaldo previo durante el bloque de implementación y ajustes. Sus detalles vigentes están al inicio de los README de Android, Spring, C# y panel. Las frases «no se aplicó» del cuerpo siguiente describen la entrega inicial de estos seis puntos. La aceptación funcional continúa pendiente; no se ejecutaron emuladores ni acciones Git.

Fecha: 20 de noviembre de 2026. Android y las APIs Spring Boot y C# incorporan almacenamiento local, sincronización y gestión de operaciones. Por indicación del usuario, la validación de este cambio se limita a compilación. No se ejecutaron APIs, consultas HTTP, pruebas funcionales, pruebas unitarias, dispositivos ni emuladores. No se realizaron commits ni publicaciones en Git.

## Funciones incorporadas

1. **Reportes sin conexión.** SQLite privado guarda el reporte antes de enviarlo. Cada fila conserva servidor, cuenta ciudadana, tipo, descripción, coordenadas, fecha local e identificador UUID. Se mantienen catálogo e historial descargados para consultarlos sin conexión. Hace falta iniciar sesión y descargar el catálogo al menos una vez antes de crear reportes offline.
2. **Sincronización.** WorkManager programa un trabajo único por reporte con conexión de red y reintento exponencial desde 30 segundos. `Idempotency-Key` permite repetir el envío sin crear otro reporte. Ambas APIs serializan la creación por autor y conservan una clave única autor/UUID. Los estados locales son PENDIENTE, ESPERA_SESION, ERROR y SINCRONIZADO. Una sesión vencida requiere volver a entrar; los rechazos definitivos quedan visibles y requieren un reintento manual. Cambiar de cuenta o servidor no envía pendientes ajenos. Cerrar sesión conserva la cola privada para cuando vuelva su propietario.
3. **Recursos.** Alcaldía registra unidades, asocia cuentas operativas ya autorizadas, registra patrullas y cuadrillas y modifica su disponibilidad o activación. No se puede modificar un recurso con una orden activa. Los recursos históricos sin unidad deben configurarse antes de asignarlos.
4. **Órdenes.** Un reporte VALIDADO recibe una orden con tipo, recurso e instrucciones de 10 a 2000 caracteres. Patrullaje y derivación usan PATRULLA; mantenimiento usa CUADRILLA. La reserva, la orden, el cambio a EN_ATENCION y el aviso se guardan en una transacción. Una solicitud UUID repetida devuelve la misma orden; la misma clave con otros datos se rechaza. Hay como máximo una orden ASIGNADA por reporte y por recurso.
5. **Bandeja operativa.** Las cuentas con rol UNIDAD_OPERATIVA solo ven órdenes de su unidad activa. Consultan ubicación e instrucciones, registran un resultado de 10 a 2000 caracteres y cierran la atención. El servidor conserva responsable y fecha, marca ATENDIDO y libera el recurso en una transacción. Repetir un cierre con el mismo resultado conserva el cierre anterior. Alcaldía no ejecuta el cierre operativo.
6. **Avisos de órdenes.** Crear la orden genera un aviso duradero en la bandeja de la unidad destinataria. Se muestra EN_BANDEJA o LEIDA. La unidad puede marcarlo como leído y el municipio consultar ese estado. La pantalla actualiza cada 30 segundos mientras está visible. Es comunicación dentro de la aplicación, no SMS, correo ni notificación push externa.

La coordenada del reporte es la capturada al guardarlo, incluso cuando se envía después. La fecha central de registro corresponde a la recepción en la API. El punto azul de ubicación personal continúa siendo independiente.

## Preparación de la base para usar esta versión

Estas instrucciones están preparadas y **no se ejecutaron** en esta actualización, conforme al límite de compilación solicitado.

**Spring Boot:** sobre la base existente, aplicar en orden `backend-spring/sql/sprint2-verificacion.sql` si aún no estaba instalada y `backend-spring/sql/sprint2-operaciones.sql`, ejecutando todos sus lotes GO. No volver a ejecutar el esquema inicial sobre una base existente. Para una instalación nueva, `zona_segura.sql` ya incorpora el esquema de operaciones.

El script incorpora claves de sincronización, unidades, miembros, ampliación de recursos y órdenes y la tabla de avisos. Conserva las tablas y registros anteriores, permite órdenes sin zona precalculada y agrega la relación directa con el reporte. No crea cuentas ni concede permisos a usuarios existentes; únicamente incorpora la definición de rol UNIDAD_OPERATIVA.

**C#:** aplicar la migración incluida `AgregarSincronizacionYOperaciones` con `dotnet ef database update`, después de configurar la conexión correcta. La migración, su Designer y el snapshot están incluidos. La especialización municipal continúa representada mediante el rol de Usuario en esta API.

Un responsable debe configurar previamente las cuentas institucionales. En Spring se asigna el rol UNIDAD_OPERATIVA mediante UsuarioRoles; en C# mediante Usuarios.Rol. No se aceptan roles elevados desde el registro público. Luego Alcaldía puede asociar el ID del usuario autorizado a una unidad desde la pantalla Unidades.

Tras instalar el esquema, iniciar la API recompilada e instalar el APK nuevo. El servidor antiguo no ofrece la garantía de idempotencia ni los nuevos endpoints. Usuarios, sesiones y claves de ambas bases continúan separados.

## Rutas comunes a las dos APIs

| Método y ruta | Acceso y operación |
| --- | --- |
| POST /api/reportes | Ciudadano. Acepta Idempotency-Key UUID para sincronización segura. |
| GET /api/unidades | Alcaldía. Lista unidades. |
| POST /api/unidades | Alcaldía. Crea unidad con nombre. |
| POST /api/unidades/{id}/miembros | Alcaldía. Asocia usuarioId que ya posee rol operativo. |
| GET /api/recursos | Alcaldía. Lista recursos. |
| POST /api/recursos | Alcaldía. nombre, tipo, unidadId, disponible y activo. |
| PUT /api/recursos/{id} | Alcaldía. Actualiza un recurso sin orden activa. |
| GET /api/intervenciones | Alcaldía. Reportes gestionables y recursos disponibles. |
| POST /api/intervenciones | Alcaldía. reporteId, recursoId, tipoOrden, instrucciones y solicitudId UUID. |
| GET /api/intervenciones/ordenes | Alcaldía. Seguimiento de órdenes y avisos. |
| GET /api/operativa/ordenes | Unidad operativa. Solo su propia bandeja. |
| POST /api/operativa/ordenes/{id}/atender | Unidad operativa. resultado obligatorio; el ID corresponde a la orden. |
| POST /api/operativa/avisos/{id}/leer | Unidad operativa destinataria. Confirma lectura del aviso. |

El servidor obtiene autor, funcionario y operador de la sesión y comprueba cuenta activa, rol vigente y unidad. No acepta esos responsables del formulario. Rechaza recursos ocupados y estados incompatibles con 409, referencias o campos inválidos con 400 y accesos indebidos con 403. La consulta y cierre de órdenes ajenas devuelve 404.

## Compilación permitida

Android: `gradlew.bat :app:assembleDebug` (WorkManager Java 2.10.1).

Spring Boot: `gradlew.bat bootJar -x test --offline`.

C#: `dotnet build --no-restore`.

Resultado de la compilación final del 20/11/2026: Android `BUILD SUCCESSFUL`; Spring Boot `BUILD SUCCESSFUL`; C# compilación correcta con cero advertencias y cero errores. Android conserva un aviso de API obsoleta y Gradle advierte sobre compatibilidad futura de sus plugins. El APK está en `app/build/outputs/apk/debug/app-debug.apk` y el JAR en `backend-spring/build/libs/zona-segura-api-0.0.1-SNAPSHOT.jar`.

Las compilaciones verifican fuentes y empaquetado. No demuestran funcionamiento del GPS, ejecución de WorkManager, SQL de actualización, permisos en ejecución ni transacciones concurrentes. Los resultados HTTP previos de la versión 2.2 no certifican este cambio. El Excel de planificación no se modificó.
