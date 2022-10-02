const { ServiceBroker, Errors: MoleculerErrors } = require("moleculer");
const { randomUUID } = require("crypto");
const { setTimeout } = require("timers/promises");

const transporterUrl =
  process.env.MOLECULER_TRANSPORT_URL || "nats://localhost:4222";

/**
 * @type {ServiceBroker[]}
 */
const brokers = [];
const services = [
  {
    name: "auth",
    version: 3,
    actions: {
      isAuthorized(ctx) {
        if (Math.random() < 0.4) {
          throw new MoleculerErrors.MoleculerError("tragedy struck");
        }

        if (Math.random() < 0.3) {
          ctx.call("v3.auth.login");
        }
      },
      login(ctx) {
        ctx.emit("auth.login");
      },
    },
  },
  {
    name: "tfrs",
    version: 1,
    actions: {
      check(ctx) {
        ctx.emit("tfr.created", { source: "faa" });
      },
    },
  },
  {
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
  },
  {
    name: "pilots",
    version: 4,
    actions: {
      create(ctx) {
        if (Math.random() < 0.4) {
          throw new MoleculerErrors.ValidationError("bad input");
        }

        return "";
      },
      isCurrent(ctx) {
        return Math.random() > 0.5;
      },
    },
  },
];

const callables = [
  "v3.auth.isAuthorized",
  "v1.tfrs.check",
  "v4.pilots.create",
  "v4.pilots.isCurrent",
  "v0.schedules.assign",
];

async function startBroker() {
  // emulate a 1 pod:1 service kubernetes deployment
  const svc = services[Math.floor(Math.random() * services.length)];
  const broker = new ServiceBroker({
    transporter: transporterUrl,
    nodeID: `${svc.name}-v${svc.version}-${randomUUID().substring(0, 8)}`,
    disableBalancer: true,
    internalServices: Math.random() < 0.4 ? true : false,
  });
  broker.createService(svc);
  await broker.start();
  return broker;
}

let isTicking = false;
async function tick() {
  if (isTicking) {
    console.log(`[${Date.now()}] skipping: behind on tick`);
    return;
  }

  try {
    isTicking = true;
    if (Math.random() < 0.2) {
      console.log(`[${Date.now()}] skipping: random chance to skip`);
      return;
    }

    if (Math.random() < 0.04) {
      console.log(`[${Date.now()}] adding an instance`);
      startBroker().then((b) => brokers.push(b));
      return;
    }

    if (brokers.length === 0) {
      console.log(`[${Date.now()}] skipping: no brokers available`);
      return;
    }

    const brokerIdx = Math.floor(Math.random() * brokers.length);
    const broker = brokers[brokerIdx];

    if (Math.random() < 0.04) {
      console.log(`[${Date.now()}] removing ${broker.nodeID}`);
      brokers.splice(brokerIdx, 1);
      await broker.stop();
      return;
    }

    const action = callables[Math.floor(Math.random() * callables.length)];

    const times = Math.random() < 0.2 ? Math.floor(Math.random() * 3) : 1;

    for (let i = 0; i < times; i++) {
      console.log(`[${Date.now()}] calling ${action} from ${broker.nodeID}`);
      try {
        // fire and forget
        broker.call(action);
      } catch (error) {}
    }
  } finally {
    isTicking = false;
  }
}

async function run() {
  // need something listening to events for moleculer to emit them
  const eventingBroker = new ServiceBroker({
    transporter: transporterUrl,
    nodeID: "eventing",
  });
  eventingBroker.createService({
    name: "eventListener",
    events: {
      "**"(ctx) {},
      "RES.*"(ctx) {},
      "MOL.REQB.*"(ctx) {},
    },
  });
  await eventingBroker.start();

  const starts = [];
  for (let i = 0; i < 8; i++) {
    starts.push(startBroker());
  }
  await Promise.all(starts);

  // hope discovery finishes in time
  await setTimeout(5000);

  setInterval(tick, 200);
}

process.on("uncaughtException", (error) =>
  console.error("Uncaught exception (process)", error)
);
process.on("unhandledRejection", (error) =>
  console.error("Uncaught rejection (process)", error)
);

run().catch((error) => {
  console.error("uncaught error", error);
  process.exit(1);
});
