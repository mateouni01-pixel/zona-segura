# Zona Segura (Android)

Aplicación Android del proyecto Zona Segura. Este repositorio contiene el código de la app, sus pruebas y el esquema inicial de SQL Server (`zona_segura.sql`).

## Abrir el proyecto

1. Abre esta carpeta en Android Studio.
2. Deja que Gradle sincronice las dependencias.
3. Ejecuta el módulo `app` en un emulador o dispositivo Android.

La pantalla de inicio de sesión necesita la API de Zona Segura. Configura `api_base_url` en `app/src/debug/res/values/strings.xml` para las pruebas locales; en la configuración principal está vacío. La API de prueba se encuentra fuera de esta carpeta y no está incluida en este repositorio.
