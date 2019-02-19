/*
 * Copyright (C) 2019. Uangel Corp. All rights reserved.
 *
 */
package media.platform.amf.oam;

import com.uangel.svc.oam.AlarmGen;
import com.uangel.svc.oam.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class AlarmHandler {
    private static final Logger logger = LoggerFactory.getLogger(AlarmHandler.class);

    AlarmGen alarmGen = new AlarmGen(new File(System.getProperty("user.home"), "/HOME/alarm/amf.alarm")); //todo

    public void onApplicationEvent(String key, Level level, String reason) {
        logger.info("Alarm [{}] level [{}] reason [{}]", key, level, reason);
        alarmGen.alarm(key, level, reason);
    }
}
