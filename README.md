# Capture the Flag - Proyecto Final

Es un juego multijugador desarrollado en **Java** con una arquitectura **cliente-servidor**, utilizando **TCP** para la comunicación principal y **UDP** para el descubrimiento automático de servidores.

El proyecto implementa un protocolo binario (**PRFC v3**) para la comunicación entre clientes y servidor, así como una interfaz gráfica desarrollada con **Swing** para la administración del servidor y la visualización del juego.

---

# Características

- Arquitectura cliente-servidor.
- Comunicación mediante TCP.
- Descubrimiento automático de servidores mediante UDP.
- Conexión manual por IP y puerto.
- Protocolo binario PRFC v3.
- Sincronización de jugadores mediante ticks.
- Lobby de jugadores.
- Cuenta regresiva antes del inicio de la partida.
- Captura y robo de la bandera.
- Interfaz gráfica para clientes.
- Panel de administración del servidor.
- Configuración mediante `server.properties`.

---

# Historial de uso de inteligencia artificial

Durante el desarrollo del proyecto se utilizó **Codex - GPT-5.5** como herramienta de apoyo en las diferentes etapas del desarrollo. Su uso estuvo orientado a la planificación de la arquitectura, la adaptación del proyecto a los requisitos oficiales del curso y la revisión final del código.

La inteligencia artificial fue utilizada únicamente como apoyo técnico. Las decisiones de diseño, implementación, integración, pruebas y validación del funcionamiento fueron realizadas por los integrantes del equipo.

| Prompt | Etapa | Objetivo |
|---|---|---|
| Prompt 1 | Planificación inicial | Diseñar la arquitectura del proyecto y definir la estructura base antes de comenzar la implementación. |
| Prompt 2 | Adaptación | Ajustar el proyecto a las instrucciones oficiales y migrar al protocolo PRFC v3. |
| Prompt 3 | Revisión final | Refactorizar, mejorar la estructura del código y eliminar elementos sin uso sin modificar el comportamiento del proyecto. |

---

## Prompt 1: planificación y arquitectura inicial

Este prompt se utilizó al inicio del proyecto para diseñar la arquitectura antes de escribir código. En ese momento el proyecto aún se encontraba en fase de planificación y la comunicación estaba planteada inicialmente mediante JSON. Posteriormente, la implementación fue adaptada al protocolo oficial **PRFC v3**.

