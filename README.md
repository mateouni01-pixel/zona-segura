# Zona Segura (Android)

Aplicación Android del proyecto Zona Segura. Este repositorio contiene el código de la app, sus pruebas y el esquema inicial de SQL Server (`zona_segura.sql`).

## Abrir el proyecto

1. Abre esta carpeta en Android Studio.
2. Deja que Gradle sincronice las dependencias.
3. Ejecuta el módulo `app` en un emulador o dispositivo Android.

La pantalla de inicio de sesión necesita la API de Zona Segura. Configura `api_base_url` en `app/src/debug/res/values/strings.xml` para las pruebas locales; en la configuración principal está vacío. La API de prueba se encuentra fuera de esta carpeta y no está incluida en este repositorio.

## Sprint 2 conectado

Abre **Zona Segura** e inicia sesión como ciudadano. **Mis reportes** consulta los tipos activos y tus reportes del servidor. Para registrar uno, selecciona el tipo, escribe una descripción de 10 a 2000 caracteres y captura el GPS. La app confirma el registro cuando la API responde con HTTP 201.

En la variante debug, Android espera el servicio de sesión en el puerto 8080 y la API ASP.NET Core de reportes en el 5080. Para un dispositivo conectado por ADB, configura ambas rutas:

```powershell
adb reverse tcp:8080 tcp:8080
adb reverse tcp:5080 tcp:5080
```

El servicio de sesión debe identificar al ciudadano mediante el rol `CIUDADANO` y el campo numérico `ciudadanoId` en `GET /api/auth/me`. La API de reportes debe correr con SQL Server configurado. La rama local revisada de la API aún necesita autenticación y autorización propias antes de su despliegue, ya que acepta el ciudadano en el JSON y su listado devuelve todos los reportes.

## Entrega a Mateo

Descomprime el proyecto y abre la carpeta que contiene settings.gradle.kts. Android Studio debe resolver el SDK de tu propia computadora. Instala la variante debug y abre Zona Segura.

Antes de la presentación, comprueba que una cuenta ciudadana recibe su ciudadanoId, que existen tipos activos y que un reporte creado desde Android figura en SQL Server. Cierra la sesión, vuelve a entrar y consulta el historial. Las pantallas de cuenta, mapa, votos, evidencia, moderación e intervenciones consumen el contrato propuesto a continuación. Mateo debe confirmar o implementar esas rutas y verificar el recorrido con SQL Server.
## Contrato propuesto para los recorridos añadidos

El cliente espera una respuesta HTTP 2xx para confirmar una acción. Las listas son arreglos JSON. Las llamadas de sesión usan el puerto 8080. Las demás usan el servicio de reportes en 5080 y los recorridos añadidos incluyen el JWT de sesión como Bearer. El servidor debe verificar identidad y rol en cada operación.

| Operación | Ruta propuesta y datos |
|---|---|
| Registro ciudadano | POST /api/auth/registro con nombre, correo y password. El servidor asigna CIUDADANO. |
| Recuperación | POST /api/auth/recuperacion con correo. El servidor entrega un enlace de recuperación y completa el cambio de contraseña. |
| Evidencia | POST /api/reportes/{id}/foto con los bytes de la imagen y su Content-Type. Verificar autor y límites del archivo. |
| Pendientes cercanos | GET /api/reportes/cercanos?lat=…&lng=…&radio=500. Devolver id, tipoIncidenteNombre, descripcion y estado. |
| Voto o spam | POST /api/reportes/{id}/votos con confirma, latitud y longitud. Validar radio, unicidad y umbrales en servidor. |
| Mapa | GET /api/mapa/zonas. Cada zona incluye nombre, nivel (ALTO, MEDIO, BAJO) y poligono como anillo de pares numéricos [longitud, latitud]. Android ajusta los polígonos a la pantalla y permite alternar su sombreado. |
| Moderación | GET /api/moderacion/reportes para listar los casos revisables con sancionado. POST /api/reportes/{id}/rechazo o /sancion con objeto vacío. |
| Recursos y órdenes | GET /api/intervenciones devuelve los reportes gestionables con recursosDisponibles (id y nombre) y estadoNotificacion. POST /api/intervenciones con reporteId y recursoId reserva el recurso, crea la orden e inicia su notificación. |
| Cierre | POST /api/intervenciones/{idReporte}/atender con objeto vacío. El servidor libera el recurso. |
| Exportación | Android genera PDF o XLSX del listado municipal cargado y permite elegir el archivo de destino. |

La cartografía de calles, el cálculo real de peligrosidad y la entrega de avisos externos requieren servicios y datos del equipo. El sombreado representa el nivel publicado para cada polígono.

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
