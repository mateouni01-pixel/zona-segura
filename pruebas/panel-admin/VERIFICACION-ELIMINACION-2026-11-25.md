# Eliminación de cuentas — 25/11/2026

Implementada eliminación física de cuentas sin actividad vinculada mediante DELETE de Spring y confirmación desde MVC. La cuenta propia y el último administrador activo están protegidos. Las FK y comprobaciones de actividad impiden borrar reportes, decisiones, órdenes o sanciones por eliminar una cuenta. La auditoría conserva IDs históricos del objetivo y su responsable.

## Base local

Se verificó el respaldo `C:\Program Files\Microsoft SQL Server\MSSQL17.MSSQLSERVER\MSSQL\Backup\ZonaSegura_antes_eliminacion_cuentas_20261125_1930.bak` (COPY_ONLY, CHECKSUM y RESTORE VERIFYONLY). Se aplicó `backend-spring/sql/sprint3-eliminacion-cuentas.sql`: solo retiró la FK del objetivo de auditoría; mantuvo la del responsable. Antes y después había 5 cuentas y 1 auditoría. No se eliminó ninguna cuenta real.

## Comprobaciones

- Panel principal y copia del repositorio: compilación C#/Razor sin advertencias ni errores. El verificador MVC también compila.
- Spring: 48 pruebas aprobadas sin errores ni omisiones (31 de administración, 3 de SQL Server, 8 de verificación y 6 de seguridad HTTP), además de bootJar correcto.
- Las pruebas de servicio comprueban motivo y confirmación, borrado con auditoría, restricción por reportes, cuenta propia, último administrador y denegación a ciudadanos. Los filtros HTTP reales rechazan DELETE anónimo y de los otros tres roles y admiten al administrador.
- La prueba SQL crea cuentas temporales con cada uno de los cuatro roles, las elimina, comprueba que desaparezcan cuenta y roles, conserva las dos auditorías y verifica que el perfil eliminado ya no autentique. Cada escenario se revierte; no deja cuentas ni auditorías de prueba persistentes. SQL Server puede avanzar los contadores IDENTITY al revertir.
- Se añadieron 9 comprobaciones MVC de eliminación (57 en total): autorización, confirmación, antifalsificación, cuenta propia, error de historial, método DELETE y confirmación en el directorio. Su ejecución quedó bloqueada al cargar la DLL por la política de Control de aplicaciones de Windows (`0x800711C7`). No se alteró ni evitó esa política; estos nuevos recorridos de interfaz no se certifican como ejecutados.

Las pruebas de Spring se ejecutan con el comando y variables descritos en el README del backend. No se hizo revisión visual ni borrado desde un navegador. Para cargar las vistas, recompilar y reiniciar el panel en Visual Studio. La API se reinicia en el puerto 8080; el cambio de clave JWT del lanzador exige volver a iniciar sesión.
