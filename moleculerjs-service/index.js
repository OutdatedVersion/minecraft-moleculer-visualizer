const { ServiceBroker } = require("moleculer");

let brokerCounter = 0;
function createBroker() {
  return new ServiceBroker({
    transporter: process.env.MOLECULER_TRANSPORT_URL || "nats://localhost:4222",
    nodeID: `moleculerjs-fixture-${brokerCounter++}`,
    disableBalancer: true,
    internalServices: brokerCounter === 1 ? true : false,
  });
}

const brokers = [
  createBroker(),
  createBroker(),
  createBroker(),
  createBroker(),
  createBroker(),
  createBroker(),
  // createBroker(),
  // createBroker(),
  // createBroker(),
];

const callables = [
  "v1.tfrs.check",
  "v4.pilots.create",
  "v4.pilots.isCurrent",
  "v0.schedules.assign",
];
async function tick() {
  if (Math.random() > 0.7) {
    console.log(`[${Date.now()}] skipping: random chance to skip`);
    return;
  }

  const broker = brokers[Math.floor(Math.random() * brokers.length)];
  const action = callables[Math.floor(Math.random() * callables.length)];
  console.log(`[${Date.now()}] calling ${action} from ${broker.nodeID}`);

  try {
    // fire and forget
    broker.call(action);
  } catch (error) {}
}

async function run() {
  for (const broker of brokers) {
    broker.createService({
      name: "tfrs",
      version: 1,
      actions: {
        check(ctx) {
          ctx.emit("tfr.created", { source: "faa" });
        },
      },
    });

    broker.createService({
      name: "schedules",
      version: 0,
      actions: {
        async assign(ctx) {
          if (Math.random() < 0.25) {
            return ctx.call("v4.pilots.isCurrent", { series: "757" });
          } else {
            console.log(`[${Date.now()}] timed out request '${ctx.id}'`);
          }
        },
      },
    });

    broker.createService({
      name: "pilots",
      version: 4,
      actions: {
        create(ctx) {
          return "";
        },
        isCurrent(ctx) {
          return Math.random() > 0.5;
        },
      },
    });

    broker.createService({
      name: "eventListener",
      events: {
        "**"(ctx) {},
        "RES.*"(ctx) {},
        "MOL.REQB.*"(ctx) {},
      },
    });
  }
  await Promise.all(brokers.map((b) => b.start()));

  setInterval(tick, 1000);
}

run().catch((error) => {
  console.error("uncaught error", error);
  process.exit(1);
});
