# Zona Segura (Android)

## Colores por interfaz móvil — 25/11/2026

Ciudadano conserva su color actual; Personal de Alcaldía usa azul y Administración morado. El tema acompaña el panel y sus pantallas internas, con tarjetas y botones del selector del mismo color. Se aplican temas derivados de Material, sin cambiar permisos ni los colores de riesgo del mapa. Unidad Operativa conserva la paleta base. Instalar el APK actualizado para ver los cambios.

## Elegir interfaz en web y móvil — 25/11/2026

El login abre un selector con los roles disponibles: Ciudadano, Personal de Alcaldía, Administrador y Unidad Operativa. Se conservan todos los roles de `/api/auth/me`; el usuario elige y puede **Cambiar interfaz** sin cerrar sesión. El catálogo contempla las 15 combinaciones sin crear pantallas por combinación, mediante Strategy y un registro de interfaces. Android mantiene el modo ciudadano sin conexión; las interfaces institucionales requieren comprobar permisos en línea. La web incluye ahora también reportes y mapa ciudadano y la bandeja operativa. Ver [implementación y uso](docs/INTERFACES-POR-ROL.md). Instalar el APK actualizado y recompilar/reiniciar MVC.

## Web con paneles según el rol — 25/11/2026

La web institucional abre `/Admin/Inicio` para Administración y `/Panel` para Personal de Alcaldía, con interfaces, menús y encabezados propios. Las cuentas con ambos roles pueden cambiar de espacio desde el menú. La validación manual sigue siendo administrativa; Alcaldía consulta incidentes y coordina recursos y órdenes. La entrada `/` reconoce la sesión y el acceso sin permisos muestra una pantalla 403 con regreso al panel. Recompilar y reiniciar MVC. Ambas copias del panel compilan y pasaron 48 comprobaciones HTTP con API simulada; no se probaron cuentas reales ni la presentación en navegador. Ver [panel](ZonaSegura.Panel/README.md) y [verificación](pruebas/panel-admin/VERIFICACION-PANELES-2026-11-25.md).

## Administrador/moderador valida manualmente — 25/11/2026

La revisión manual de reportes corresponde al rol `ADMINISTRADOR`. Se trasladó **Verificar reportes** del menú de Personal de Alcaldía al de Administración, usando las rutas `/api/admin/reportes-verificacion` y `/api/admin/reportes/{id}/verificacion`. Las APIs también rechazan llamadas directas de personal municipal, incluidas las rutas antiguas. Personal de Alcaldía conserva mapa, recursos, unidades e intervenciones. Las tres confirmaciones ciudadanas siguen validando automáticamente, según la decisión del usuario.

Recompilar Android y reiniciar el backend actualizado. En Spring, las bases existentes necesitan `backend-spring/sql/sprint3-validacion-administrativa.sql`; se aplicó a `MateoMS/ZonaSegura` después de un respaldo verificado, conservando los reportes y revisiones. El panel web ofrece **Administración > Validar reportes**, además de creación de usuarios, incluidos otros administradores.

## Captura de ubicación en reportes — 25/11/2026

El formulario consulta GPS y ubicación por red, como el mapa. Reutiliza posiciones de esas fuentes de hasta 30 segundos de antigüedad y escucha nuevas posiciones durante un máximo de 45 segundos; una lectura imprecisa no termina la búsqueda. Se mantienen el permiso de ubicación precisa, el límite de 100 m, la vigencia de dos minutos al guardar y la cobertura de los distritos 1–12. La búsqueda se cancela al salir del formulario. El marcador del mapa también admite ubicaciones aproximadas, por lo que verlo no garantiza que cumpla los requisitos del reporte. Validación local: compilación y pruebas unitarias existentes; pendiente comprobar el GPS físico en el teléfono.

## Prueba por Wi-Fi — 24/11/2026

