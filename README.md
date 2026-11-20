# Zona Segura (Android)

Proyecto Zona Segura: aplicación Android, API principal Spring Boot en `backend-spring`, pruebas de integración y esquema de SQL Server (`zona_segura.sql`). La implementación equivalente en C# se conserva en [Zona-Segura-Api, rama sprint-2/Mateo](https://github.com/mateouni01-pixel/Zona-Segura-Api/tree/sprint-2/Mateo).

## Backend incluido

`backend-spring` contiene el código, el wrapper Gradle, SQL de actualización y lanzadores de Spring Boot. Su [README](backend-spring/README.md) explica registro, sesión, reportes y mapa. Para Windows con autenticación integrada de SQL Server, colocar localmente `mssql-jdbc_auth-13.6.0.x64.dll` del controlador oficial de Microsoft en `backend-spring/auth`. Esa dependencia y las configuraciones privadas no se versionan.

Configurar SQL Server y su esquema, instalar JDK 21 y compilar desde `backend-spring` con `gradlew.bat test bootJar`. Para iniciar en este equipo usar `backend-spring/iniciar-backend.cmd`. Si la instancia de SQL Server es distinta, configurar `SPRING_DATASOURCE_URL` antes de iniciar. Nunca guardar contraseñas o claves JWT en archivos versionados.

Las [pruebas reproducibles](pruebas/README.md) incluyen el contrato HTTP de ambas APIs y la interacción del mapa. Los resultados guardados registran 96 comprobaciones HTTP correctas; no certifican el sprint completo. Votación comunitaria, sincronización offline y recursos municipales continúan pendientes.

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

La regla inicial aprobada agrupa en celdas de 0,005 grados (aproximadamente 500 m en Santa Cruz): 1–2 incidentes, BAJO; 3–4, MEDIO; desde 5, ALTO. Se cuentan reportes VALIDADO, EN_ATENCION y ATENDIDO dentro del período, usando su fecha de registro UTC. Pendientes, rechazados y fechas futuras no cuentan. Los límites de consulta se ajustan a celdas completas para evitar cambios de conteo al desplazar unos metros el mapa.

El mapa muestra cantidades agrupadas de todas las cuentas, sin autores, descripciones ni coordenadas exactas. Las áreas sin datos no se pintan de verde: ausencia de reportes no equivale a seguridad. Las calles requieren internet en el dispositivo; la API sigue siendo la fuente exclusiva de la capa de riesgo.

Leaflet 1.9.4 y su licencia están incluidos en `app/src/main/assets/mapa`. Las teselas solo se solicitan para la vista actual, con identificación de la app, caché HTTP normal y atribución visible de OpenStreetMap. No hay descarga de mapas offline.

Ambas APIs ofrecen `GET /api/mapa/resumen` y `GET /api/mapa/zonas` con Bearer. Parámetros: `sur`, `oeste`, `norte`, `este` (los cuatro juntos), `dias` (1–90, predeterminado 7) y `tipoIncidenteId` opcional. Sin límites se consulta el área urbana inicial de Santa Cruz. El resumen devuelve zonas, total, período, fechas UTC y umbrales; `/zonas` conserva la respuesta de arreglo del contrato inicial.

## Entrega a Mateo

Descomprime el proyecto y abre la carpeta que contiene settings.gradle.kts. Android Studio debe resolver el SDK de tu propia computadora. Instala la variante debug y abre Zona Segura.

Antes de la presentación, comprueba que una cuenta ciudadana recibe su ciudadanoId, que existen tipos activos y que un reporte creado desde Android figura en SQL Server. Cierra la sesión, vuelve a entrar y consulta el historial. Las pantallas de cuenta, mapa, votos, evidencia, moderación e intervenciones consumen el contrato propuesto a continuación. Mateo debe confirmar o implementar esas rutas y verificar el recorrido con SQL Server.
## Contrato propuesto para los recorridos añadidos

El cliente espera una respuesta HTTP 2xx para confirmar una acción. Las listas son arreglos JSON. Todas las llamadas usan el backend elegido; las operaciones autenticadas incluyen su JWT como Bearer. El servidor debe verificar identidad y rol en cada operación. Registro y mapa ya están implementados; fotografía, votos, moderación, recursos y recuperación continúan como contrato propuesto.

| Operación | Ruta propuesta y datos |
|---|---|
| Registro ciudadano | POST /api/auth/register con nombre, correo y password. El servidor asigna CIUDADANO. |
| Recuperación | POST /api/auth/recuperacion con correo. El servidor entrega un enlace de recuperación y completa el cambio de contraseña. |
| Evidencia | POST /api/reportes/{id}/foto con los bytes de la imagen y su Content-Type. Verificar autor y límites del archivo. |
| Pendientes cercanos | GET /api/reportes/cercanos?lat=…&lng=…&radio=500. Devolver id, tipoIncidenteNombre, descripcion y estado. |
| Voto o spam | POST /api/reportes/{id}/votos con confirma, latitud y longitud. Validar radio, unicidad y umbrales en servidor. |
| Mapa | GET /api/mapa/zonas. Cada zona incluye nombre, nivel (ALTO, MEDIO, BAJO) y poligono como anillo de pares numéricos [longitud, latitud]. Android ajusta los polígonos a la pantalla y permite alternar su sombreado. |
| Moderación | GET /api/moderacion/reportes para listar los casos revisables con sancionado. POST /api/reportes/{id}/rechazo o /sancion con objeto vacío. |
| Recursos y órdenes | GET /api/intervenciones devuelve los reportes gestionables con recursosDisponibles (id y nombre) y estadoNotificacion. POST /api/intervenciones con reporteId y recursoId reserva el recurso, crea la orden e inicia su notificación. |
| Cierre | POST /api/intervenciones/{idReporte}/atender con objeto vacío. El servidor libera el recurso. |
| Exportación | Android genera PDF o XLSX del listado municipal cargado y permite elegir el archivo de destino. |

El mapa conectado usa calles de OpenStreetMap y conteos agrupados calculados por la API con los umbrales aprobados. La entrega de avisos externos sigue pendiente.

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

La integración requiere la URL de Mateo y los contratos de registro, recuperación, tipos de incidente, carga de fotos, reportes, votos, mapa y gestión municipal. También faltan los umbrales oficiales, la geometría y criterios de riesgo, la decisión sobre permisos de moderación y el catálogo de prueba. Para el flujo conectado quedan pendientes Room, reintentos con WorkManager, el cliente HTTP y la integración con Spring Boot y SQL Server. El panel web y las alertas de emergencia quedan pendientes de decisión de alcance.
