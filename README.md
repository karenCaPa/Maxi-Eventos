# Maxi Eventos

## Descripción

Maxi Eventos es una plataforma digital orientada a la gestión integral de eventos sociales. El sistema permite administrar clientes, fechas, eventos, cotizaciones, contratos, pagos, servicios adicionales, lugares, proveedores y requerimientos logísticos.

La plataforma busca centralizar la información relacionada con cada evento y facilitar el trabajo de la organizadora, permitiendo controlar desde la reserva inicial hasta la planificación y seguimiento de los diferentes servicios necesarios para la realización del evento.

La solución se plantea mediante una arquitectura basada en microservicios, donde cada servicio tiene una responsabilidad específica dentro del negocio. La comunicación externa se realiza mediante un GraphQL Gateway, mientras que los microservicios exponen APIs REST documentadas mediante OpenAPI.

## Objetivo

Desarrollar una arquitectura de software que permita gestionar de manera organizada y escalable el proceso de planificación de eventos, separando las principales responsabilidades del negocio y permitiendo la comunicación síncrona y asíncrona entre los diferentes componentes del sistema.

## Arquitectura

La arquitectura está basada en microservicios. El cliente externo se comunica con el sistema mediante un GraphQL Gateway, que funciona como punto de entrada y dirige las solicitudes hacia los servicios correspondientes.

Los principales servicios definidos son:

- **Auth Service:** autenticación de la organizadora y generación de tokens JWT.
- **Clientes Service:** gestión de la información de los clientes.
- **Eventos Service:** creación, consulta, confirmación y cancelación de eventos, además de la gestión de fechas.
- **Finanzas Service:** gestión de cotizaciones, pagos, balances y servicios adicionales asociados a los eventos.
- **Contratos Service:** generación y gestión de contratos y documentos relacionados.
- **Lugares Service:** gestión de salones, fincas y lugares para los eventos.
- **Proveedores Service:** administración y asignación de proveedores a los eventos.
- **Logística Service:** generación de requerimientos logísticos y control de compras.

Cada microservicio mantiene una responsabilidad específica, evitando concentrar toda la lógica del sistema en un único servicio.

Los procesos de notificación y auditoría se manejan mediante consumidores de eventos de Kafka, permitiendo procesar estas operaciones de manera asíncrona sin incorporarlos inicialmente como microservicios principales.

## Comunicación entre servicios

La comunicación se divide en dos tipos:

### Comunicación síncrona

Las operaciones que requieren una respuesta inmediata utilizan comunicación REST. Por ejemplo, la creación de un cliente, consulta de un evento, registro de un pago o consulta de una cotización.

Las APIs REST de los servicios serán documentadas utilizando OpenAPI.

### Comunicación asíncrona

Para operaciones que generan procesos posteriores e independientes se utiliza Apache Kafka.

Se definieron inicialmente los siguientes eventos:

- `EVENTO_CONFIRMADO`
- `PAGO_REGISTRADO`
- `SERVICIO_ADICIONAL_AGREGADO`

Estos eventos son publicados por los servicios responsables de generar el cambio y posteriormente consumidos por los componentes que necesitan reaccionar ante dicha operación.

Por ejemplo, cuando se confirma un evento, el Eventos Service publica `EVENTO_CONFIRMADO`. El Logística Service consume este evento para calcular los requerimientos del evento. Adicionalmente, el componente de Notificaciones puede procesar el evento para generar el aviso correspondiente y el componente de Auditoría puede registrar la operación.

De esta manera, Kafka no se utiliza únicamente como mecanismo de comunicación, sino para implementar casos de uso asíncronos reales y desacoplar procesos posteriores de la operación principal.

## Kafka

Los principales tópicos definidos son:

- `eventos.confirmados`
- `pagos.registrados`
- `servicios-adicionales.agregados`

### Productores

Los principales productores de eventos son:

- **Eventos Service:** publica `EVENTO_CONFIRMADO`.
- **Finanzas Service:** publica `PAGO_REGISTRADO`.
- **Finanzas Service:** publica `SERVICIO_ADICIONAL_AGREGADO`.

### Consumidores

Los consumidores dependen del tipo de evento recibido. Entre los principales se encuentran:

