# Guías de uso por interfaz — 25/11/2026

La opción **Guías de uso** aparece en las cuatro interfaces de la web y Android. El selector también ofrece una guía para cada rol disponible. Las explicaciones se agrupan por pantalla y describen el control, su función, requisitos y resultado. Se pueden buscar palabras sin distinguir mayúsculas ni acentos y abrir o cerrar secciones.

El catálogo contiene 129 explicaciones entre las dos plataformas: navegación común y funciones específicas de Ciudadano, Personal de Alcaldía, Administrador y Unidad Operativa. Se utilizan los nombres de los controles existentes, incluidos los diálogos de verificación y moderación. Se documenta que adjuntar fotografías aún no está habilitado en el servidor actual.

## Acceso y comportamiento

- Web: enlace en el menú de cada interfaz y en su tarjeta del selector. `/Guias?rol=...` exige sesión y el rol solicitado. La guía conserva el aspecto de ese espacio y **Volver a mi interfaz** abre su inicio. Leerla no modifica el rol seleccionado ni consulta la API.
- Android: botón en los cuatro paneles iniciales, en las pantallas de operaciones y en las tarjetas del selector. La ayuda se empaqueta en el APK y puede leerse sin internet. Conserva el color del rol descrito; **Volver** cierra la guía y conserva la pantalla anterior. No abre formularios de acciones ni modifica permisos.

## Mantenimiento

La fuente única es `compartido/guias-uso.json`. Cada rol contiene secciones `web` y `android`; `comunes` contiene la navegación compartida de cada plataforma. Cada explicación tiene `boton`, `funcion`, `requisito` y `resultado`. Para modificar una función, actualizar su explicación en ese archivo y recompilar ambos clientes.

Gradle incorpora `compartido` como directorio adicional de assets. MVC enlaza el mismo JSON como `Content/guias-uso.json` y lo copia al compilar y publicar. El `.csproj` admite tanto la copia principal junto al repositorio como la incluida dentro de él. Para compilar el panel se debe conservar esta estructura y el catálogo; no copiar únicamente su carpeta sin `compartido`.

## Verificación

- Compilación C#/Razor de las dos copias del panel sin advertencias ni errores.
- **178 comprobaciones MVC aprobadas** con API simulada. Las 27 nuevas comprueban guía por rol, búsqueda sin acentos, estado sin coincidencias, aspecto de la interfaz, rechazo de roles ajenos, ausencia de llamadas a la API y conservación de la interfaz seleccionada. Se mantienen las 151 comprobaciones anteriores.
- Android: `:app:testDebugUnitTest :app:assembleDebug --offline` correcto y **7 pruebas existentes aprobadas**. Se verificó que el APK contenga `assets/guias-uso.json`.
- Se validó la estructura completa del JSON para los cuatro roles y las dos plataformas, sin explicaciones vacías. El archivo copiado al verificador MVC coincide con el catálogo compartido.

No se modificaron cuentas, roles ni datos reales. No se hizo revisión visual en navegador ni se ejecutó el APK en teléfono o emulador. Recompilar/reiniciar MVC e instalar el APK actualizado para usar la ayuda.