> Necesito ayuda para un desarrollo. No quiero únicamente código, quiero arquitectura bien justificada, buenas prácticas y explicaciones técnicas.
>
> ### Contexto del proyecto
>
> Estoy desarrollando un proyecto universitario llamado **Captura la Bandera**.
>
> **Tecnologías obligatorias:**
>
> - Java 21.0.7 LTS
> - IntelliJ IDEA
> - Java Swing para la interfaz gráfica
> - TCP Sockets
> - Arquitectura Cliente-Servidor
> - Comunicación mediante JSON
> - UTF-8
> - Proyecto Java simple, sin utilizar Maven o Gradle.
>
> El objetivo principal **no** es la interfaz gráfica.
>
> El objetivo principal es desarrollar un servidor robusto, escalable y compatible con implementaciones realizadas por otros compañeros en diferentes lenguajes de programación.
>
> Debemos tomar en cuenta que nuestra implementación puede funcionar como servidor o como cliente, pero no como ambos al mismo tiempo.
>
> Todos los clientes deberán poder conectarse entre sí independientemente del lenguaje utilizado.
>
> Por esa razón, el protocolo de comunicación es el aspecto más importante.
>
> También adjuntaré el documento oficial donde se especifica completamente el protocolo que todos los grupos deben respetar.
>
> Ese documento tiene prioridad sobre cualquier decisión que propongas.
>
> ### Restricciones importantes
>
> La interfaz gráfica será sencilla utilizando Swing.
>
> No quiero utilizar motores gráficos.
>
> La prioridad absoluta será:
>
> 1. Arquitectura.
> 2. Comunicación mediante sockets.
> 3. Sincronización del juego.
> 4. Concurrencia.
> 5. Compatibilidad con otros lenguajes.
> 6. Rendimiento.
> 7. Finalmente, la interfaz.
>
> El servidor deberá soportar aproximadamente 50 jugadores simultáneos.
>
> Debe minimizar problemas de latencia.
>
> Debe evitar condiciones de carrera.
>
> Debe tener un diseño limpio.
>
> Debe ser fácilmente mantenible.
>
> Debe seguir principios SOLID cuando sea posible.
>
> El servidor será la única autoridad del juego.
>
> Los clientes nunca decidirán posiciones ni resultados.
>
> Toda la lógica debe ejecutarse en el servidor.
>
> La interfaz únicamente representará el estado recibido.
>
> ### Estructura inicial del proyecto
>
> ```text
> capture_the_flag/
> └── src/
>     ├── connect/
>     │   ├── Client.java
>     │   └── Server.java
>     ├── model/
>     │   └── Game.java
>     ├── view/
>     │   └── PanelGame.java
>     └── Main.java
> ```
>
> Actualmente estos archivos se encuentran prácticamente vacíos. Quiero que me ayudes a construir el proyecto paso a paso.
>
> ### Forma de trabajar
>
> No escribas todo el proyecto de una vez.
>
> Trabajaremos de forma incremental.
>
> Antes de escribir código:
>
> - Analiza el problema.
> - Identifica responsabilidades.
> - Propón una arquitectura.
> - Explica ventajas y desventajas.
> - Después implementaremos solamente una pequeña parte.
>
> Cuando propongas una solución:
>
> - Explica por qué.
> - Qué problema resuelve.
> - Cómo afectará al resto del proyecto.
>
> Siempre intenta que el código sea desacoplado.
>
> Evita clases gigantes.
>
> Evita duplicación.
>
> Utiliza nombres claros.
>
> Piensa como si este proyecto fuera software profesional.
>
> Si detectas que la estructura puede mejorar, proponla antes de escribir código.
>
> Cada decisión deberá considerar que posteriormente otros clientes escritos en Python, C#, Go, Node.js o C++ deberán poder conectarse sin modificar el protocolo.
>
> No asumas información que no aparezca en el documento oficial del protocolo.
>
> Si alguna regla entra en conflicto con una decisión de diseño, deberá respetarse el protocolo oficial.
>
> Nuestro primer objetivo será diseñar correctamente la arquitectura del proyecto antes de escribir cualquier línea de código.

---

## Prompt 2: adaptación a los cambios grupales y PRFC v3

Este prompt se utilizó cuando el proyecto ya tenía un avance inicial, pero surgieron nuevos requerimientos dentro del grupo y fue necesario adaptar la implementación al protocolo oficial **PRFC v3** y a las instrucciones actualizadas del curso.

> Hay que hacer varias mejoras, ya que como es grupal surgieron cambios y ya están las instrucciones; nosotros habíamos adelantado parte del desarrollo.
>
> `CapturaLaBandera.md` fue la idea principal en la que se basó inicialmente el proyecto. Sin embargo, ahora debemos seguir la documentación oficial disponible en:
>
> `https://github.com/erickm13/CC8-Protocolo/tree/main`
>
> El archivo que nos interesa es **PRFC-VERSION-3**.
>
> Ayúdame a realizar los cambios necesarios tomando en cuenta las instrucciones del PDF. El proyecto ya está implementado parcialmente, pero existen varios cambios relacionados con la interfaz gráfica y el protocolo.

---

## Prompt 3: revisión y refactorización final

Este prompt se utilizó durante la etapa final del proyecto para mejorar la calidad del código sin modificar las partes que ya habían sido probadas con otros equipos.

