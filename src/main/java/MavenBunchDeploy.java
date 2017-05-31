import java.io.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

/**
 * 注意:
 * org.apache.maven.plugins:maven-deploy-plugin:2.7:deploy-file
 * 此插件版本不允许直接从[本地仓库]直接发布到私服. 需要建一个tmp.
 * <p>
 * Created by guodage on 2017/5/31.
 */
public class MavenBunchDeploy {

    String releasesURL = "";
    String snapshotsURL = "";
    String thirdpartyURL = "";//暂时还用不着,因为现在deployList中只拿到了"自洽"检查出来的依赖

    public static void main(String[] args) throws InterruptedException {

        String m2path = "/Users/guodage/.m2/repository";
        String[] deployList = new String[]{"com.netfinworks.ifp:ifp-workctrl:jar:3.2.0-SNAPSHOT", "com.netfinworks.ifp:plugin-workflow-base:jar:3.2.0-SNAPSHOT", "com.netfinworks.ifp:plugin-workctrl-flow:jar:3.2.0-SNAPSHOT", "com.netfinworks.ifp:ifp-common-multinst:jar:3.0.0-SNAPSHOT", "com.netfinworks.ifp:ifp-common-util:jar:3.2.0-SNAPSHOT", "com.netfinworks.ifp:ifp-common-task:jar:3.2.0-SNAPSHOT", "com.netfinworks.ifp:ifp-common-lang:jar:3.2.0-SNAPSHOT", "com.netfinworks.ifp:ifp-common-template:jar:3.2.0-SNAPSHOT", "com.netfinworks.ifp:ifp-common-web:jar:3.2.0-SNAPSHOT", "com.netfinworks.payment.common:payment-common-domain:jar:2.1.0-SNAPSHOT", "com.netfinworks.payment.common:payment-common-domain:jar:2.1.0-zm-SNAPSHOT", "com.netfinworks.payment.common:payment-common-domain:jar:2.0.0", "com.netfinworks.payment.domain:payment-domain-common:jar:2.0.0", "com.netfinworks.payment.domain:payment-domain-settlement:jar:2.1.0-SNAPSHOT", "com.netfinworks.payment.domain:payment-domain-common:jar:2.1.0-SNAPSHOT", "com.netfinworks.payment.domain:payment-domain-payment:jar:2.1.0-SNAPSHOT", "com.netfinworks.payment.domain:payment-domain-clearing:jar:2.1.0-SNAPSHOT", "com.netfinworks.payment.service:payment-service-facade:jar:2.1.0-SNAPSHOT", "com.netfinworks.pfs:pfs-service-basis:jar:2.1.0-SNAPSHOT", "com.netfinworks.pfs:pfs-service-payment:jar:2.1.0-SNAPSHOT", "com.netfinworks.pfs:pfs-service-payment:jar:2.0.0", "com.netfinworks.pfs:pfs-service-payment:jar:2.1.1-zm-SNAPSHOT", "com.netfinworks.pfs:pfs-service-manager:jar:2.1.0-SNAPSHOT", "com.netfinworks.bcss.core.common:bcss-core-common:jar:2.0.0", "com.netfinworks.bcss.core.common:bcss-core-common:jar:3.0.3", "com.netfinworks.common:ibatis-sqlmap-mod:jar:2.0.0", "com.netfinworks.common:common-integration-util:jar:1.0.0-SNAPSHOT", "com.netfinworks.common:netfinworks-common-extensions:jar:2.0.0", "com.netfinworks.common.bcss.bank.common:bank-common:jar:2.0.0", "com.netfinworks.common:common-config:jar:2.0.0", "com.netfinworks.common.bcss.bank.common:bank-common:jar:3.0.3", "com.netfinworks.common:common-cxf:jar:1.0.0-SNAPSHOT", "com.netfinworks.mns:notifychannel-common:jar:1.0.0-SNAPSHOT", "com.netfinworks.autosettle:autosettle-service-facade:jar:2.0.0-SNAPSHOT", "com.netfinworks.unify:unify-tree-db:jar:1.0.0-SNAPSHOT", "com.netfinworks.unify:unify-tree:jar:1.0.0-SNAPSHOT", "com.netfinworks.invest:invest-service-facade-iwjw:jar:3.0.0-SNAPSHOT", "com.netfinworks.channel:bcss-common:jar:2.0-SNAPSHOT", "com.netfinworks.util:netfinworks-util:jar:2.0.0", "com.netfinworks.deduct:deduct-service-facade:jar:2.0.0", "com.netfinworks.abs:abs-service-facade:jar:3.0.0-SNAPSHOT", "com.netfinworks.efs:efs-service-facade:jar:2.0.0-awjw-SNAPSHOT", "com.netfinworks.commons:rootpom:pom:1.1", "com.netfinworks:netfinworks-parent:pom:2.0.0-SNAPSHOT", "com.netfinworks.common:netfinworks-common-batissource:pom:2.0.0", "com.netfinworks.common:netfinworks-common-exbaits:pom:2.0.0", "com.netfinworks.common:netfinworks-common-config:jar:2.0.0-SNAPSHOT", "com.netfinworks.unify:tree-parent:pom:1.0.0-SNAPSHOT", "com.netfinworks.autosettle:autosettle-service-parent:pom:2.0.0-SNAPSHOT", "com.netfinworks.autosettle:autosettle-platform-root:pom:2.0.0-SNAPSHOT", "com.netfinworks:netfinworks-bcss-parent:pom:2.0.0", "com.netfinworks:netfinworks-bcss-parent:pom:3.0.3"};


        MavenBunchDeploy mbc = new MavenBunchDeploy();

        BlockingQueue<Runnable> queue = new ArrayBlockingQueue(100);
        ConcurrentHashMap.KeySetView wrongSet = ConcurrentHashMap.newKeySet(20);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(5,
                                                             15,
                                                             30,
                                                             TimeUnit.SECONDS,
                                                             queue,
                                                             new ThreadPoolExecutor.AbortPolicy());

        Stream.of(deployList).forEach(mavenInfo -> {
            executor.execute(() -> {
                String command = mbc.bulidDeployCommandByMavenInfo(m2path, mavenInfo);
                boolean result = mbc.deploy(command);
                System.out.println(result + " " + command);
                if (!result) {
                    wrongSet.add(mavenInfo);
                }
            });
        });

        System.out.println("目前运行任务数\t已完成任务数\t任务总数\t剩余任务数");
        while (true) {
            Thread.sleep(10000);
            System.out.println(executor.getActiveCount() + "\t\t" + executor.getCompletedTaskCount() + "\t\t" + executor.getTaskCount() + "\t\t" + executor.getQueue().size());
            if (executor.getCompletedTaskCount() == executor.getTaskCount()) {
                if (!executor.isShutdown()) {
                    executor.shutdown();
                    System.out.println("deploy compelete");
                    break;
                }
            }
        }
        System.out.println("发布失败的项目有:");
        wrongSet.forEach(System.out::println);

        File tmp = new File("tmp");
        if (tmp.exists()) {
            tmp.delete();
        }
    }

