# Trabajo pendiente para Ronny e integración a main

Fecha de revisión: 21/11/2026. Equipo: Mateo y Ronny.

Repositorio: https://github.com/mateouni01-pixel/zona-segura

Este documento propone el trabajo de Ronny a partir del código y la documentación disponibles. Las casillas están pendientes de comprobación; no representan fallos ya reproducidos. Prioridad: cerrar el alcance de los sprints 2 y 3, corregir errores reales y entregar evidencias. Las ampliaciones opcionales se separan al final.

## 1. Base desde la que trabajar

La copia local revisada está limpia en `sprint-2/Mateo`, commit `8ba4f62` (`feat: completar panel municipal, administracion y operaciones`). Esta rama contiene cuatro commits que todavía no están en la rama `main` local. Las referencias remotas locales no sustituyen una consulta actual al servidor: comprobarlas con `git fetch origin` antes de empezar.

Ronny trabajará sobre el avance de Mateo, en una rama propia, por ejemplo `ronny/cierre-sprint-3`. No debe empezar únicamente desde el `main` antiguo.

Ya existen en código: autenticación, reportes, GPS, cobertura de los distritos 1–12, validación comunitaria e institucional, mapa, recursos, órdenes, bandeja operativa, avisos internos, administración y moderación, además del panel MVC. El trabajo será verificarlos y corregirlos, sin reconstruirlos por indicaciones históricas de los README.

**Alcance actualizado:** el guardado de reportes sin internet y la sincronización se retiraron de la planificación por decisión del equipo. No son entregables pendientes de Ronny. El código existente de estas funciones no se eliminará automáticamente como parte de este encargo; cualquier cambio en ese comportamiento se tratará por separado.

## 2. Preparación del entorno y datos de prueba — prioridad alta

- [ ] Clonar o actualizar el repositorio y crear la rama de Ronny desde `origin/sprint-2/Mateo`.
- [ ] Configurar Android Studio, JDK 21, SQL Server, .NET 10 para MVC y la dependencia JDBC local indicada en `backend-spring/README.md`.
- [ ] Preparar una base de prueba propia. Aplicar los scripts de actualización necesarios, en el orden documentado, sin volver a crear el esquema sobre una base con datos.
- [ ] Preparar cuentas ficticias: autor ciudadano, tres ciudadanos confirmadores, personal de Alcaldía, administrador y unidad operativa. Asignar roles mediante el procedimiento autorizado; nunca desde el registro público.
- [ ] Configurar HTTPS y la dirección de la API para su equipo según los lanzadores y README. No copiar contraseñas, certificados privados ni configuraciones personales del equipo de Mateo.
- [ ] Compilar Android, Spring y MVC desde una copia limpia. Registrar los comandos y resultados reales.

**Aceptación:** los tres componentes arrancarán con la configuración del equipo de Ronny y podrán acceder a la misma API Spring y base de prueba. No se versionarán secretos, respaldos ni cuentas reales.

## 3. Pruebas funcionales y correcciones del sprint 2 — prioridad alta

### 3.1 Autenticación y reportes

- [ ] Comprobar registro, correo duplicado, inicio y cierre de sesión, sesión vencida y cuenta suspendida.
- [ ] Crear un reporte desde Android con tipo activo, descripción válida y GPS real. Confirmar respuesta del servidor, autor correcto y persistencia en SQL Server.
- [ ] Reiniciar la API y volver a consultar el mismo reporte; verificar que conserva sus datos.
- [ ] Comprobar descripción vacía/corta, tipo inválido o inactivo, coordenadas inválidas y acceso a reportes ajenos.
- [ ] Mostrar errores comprensibles ante API inaccesible; no confirmar un envío que no fue aceptado por el servidor.

**Aceptación:** la sesión determinará el autor y los permisos; los datos guardados coincidirán con lo mostrado en Android después de reiniciar.

### 3.2 GPS y cobertura

