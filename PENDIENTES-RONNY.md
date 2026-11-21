# Cierre local de Ronny · 21 de noviembre de 2026

Rama local `ronny/cierre-sprint-3`, creada desde `origin/sprint-2/Mateo` en `8ba4f62`. Cambios de Ronny en el directorio de trabajo, pendientes de revisión. El servicio Spring usó la base exclusiva `ZonaSeguraRonny_c1580cd48b` en la instancia local `.\ZONASEGURA`.

## Evidencia de esta revisión

| Prueba | Resultado observado |
| --- | --- |
| Compilación Android | `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest` generó el APK debug. Cuatro pruebas locales pasaron. Se recompiló el APK tras generar la CA de Ronny. |
| Compilación Spring | Desde `backend-spring`, `.\gradlew.bat test bootJar` con JDK 21.0.13.1. JAR generado y una prueba de contexto aprobada. |
| Panel MVC | `dotnet build ZonaSegura.Panel/ZonaSegura.Panel.csproj` con SDK 10.0.303 terminó con cero errores y advertencias. El formulario de ingreso respondió HTTP 200 por HTTPS con la CA local. |
| Base nueva | Se creó una base con nombre exclusivo, se aplicaron los cinco scripts y se contaron 22 tablas y cero reportes antes del ensayo. La primera ejecución parcial dejó el reporte 1. |
| Registro y votos | Registro, rechazo de correo duplicado (409), login, catálogo y creación de reporte por HTTPS. El reporte 2 quedó en PENDIENTE_VALIDACION con uno y dos votos y pasó a VALIDADO con el tercero. |
| Atención | Se creó una unidad con operador y patrulla. La asignación repetida con la misma clave devolvió la orden 1. La unidad recibió el reporte 2 en EN_ATENCION, leyó el aviso y cerró con resultado. Spring devolvió ATENDIDO, aviso LEIDA y recurso 1 disponible. |
| Interacción del mapa | El script existente de Playwright pasó en Edge con teselas y coordenadas simuladas. Comprobó ficha, capa, zoom, desplazamiento, estado vacío, punto de ubicación y precisión. Cero errores JavaScript. |
| Riesgo calculado por Spring | En la misma celda y ventana de siete días se observaron BAJO con 2 reportes, MEDIO con 3 y 4, y ALTO con 6. El alta pendiente conservó el conteo previo. En el ensayo del quinto reporte computable la primera consulta aún devolvió 4. Se añadió un segundo refresco al ensayo para admitir la precisión de segundos. Falta repetir el punto exacto de 5. |
| Contraste con SQL | De siete reportes de prueba, los IDs 2, 3, 4, 5 y 7 terminaron ATENDIDO, el 6 quedó VALIDADO y el 1 siguió PENDIENTE_VALIDACION. Los seis computables coincidieron con la última consulta del mapa. SQL Server tuvo una interrupción durante los ensayos y posteriormente volvió a responder. |

## Repetir desde la raíz

1. **Base nueva.** Trabajar en una base de prueba. En una copia temporal de `zona_segura.sql`, adaptar ambas sentencias iniciales `CREATE DATABASE` y `USE` al mismo nombre nuevo. Ejecutar con `sqlcmd -E -C -I -b -f 65001`. Para repetir el ensayo automático, usar el prefijo `ZonaSeguraRonny_` y diez dígitos hexadecimales.
2. **Base existente.** Respaldarla y revisar las actualizaciones ya aplicadas. Ejecutar los scripts faltantes de `backend-spring/sql` en orden: reportes, mapa, verificación, operaciones y administración. Especificar la base con `-d`. Conservar el esquema inicial para instalaciones nuevas.
3. Configurar `JAVA_HOME` con el JDK 21 y disponer de la DLL JDBC de autenticación en `backend-spring/auth`. Ajusté el lanzador HTTPS para respetar el JDK seleccionado y permitir la descarga de dependencias en la primera compilación.
4. Ejecutar `.\configurar-https-local.ps1`. Para Spring, indicar la base aislada mediante `SPRING_DATASOURCE_URL`, por ejemplo `jdbc:sqlserver://localhost;instanceName=ZONASEGURA;databaseName=ZonaSeguraRonny_c1580cd48b;integratedSecurity=true;encrypt=true;trustServerCertificate=true` (conexión SQL de desarrollo). Arrancar Spring y el panel en consolas distintas con `.\iniciar-https-local.ps1 -Componente Spring` y `-Componente Panel`.
5. En otra consola fijar `$env:NODE_EXTRA_CA_CERTS=(Resolve-Path Certificados/ca-desarrollo.crt).Path`. Ejecutar `node pruebas/cierre-ronny.cjs '.\ZONASEGURA' ZonaSeguraRonny_c1580cd48b`. El ensayo crea cuentas aleatorias y conserva sus datos en la base de prueba. Antes de escribir comprueba el nombre de base devuelto por la API. La CA pública generada para este equipo aparece en dos archivos rastreados por Git. Excluir esas copias locales al preparar los cambios para revisión.

## Pendientes para la aceptación

- [ ] **Android físico.** ADB no enumeró dispositivos. Probar registro, envío, cierre de sesión, sesión vencida, persistencia al reiniciar Spring, permisos preciso/aproximado/denegado, GPS apagado, falta de señal, reintento y cese de ubicación al salir. Registrar modelo, posiciones de borde/dentro/fuera y resultado en servidor.
- [ ] **Mapa y territorio.** Completar exclusiones de rechazados y fechas futuras, privacidad ciudadana, límites de los doce distritos y correspondencia de tabla e indicadores MVC con el área visible.
- [ ] **Autenticación e inspección.** Repetir sobre esta revisión los casos de entrada inválida, propiedad, voto duplicado/lejano/negativo y decisión institucional con motivo, auditoría e imposibilidad de sobrescritura. Las 176 comprobaciones archivadas corresponden a otra ejecución.
- [ ] **Operaciones.** Probar asignaciones simultáneas al mismo recurso, recurso incompatible, cierre sin resultado y aislamiento entre dos unidades. Verificar responsable y fecha de cierre en SQL y reflejo en ambas interfaces.
- [ ] **Administración y panel.** Preparar administrador ficticio y probar inicio de sesión MVC contra Spring, permisos por rol, suspensión y levantamiento, auditoría y protecciones del último administrador y del propio usuario.
- [ ] **Presentación.** Ejecutar el guion de `ENTREGA-MATEO.md` en los clientes, revisar pantallas pequeñas, teclado, navegación, errores y textos largos. Medir los requisitos de rendimiento con un volumen documentado. Contrastar la entrega PDF, los casos de uso y el TO-BE antes de presentar.
- [ ] **Entrega en Git.** Revisar el diff con Ronny y seleccionar los archivos. Publicación, PR, integración y validación desde una copia limpia quedan para después de esa revisión.

Fotografía en Spring, recuperación de contraseña, exportación web, reputación, alertas externas y edición/borrado de reportes en Spring quedan fuera del cierre acordado. El equipo también retiró guardado sin internet y sincronización de la aceptación.
