# Nota de entrega para Mateo

Rama `ronny/cierre-sprint-3`, basada en `sprint-2/Mateo` (`8ba4f62`).

## Qué hice

- Adapté el diseño de Stitch a Android. El mapa sigue la variante B con filtros arriba, controles sobre el mapa y leyenda debajo. La navegación inferior conecta Inicio, Mapa, Mis reportes y Validar. También actualicé el acceso, el formulario, el historial y la validación.
- Ajusté el lanzador HTTPS para respetar el JDK seleccionado. Documenté la instalación, el guion de demostración y el ensayo automatizado contra una base de prueba aislada.
- Stitch proponía acceso con Google o cédula, edición manual del GPS, desenfoque automático y métricas de efectividad. Esas funciones requieren definir alcance y desarrollo propio.

## Qué pude comprobar

- Android compiló, pasó las pruebas unitarias y se instaló en el Samsung. Ronny confirmó el inicio de sesión por HTTPS antes del rediseño. La última compilación del rediseño también se instaló.
- Spring y el panel MVC compilaron. El panel sirvió el formulario HTTPS. La prueba en Spring confirmó el flujo de registro, reporte, tres votos, validación, orden idempotente, aviso, cierre y liberación del recurso.
- El mapa mostró niveles bajo, medio y alto con datos de prueba. El ensayo de interacción con teselas simuladas comprobó zoom, capa, desplazamiento y punto de ubicación.

## Qué queda por validar

Falta completar el recorrido visual y GPS en Android, verificar los casos de borde del mapa, realizar las pruebas de roles y concurrencia, y medir el rendimiento. El detalle y los comandos están en [PENDIENTES-RONNY.md](PENDIENTES-RONNY.md).