- [ ] Probar permisos concedidos, denegados y aproximados; GPS desactivado, falta de señal y reintento.
- [ ] Comprobar posición reciente y precisión, mensajes de espera y recuperación del formulario.
- [ ] Probar coordenadas dentro y fuera de los 12 distritos, además de puntos de borde, comprobando también la validación del servidor.
- [ ] Verificar el punto de ubicación del mapa y que deje de consultar ubicación al salir de la pantalla.

**Aceptación:** se rechazarán ubicaciones que no cumplan las reglas implementadas y se explicará cómo corregirlas. No se afirmará haber verificado GPS físico usando únicamente coordenadas simuladas.

### 3.3 Validación comunitaria e institucional

- [ ] Mantener el reporte pendiente con una y dos confirmaciones; validarlo con tres ciudadanos distintos del autor a un máximo de 500 m.
- [ ] Rechazar voto del autor, voto duplicado y voto fuera del radio.
- [ ] Confirmar que un voto negativo no rechaza automáticamente el reporte.
- [ ] Probar confirmación y rechazo de Alcaldía con motivo, responsable y fecha registrados; sin exigir votos ni cercanía al funcionario.
- [ ] Impedir verificación propia y sobrescritura de una decisión institucional.
- [ ] Comprobar permisos con solicitudes directas a la API, además de la visibilidad de botones.

**Aceptación:** se respetarán las reglas v2.2 y el mapa reflejará el estado después de consultar nuevamente los datos.

### 3.4 Mapa y zonas rojas

- [ ] Preparar datos de prueba suficientes para demostrar niveles BAJO, MEDIO y ALTO en una misma celda y período: 1–2, 3–4 y 5 o más reportes computables, respectivamente.
- [ ] Excluir pendientes, rechazados y fechas futuras del cálculo del riesgo.
- [ ] Verificar filtros, zoom, desplazamiento, refresco, capa visible/oculta y estado sin datos.
- [ ] Comparar tabla, indicadores y zonas del panel con los reportes de prueba y el área consultada.
- [ ] Verificar que la vista ciudadana no revele autores ni coordenadas precisas de terceros.

**Aceptación:** habrá evidencia de «zonas rojas bien definidas», con datos y reglas explicables. Una zona sin reportes no se presentará como una zona cuya seguridad esté confirmada.

### 3.5 Recursos, órdenes y atención

- [ ] Crear unidad, asociar una cuenta operativa autorizada y registrar patrulla o cuadrilla disponible.
- [ ] Crear una orden sobre un reporte VALIDADO; comprobar recurso compatible, reserva y cambio a EN_ATENCION.
- [ ] Rechazar asignaciones sobre recursos ocupados y estados incompatibles.
- [ ] Repetir una solicitud con la misma clave y comprobar que no duplica la orden; probar dos asignaciones simultáneas al mismo recurso.
- [ ] Comprobar que la unidad solo vea sus propias órdenes, reciba el aviso y pueda confirmar su lectura.
- [ ] Cerrar la atención con resultado obligatorio, conservar responsable/fecha, marcar ATENDIDO y liberar el recurso.
- [ ] Impedir el cierre por otra unidad o por un usuario sin el permiso operativo.

**Aceptación:** el recorrido completo funcionará desde la asignación municipal hasta el cierre operativo, sin doble reserva y con trazabilidad.

### 3.6 Administración, moderación y HTTPS

- [ ] Probar los menús y permisos de ciudadano, Alcaldía, administrador y unidad operativa.
- [ ] Verificar cambios de rol, suspensión, levantamiento y bloqueo de acceso de una cuenta suspendida.
- [ ] Comprobar motivo y auditoría de cada acción, protección del último administrador y prevención de autoanulación.
- [ ] Probar Android → Spring y MVC → Spring con HTTPS en el equipo de Ronny; registrar cualquier problema de certificados o direcciones.
- [ ] Corregir errores reproducidos mediante cambios pequeños y acompañarlos con su comprobación correspondiente.

**Aceptación:** no bastará con ocultar controles; el servidor rechazará las acciones no autorizadas. La conexión funcionará sin desactivar globalmente la validación de certificados.