La compilación debug usa por defecto `http://192.168.0.5:8080`, la IP Wi-Fi actual de la PC. Android debug permite HTTP para esa IP. Recompilar e instalar el APK actualizado; abrir `backend-spring/iniciar-wifi.cmd` para iniciar la API sin ADB, con SQL Server activo. Comprobar desde el celular `http://192.168.0.5:8080/api/conexion` antes de iniciar sesión. Ambos equipos deben estar en la misma red y el firewall debe permitir Java en el puerto 8080. Si cambia la IP, actualizar la URL debug y el dominio permitido en `app/src/debug/res/xml/debug_network_security_config.xml`, y volver a compilar. Se conserva la opción `-PzonaApiUrl` para seleccionar otra URL; si usa HTTP, también requiere un dominio permitido en debug. La versión release sigue exigiendo HTTPS.

## Estructura incluida en Git

Este repositorio incluye Android (`app`), la API Spring (`backend-spring`), el panel MVC (`ZonaSegura.Panel`), certificados públicos (`Certificados`), lanzadores HTTPS y documentos de verificación (`docs/verificacion-v2.2`). Las claves privadas, bases de datos y respaldos no se incluyen. El panel incluido es la copia preparada para esta publicación del proyecto que se desarrolló junto a Android en el espacio de trabajo.

Desde la raíz del repositorio: `configurar-https-local.ps1` prepara los certificados privados de cada equipo; `iniciar-https-local.ps1 -Componente Spring` inicia la API y `iniciar-https-local.ps1 -Componente Panel` inicia MVC. Regenerar la configuración local antes de compilar Android en otro equipo para usar su propia CA. Para compilar MVC: `dotnet build ZonaSegura.Panel/ZonaSegura.Panel.csproj`. La API C# independiente y sus migraciones permanecen en el repositorio `Zona-Segura-Api`, rama `sprint-2/Mateo`; su opción de lanzamiento requiere conservar la estructura de carpetas de ese proyecto junto al repositorio principal.

## Actualización de implementación y ajustes — 20/11/2026

