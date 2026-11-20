# Entrega Android · Sprint 2

Mateo, esta rama parte del Sprint 1 e incorpora los recorridos de ciudadano, Alcaldía y administrador en la aplicación principal. Abre la raíz del repo en Android Studio y ejecuta el módulo **app**, variante **debug**. La entrada es **Zona Segura**.

## Qué comprobar primero en tu PC

1. Elige un backend para toda la sesión: Spring Boot en 8080 (principal) o C# en 5080 (réplica para la otra materia). Configura únicamente `backend_base_url` y el `adb reverse` de ese puerto. No mezcles usuarios ni IDs de las dos bases. Ambas APIs permiten registro, sesión, catálogo, alta e historial de reportes propios.
2. Entra con una cuenta ciudadana. La respuesta de `GET /api/auth/me` debe incluir el rol y el `ciudadanoId` que corresponde a la cuenta en la base de reportes.
3. Pulsa **Crear reporte** desde el inicio ciudadano, captura GPS y envía el formulario. Comprueba la respuesta 201 y verifica en SQL Server el autor y las coordenadas. Vuelve a entrar y confirma que aparece en **Mis reportes**.
4. Con una segunda cuenta cercana, prueba el voto y el rechazo de votos duplicados. Con los roles municipales y de moderación, verifica los permisos también en el servidor.

## Qué falta para la presentación integrada

La API revisada ya tiene tipos de incidente y operaciones sobre reportes. Android incorpora además registro y recuperación, fotografía, votos a 500 m, polígonos de riesgo, moderación, órdenes, estado de notificación y exportación municipal PDF/XLSX. Las rutas adicionales son propuestas y están detalladas en el README. Confirma sus nombres, permisos y respuestas antes de la presentación. El servidor debe comprobar la identidad del autor y filtrar los datos que entrega a cada rol.

El mapa dibuja polígonos recibidos y sombrea su nivel de riesgo. Quedan pendientes la cartografía de calles y los datos oficiales de zonas. El envío de correos y avisos externos corresponde al backend. El guardado offline con reintentos automáticos también queda pendiente en Android.

En el Samsung se verificaron la apertura del login y el control de visibilidad de contraseña en login y registro. Sigue pendiente la verificación de punta a punta con cuentas reales, SQL Server y los archivos exportados.
