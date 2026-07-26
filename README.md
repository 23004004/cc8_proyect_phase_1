# PROYECTO

### IA Utilizada: Codex - GPT-5.5

#### PROMP:
Hay que hacer varias mejoras, ya que como es grupal surguieron cambios y ya estan las instrucciones, estabamos adelantando.
CapturaLaBandera.md fue la idea principal en eso esta basado actualmente ahora bien, lo que debemos de seguir ahora es este: https://github.com/erickm13/CC8-Protocolo/tree/main
Pero el que nos interesa es el archivo PRFC-VERSION-3, pero si ayudame a hacer los cambios necesario porfavor, y tomar en cuenta las intrucciones del pdf, lo que pasa es que ya esta implementado pero hay varios cambios de UI y de protocolo


# Captura la Bandera - PRFC v3

Proyecto Java Swing con arquitectura cliente-servidor, TCP sockets para partida y UDP broadcast para descubrimiento. La implementación actual sigue `PRFC-CC8-2026`, documento `3.0.0`, con `protocolVersion` byte `3`.

## Cambios principales de esta versión

- Protocolo binario big-endian con framing TCP `u16 length + payload`.
- Discovery por UDP broadcast en `discoveryPort`.
- Mapa continuo centrado en `(0, 0)`, sin grilla ni obstáculos.
- Círculo central, bandera en el centro y victoria al salir completamente del círculo con la bandera.
- Movimiento con `INPUT`: `NONE`, `UP`, `DOWN`, `LEFT`, `RIGHT`.
- Interacción explícita con `INTERACT`; tomar y robar bandera ya no ocurre automáticamente.
- Sin protección ni inmunidad después del robo.
- Servidor no juega: solo hospeda, valida y muestra/loguea estado.

## Ejecución local

Compilar:

```bash
make compile
```

Levantar servidor:

```bash
make run-server
```

Levantar servidor en otros puertos:

```bash
make run-server PORT=5000 DISCOVERY=5000
```

Levantar cliente con discovery UDP:

```bash
make run-client
```

Levantar cliente con conexión manual:

```bash
make run-client HOST=127.0.0.1 PORT=5000
```

En el servidor escribe `start` para iniciar el countdown y la partida.

Por compatibilidad con la clase, el discovery UDP queda por defecto en `5000`.
TCP y UDP pueden usar el mismo número de puerto sin conflicto.

## Controles del cliente

- `W`: moverse arriba.
- `A`: moverse izquierda.
- `S`: moverse abajo.
- `D`: moverse derecha.
- Soltar la tecla de movimiento: enviar `NONE`.
- `R` o barra espaciadora: enviar `INTERACT`.
- Cerrar la ventana: enviar `LEAVE`.

## Configuración

Archivo: `config/server.properties`

Parámetros PRFC v3 incluidos:

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