> Estamos en la etapa final del proyecto y quiero que lo revises con estos objetivos, en este orden:
>
> 1. Identifica código que no se usa (métodos, clases e imports muertos) y lístalo antes de eliminarlo, explicando brevemente por qué consideras que no se utiliza.
> 2. Señala puntos de mejora de legibilidad y estructura (nombres, organización de paquetes y clases, duplicación de código).
> 3. Refactoriza donde tenga sentido, sin cambiar el comportamiento funcional del proyecto.
> 4. Añade comentarios breves (una línea) únicamente en las partes clave o complejas, evitando párrafos largos.
> 5. No modifiques el protocolo, la lógica del servidor ni el funcionamiento del broadcast, ya que esas partes fueron probadas correctamente con otros compañeros.
> 6. Si es posible, mejora la estructura actual del proyecto tratando de no romper su funcionamiento.
> 7. Al finalizar, proporciona un resumen de todos los cambios realizados y verifica que el proyecto continúe compilando y funcionando correctamente.
>
> Trabaja de forma incremental y avísame si algún cambio requiere mi confirmación antes de aplicarlo.

# Ejecución local

## Compilar

```bash
make compile
```

## Levantar servidor

```bash
make run-server
```

## Levantar servidor en otros puertos

```bash
make run-server PORT=5000 DISCOVERY=5001
```

## Levantar servidor anunciándose en un puerto UDP adicional

```bash
make run-server PORT=5000 DISCOVERY=5001 EXTRA_DISCOVERY=5000
```

## Levantar cliente utilizando descubrimiento UDP

```bash
make run-client
```

## Levantar cliente mediante conexión manual

```bash
make run-client HOST=127.0.0.1 PORT=5000
```

Una vez conectado al lobby, inicia la partida utilizando el mecanismo definido por la aplicación (panel del servidor o consola, según la forma en que se esté ejecutando).

---

# Descubrimiento UDP

Por compatibilidad con la práctica del curso, el puerto de descubrimiento UDP predeterminado es:

```
5001
```

TCP y UDP pueden utilizar el mismo número de puerto sin generar conflictos, ya que corresponden a protocolos de transporte distintos.

Para utilizar únicamente el puerto principal de descubrimiento:

```properties
discoveryPort=5001
extraDiscoveryPorts=
```

Para mantener compatibilidad con otras configuraciones:

```properties
discoveryPort=5001
extraDiscoveryPorts=5000
```

---

# Controles del cliente

| Tecla | Acción |
|-------|--------|
| **W** | Mover hacia arriba |
| **A** | Mover hacia la izquierda |
| **S** | Mover hacia abajo |
| **D** | Mover hacia la derecha |
| **R** | Interactuar |
| **Espacio** | Interactuar |
| Cerrar ventana | Enviar `LEAVE` |

---

# Configuración

Archivo:

```
config/server.properties
```

Parámetros disponibles:

- `serverName`
- `mapSize`
- `circleRadius`
- `playerRadius`
- `spawnMargin`
- `playerSpeed`
- `interactionRadius`
- `tickIntervalMs`
- `countdownSeconds`
- `maximumPlayers`
- `serverPort`
- `discoveryPort`
- `extraDiscoveryPorts`

---

# Arquitectura

```
                +----------------+
                |    Cliente     |
                +----------------+
                        │
                  Solicitudes TCP
                        │
                        ▼
                +----------------+
                |    Servidor    |
                +----------------+
                        │
            Ejecuta la lógica del juego
                        │
                        ▼
                +----------------+
                |  GameSession   |
                +----------------+
                        │
             Actualiza el estado oficial
                        │
                        ▼
                +----------------+
                | Estado del juego |
                +----------------+
                        │
            Envía actualizaciones TCP
                        │
                        ▼
                +----------------+
                |    Clientes    |
                +----------------+
```

El servidor mantiene el estado oficial de la partida. Los clientes únicamente envían solicitudes de movimiento e interacción y representan gráficamente el estado recibido.

---

# Estructura del proyecto

```
src/
├── connect/
├── engine/
├── model/
├── protocol/
│   ├── core/
│   ├── dto/
│   ├── enums/
│   ├── mapping/
│   └── messages/
├── view/
└── Main.java
```

---

# Integrantes

- Carlos Alvarez
- Samantha Rodas
