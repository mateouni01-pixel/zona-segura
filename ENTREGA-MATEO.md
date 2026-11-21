# Entrega Android · Sprint 2

Mateo, esta rama parte del Sprint 1 e incorpora los recorridos de ciudadano, Alcaldía y administrador en la aplicación principal. Abre la raíz del repo en Android Studio y ejecuta el módulo **app**, variante **debug**. La entrada es **Zona Segura**.

## Guion de demostración con Spring

1. Prepara en una base aislada un autor, tres ciudadanos confirmadores y cuentas de Alcaldía, administración y unidad operativa. Usa el registro ciudadano y asigna los roles institucionales mediante el procedimiento administrativo del equipo.
2. Desde Android, crea con el autor un incidente dentro de la cobertura y anota su ID. Comprueba el historial después de reiniciar la API e iniciar sesión de nuevo.
3. Con tres ciudadanos distintos a menos de 500 m confirma el mismo incidente. Después de cada voto refresca el listado. Se espera PENDIENTE con uno y dos votos y VALIDADO con tres.
4. Abre el mapa y filtra la celda y el período del reporte. Repite con reportes confirmados en la misma celda hasta observar BAJO con 1–2, MEDIO con 3–4 y ALTO desde 5. Contrasta el conteo con el panel municipal.
5. Desde Alcaldía asocia la cuenta operativa a una unidad y registra una patrulla disponible. Crea la orden de patrullaje sobre el reporte validado. Comprueba el cambio a EN_ATENCION y la reserva.
6. Entra con la unidad asignada, marca el aviso como leído y registra el resultado del cierre. Desde Alcaldía comprueba ATENDIDO, responsable, fecha y disponibilidad de la patrulla.

En el Samsung, Mateo documentó la apertura del login y la visibilidad de contraseña. El recorrido anterior describe resultados esperados. Consulta las evidencias y pendientes de Ronny en [el informe local](PENDIENTES-RONNY.md).