- Administración de cuentas, roles, moderación, sanciones y levantamiento auditados en Spring y C#, con interfaz completa en el panel MVC. Android conserva su acceso a moderación y exige motivo. Suspender una cuenta bloquea su sesión; esta entrega no modifica puntajes de reputación.
- El formulario captura GPS automáticamente. Reutiliza una posición de hasta 30 segundos cuando es precisa, espera como máximo 20 segundos y ofrece reintento. Antes de guardar exige posición de hasta 2 minutos y precisión reportada de hasta 100 m. No certifica la autenticidad física del GPS ni garantiza un tiempo de captura de 2 segundos.
- Cobertura local y del servidor mediante polígonos MultiPolygon de los distritos urbanos 1–12. Incluye bordes y respeta huecos; rechaza nuevas ubicaciones fuera del alcance. Los reportes históricos se conservan y los reintentos idempotentes existentes siguen devolviendo su reporte original. El mapa muestra contornos distritales.
- Fuente de geometría: capa de planificación municipal publicada en [YoViajo/geodatos](https://github.com/YoViajo/geodatos/blob/master/gsc/scz_distritos_municipales_planif.geojson), consultada el 20/11/2026 y filtrada por identificadores 1–12, sin simplificación adicional. Es una copia publicada de la capa; no se presenta como delimitación catastral certificada. El archivo incluye procedencia y se distribuye igual en Android, Spring y C#.
- Migraciones de operaciones y administración aplicadas en `MateoMS`, bases `ZonaSegura` y `ZonaSeguraEntregaApi`, después de respaldarlas en `06_Archivos_de_trabajo/backups/cierre-20261120-195716`. Se conservaron las 3 cuentas/3 reportes de Spring y las 2 cuentas/2 reportes de C#. No se crearon cuentas ni se concedieron permisos a usuarios existentes.

### HTTPS local y compilación

Desde `04_Codigo`, `configurar-https-local.ps1` prepara una CA de desarrollo y un certificado servidor para localhost/127.0.0.1. Las claves y contraseña quedan bajo ACL privada en `.https-local`, excluida de Git; los certificados públicos se copian a `Certificados` y Android debug. No instala confianza global en Windows. La versión release bloquea HTTP y usa certificados del sistema; la confianza en la CA local se limita a debug y a localhost/127.0.0.1.

Desde este proyecto, el APK HTTPS se compila sin ejecutar:

```powershell
.\gradlew.bat :app:assembleDebug --offline -PzonaApiUrl=https://localhost:8443
```

Para usarlo posteriormente con un teléfono USB: iniciar Spring mediante `04_Codigo/iniciar-https-local.ps1 -Componente Spring`, configurar `adb reverse tcp:8443 tcp:8443` en el equipo y volver a iniciar sesión. No se ejecutó ADB ni se instaló el APK durante esta entrega. La compilación debug sin propiedad `zonaApiUrl` conserva el HTTP de desarrollo en 127.0.0.1:8080; no mezclar sesiones ni colas entre URLs.

La validación de código de este bloque es compilación de Android, Spring, API C# y MVC, revisión estática de permisos y sintaxis JavaScript/PowerShell. Las actualizaciones de esquema son preparación real de las bases, no pruebas de los flujos. GPS físico, TLS entre clientes, moderación, concurrencia y aceptación visual quedan sin prueba funcional por la indicación del usuario. Los resultados anteriores de 176 comprobaciones no certifican este bloque.

## Implementación local de almacenamiento y operaciones

Los puntos 1 al 6 están implementados en código: SQLite para reportes pendientes, sincronización automática con WorkManager, recursos y unidades, órdenes con reserva, bandeja operativa y avisos de órdenes. Android, Spring y C# comparten el contrato. Consultar [instalación y alcance](IMPLEMENTACION-PUNTOS-1-A-6.md). Las migraciones de operaciones y administración se aplicaron posteriormente en los entornos indicados en la sección de actualización. Cada instalación requiere preparar su propia base.

Proyecto Zona Segura: aplicación Android, API principal Spring Boot en `backend-spring`, pruebas de integración y esquema de SQL Server (`zona_segura.sql`). La implementación equivalente en C# se conserva en [Zona-Segura-Api, rama sprint-2/Mateo](https://github.com/mateouni01-pixel/Zona-Segura-Api/tree/sprint-2/Mateo).

## Backend incluido

`backend-spring` contiene el código, el wrapper Gradle, SQL de actualización y lanzadores de Spring Boot. Su [README](backend-spring/README.md) explica registro, sesión, reportes y mapa. Para Windows con autenticación integrada de SQL Server, colocar localmente `mssql-jdbc_auth-13.6.0.x64.dll` del controlador oficial de Microsoft en `backend-spring/auth`. Esa dependencia y las configuraciones privadas no se versionan.

Configurar SQL Server y su esquema, instalar JDK 21 y compilar desde `backend-spring` con `gradlew.bat test bootJar`. Para iniciar en este equipo usar `backend-spring/iniciar-backend.cmd`. Si la instancia de SQL Server es distinta, configurar `SPRING_DATASOURCE_URL` antes de iniciar. Nunca guardar contraseñas o claves JWT en archivos versionados.

Las [pruebas reproducibles](pruebas/README.md) conservan 176 comprobaciones de la versión 2.2: autenticación, reportes, mapa y validación. Son anteriores a almacenamiento offline y operaciones y no certifican estos módulos nuevos. Su verificación en ejecución queda pendiente; recuperación y evidencia fotográfica en servidor siguen fuera del cierre acordado.

## Abrir el proyecto

1. Abre esta carpeta en Android Studio.
2. Deja que Gradle sincronice las dependencias.
3. Ejecuta el módulo `app` en un emulador o dispositivo Android.

La pantalla de inicio de sesión necesita la API de Zona Segura. Configura **solo `backend_base_url`** en `app/src/debug/res/values/strings.xml`: por defecto apunta a Spring Boot en `http://127.0.0.1:8080`. Tanto autenticación como reportes utilizan esa misma dirección. Para probar la implementación equivalente en C#, cambia el puerto a `5080`, recompila e inicia sesión con una cuenta registrada en esa API. No se comparten usuarios ni contraseñas entre las bases. Al cambiar de backend se descarta la sesión anterior.

## Registro y sesión

Las dos implementaciones usan el mismo contrato:

| Método | Ruta | Resultado |
| --- | --- | --- |
| POST | `/api/auth/register` | Nombre, correo y password; crea un ciudadano, devuelve 201 e `idUsuario` |
| POST | `/api/auth/login` | Correo y password; devuelve token Bearer, roles, `idUsuario` y `ciudadanoId` |
| GET | `/api/auth/me` | Requiere Bearer; devuelve la identidad vigente y el ciudadano de esa misma base |

El registro asigna `CIUDADANO` en el servidor. Un correo duplicado devuelve 409; datos inválidos, 400; una sesión inválida, vencida o una cuenta desactivada, 401. La contraseña requiere al menos 8 caracteres y un máximo de 72 bytes UTF-8. Recuperación de contraseña continúa pendiente.

## Sprint 2 conectado

Abre **Zona Segura** e inicia sesión como ciudadano. **Crear reporte** abre directamente el formulario; **Mis reportes** abre el historial. Selecciona el tipo, escribe una descripción de 10 a 2000 caracteres y captura el GPS. La app confirma el registro cuando la API responde con HTTP 201 y abre el historial actualizado. La carga del formulario no depende del historial.

Para un dispositivo conectado por ADB, habilita el puerto del backend elegido:

```powershell
adb reverse tcp:8080 tcp:8080
# Para C#, usar en cambio: adb reverse tcp:5080 tcp:5080
```

El servicio de sesión identifica al ciudadano mediante el rol `CIUDADANO` y `ciudadanoId` en `GET /api/auth/me`. Los IDs solo son válidos en su propia base. Ambas APIs permiten consultar el catálogo, crear reportes y consultar los propios. El autor se obtiene del token, no del formulario. C# también restringe la modificación y eliminación al propietario.

## Mapa de riesgo conectado

**Mapa de riesgo** abre calles de OpenStreetMap con zoom y desplazamiento. Al mover el mapa consulta el área visible; permite filtrar por tipo de incidente y por los últimos 7, 30 o 90 días. La capa se puede ocultar y tocar cada zona muestra su nivel y cantidad de incidentes. Actualiza los datos cada 30 segundos mientras la pantalla está visible; se detiene al salir y no vuelve a descargar las calles por ese refresco.

**Mi ubicación** solicita permiso de ubicación y centra el mapa en el ciudadano, con un punto azul y un círculo que representa la precisión. Si ya existe permiso, busca y centra la ubicación al abrir el mapa. Admite ubicación aproximada por red o precisa por GPS, según el permiso y los proveedores disponibles. Actualiza el punto mientras el mapa está visible sin volver a centrarlo en cada movimiento; el botón permite centrarlo de nuevo. La ubicación permanece visible al ocultar las zonas de riesgo y se deja de consultar al salir. Si se rechaza el permiso, se desactiva la ubicación o no se obtiene señal, muestra instrucciones y permite seguir explorando el mapa. Se descartan ubicaciones de más de dos minutos y se retira el punto si deja de actualizarse durante ese tiempo.

La regla inicial aprobada agrupa en celdas de 0,005 grados (aproximadamente 500 m en Santa Cruz): 1–2 incidentes, BAJO; 3–4, MEDIO; desde 5, ALTO. Se cuentan reportes VALIDADO, EN_ATENCION y ATENDIDO dentro del período, usando su fecha de registro UTC. Pendientes, rechazados y fechas futuras no cuentan. Los límites de consulta se ajustan a celdas completas para evitar cambios de conteo al desplazar unos metros el mapa.

El mapa muestra cantidades agrupadas de todas las cuentas, sin autores, descripciones ni coordenadas exactas. Las áreas sin datos no se pintan de verde: ausencia de reportes no equivale a seguridad. Las calles requieren internet en el dispositivo; la API sigue siendo la fuente exclusiva de la capa de riesgo.

Leaflet 1.9.4 y su licencia están incluidos en `app/src/main/assets/mapa`. Las teselas solo se solicitan para la vista actual, con identificación de la app, caché HTTP normal y atribución visible de OpenStreetMap. No hay descarga de mapas offline.

Ambas APIs ofrecen `GET /api/mapa/resumen` y `GET /api/mapa/zonas` con Bearer. Parámetros: `sur`, `oeste`, `norte`, `este` (los cuatro juntos), `dias` (1–90, predeterminado 7) y `tipoIncidenteId` opcional. Sin límites se consulta el área urbana inicial de Santa Cruz. El resumen devuelve zonas, total, período, fechas UTC y umbrales; `/zonas` conserva la respuesta de arreglo del contrato inicial.

## Confirmación de incidentes versión 2.2

Un reporte pasa de `PENDIENTE_VALIDACION` a `VALIDADO` cuando **tres ciudadanos distintos del autor** lo confirman dentro de 500 m, o cuando **personal autorizado de Alcaldía** registra una comprobación institucional positiva. El autor no puede votar ni verificar su propio reporte. Un voto total por cuenta y reporte; uno o dos positivos no validan. Los votos negativos quedan registrados para revisión y no provocan rechazo automático. El índice informativo es positivos/votos totales; el criterio de validación es el contador de tres positivos, incluso si hay negativos.

En Android, **Validar reportes** obtiene el GPS y muestra pendientes ajenos cercanos con contador de confirmaciones y estado de voto. El menú municipal incluye **Verificar reportes**: muestra incidentes pendientes o validados por la comunidad, su ubicación para inspección y permite confirmar o rechazar describiendo la comprobación (10–2000 caracteres). La revisión institucional no exige tres votos ni cercanía; guarda reporte, funcionario, resultado, motivo y fecha UTC en una transacción. Una decisión por reporte, sin sobrescritura; el rechazo fundamentado cambia a `RECHAZADO`. La administración y la moderación vigentes se describen al inicio de este README. Las apelaciones siguen fuera del alcance.

| Operación implementada en ambas APIs | Contrato |
| --- | --- |
| `GET /api/reportes/cercanos` | `lat`, `lng`, `radio=500`; pendientes ajenos, sin autor ni ubicación exacta |
| `POST /api/reportes/{id}/votos` | `confirma`, `latitud`, `longitud`; identidad desde Bearer |
| `GET /api/alcaldia/reportes-verificacion` | Solo personal municipal activo; ubicación precisa para comprobar el incidente |
| `POST /api/alcaldia/reportes/{id}/verificacion` | `confirma`, `motivo`; responsable desde Bearer |

Spring: aplicar `backend-spring/sql/sprint2-verificacion.sql` en una base existente; las instalaciones nuevas ya lo incluyen en `zona_segura.sql`. C#: aplicar la migración `AgregarVerificacionReportes`. No se crean cuentas municipales ni se conceden roles automáticamente. C# representa al revisor mediante Usuario con rol `PERSONAL_ALCALDIA`; Spring conserva además su registro `PersonalAlcaldia`. C# impide modificar o borrar reportes con votos o verificaciones para conservar su auditoría.

Los diagramas y la especificación vigentes están en `02_Diagramas/diagramas actualizados/Validacion_v2_2` y `01_Documentos/Zona_Segura_Requisitos_Verificacion_v2_2.pdf`, dentro de la carpeta general del proyecto. El mapa incluye los incidentes confirmados en su siguiente consulta; confirmar no crea una orden de intervención.

## Entrega a Mateo

Descomprime el proyecto y abre la carpeta que contiene settings.gradle.kts. Android Studio debe resolver el SDK de tu propia computadora. Instala la variante debug y abre Zona Segura.

Antes de la presentación, comprueba que una cuenta ciudadana recibe su ciudadanoId, que existen tipos activos y que un reporte creado desde Android figura en SQL Server. Cierra la sesión, vuelve a entrar y consulta el historial. Las pantallas de cuenta, mapa, votos, evidencia, moderación e intervenciones consumen el contrato propuesto a continuación. Mateo debe confirmar o implementar esas rutas y verificar el recorrido con SQL Server.
## Contrato propuesto para los recorridos añadidos

El cliente espera una respuesta HTTP 2xx para confirmar una acción. Las listas son arreglos JSON. Todas las llamadas usan el backend elegido; las operaciones autenticadas incluyen su JWT como Bearer. El servidor debe verificar identidad y rol en cada operación. Registro, mapa, votos y verificación municipal ya están implementados; fotografía, moderación general, recursos y recuperación continúan como contrato propuesto.

| Operación | Ruta propuesta y datos |
|---|---|
| Registro ciudadano | POST /api/auth/register con nombre, correo y password. El servidor asigna CIUDADANO. |
| Recuperación | POST /api/auth/recuperacion con correo. El servidor entrega un enlace de recuperación y completa el cambio de contraseña. |
| Evidencia | POST /api/reportes/{id}/foto con los bytes de la imagen y su Content-Type. Verificar autor y límites del archivo. |
| Pendientes cercanos | GET /api/reportes/cercanos?lat=…&lng=…&radio=500. Devolver id, tipoIncidenteNombre, descripcion y estado. |
| Voto o spam | POST /api/reportes/{id}/votos con confirma, latitud y longitud. Validar radio, unicidad y umbrales en servidor. |
| Mapa | GET /api/mapa/zonas. Cada zona incluye nombre, nivel (ALTO, MEDIO, BAJO) y poligono como anillo de pares numéricos [longitud, latitud]. Android ajusta los polígonos a la pantalla y permite alternar su sombreado. |
| Moderación | GET /api/moderacion/reportes para listar los casos revisables con sancionado. POST /api/reportes/{id}/rechazo o /sancion con objeto vacío. |
| Recursos y órdenes implementados | GET /api/intervenciones devuelve reportes gestionables y recursosDisponibles. POST /api/intervenciones exige reporteId, recursoId, tipoOrden, instrucciones y solicitudId UUID. GET /api/intervenciones/ordenes muestra el seguimiento y el estado del aviso. |
| Cierre operativo implementado | POST /api/operativa/ordenes/{idOrden}/atender con resultado obligatorio. Solo la unidad asignada cierra la orden, marca ATENDIDO y libera el recurso. |
| Exportación | Android genera PDF o XLSX del listado municipal cargado y permite elegir el archivo de destino. |

El mapa conectado usa calles de OpenStreetMap y conteos agrupados calculados por la API con los umbrales aprobados. Los avisos de órdenes están implementados dentro de la bandeja operativa. El envío de avisos externos sigue fuera de esta implementación.

## Demo archivada del Sprint 2

El ZIP de pruebas conserva el APK y guion anteriores. En la compilación actual, la entrada principal es **Zona Segura**. A continuación se documenta el recorrido del APK archivado.

### Recorrido de la demo

1. Entra como ciudadano de prueba y abre **Mapa de zonas de riesgo**. Las zonas A y B son rojas y la C es verde. Activa y desactiva la capa térmica. Los marcadores aparecen para reportes que pasaron por validación y se redondean para evitar mostrar la ubicación exacta.
2. Registra un incidente con tipo, descripción y ubicación. Puedes capturar GPS o elegir **Usar ubicación de prueba**. Adjunta una foto si quieres mostrar evidencia. El reporte aparece en **Mis reportes** como pendiente de sincronización. Pulsa **Simular sincronización** para hacerlo visible para los demás perfiles de la demo en ese dispositivo.
3. Para ver el umbral positivo completo, usa el reporte de ejemplo en **Validar reportes cercanos** con la ubicación de prueba. Confírmalo desde los dos perfiles ciudadanos de prueba. La base local aplica un voto por cuenta y reporte y la lista utiliza un radio de 500 m. El reporte validado aparece en el mapa.
4. Como personal de Alcaldía, abre **Asignar recurso preventivo**, genera una orden y márcala como atendida. Abre **Avisos de intervención** para consultar el aviso local dirigido a Fuerzas del Orden. Usa **Exportar PDF** o **Exportar Excel** para guardar el listado en el dispositivo. Hay una brigada de prueba disponible a la vez.
5. Para demostrar moderación, reporta como falso un incidente pendiente y sincronizado desde otra cuenta. Entra como administrador y abre **Moderar reportes**. Puedes rechazarlo y sancionar la cuenta de prueba. El administrador tiene su propio menú de permisos y moderación.
6. Desde el acceso inicial también puedes **Crear cuenta** con un correo ficticio. Guarda el código que aparece para probar **Recuperar contraseña**. Usa esa cuenta para iniciar y cerrar sesión.

### Alcance y dependencias

La demo usa SQLite local, cuentas ficticias y un plano ilustrativo con zonas dibujadas de antemano. El umbral de dos votos y la brigada única son datos de demostración. La recuperación usa un código local. Los cambios del modo de demostración están en `app/src/debug` y la pantalla de login del Sprint 1 sigue disponible como **Zona Segura**.

Para la aplicación conectada consulta el estado vigente al inicio de este README y el guion de entrega. El equipo retiró el guardado sin internet y la sincronización del alcance de aceptación.
