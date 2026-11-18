# Evidencias de la entrega final

Capturas de la entrega final, en [`evidencias/`](evidencias/). Las Imágenes 1 a 23 se tomaron el 13 de noviembre de 2026 y las
Imágenes 24 a 30 el 18 de noviembre de 2026, todas sobre los **datos de demostración** (ficticios, cargados con
`scripts\verificacion\cargar-demo.ps1`). Los resultados y su estado están en [`matriz-pruebas.md`](matriz-pruebas.md).

La numeración es la misma del informe final (Imagen N). Cada captura dice cómo repetirla.

| Imagen | Qué muestra | Cómo repetirla |
|---|---|---|
| 1 | Arquitectura final de la plataforma: 16 contenedores | docs/arquitectura.md |
| 2 | Los 16 contenedores en marcha (docker compose ps): 15 sanos y Kafdrop, que no tiene healthcheck | docker compose ps |
| 3 | Resumen de ejecutar-pruebas.ps1: 463 pruebas en 11 módulos, 0 fallos, 0 saltadas | scripts\verificacion\ejecutar-pruebas.ps1 |
| 4 | Resumen de Newman: 277 peticiones, 763 aserciones, 0 fallos | scripts\verificacion\ejecutar-postman.ps1 |
| 5 | Pantalla de inicio de sesión | http://localhost:5173 |
| 6 | Agenda de la administradora con «Próximos 7 días» y la lista de eventos | página Eventos |
| 7 | Ficha de «Quince años de Lucía» en «Ver todo junto», con la franja de resumen | abrir el evento |
| 8 | Paso 7 «Resumen» de la «Boda de Valentina y Santiago»: todo listo | paso 7 del evento |
| 9 | Paso 7 de «Quince años de Sofía»: quedan 4 pendientes | paso 7 del evento |
| 10 | Error de negocio 409 al confirmar «Quince años de Sofía» con el contrato pendiente | «Quince años de Sofía» → Confirmar |
| 11 | Nuevo evento, paso Fecha, diciembre de 2026: los días de un fin de semana ocupado no se pueden elegir | Nuevo evento → Fecha |
| 12 | Kafdrop: resumen del clúster y los 9 tópicos (4 tópicos, sus 4 DLT y el tópico interno __consumer_offsets) | Kafdrop, http://localhost:9000 |
| 13 | Mensaje EVENTO_CONFIRMADO en eventos.confirmados, con su clave y el sobre común | Kafdrop, http://localhost:9000 |
| 14 | Campanita en 0, antes de ejecutar los recordatorios | botón «Ejecutar recordatorios» como administradora |
| 15 | Campanita en 1 sin recargar la página, con el aviso «1 recordatorio enviado» | botón «Ejecutar recordatorios» como administradora |
| 16 | Panel de notificaciones: «Falta 1 día para su evento: Boda de Gabriela y Mateo», con la hora de llegada | botón «Ejecutar recordatorios» como administradora |
| 17 | Sesión de la asistente: rol «Asistente» en la barra y sin el botón «Ejecutar recordatorios» | sesión de la asistente |
| 18 | Lugares vistos por la asistente: solo consulta, sin botones de administración | sesión de la asistente, página Lugares |
| 19 | Con contratos-service detenido, la ficha muestra un mensaje genérico con «Ref: 220F8EE9» | detener contratos-service y abrir una ficha |
| 20 | Log del gateway con la misma referencia: contratos sin respuesta y error técnico 503 | log del gateway, buscando la referencia |
| 21 | Swagger de eventos-service: controladores de fechas, recordatorios y eventos, y sus esquemas | http://localhost:8081/swagger-ui/index.html |
| 22 | Ficha de Lucía en un celular (390 px), sin desplazamiento horizontal | navegador en vista móvil |
| 23 | Calendario de Nuevo evento en un celular (390 px) | navegador en vista móvil |
| 24 | Resultado de las pruebas de la interfaz con Vitest: 19 archivos y 146 pruebas pasadas | carpeta frontend, comando npx vitest run |
| 25 | Prueba C06, antes: «Quince años de Lucía» con 2 pagos, total abonado $2.610.000 y saldo pendiente $6.270.000 | paso 4 «Abonos» de la ficha del evento |
| 26 | Prueba C06: la misma ficha con 4 pagos (se suman $100.000 y $50.000), total abonado $2.760.000, saldo $6.120.000 y campanita en 2 | paso 4 «Abonos» de la ficha del evento |
| 27 | Prueba C06: contenedor kafka_broker detenido (Exited) en Docker Desktop; el registro muestra el apagado a las 13:38:22 UTC (8:38 hora local) | Docker Desktop, contenedor kafka_broker, o docker compose stop kafka |
| 28 | Prueba C06, con Kafka detenido: el abono de $900.000 queda registrado (5 pagos, saldo $5.220.000) y la campanita sigue en 2, sin su aviso todavía | registrar un pago con Kafka detenido |
| 29 | Prueba C06: kafka_broker de nuevo en ejecución (Running) a las 13:43:39 UTC (8:43 hora local), unos 5 minutos después del apagado | Docker Desktop, o docker compose start kafka |
| 30 | Prueba C06, después: la campanita pasa a 3 y llega un solo aviso del abono de $900.000, sin avisos repetidos ni perdidos | abrir la campanita tras reiniciar Kafka |

## Capturas

### Imagen 1

