# PROYECTO

### IA Utilizada: Codex - GPT-5.5

#### PROMP:
Estamos en la etapa final del proyecto y quiero que lo revises con estos objetivos, en este orden:

1.Identifica código que no se usa (métodos, clases, imports muertos) y lístalo antes de eliminarlo, explicando brevemente por qué crees que no se usa.
2.Señala puntos de mejora de legibilidad y estructura (nombres, organización de paquetes/clases, duplicación de código).
3.Refactoriza donde tenga sentido, sin cambiar el comportamiento funcional del proyecto.
4.Añade comentarios breves (una línea) solo en las partes clave o complejas, evitando párrafos largos.
5.El protocolo ya no lo toques por que con las pruebas con compañeros ya fue funcional y todo lo que tenga que ver logica del servidor o Broadcast
6.Si es posible trata de mejorar la estructura actual del proyecto tratando de no romper nada
7.Al final, dame un resumen de todos los cambios realizados y verifica que el proyecto siga compilando/funcionando correctamente.

Trabaja de forma incremental y avísame si hay algún cambio que requiera mi confirmación antes de aplicarlo.

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
make run-server PORT=5000 DISCOVERY=5001
```

Levantar servidor anunciando tambien en un puerto extra:

```bash
make run-server PORT=5000 DISCOVERY=5001 EXTRA_DISCOVERY=5000
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

Por compatibilidad con la clase, el discovery UDP queda por defecto en `5001`.
TCP y UDP pueden usar el mismo numero de puerto sin conflicto.

Para discovery rapido, deja solo `discoveryPort=5001`. Para compatibilidad con
otra version, agrega puertos separados por coma en `extraDiscoveryPorts`, por
ejemplo `extraDiscoveryPorts=5000`.

## Controles del cliente

- `W`: moverse arriba.
- `A`: moverse izquierda.
- `S`: moverse abajo.
- `D`: moverse derecha.
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
- `extraDiscoveryPorts`