    /**
     * 构造deploy命令
     * eg:
     * mvn deploy:deploy-file
     * -Durl=http://pay.cm-dev.cn/nexus/content/repositories/snapshots/
     * -Dfile=./plugin-workctrl-flow-3.2.0-SNAPSHOT.jar
     * -DpomFile=./plugin-workctrl-flow-3.2.0-SNAPSHOT.pom  注意:pom中很有可能会有来自parent的参数,这个办法pass
     * -DrepositoryId=snapshots
     * <p>
     * 换为在命令中定义坐标,pom还得用,因为有的jar会传递别的依赖
     * eg:
     * mvn deploy:deploy-file
     * -DgroupId=com.auth.zs
     * -DartifactId=zs-wndc
     * -Dversion=1.0
     * -Dpackaging=jar
     * -Durl=http://pay.cm-dev.cn/nexus/content/repositories/thirdparty/
     * -Dfile=./zs-wndc-1.0.jar
     * -DrepositoryId=thirdparty
     *
     * @param m2Path
     * @param mavenInfo
     * @return
     */
    private String bulidDeployCommandByMavenInfo(String m2Path, String mavenInfo) {
        String[] info = mavenInfo.split(":");
        String groupId = info[0];
        String artifactId = info[1];
        String type = info[2];
        String version = info[3];

        String filePath = m2Path.endsWith("/") ? m2Path : m2Path + "/";
        for (String subPath : groupId.split("\\.")) {
            filePath += subPath + File.separator;
        }
        filePath += artifactId + File.separator + version;
        String file = filePath + File.separator + artifactId + "-" + version + "." + type;
        String pomFile = filePath + File.separator + artifactId + "-" + version + ".pom";
        String url = null;
        if (isReleases(mavenInfo)) {
            url = releasesURL;
        } else {
            url = snapshotsURL;
        }
        //把依赖copy到tmp下,再deploy
        File tmp = new File("tmp");
        if (!tmp.exists()) {
            tmp.mkdir();
        }
        file = copyFile(new File(file), new File("tmp" + File.separator + artifactId + "-" + version + "." + type));
        pomFile = copyFile(new File(pomFile), new File("tmp" + File.separator + artifactId + "-" + version + ".pom"));

        String repositoryId = getRepositoryId(url);
        StringBuilder builder = new StringBuilder("mvn deploy:deploy-file");
        builder.append(" -DgroupId=" + groupId);
        builder.append(" -DartifactId=" + artifactId);
        builder.append(" -Dversion=" + version);
        builder.append(" -Dpackaging=" + type);
        builder.append(" -Durl=" + url);
        builder.append(" -Dfile=" + file);
        builder.append(" -DpomFile=" + pomFile);
        builder.append(" -DrepositoryId=" + repositoryId);
        return builder.toString();
    }

    private boolean isReleases(String mavenInfo) {
        return !mavenInfo.contains("-SNAPSHOT");
    }

    private String getRepositoryId(String repURL) {
        repURL = repURL.endsWith("/") ? repURL.substring(0, repURL.length() - 1) : repURL;
        String[] strs = repURL.split("/");
        return strs[strs.length - 1];
    }

    /**
     * 将文件拷贝到目标文件
     *
     * @param src
     * @param dest 注意目标文件不能存在
     * @return 返回dest文件绝对路径
     */
    private String copyFile(File src, File dest) {
        try {
            InputStream in = new FileInputStream(src);
            OutputStream out = new FileOutputStream(dest);

            byte[] buffer = new byte[1024];

            int length;

            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
            in.close();
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return dest.getAbsolutePath();
    }

    private boolean deploy(String command) {
        boolean result = false;
        try {
            //注意当shell命令有管道时的用法 将整个命令作为sh的参数
            ProcessBuilder pb = new ProcessBuilder("sh", "-c", command);

            Process p = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));

            String line = null;
            while ((line = reader.readLine()) != null) {
                if (line.contains("[INFO] BUILD SUCCESS")) {
                    result = true;
                    break;
                }
            }
            reader.close();
            p.destroy();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

}