- **Logística Service:** procesa `EVENTO_CONFIRMADO` para calcular los requerimientos operativos del evento.
- **Contratos Service:** puede procesar `SERVICIO_ADICIONAL_AGREGADO` para gestionar la información necesaria para el anexo de servicios adicionales.
- **Notificaciones:** procesa eventos de negocio para generar las notificaciones correspondientes.
- **Auditoría:** procesa eventos para registrar las operaciones importantes y mantener la trazabilidad.
- **Eventos Service:** puede consumir `PAGO_REGISTRADO` cuando el registro de un pago requiera una actualización relacionada con el estado del evento.

Los eventos utilizan como referencia principal el identificador del evento para facilitar la asociación de los mensajes con la operación de negocio correspondiente.

## Decisiones principales de arquitectura

### Uso de microservicios

Se decidió utilizar microservicios para separar las responsabilidades del negocio. Esta división permite que cada servicio pueda evolucionar, mantenerse y escalar de manera independiente.

### GraphQL Gateway

Se utiliza un GraphQL Gateway como punto de entrada para el cliente. Esto permite centralizar el acceso al sistema y consultar información proveniente de diferentes microservicios mediante una única interfaz.

### APIs REST

Los microservicios utilizan APIs REST para las operaciones síncronas. Estas APIs serán documentadas mediante contratos OpenAPI para definir claramente endpoints, parámetros, solicitudes y respuestas.

### Apache Kafka

Kafka se incorpora para los procesos que pueden ejecutarse de manera asíncrona. Los eventos de negocio permiten que diferentes componentes reaccionen a una operación sin que el servicio que la originó tenga que ejecutar directamente todos los procesos posteriores.

### Separación de responsabilidades

Cada microservicio administra un dominio específico. Por ejemplo, Finanzas se encarga de cotizaciones, pagos y servicios adicionales, mientras que Logística se encarga de los requerimientos operativos.

Esta separación permite distribuir las responsabilidades del sistema y evita concentrar toda la lógica de negocio en un único servicio.

### Base de datos

El proyecto utiliza una única base de datos PostgreSQL compartida para la persistencia de la información.

Aunque la persistencia se encuentra centralizada, cada microservicio mantiene la responsabilidad lógica sobre las entidades y operaciones correspondientes a su dominio.

Esta decisión permite mantener el modelo de datos relacional y sus relaciones de integridad, de acuerdo con el modelo inicial definido para el proyecto, mientras se conserva la separación de responsabilidades entre los microservicios.

## Flujo asíncrono principal

Uno de los principales casos de uso asíncronos corresponde a la confirmación de un evento.

La organizadora confirma una reserva mediante el sistema. El Eventos Service valida las condiciones correspondientes, actualiza el evento a estado `RESERVADO` y la fecha a estado `OCUPADA`.

Una vez completada correctamente la operación, el Eventos Service publica el evento `EVENTO_CONFIRMADO` en el tópico `eventos.confirmados` de Kafka.

El evento contiene información necesaria para identificar la operación, como el identificador del evento, el cliente, la fecha y el tipo de evento.

Posteriormente, los consumidores procesan el evento de forma independiente. El Logística Service consume el evento y aplica las reglas de negocio para calcular los requerimientos necesarios, como cantidad de meseros, personal de cocina y transporte.

Al mismo tiempo, el componente de Notificaciones puede procesar el evento para generar una notificación de confirmación, mientras que el componente de Auditoría registra la operación para mantener la trazabilidad del sistema.

Estos procesos se ejecutan de forma asíncrona y no necesitan bloquear la operación principal de confirmación.

De manera similar, el registro de un pago y la incorporación de un servicio adicional pueden generar eventos que serán procesados posteriormente por los componentes correspondientes.

## Estado inicial del proyecto

Actualmente el repositorio contiene la definición inicial de la arquitectura, las decisiones principales y los contratos de comunicación entre los servicios.

La implementación de los microservicios, las APIs REST, los productores y consumidores de Kafka y los demás componentes de la arquitectura se desarrollará progresivamente durante las siguientes etapas del proyecto.

## Tecnologías principales

- Arquitectura de microservicios
- GraphQL
- APIs REST
- OpenAPI
- Apache Kafka
- PostgreSQL
- JWT
- Docker Compose

## Organización del repositorio

La estructura del repositorio se irá completando a medida que avance la implementación del proyecto. Inicialmente se contemplan espacios para:

- Documentación
- Contratos OpenAPI
- Microservicios
- Configuración de infraestructura
- Configuración de Kafka
- Diagramas de arquitectura
- Pruebas
