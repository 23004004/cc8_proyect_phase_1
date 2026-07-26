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

Durante el desarrollo del proyecto se utilizó **Codex - GPT-5.5** como herramienta de apoyo. Su uso se enfocó en la planificación de la arquitectura, la adaptación del proyecto a los requisitos grupales y la revisión final del código.

La inteligencia artificial fue utilizada como apoyo técnico. Las decisiones, pruebas, integración y validación del funcionamiento fueron realizadas por los integrantes del equipo.

| Prompt | Etapa | Objetivo |
|---|---|---|
| Prompt 1 | Planificación | Solicitar una arquitectura justificada, buenas prácticas y explicaciones técnicas |
| Prompt 2 | Adaptación | Ajustar el proyecto a PRFC v3 y a los cambios establecidos por el grupo |
| Prompt 3 | Revisión final | Identificar código sin uso, mejorar la estructura y refactorizar sin alterar el funcionamiento |

---

## Prompt 1: planificación y arquitectura

Este prompt se utilizó al inicio para solicitar apoyo en la organización técnica del proyecto, evitando que la respuesta se limitara únicamente a generar código.

> Necesito ayuda para un desarrollo. No quiero únicamente código; quiero una arquitectura bien justificada, buenas prácticas y explicaciones técnicas.

---

## Prompt 2: adaptación a los cambios grupales y PRFC v3

Este prompt se utilizó cuando surgieron nuevos requerimientos dentro del grupo y fue necesario adaptar el avance inicial a las instrucciones oficiales y a la versión 3 del protocolo.

> Hay que hacer varias mejoras, ya que, como es un proyecto grupal, surgieron cambios y ya se encuentran disponibles las instrucciones oficiales. Nosotros habíamos comenzado a adelantar parte del desarrollo.
>
> El archivo `CapturaLaBandera.md` contiene la idea principal en la que se basa actualmente el proyecto. Sin embargo, ahora debemos seguir la documentación disponible en:
>
> `https://github.com/erickm13/CC8-Protocolo/tree/main`
>
> El archivo que nos interesa principalmente es `PRFC-VERSION-3`.
>
> Ayúdame a realizar los cambios necesarios, tomando también en cuenta las instrucciones del PDF. El proyecto ya está implementado parcialmente, pero existen varios cambios relacionados con la interfaz gráfica y el protocolo.

---

## Prompt 3: revisión y refactorización final

Este prompt se utilizó durante la etapa final del proyecto para mejorar la calidad y organización del código sin modificar las partes que ya habían sido probadas con otros equipos.

> Estamos en la etapa final del proyecto y quiero que lo revises con estos objetivos, en este orden:
>
> 1. Identifica código que no se usa, como métodos, clases o imports muertos, y lístalo antes de eliminarlo, explicando brevemente por qué consideras que no se utiliza.
> 2. Señala puntos de mejora de legibilidad y estructura, incluyendo nombres, organización de paquetes y clases, y duplicación de código.
> 3. Refactoriza donde tenga sentido, sin cambiar el comportamiento funcional del proyecto.
> 4. Añade comentarios breves, de una sola línea, únicamente en las partes clave o complejas, evitando párrafos largos.
> 5. No modifiques el protocolo, la lógica del servidor ni el funcionamiento del broadcast, debido a que estas partes ya fueron probadas correctamente con otros compañeros.
> 6. Si es posible, mejora la estructura actual del proyecto tratando de no romper nada.
> 7. Al finalizar, proporciona un resumen de todos los cambios realizados y verifica que el proyecto continúe compilando y funcionando correctamente.
>
> Trabaja de forma incremental y avísame si algún cambio requiere mi confirmación antes de aplicarlo.
---

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
