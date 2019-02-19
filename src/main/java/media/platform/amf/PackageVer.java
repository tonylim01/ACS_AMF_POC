package media.platform.amf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Date;
import java.util.Properties;

public class PackageVer {

    private static final Logger logger = LoggerFactory.getLogger(PackageVer.class);

    final static String VERSION_INFO_FILE = "/ver.properties";
    final static String BUILD_INFO_FILE = "/buildinfo.properties";
    final static String PROC_NAME = "amf";

    public static void write() {
        try {
            InputStream in = PackageVer.class.getResourceAsStream(VERSION_INFO_FILE);
            Properties verProperties = new Properties();
            try {
                verProperties.load(in);
            } finally {
                try {
                    in.close();
                } catch (IOException e) {
                    logger.error(e.getMessage());
                }
            }

            String packageVersion = AppInstance.getInstance().getUserConfig().getVersionPath() + PROC_NAME + ".ver";
            logger.info("Version file [{}]", packageVersion);

            File f = new File(packageVersion);

            File dir = f.getParentFile();
            if (dir != null) {
                if (!dir.exists()) {
                    logger.info("mkdirs [{}] [{}]", dir, dir.mkdirs());
                }
            }

            PrintWriter pw = null;

            String pkgVersion;// = getpkgver();

            StringBuilder versionInfo = new StringBuilder();

            pkgVersion = verProperties.getProperty("package.version");
            String pkgDate = verProperties.getProperty("package.date");

            logger.info("Package version [{}] date [{}]", pkgVersion, pkgDate);

            if (pkgDate == null) {

                Properties p = new Properties();
                /*InputStream*/
                in = PackageVer.class.getResourceAsStream(BUILD_INFO_FILE);
                if (in != null)
                    try {
                        p.load(in);
                        pkgDate = p.getProperty("build");
                    } catch (IOException e) {
                        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                    } finally {
                        try {
                            in.close();
                        } catch (IOException e) {
                            logger.error(e.getMessage());
                        }
                    }

            }

            versionInfo.append(pkgVersion);
            versionInfo.append(",");
            versionInfo.append(pkgDate);

            //pw = new PrintWriter(new FileWriter(f, false), true);
            pw = new PrintWriter(f);
            try {
                pw.print(versionInfo.toString());
                logger.info("Version [{}]", versionInfo.toString());
            } finally {
                pw.close();
            }

        } catch (IOException e) {
            logger.error(e.getMessage());
        }
    }
}