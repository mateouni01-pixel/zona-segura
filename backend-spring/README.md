# Zona Segura - API principal Spring Boot

Backend principal del proyecto, conectado a la base SQL Server `ZonaSegura`. El módulo C# de la otra materia replica el mismo contrato HTTP de autenticación, con usuarios y base independientes.

## Ejecutar

Aplicar el esquema `zona_segura.sql` del proyecto Android en una instalación nueva. Configurar la conexión de SQL Server en `src/main/resources/application.properties` o mediante `SPRING_DATASOURCE_URL`. Para autenticación de Windows se necesita la DLL JDBC disponible en `auth`.

Desde esta carpeta, ejecutar `iniciar-backend.ps1`. Usa el puerto 8080 y genera una clave JWT de desarrollo si `ZONA_JWT_SECRET` no está definida. Si cambia la clave al reiniciar, las sesiones anteriores dejan de ser válidas.

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

En Android, **Crear reporte** abre el formulario y **Mis reportes** abre el historial. Votos, recursos, fotografía y recuperación de contraseña continúan pendientes en este backend. La modificación y eliminación de reportes siguen pendientes en Spring; C# conserva su CRUD completo con control de propietario.

## Mapa de riesgo

Aplicar `sql/sprint2-mapa.sql` para agregar el índice del mapa en una base existente. No cambia reportes ni estados. El esquema Android nuevo ya incluye el índice.

`GET /api/mapa/resumen` requiere una cuenta activa (ciudadano, personal municipal o administrador). Acepta `sur`, `oeste`, `norte`, `este` juntos, `dias` de 1 a 90 (7 por defecto) y `tipoIncidenteId` positivo opcional. El área no puede superar 2 grados por lado. Sin límites se usa el área inicial de Santa Cruz. `GET /api/mapa/zonas` devuelve solo el arreglo de zonas para conservar el contrato previo.

SQL agrupa reportes confirmados (VALIDADO, EN_ATENCION, ATENDIDO) por celdas de 0,005 grados, con filtro de fecha de registro UTC y tipo. Excluye pendientes, rechazados y fechas futuras. Consulta celdas completas para mantener estable su conteo cerca de los bordes. Regla aprobada: 1–2 BAJO, 3–4 MEDIO, 5 o más ALTO. Una celda vacía no se genera.

El resumen incluye `zonas`, `totalReportes`, `dias`, `desdeUtc`, `actualizadoUtc`, `tamanoCeldaGrados`, `umbrales`. Cada zona incluye identificador de celda, nombre genérico, nivel, cantidad y polígono cerrado en orden longitud/latitud. Nunca devuelve la identidad, descripción, ID de reporte o ubicación exacta de un incidente.

La implementación C# usa el mismo contrato y regla. El mapa expresa concentración de incidentes confirmados; no es una predicción ni una garantía de seguridad.
