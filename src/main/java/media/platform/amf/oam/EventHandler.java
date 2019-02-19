/*
 * Copyright (C) 2019. Uangel Corp. All rights reserved.
 *
 */

package media.platform.amf.oam;

import com.uangel.svc.oam.EventGenerator;
import com.uangel.svc.oam.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EventListener;

public class EventHandler {
    private static final Logger logger = LoggerFactory.getLogger(EventListener.class);

    EventGenerator eventGen=new EventGenerator(System.getProperty("user.home") + "/HOME/event/server.event");

    public void onApplicationEvent(Level level, String msg) {
        logger.info("Event level [{}] msg [{}]", level, msg);
        eventGen.generate(level, msg);
    }
}