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

- Auth Service: autenticación de la organizadora y generación de tokens JWT.
- Clientes Service: gestión de la información de los clientes.
- Eventos Service: creación, consulta, confirmación y cancelación de eventos, además de la gestión de fechas.
- Finanzas Service: gestión de cotizaciones, pagos y balances.
- Contratos Service: generación y gestión de contratos.
- Servicios Adicionales Service: administración y asignación de servicios adicionales.
- Lugares Service: gestión de salones y fincas.
- Proveedores Service: administración y asignación de proveedores.
- Logística Service: generación de requerimientos logísticos y control de compras.
- Notificaciones Service: procesamiento de notificaciones generadas por eventos de negocio.
- Auditoría Service: registro de las operaciones importantes para mantener la trazabilidad.

Cada microservicio mantiene una responsabilidad específica, evitando concentrar toda la lógica del sistema en un único servicio.

## Comunicación entre servicios

La comunicación se divide en dos tipos:

### Comunicación síncrona

Las operaciones que requieren una respuesta inmediata utilizan comunicación REST. Por ejemplo, la creación de un cliente, consulta de un evento, registro de un pago o consulta de una cotización.

Las APIs REST de los servicios serán documentadas utilizando OpenAPI.

### Comunicación asíncrona

Para operaciones que generan procesos posteriores e independientes se utiliza Apache Kafka.

Se definieron inicialmente los siguientes eventos:

- EVENTO_CONFIRMADO
- PAGO_REGISTRADO
- SERVICIO_ADICIONAL_AGREGADO

Estos eventos son publicados por los servicios responsables de generar el cambio y posteriormente consumidos por otros servicios que necesitan reaccionar ante dicha operación.

Por ejemplo, cuando se confirma un evento, el Eventos Service publica EVENTO_CONFIRMADO. El Logística Service consume este evento para calcular los requerimientos del evento, el Notificaciones Service puede generar el aviso correspondiente y el Auditoría Service registra la operación.

De esta manera, Kafka no se utiliza únicamente como mecanismo de comunicación, sino para implementar casos de uso asíncronos reales y desacoplar procesos entre los microservicios.

## Kafka

Los principales tópicos definidos son:

- eventos.confirmados
- pagos.registrados
- servicios-adicionales.agregados

Los productores principales son Eventos Service, Finanzas Service y Servicios Adicionales Service.

Los consumidores principales son Logística Service, Notificaciones Service, Auditoría Service y, dependiendo del evento, Finanzas Service y Eventos Service.

Los eventos utilizan como referencia principal el identificador del evento para facilitar la relación y el orden de los mensajes asociados a una misma operación.

## Decisiones principales de arquitectura

### Uso de microservicios

Se decidió utilizar microservicios para separar las responsabilidades del negocio. Esta división permite que cada servicio pueda evolucionar, mantenerse y escalar de manera independiente.

### GraphQL Gateway

Se utiliza un GraphQL Gateway como punto de entrada para el cliente. Esto permite centralizar el acceso al sistema y consultar información proveniente de diferentes microservicios mediante una única interfaz.

### APIs REST

Los microservicios utilizan APIs REST para las operaciones síncronas. Estas APIs serán documentadas mediante contratos OpenAPI para definir claramente endpoints, parámetros, solicitudes y respuestas.

### Apache Kafka

Kafka se incorpora para los procesos que pueden ejecutarse de manera asíncrona. Los eventos de negocio permiten que diferentes servicios reaccionen a una operación sin que el servicio que la originó tenga que ejecutar directamente todos los procesos posteriores.

### Separación de responsabilidades

Cada microservicio administra un dominio específico. Por ejemplo, Finanzas se encarga de cotizaciones y pagos, mientras que Logística se encarga de requerimientos operativos. Esto evita que un único servicio concentre todas las responsabilidades.

### Bases de datos

Cada dominio mantiene la información relacionada con su propia responsabilidad, evitando una dependencia directa de todos los microservicios sobre una única base de datos.

## Flujo asíncrono principal

Uno de los principales casos de uso asíncronos corresponde a la confirmación de un evento.

La organizadora confirma una reserva mediante el sistema. El Eventos Service valida las condiciones correspondientes, actualiza el evento a estado RESERVADO y la fecha a estado OCUPADA. Después de completar la operación, publica el evento EVENTO_CONFIRMADO en Kafka.

El Logística Service consume este evento y aplica las reglas de negocio para calcular los requerimientos necesarios, como meseros, personal de cocina, transporte e insumos.

Al mismo tiempo, el Notificaciones Service puede procesar el evento para generar una notificación de confirmación y el Auditoría Service puede registrar la operación.

Este flujo permite que los procesos posteriores se ejecuten de manera independiente y no bloqueen la operación principal.

## Estado inicial del proyecto

Actualmente el repositorio contiene la definición inicial de la arquitectura, las decisiones principales y los contratos de comunicación entre los servicios.

La implementación de los microservicios, las APIs REST, los consumidores y productores de Kafka se desarrollará progresivamente durante las siguientes etapas del proyecto.

## Tecnologías principales

- Arquitectura de microservicios
- GraphQL
- APIs REST
- OpenAPI
- Apache Kafka
- JWT
- Bases de datos independientes por dominio

## Organización del repositorio

La estructura del repositorio se irá completando a medida que avance la implementación del proyecto. Inicialmente se contemplan espacios para:

- Documentación
- Contratos OpenAPI
- Microservicios
- Configuración de Kafka
- Diagramas de arquitectura
- Pruebas
