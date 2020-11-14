const { ServiceBroker } = require('moleculer')

const broker = new ServiceBroker({
  transporter: 'nats://localhost:4222',
  nodeID: process.env.MOLECULER_NODE_ID,
  disableBalancer: true
})

async function tick() {
  await broker.call('accounts.create')
  await broker.call('payments.charge')
}

async function run() {
  broker.createService({
    name: 'accounts',
    actions: {
      create(ctx) {
        return '';
      },
      emit(ctx) {
        ctx.emit('test', { test: true })
      }
    }
  })
  
  broker.createService({
    name: 'payments',
    actions: {
      charge(ctx) {
        return '';
      }
    }
  })
  
  broker.createService({
  name: 'eventListener',  
    events: {
      '**'(ctx) {
        console.log('event!!!', ctx)
      },
      'RES.*'(ctx) {
        console.log('moleculer res', ctx)
      },
      'MOL.REQB.*'(ctx) {
        console.log('moleculer request', ctx)
      },
    }
  })

  await broker.start()

  await broker.waitForServices(["accounts", "payments"])

  setInterval(tick, 3000)
}

run().catch(console.error)