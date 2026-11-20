# Pantallas Android de Zona Segura

Prototipo ciudadano en Java y XML. Implementado sobre el proyecto Android existente; no reemplaza la API ni la autenticación actual. Los cambios previos del panel de administración se conservan.

## Cómo explorarlo

1. Abrir esta carpeta (`04_Codigo/SPRINT 1`) en Android Studio.
2. Ejecutar el módulo `app` en un emulador o dispositivo.
3. En el acceso, tocar **Explorar pantallas de demostración**. No requiere correo, contraseña ni backend.
4. Navegar con **Mapa**, **Reportar**, **Validar** y **Perfil**.
5. Si ya hay una sesión ciudadana, el inicio incluye el mismo botón. Si se redirige a otro rol, cerrar esa sesión para volver al acceso y entrar al prototipo.

En el acceso también está **¿Olvidaste tu contraseña?**: valida el formato del correo y presenta el siguiente paso como una demostración, sin enviar mensajes.

## Pantallas y estados

| Pantalla | Interacción disponible |
| --- | --- |
| Acceso existente | Login conservado; entradas a recuperación y demostración |
| Recuperar acceso | Campo de correo, error de validación y confirmación explícita de que no hubo envío |
| Mapa de ejemplo | Ilustración local, filtros por tipo, tarjetas de reportes y entrada a nuevo reporte |
| Nuevo reporte | Tipo, descripción, ubicaciones de ejemplo, elección y vista previa de fotografía, errores y guardado local |
| Reporte guardado | Referencia propia, tipo, estado y acciones para consultar el detalle o volver al mapa |
| Validar reportes | Reportes dentro de 500 m de la ubicación simulada y filtro Mis reportes con estado vacío accionable |
| Detalle | Descripción, estado, coordenadas, evidencia opcional, distancia y barra de confirmaciones de ejemplo |
| Confirmación de voto | Diálogo distinto para confirmar o marcar falso/spam |
| Resultado del voto | Resultado positivo o negativo, referencia y aclaración sobre umbrales pendientes |
| Voto no disponible | Mensajes para duplicado, fuera de radio, reporte propio o validación cerrada; botones deshabilitados |
| Perfil | Cantidad de reportes/votos propios, privacidad, restablecer datos y salir con confirmación |

## Recorridos de revisión

- **Crear:** acceso → demostración → Reportar → elegir tipo → escribir al menos 10 caracteres → guardar → detalle → mapa.
- **Error:** intentar guardar sin tipo o con descripción corta; no se crea un reporte.
- **Borrador:** escribir una descripción, cambiar de pestaña y volver, o girar el dispositivo; comprobar conservación del formulario.
- **Mis reportes:** Validar → Mis reportes. Al inicio aparece el estado vacío; después de crear un reporte, debe aparecer en la lista.
- **Votar:** Mapa → Ver detalle de R-1042 o R-1040 → confirmar/falso → confirmar diálogo → resultado. Volver al detalle: el segundo voto está bloqueado.
- **Distancia:** consultar R-1037 desde Mapa; se puede leer, pero no votar porque supera 500 m. R-1038 ya está validado y tiene votación cerrada.
- **Persistencia local:** cerrar y abrir el prototipo; reportes y votos siguen disponibles. Perfil → Restablecer datos elimina solo los datos de esta demostración, previa confirmación.

## Límites de esta etapa

- La banda **DEMOSTRACIÓN · DATOS LOCALES** identifica el prototipo. Las referencias R-1042, R-1040, R-1038 y R-1037 son ficticias y no corresponden a registros de SQL Server.
- El mapa es una ilustración fija; los filtros actúan sobre las tarjetas. No hay cartografía online ni mapa de calor calculado.
- La ubicación inicial es simulada (`-17.783300, -63.182100`), con tres alternativas de ejemplo. No se pide permiso GPS ni se obtiene ubicación real.
- Se permite elegir una imagen mediante el selector del sistema; no se sube ni se comparte. Si se mueve o elimina el archivo original, puede dejar de estar disponible.
- Guardado y votos usan un almacén local exclusivo de demostración. No constituyen Room/SQLite, cola de sincronización ni evidencias de persistencia en SQL Server.
- El prototipo simula un ciudadano. Por decisión de UI, no permite votar sus propios reportes; confirmar esta regla con los requisitos antes de implementar el backend.
- Las confirmaciones de ejemplo se calculan como votos positivos / votos totales; no definen la fórmula final de veracidad ni cambian el estado por umbrales sin acordar.
- Recuperación no envía correos; no hay registro de cuentas, alertas de emergencia ni paneles municipales en esta etapa ciudadana.