Arquitectura final de la plataforma: 16 contenedores.

![Imagen 1](evidencias/imagen-01.png)

### Imagen 2

Los 16 contenedores en marcha (docker compose ps): 15 sanos y Kafdrop, que no tiene healthcheck.

![Imagen 2](evidencias/imagen-02.png)

### Imagen 3

Resumen de ejecutar-pruebas.ps1: 463 pruebas en 11 módulos, 0 fallos, 0 saltadas.

![Imagen 3](evidencias/imagen-03.png)

### Imagen 4

Resumen de Newman: 277 peticiones, 763 aserciones, 0 fallos.

![Imagen 4](evidencias/imagen-04.png)

### Imagen 5

Pantalla de inicio de sesión.

![Imagen 5](evidencias/imagen-05.png)

### Imagen 6

Agenda de la administradora con «Próximos 7 días» y la lista de eventos.

![Imagen 6](evidencias/imagen-06.png)

### Imagen 7

Ficha de «Quince años de Lucía» en «Ver todo junto», con la franja de resumen.

![Imagen 7](evidencias/imagen-07.png)

### Imagen 8

Paso 7 «Resumen» de la «Boda de Valentina y Santiago»: todo listo.

![Imagen 8](evidencias/imagen-08.png)

### Imagen 9

Paso 7 de «Quince años de Sofía»: quedan 4 pendientes.

![Imagen 9](evidencias/imagen-09.png)

### Imagen 10

Error de negocio 409 al confirmar «Quince años de Sofía» con el contrato pendiente.

![Imagen 10](evidencias/imagen-10.png)

### Imagen 11

Nuevo evento, paso Fecha, diciembre de 2026: los días de un fin de semana ocupado no se pueden elegir.

![Imagen 11](evidencias/imagen-11.png)

### Imagen 12

Kafdrop: resumen del clúster y los 9 tópicos (4 tópicos, sus 4 DLT y el tópico interno __consumer_offsets).

![Imagen 12](evidencias/imagen-12.png)

### Imagen 13

Mensaje EVENTO_CONFIRMADO en eventos.confirmados, con su clave y el sobre común.

![Imagen 13](evidencias/imagen-13.png)

### Imagen 14

Campanita en 0, antes de ejecutar los recordatorios.

![Imagen 14](evidencias/imagen-14.png)

### Imagen 15

Campanita en 1 sin recargar la página, con el aviso «1 recordatorio enviado».

![Imagen 15](evidencias/imagen-15.png)

### Imagen 16

Panel de notificaciones: «Falta 1 día para su evento: Boda de Gabriela y Mateo», con la hora de llegada.

![Imagen 16](evidencias/imagen-16.png)

### Imagen 17

Sesión de la asistente: rol «Asistente» en la barra y sin el botón «Ejecutar recordatorios».

![Imagen 17](evidencias/imagen-17.png)

### Imagen 18

Lugares vistos por la asistente: solo consulta, sin botones de administración.

![Imagen 18](evidencias/imagen-18.png)

### Imagen 19

Con contratos-service detenido, la ficha muestra un mensaje genérico con «Ref: 220F8EE9».

![Imagen 19](evidencias/imagen-19.png)

### Imagen 20

Log del gateway con la misma referencia: contratos sin respuesta y error técnico 503.

![Imagen 20](evidencias/imagen-20.png)

### Imagen 21

Swagger de eventos-service: controladores de fechas, recordatorios y eventos, y sus esquemas.

![Imagen 21](evidencias/imagen-21.png)

### Imagen 22

Ficha de Lucía en un celular (390 px), sin desplazamiento horizontal.

![Imagen 22](evidencias/imagen-22.png)

### Imagen 23

Calendario de Nuevo evento en un celular (390 px).

![Imagen 23](evidencias/imagen-23.png)

### Imagen 24

Resultado de las pruebas de la interfaz con Vitest: 19 archivos y 146 pruebas pasadas.

![Imagen 24](evidencias/imagen-24.jpg)

### Imagen 25

Prueba C06, antes: «Quince años de Lucía» con 2 pagos, total abonado $2.610.000 y saldo pendiente $6.270.000.

![Imagen 25](evidencias/imagen-25.jpg)

### Imagen 26

Prueba C06: la misma ficha con 4 pagos (se suman $100.000 y $50.000), total abonado $2.760.000, saldo $6.120.000 y campanita en 2.

![Imagen 26](evidencias/imagen-26.jpg)

### Imagen 27

Prueba C06: contenedor kafka_broker detenido (Exited) en Docker Desktop; el registro muestra el apagado a las 13:38:22 UTC (8:38 hora local).

![Imagen 27](evidencias/imagen-27.jpg)

### Imagen 28

Prueba C06, con Kafka detenido: el abono de $900.000 queda registrado (5 pagos, saldo $5.220.000) y la campanita sigue en 2, sin su aviso todavía.

![Imagen 28](evidencias/imagen-28.jpg)

### Imagen 29

Prueba C06: kafka_broker de nuevo en ejecución (Running) a las 13:43:39 UTC (8:43 hora local), unos 5 minutos después del apagado.

![Imagen 29](evidencias/imagen-29.jpg)

### Imagen 30

Prueba C06, después: la campanita pasa a 3 y llega un solo aviso del abono de $900.000, sin avisos repetidos ni perdidos.

![Imagen 30](evidencias/imagen-30.jpg)
