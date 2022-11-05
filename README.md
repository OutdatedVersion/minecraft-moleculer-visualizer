# minecraft moleculer visualizer

Server-side Bukkit plugin that visualizes the state of
[Moleculer](https://moleculer.services) instances.

## Key

### Hosts

- Cow: A ServiceBroker instance (https://moleculer.services/docs/0.14/api/service-broker.html)
- Baby panda: A user service on a broker
- Baby pig: An internal service on a broker

### Discovery / Registry

This project supports only the in-process registry mechanism.

- Hearts: A heartbeat. Every `ServiceBroker` will listen for these to inform its registry.
- Blue lines: A discovery sponsor packet. When a new `ServiceBroker` connects it will emit a discovery packet that every other `ServiceBroker` must acknowledge (this lets the original broker know who else is there).

### Requests

- Yellow line: An outgoing request. This line will trace from the requestor to whichever `ServiceBroker` instance picked up the request.
- Green line: An outgoing, successful, response. This line will trace back to the `ServiceBroker` that brokered the original request.
- Red line: An outgoing, unsuccessful, response. This line will trace back to the `ServiceBroker` that brokered the original request.
- TNT: A neglected request---when a `ServiceBroker` didn't end up hearing back from any other broker on a request it announced.

### Eventing

- Firework: An event

## Video

[![Watch the video](https://i.ytimg.com/vi/atvKUGFIDAo/hqdefault.jpg)](https://www.youtube.com/watch?v=atvKUGFIDAo)