## 4. Acabado y entrega del sprint 3 — prioridad alta

- [ ] Revisar navegación, regreso, cierre de sesión y conservación de formularios ante errores recuperables.
- [ ] Unificar textos, colores, tamaños, estados de carga y mensajes en Android y el panel.
- [ ] Comprobar pantallas pequeñas, teclado, desplazamiento y textos largos; corregir elementos cortados o superpuestos.
- [ ] Revisar que no queden datos ficticios de la demo antigua en los recorridos conectados.
- [ ] Medir los criterios de rendimiento exigidos por la especificación con un conjunto de datos descrito; registrar equipo, cantidad de reportes, tiempos y limitaciones. No declarar cumplimientos sin medirlos.
- [ ] Preparar un guion de demostración con cuentas ficticias, datos iniciales y resultados esperados.
- [ ] Conservar solicitudes reproducibles y resultados de las pruebas nuevas en `pruebas/`. Ampliar las existentes cuando corresponda.
- [ ] Actualizar README y `ENTREGA-MATEO.md`: corregir frases históricas que todavía presentan recursos, moderación, mapa o migraciones como pendientes.
- [ ] Documentar instalación desde cero y actualización de una base existente por separado.
- [ ] Contrastar planificación, casos de uso y modelo TO-BE; reflejar el alcance acordado y conservar el enlace de GitHub en la entrega PDF.

**Entrega de Ronny:** código corregido, instrucciones reproducibles, pruebas nuevas, evidencias y lista de limitaciones reales. Las 176 comprobaciones históricas no certifican por sí solas operaciones, administración, interfaz ni HTTPS añadidos después.

## 5. Funciones adicionales detectadas — opcionales, fuera del cierre obligatorio

Estas funciones no deben retrasar la demostración del sprint 2. Se implementarán si el equipo las incorpora expresamente al alcance. La existencia de una pantalla o ruta propuesta no prueba que el servidor ya la soporte.

| Función | Pendiente y aceptación si se incorpora |
| --- | --- |
| Fotografía del reporte | Android intenta usar `/api/reportes/{id}/foto`, pero falta el soporte real de Spring. Implementar carga y consulta autorizadas, límites de tamaño y formato, persistencia y manejo de errores. Probar autor autorizado, tercero rechazado y archivo inválido. |
| Recuperación de contraseña | Existe una llamada Android a `/api/auth/recuperacion`, pero falta el flujo real de servidor. Definir canal de entrega, token aleatorio de un solo uso con vencimiento y cambio de contraseña. No usar el código local de la demo como recuperación real. |
| Exportaciones del panel web | El diagrama contempla exportar PDF/Excel como opcional. Implementar exportación autorizada con filtros, encabezados y datos verificables. Android ya tiene un generador local: primero probar su recorrido actual y sus archivos. |
| Reputación numérica | Las sanciones actuales suspenden acceso, pero no modifican puntajes. Definir primero fórmula, eventos y reversión; después implementar historial y pruebas. No inventar descuentos de puntos. |
| Alertas de emergencia y avisos externos | No confundir con los avisos internos de órdenes, que ya existen. Definir destinatario, canal, permisos y alcance antes de desarrollar. No prometer respuesta policial ni atención inmediata garantizada. |
| Edición y borrado de reportes en Spring | La API C# tiene CRUD; Spring no ofrece el equivalente completo. Incorporarlo solo si se exige para este repositorio, con control de propietario y protección del historial de votos, decisiones y órdenes. |

## 6. Evidencias mínimas por tarea

Para cada escenario ejecutado registrar:

| Campo | Contenido |
| --- | --- |
| Identificador y fecha | Por ejemplo `RONNY-MAPA-01`, fecha real de ejecución |
| Versión | Rama y hash del commit probado |
| Preparación | Rol, datos ficticios, base de prueba y condiciones necesarias |
| Pasos | Acciones reproducibles en app, panel o solicitud HTTP |
| Resultado esperado y obtenido | Estado, datos y código HTTP cuando aplique |
| Evidencia | Captura legible, resultado automatizado o consulta de comprobación |
| Incidencia | Error encontrado, corrección y resultado de la repetición |

