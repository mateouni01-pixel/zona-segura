# Verificación del avance

## Contrato HTTP de Spring Boot y C#

Requiere PowerShell 7, SQL Server local con autenticación de Windows y permiso para crear/eliminar bases de prueba, Java 21 o superior, .NET 10 y la API C# clonada. Compilar primero `backend-spring` con `gradlew.bat test bootJar`; colocar la DLL JDBC de autenticación en su carpeta local `auth` y restaurar las dependencias/herramientas de C# según su README.

Desde la raíz de este repositorio:

```powershell
.\pruebas\contrato-api\Verificar-ContratoAuth.ps1 -Servidor 'TU_INSTANCIA' -ApiCsharp 'C:\ruta\Zona-Segura-Api'
```

El script crea dos bases con nombres únicos `ZonaSeguraAuthPrueba_*`, usa puertos 18080/15080 y limpia únicamente sus procesos y bases al terminar. No usa las cuentas ni reportes de la base de trabajo y no imprime tokens, claves JWT ni contraseñas. Configurar JAVA_HOME si Java no está en la ruta habitual de Android Studio. Si C# se clona al lado con el nombre `Zona-Segura-Api`, se detecta automáticamente.

`contrato-api/resultados/contrato-auth.json` registra 96 comprobaciones HTTP correctas de la ejecución guardada: registro, sesión, propiedad de reportes, validación de entrada y mapa (estados incluidos/excluidos, filtros, umbrales, bordes y anonimización).

## Interacción del mapa

Requiere Node.js, Playwright y Microsoft Edge. Desde la raíz:

```powershell
node .\pruebas\mapa\verificar-mapa.cjs
```

Se puede pasar como segundo argumento del proceso Node la ruta al módulo Playwright si no está en la resolución habitual. `EDGE_PATH` permite indicar otro ejecutable Chromium. La prueba verifica fichas, activación de capa, zoom, desplazamiento y estado vacío. Simula las teselas: no descarga mapas de OpenStreetMap. `mapa/resultados/verificacion.json` registra el resultado; las capturas y logs generados no se versionan.

Estas pruebas cubren el avance implementado. No constituyen una prueba integral de los módulos pendientes ni una medición certificada de rendimiento sobre 10.000 reportes.
