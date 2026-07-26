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

# IA utilizada

**Modelo:** Codex - GPT-5.5

### Prompt utilizado

Durante la etapa final del proyecto se utilizó Codex para apoyar el proceso de refactorización y mejora del código, utilizando el siguiente prompt:

> Estamos en la etapa final del proyecto y quiero que lo revises con estos objetivos, en este orden:
>
> 1. Identifica código que no se usa (métodos, clases, imports muertos) y lístalo antes de eliminarlo, explicando brevemente por qué crees que no se usa.
> 2. Señala puntos de mejora de legibilidad y estructura (nombres, organización de paquetes/clases, duplicación de código).
> 3. Refactoriza donde tenga sentido, sin cambiar el comportamiento funcional del proyecto.
> 4. Añade comentarios breves (una línea) solo en las partes clave o complejas, evitando párrafos largos.
> 5. El protocolo ya no lo toques porque con las pruebas con compañeros ya fue funcional, así como toda la lógica del servidor y el Broadcast.
> 6. Si es posible, mejora la estructura actual del proyecto tratando de no romper nada.
> 7. Al final, dame un resumen de todos los cambios realizados y verifica que el proyecto siga compilando y funcionando correctamente.
>
> Trabaja de forma incremental y avísame si hay algún cambio que requiera mi confirmación antes de aplicarlo.

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