No guardar tokens, claves privadas ni contraseñas en las evidencias. Mantener las pruebas separadas de la base de trabajo del equipo.

## 7. Trabajo en la rama de Ronny

Ejecutar estos comandos desde la raíz del repositorio. Antes de cambiar de rama, revisar `git status` y conservar cualquier trabajo propio sin sobrescribirlo.

```powershell
git fetch origin
git switch -c ronny/cierre-sprint-3 origin/sprint-2/Mateo
```

Si la rama de Ronny ya existe, cambiar a ella y revisar su base en vez de volver a crearla. Comprobar que contiene el avance de Mateo antes de empezar a corregir.

Trabajar por cambios concretos y hacer commits con la cuenta real de Ronny. Ejemplos de mensajes, únicamente cuando exista el trabajo:

```text
fix: corregir cierre de orden y liberacion de recurso
test: verificar permisos y concurrencia de operaciones
fix: ajustar formularios y mensajes del panel
docs: documentar instalacion y evidencias finales
```

Revisar `git diff` y agregar únicamente los archivos correspondientes a cada cambio. Antes de subir, comprobar que no se incluyan bases, `local.properties`, certificados privados, credenciales, compilaciones ni archivos temporales.

```powershell
git push -u origin ronny/cierre-sprint-3
```

Abrir un pull request hacia `main` con descripción de cambios, comandos ejecutados, resultados y pendientes. Indicar que la rama parte del avance de Mateo para que los commits incluidos se entiendan correctamente.

## 8. Integración posterior a la rama principal

La integración se hará cuando las pruebas estén documentadas y se haya revisado el diff. Se recomienda integrar primero el avance de Mateo y después el de Ronny para conservar claramente la participación de ambos.

1. Actualizar las referencias con `git fetch origin` y revisar qué cambios ya están en `origin/main`.
2. Integrar `sprint-2/Mateo` a `main` mediante su pull request, si todavía está pendiente.
3. En la rama de Ronny, incorporar el `origin/main` actualizado, resolver conflictos y repetir las comprobaciones afectadas.
4. Revisar el pull request de Ronny. Conservar los commits y autores reales; no reemplazar el historial mediante un push forzado a `main`.
5. Integrar a `main` y comprobar desde una copia limpia la compilación, instalación y un recorrido completo de reporte → validación → mapa → orden → atención.
6. Etiquetar o registrar el commit final usado para la demostración y generar los entregables desde esa versión.

No copiar carpetas completas sobre `main` para integrar el trabajo. Los cambios de esquema deben viajar mediante scripts versionados con instrucciones de orden y respaldo; Git no transporta la base SQL Server del equipo.

## 9. Condición para dar por terminado el trabajo

- [ ] Las tareas de prioridad alta estarán ejecutadas o tendrán un bloqueo concreto documentado.
- [ ] Los errores reproducidos estarán corregidos y comprobados nuevamente.
- [ ] Las funciones opcionales tendrán un estado explícito: incorporada y probada, pendiente o fuera de alcance.
- [ ] El pull request incluirá aportes técnicos reales de Ronny y evidencias de la versión probada.
- [ ] La rama integrada compilará y el recorrido principal funcionará desde una instalación reproducible.

## Referencias dentro del repositorio

- `README.md`: actualización vigente y recorridos Android.
- `backend-spring/README.md`: configuración y contrato principal.
- `ZonaSegura.Panel/README.md`: interfaz municipal, permisos y HTTPS.
- `IMPLEMENTACION-PUNTOS-1-A-6.md`: operaciones existentes y límites de verificación.
- `pruebas/README.md`: pruebas anteriores y cómo ejecutarlas.
- `docs/verificacion-v2.2/`: casos de uso y reglas de validación vigentes.

La API C# independiente pertenece a otro repositorio. Cualquier cambio allí requerirá su propia rama y revisión; no se mezclará automáticamente con el pull request de `zona-segura`.
