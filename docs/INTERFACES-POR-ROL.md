# Elección de interfaces en web y Android

La cuenta conserva todos los roles asignados por la API. Después de iniciar sesión, el selector muestra únicamente las interfaces autorizadas. Un rol produce una opción; dos roles, dos opciones; los cuatro roles, cuatro opciones. Las 15 combinaciones no necesitan pantallas independientes.

| Rol de la API | Interfaz web | Interfaz Android |
| --- | --- | --- |
| `CIUDADANO` | Mis reportes, creación con ubicación y mapa de incidentes. | Inicio ciudadano, reportes, mapa y confirmaciones comunitarias existentes. |
| `PERSONAL_ALCALDIA` | Mapa municipal, recursos, unidades y órdenes existentes. | Panel municipal y sus acciones existentes. |
| `ADMINISTRADOR` | Usuarios y permisos, validación, moderación y auditoría existentes. | Validación y moderación existentes. |
| `UNIDAD_OPERATIVA` | Bandeja de la unidad, avisos y registro de atención. | Bandeja operativa y sus acciones existentes. |

Cada cliente consulta `GET /api/auth/me` al abrir el selector y vuelve a comprobar los roles al elegir. La elección modifica el espacio de trabajo, no los permisos de la cuenta. Las API siguen comprobando roles y cuenta vigente en cada operación. Web y móvil utilizan el mismo backend Spring; no necesita nuevas tablas, migraciones ni endpoints.

## Patrón aplicado

Se usa **Strategy con un registro de estrategias** y autorización por roles. Cada estrategia conoce la entrada y navegación de una interfaz. El selector filtra las estrategias usando los roles autorizados; una selección resuelve su estrategia y abre la pantalla correspondiente.

Decorator sirve para añadir responsabilidades a un objeto envolviéndolo. Aquí el usuario elige entre contextos de trabajo completos; Strategy expresa esa selección y evita condicionales por cada combinación.

En MVC, `IInterfazStrategy` tiene cuatro implementaciones en `Services/InterfacesUsuario.cs`; `InterfacesUsuario` resuelve las opciones y el contexto. El layout genera los menús desde esa estrategia. Los controladores mantienen `[Authorize(Roles=...)]`. La ruta `/Cuenta/Interfaces` muestra las tarjetas; `POST /Cuenta/ElegirInterfaz` valida la elección con antifalsificación, actualiza los roles y guarda el rol activo en sesión. `/` vuelve a la interfaz seleccionada; una sesión anterior sin selección abre el selector.

En Android, `InterfazUsuario` contiene el catálogo y `InterfaceNavigation` registra las cuatro estrategias de navegación. `AuthApi.Profile` conserva la lista completa en lugar de dar prioridad a un solo rol. `InterfacesActivity` muestra las opciones y verifica el perfil al elegir. `SessionStore` mantiene roles y selección por separado. Cambiar de interfaz limpia la pila anterior para evitar volver a pantallas del contexto anterior con el botón Atrás.

## Comportamiento y límites

- En Android, Ciudadano conserva su paleta verde/teal, Alcaldía utiliza azul y Administración morado. Los temas se aplican al selector, al panel y a sus pantallas internas; Unidad Operativa conserva la paleta base. Las pantallas web mantienen su diseño existente.
- **Cambiar interfaz** permite elegir otro rol sin cerrar sesión. El menú muestra las funciones del espacio abierto.
- Un rol revocado desaparece al volver a comprobar el selector; manipular el rol enviado no concede acceso.
- Una cuenta operativa necesita estar asociada a una unidad activa para consultar su bandeja; esa asociación sigue gestionándose desde Alcaldía.
- Android conserva el modo ciudadano sin conexión cuando ya existe una sesión ciudadana guardada. Las opciones institucionales requieren comprobar permisos en línea.
- El nuevo formulario ciudadano web usa Geolocation del navegador, una lectura reciente y precisión de hasta 100 m. El backend verifica cobertura. Las coordenadas se interpretan con punto decimal independientemente del idioma de Windows. La solicitud conserva un UUID para la idempotencia del backend.
- Mis reportes usa `/api/reportes`, que limita los resultados al autor autenticado. La bandeja y sus acciones usan `/api/operativa/...`, que limita las órdenes a la unidad de la sesión.
- No se cambiaron cuentas ni roles reales para este desarrollo. Las comprobaciones MVC usan una API simulada. Las pruebas de Android no equivalen a una prueba en teléfono físico.

## Uso

Verificación final: **151 comprobaciones MVC y 7 pruebas Android aprobadas**, ambas copias del panel compiladas y APK generado. Se probaron las 15 combinaciones. La API fue simulada en los recorridos web y no se ejecutó la app en un teléfono; detalles en [verificación](../pruebas/panel-admin/VERIFICACION-INTERFACES-2026-11-25.md).

Web: recompilar y reiniciar el proyecto MVC en Visual Studio, iniciar sesión y elegir una tarjeta. Las copias del panel en `04_Codigo/ZonaSegura.Panel` y en el repositorio Android contienen el mismo código.

Android: instalar `app/build/outputs/apk/debug/app-debug.apk` actualizado. Después del ingreso se muestra el selector; cada pantalla inicial ofrece cambiar de interfaz. Se mantiene la API debug por Wi-Fi configurada en `http://192.168.0.5:8080`.

Compilación y comprobaciones desde la raíz del repositorio:

```powershell
dotnet build .\ZonaSegura.Panel\ZonaSegura.Panel.csproj
dotnet run --project .\pruebas\panel-admin\PanelAdmin.Checks.csproj --no-launch-profile
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --offline
```