## Preparación para conectar la API

`citizen/ReportRepository` separa las operaciones de datos de sus pantallas. `DemoReportRepository` es la implementación local; no utiliza `AuthApi`, tokens ni usuarios reales.

Para integrar .NET:

1. Definir DTO y URLs reales según el contrato de la API y migrar autenticación/roles.
2. Resolver el ciudadano a partir de su sesión y cargar los tipos desde el catálogo; no trasladar IDs de demostración al servidor.
3. Implementar solicitudes HTTP asíncronas con estados de carga, éxito, error y reintento; el contrato local síncrono debe adaptarse para no usar la red en el hilo principal.
4. Reemplazar coordenadas simuladas por captura GPS y manejo de permisos.
5. Añadir almacenamiento Room y sincronización cuando se implemente el requisito sin internet.
6. Implementar foto, votos y sus reglas en servidor; el CRUD actual planificado no incluye todavía esos endpoints.
7. Cambiar el mapa ilustrativo por una biblioteca cartográfica elegida y conectar sus capas a los datos autorizados.

## Archivos principales

- `app/src/main/java/com/example/zonasegura/citizen/`: actividades, fragmentos, repositorio de demostración y reglas de UI.
- `app/src/main/res/layout/*citizen*.xml`: pantallas y tarjetas.
- `app/src/main/res/values/citizen_*.xml`: textos, colores y estilos.
- `app/src/test/java/com/example/zonasegura/citizen/ReportRulesTest.java`: reglas de formulario, coordenadas, radio y votos.
- `app/src/androidTest/java/com/example/zonasegura/CitizenPrototypeTest.java`: navegación, creación, recreación de actividad, estado vacío y persistencia de voto.

## Compilar y probar

Verificación del 18 de noviembre de 2026: APK debug generado correctamente y **9 pruebas unitarias correctas** (5 nuevas de reglas del prototipo, 3 de login y 1 de ejemplo). La suite de interfaz compiló, pero no terminó su ejecución: el emulador Android 37.1 sufrió bloqueos del sistema y se interrumpió. No se considera aprobada la verificación visual ni el recorrido completo de creación y recreación de actividad; repetir las pruebas en un dispositivo/emulador estable antes de presentar. El proceso del emulador de prueba se cerró.

Se dejó una copia instalable en `04_Codigo/SPRINT 2/ZonaSegura_Pantallas_Android.apk`, fuera del código fuente.

Se utiliza el SDK, JDK y wrapper del proyecto existente. En el equipo de Mateo, el JDK está en Android Studio:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug testDebugUnitTest
.\gradlew.bat connectedDebugAndroidTest
```

La segunda orden requiere un emulador o dispositivo conectado. El APK se genera en `app/build/outputs/apk/debug/app-debug.apk`. No se entrega `build` como código fuente ni se mezclan estos archivos Android con el repositorio separado `Zona-Segura-Api`.

## Registro de ciudadano (19/11/2026)
Desde el login, Crear cuenta de ciudadano abre un formulario de nombre, correo, contraseña y confirmación. Envía POST /api/auth/register a la API Spring configurada en api_base_url; al recibir 201 muestra la confirmación y permite volver al login. Maneja correo duplicado (409), datos inválidos, API sin configurar y conexión incierta. Requiere el backend Spring y su base ZonaSegura; no utiliza la API .NET ni datos de demostración. Pendiente comprobar el recorrido contra un servidor activo y en dispositivo.
