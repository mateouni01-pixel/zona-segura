# Usuarios administrativos y revisión manual de reportes

Implementado el 25/11/2026: creación de cuentas desde Administración, incluidos otros administradores, y traslado de la revisión manual de reportes a `ADMINISTRADOR` en MVC, Android y ambas APIs. Se mantiene la validación automática por tres confirmaciones ciudadanas, según la respuesta del usuario. Las rutas antiguas de revisión municipal también requieren administrador.

Comprobaciones terminadas:

- Spring: 30 pruebas correctas, cero errores y cero omisiones: 17 de creación de cuentas, 8 de revisión/confirmación ciudadana y 5 de seguridad HTTP con JWT y filtros reales. Incluyen denegación a Personal de Alcaldía en rutas nuevas y antiguas y revocación de permisos. Las dependencias SQL se simulan.
- Compilación del panel principal C#/Razor y API C# alternativa: cero advertencias y errores. La copia del panel dentro del repositorio Android y el verificador MVC también compilan.
- Android: `:app:compileDebugJavaWithJavac --offline` correcto. No se instaló otro APK ni se ejecutó emulador.
- Scripts PowerShell modificados: sintaxis correcta. Se actualizó el contrato compartido para que el revisor de pruebas sea administrador; no se ejecutó el contrato completo contra bases temporales en este bloque.
- `MateoMS/ZonaSegura`: respaldo COPY_ONLY con CHECKSUM y RESTORE VERIFYONLY correctos. Archivo: `C:\Program Files\Microsoft SQL Server\MSSQL17.MSSQLSERVER\MSSQL\Backup\ZonaSegura_antes_validacion_admin_20261125_140314.bak`.
- Aplicada `sprint3-validacion-administrativa.sql`. La FK del responsable apunta a `Usuarios`, está comprobada y conserva los 3 reportes y las 3 revisiones. No se crearon cuentas reales, se asignaron roles ni se cambiaron decisiones existentes.

Limitación del verificador MVC: una ejecución inicial completó 10 comprobaciones de autenticación, roles, antifalsificación, contraseñas y correo duplicado. Su expectativa de redirección se ajustó para usar la misma ruta predeterminada del panel real. Después se añadieron comprobaciones de revisión administrativa y se recompiló. Windows bloqueó la ejecución final con Control de aplicaciones, código `0x800711C7`, al cargar las DLL. No se desactivó ni modificó esa política. Por ello, la suite MVC final y la navegación visual quedan sin certificar; la compilación y las 30 pruebas Spring sí finalizaron correctamente.

Para usar los cambios hay que reiniciar Spring actualizado y recompilar/ejecutar el panel desde Visual Studio. Administración utiliza el mismo acceso institucional; una cuenta debe tener previamente `ADMINISTRADOR`. No se reiniciaron los procesos que el usuario tuviera abiertos.
