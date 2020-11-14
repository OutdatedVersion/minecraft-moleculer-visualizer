package com.outdatedversion.moleculer.service;

import org.slf4j.Logger;
import services.moleculer.eventbus.Group;
import services.moleculer.eventbus.Listener;
import services.moleculer.eventbus.Subscribe;
import services.moleculer.service.Name;
import services.moleculer.service.Service;

/**
 * @author Ben Watkins
 * @since Jul/06/2020
 */
@Name("watchdog")
public class WatchdogService extends Service {

    private Logger logger;

    public WatchdogService(Logger logger) {
        this.logger = logger;
    }

//    @Subscribe("**")
//    // @Group("")
//    Listener allEvents = ctx -> {
//        this.logger.info("Received event: {}", ctx);
//    };
//
//    @Subscribe("$**")
//    // @Group("")
//    Listener internalEvents = ctx -> {
//        this.logger.info("Received internal event: {}", ctx);
//    };

}
