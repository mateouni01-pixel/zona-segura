# Verificación del avance

## Contrato HTTP de Spring Boot y C#

Requiere PowerShell 7, SQL Server local con autenticación de Windows y permiso para crear/eliminar bases de prueba, Java 21 o superior, .NET 10 y la API C# clonada. Compilar primero `backend-spring` con `gradlew.bat test bootJar`; colocar la DLL JDBC de autenticación en su carpeta local `auth` y restaurar las dependencias/herramientas de C# según su README.

Desde la raíz de este repositorio:

```powershell
.\pruebas\contrato-api\Verificar-ContratoAuth.ps1 -Servidor 'TU_INSTANCIA' -ApiCsharp 'C:\ruta\Zona-Segura-Api'
```

El script crea dos bases con nombres únicos `ZonaSeguraAuthPrueba_*`, usa puertos 18080/15080 y limpia únicamente sus procesos y bases al terminar. No usa las cuentas ni reportes de la base de trabajo y no imprime tokens, claves JWT ni contraseñas. Configurar JAVA_HOME si Java no está en la ruta habitual de Android Studio. Si C# se clona al lado con el nombre `Zona-Segura-Api`, se detecta automáticamente.

`contrato-api/resultados/contrato-auth.json` registra 176 comprobaciones correctas de la ejecución del 20/11/2026: registro, sesión, propiedad de reportes, validación de entrada, mapa, tres confirmaciones ciudadanas e inspección institucional. Incluye dos envíos simultáneos del mismo voto (200 y 409), tercero y cuarto simultáneos (solo uno aceptado), autor y funcionario sin posibilidad de validar su propio reporte, radio, negativos sin rechazo automático, auditoría, motivo obligatorio y revocación de rol. Se crean cuentas municipales únicamente dentro de las bases de prueba y se eliminan con esas bases.

Para la comprobación de Android, usar un reporte de un autor y tres cuentas ciudadanas diferentes situadas dentro de 500 m. Tras el primer y segundo voto sigue pendiente; tras el tercero aparece VALIDADO y se incluye en el siguiente refresco del mapa. Repetir con otro incidente desde Personal de Alcaldía > Verificar reportes, describiendo la comprobación realizada; confirmar debe validar sin votos previos y rechazar debe excluirlo del mapa. Esta verificación física de GPS y pantallas no la sustituye la prueba HTTP.

## Interacción del mapa

Requiere Node.js, Playwright y Microsoft Edge. Desde la raíz:

```powershell
node .\pruebas\mapa\verificar-mapa.cjs
```

Se puede pasar como segundo argumento del proceso Node la ruta al módulo Playwright si no está en la resolución habitual. `EDGE_PATH` permite indicar otro ejecutable Chromium. La prueba verifica fichas, activación de capa, zoom, desplazamiento, estado vacío y el punto de ubicación del ciudadano: centrado, círculo de precisión, actualización sin interrumpir la navegación, independencia de la capa de riesgo y limpieza. Simula las teselas y las coordenadas del ciudadano: no descarga mapas de OpenStreetMap ni prueba el GPS físico. `mapa/resultados/verificacion.json` registra el resultado; las capturas y logs generados no se versionan.

Para verificar la ubicación en Android, abrir Mapa de riesgo y pulsar Mi ubicación. Comprobar permiso preciso y aproximado, permiso denegado y ubicación del dispositivo apagada; con señal, debe aparecer el punto azul y el círculo de precisión. Desplazar el mapa y verificar que el siguiente cambio de posición actualice el punto sin recentrar; Mi ubicación debe centrar de nuevo. Ocultar riesgo no debe ocultar el punto. Al salir del mapa debe detenerse el acceso a ubicación. Esta comprobación requiere teléfono o emulador y queda separada de la prueba de navegador.

Estas pruebas cubren el avance implementado. No constituyen una prueba integral de los módulos pendientes ni una medición certificada de rendimiento sobre 10.000 reportes.
