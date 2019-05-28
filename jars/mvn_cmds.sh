mvn install:install-file \
    -Dfile=jtrader-common-debug-20190528.jar \
    -DgroupId=net.jtrader -DartifactId=jtrader-common -Dversion=1.0.0.0-20190528 -Dpackaging=jar

#JCTP 6.3.11
mvn install:install-file \
	-Dfile=jctp-win32_x86-6.3.11_20180109-debug.jar \
	-DgroupId=net.jtrader -DartifactId=jctp -Dversion=6.3.11-20180109-win32_x86 -Dpackaging=jar

mvn install:install-file \
	-Dfile=jctp-win32_x64-6.3.11_20180109-debug.jar \
	-DgroupId=net.jtrader -DartifactId=jctp -Dversion=6.3.11-20180109-win32_x64 -Dpackaging=jar

mvn install:install-file \
	-Dfile=jctp-linux_x64-6.3.11_20180109-debug.jar \
	-DgroupId=net.jtrader -DartifactId=jctp -Dversion=6.3.11-20180109-linux_x64 -Dpackaging=jar

#JCTP 6.3.13
mvn install:install-file \
    -Dfile=jctp-linux_x64-6.3.13_20181119-debug.jar \
    -DgroupId=net.jtrader -DartifactId=jctp -Dversion=6.3.13_20181119-linux_x64 -Dpackaging=jar

mvn install:install-file \
    -Dfile=jctp-win32_x64-6.3.13_20181119-debug.jar \
    -DgroupId=net.jtrader -DartifactId=jctp -Dversion=6.3.13_20181119-win32_x64 -Dpackaging=jar

mvn install:install-file \
    -Dfile=jctp-win32_x86-6.3.13_20181119-debug.jar \
    -DgroupId=net.jtrader -DartifactId=jctp -Dversion=6.3.13_20181119-win32_x86 -Dpackaging=jar

#JCTP 6.3.15
mvn install:install-file \
    -Dfile=jctp-linux_x64-6.3.15_20190220-debug.jar \
    -DgroupId=net.jtrader -DartifactId=jctp -Dversion=6.3.15_20190220-linux_x64 -Dpackaging=jar

mvn install:install-file \
    -Dfile=jctp-win32_x64-6.3.15_20190220-debug.jar \
    -DgroupId=net.jtrader -DartifactId=jctp -Dversion=6.3.15_20190220-win32_x64 -Dpackaging=jar

mvn install:install-file \
    -Dfile=jctp-win32_x86-6.3.15_20190220-debug.jar \
    -DgroupId=net.jtrader -DartifactId=jctp -Dversion=6.3.15_20190220-win32_x86 -Dpackaging=jar
