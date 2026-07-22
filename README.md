# PROYECTO

### IA Utilizada: Codex - GPT-5.4-Mini

#### PROMP:

Necesito ayuda para un desarrollo. No quiero únicamente código, quiero arquitectura bien justificada, buenas prácticas y explicaciones técnicas.
## Contexto del proyecto

Estoy desarrollando un proyecto universitario llamado captura la bandera.

**Tecnologías obligatorias:**

- Java 21.0.7 2025-04-15 LTS
- IntelliJ IDEA
- Java Swing para la interfaz gráfica
- TCP Sockets
- Arquitectura Cliente-Servidor
- Comunicación mediante JSON
- UTF-8
- Proyecto java simple sin usar maven o Gradle.

El objetivo principal **NO** es la interfaz gráfica.

El objetivo principal es desarrollar un servidor robusto, escalable y compatible con implementaciones realizadas por otros compañeros en diferentes lenguajes de programación.

Debemos de tomar en cuenta que nosotros podemos ser el servidor y tambien el cliente. peor no los dos al mismo tiempo.

Todos los clientes deberán poder conectarse entre sí independientemente del lenguaje utilizado.

Por esa razón el protocolo de comunicación es el aspecto más importante.

También adjuntaré el documento oficial donde se especifica completamente el protocolo que TODOS los grupos deben respetar.

Ese documento tiene prioridad sobre cualquier decisión que propongas.

## Restricciones importantes

La interfaz gráfica será sencilla utilizando Swing.

No quiero utilizar motores gráficos.

La prioridad absoluta será:

1. Arquitectura
2. Comunicación mediante sockets
3. Sincronización del juego
4. Concurrencia
5. Compatibilidad con otros lenguajes
6. Rendimiento
7. Finalmente la interfaz.

El servidor deberá soportar aproximadamente 50 jugadores simultáneos.

Debe minimizar problemas de latencia.

Debe evitar condiciones de carrera.

Debe tener un diseño limpio.

Debe ser fácilmente mantenible.

Debe seguir principios SOLID cuando sea posible.

El servidor será la única autoridad del juego.

Los clientes nunca decidirán posiciones ni resultados.

Toda la lógica debe ejecutarse en el servidor.

La interfaz únicamente representará el estado recibido.

## Estructura inicial del proyecto

capture_the_flag/

src/

connect/
Client.java
Server.java

model/
Game.java

view/
PanelGame.java

Main.java


Actualmente estos archivos se encuentran prácticamente vacíos.
Quiero que me ayudes a construir el proyecto paso a paso.

## Forma de trabajar

No escribas todo el proyecto de una vez.

Trabajaremos de forma incremental.

Antes de escribir código:

- analiza el problema
- identifica responsabilidades
- propone arquitectura
- explica ventajas y desventajas
- después implementaremos solamente una pequeña parte.

Cuando propongas una solución:

- explica por qué
- qué problema resuelve
- cómo afectará al resto del proyecto

Siempre intenta que el código sea desacoplado.

Evita clases gigantes.

Evita duplicación.

Usa nombres claros.

Piensa como si este proyecto fuera software profesional.

Si detectas que mi estructura puede mejorar, proponla primero antes de escribir código.

Cada decisión deberá considerar que posteriormente otros clientes escritos en Python, C#, Go, Node.js o C++ deberán poder conectarse sin modificar el protocolo.

No asumas información que no aparezca en el documento oficial del protocolo.

Si alguna regla entra en conflicto con una decisión de diseño, deberá respetarse el protocolo oficial.

Nuestro primer objetivo será diseñar correctamente la arquitectura del proyecto antes de escribir cualquier línea de código.


### Otro promp importante

Algo antes de seguir con el promp principal, arreglemos los logs y incluso el funcionamiento del client y funcionalidad de la UI,

Cuando inicio el client todo bien, cuando empiza el juego se genera un monton de log, pero demasiado por cada tick, entonces pensaba

mejorar ese tema, incluyendo la UI

Empieza el juego y me personaje este quieta y que se mueva no escribiendo el comando, si no que con el teclado con las letras AWSD

como un video juego, y para la acción de robar sea la R o la barra espaciadora lo mas facil, y el servidor me guarde un log o

algo asi de que es lo que esta haciendo cada usuario.


## Ejecución local

Compilar:

```bash
make compile
```

Levantar servidor:

```bash
make run-server PORT=5001
```

Levantar cliente local:

```bash
make run-client HOST=127.0.0.1 PORT=5001
```

Levantar cliente desde otra computadora de la misma red:

```bash
make run-client HOST=IP_DEL_SERVIDOR PORT=5001
```

## Controles del cliente

- `W`: cambiar dirección hacia arriba.
- `A`: cambiar dirección hacia la izquierda.
- `S`: cambiar dirección hacia abajo.
- `D`: cambiar dirección hacia la derecha.
- `R` o barra espaciadora: reenviar la dirección actual para intentar contacto con el portador.
- Cerrar la ventana: enviar `LEAVE` y cerrar la conexión.

El protocolo oficial no define una acción independiente para robar. El robo ocurre cuando el servidor detecta que un jugador intenta avanzar hacia la celda ocupada por el portador de la bandera.

## Logs

El cliente ya no imprime cada `GAME_STATE` recibido, porque ese mensaje llega en cada tick y satura la consola.

El servidor escribe eventos relevantes en:

```text
logs/server.log
```

Se registran conexiones, `JOIN`, rechazos, cambios de dirección, salidas, desconexiones, toma de bandera, robo de bandera e inicio/fin de partida.

El cliente escribe eventos relevantes en:

```text
logs/client.log
```

Para verlos en tiempo real:

```bash
tail -f logs/client.log
```

En el cliente se registran conexión TCP, `JOIN` enviado, `JOIN_ACCEPTED`, `JOIN_REJECTED`, cambios de dirección enviados, errores del servidor, eventos de bandera y cierre.

Para diagnosticar conexiones desde otra computadora:

- Si aparece `TCP_ACCEPTED` en `logs/server.log`, la conexión llegó al servidor Java.
- Si no aparece `TCP_ACCEPTED`, el problema está antes del servidor: IP incorrecta, puerto incorrecto, equipos en redes distintas o firewall bloqueando Java/puerto.
- Si aparece `TCP_ACCEPTED` pero no `JOIN_ACCEPTED`, revisar las líneas `PROTOCOL_ERROR` o `JOIN_REJECTED`.
