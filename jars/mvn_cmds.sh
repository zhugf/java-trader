mvn install:install-file \
	-Dfile=jtrader-common-debug-20180920.jar \
	-DgroupId=net.jtrader -DartifactId=jtrader-common -Dversion=1.0.0.0-20180920 -Dpackaging=jar

mvn install:install-file \
	-Dfile=jctp-win32_x86-6.3.11_20180109-debug.jar \
	-DgroupId=net.jtrader -DartifactId=jctp -Dversion=6.3.11-20180109-win32_x86 -Dpackaging=jar

mvn install:install-file \
	-Dfile=jctp-win32_x64-6.3.11_20180109-debug.jar \
	-DgroupId=net.jtrader -DartifactId=jctp -Dversion=6.3.11-20180109-win32_x64 -Dpackaging=jar

mvn install:install-file \
	-Dfile=jctp-linux_x64-6.3.11_20180109-debug.jar \
	-DgroupId=net.jtrader -DartifactId=jctp -Dversion=6.3.11-20180109-linux_x64 -Dpackaging=jar
