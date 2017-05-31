import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

/**
 * 指定n个源码路径,校验这些源码在依赖关系上是否"自洽" (是否可以完成整批代码的编译)
 * 注意:
 * 1.编译需要的依赖在.m2本地仓库是必须有的,否则mvn dependency:list 无法输出Maven项目的依赖列表
 * 2.目前,上述"源码"仅指groupId为"com.netfinworks"下的源码,若扩大范围,此工具的适用性还得再考虑
 * Created by guodage on 2017/4/28.
 */
public class MvnVersion {

    private static final String MM = ":";

    //以<groupId, Properties>形式存放各个Maven项目中自定义的属性 map大小根据项目数来估算
    private Map<String, Properties> vMap = new HashMap<>(70);

    public static void main(String[] args) throws InterruptedException {

//        String[] paths = new String[]{"/Users/guodage/cm/0630/source_6", "/Users/guodage/cm/0630/source_6.1"};

        System.out.println("指定n个源码路径,校验这些源码在依赖关系上是否\"自洽\"");
        if (args.length == 0) {
            System.out.println("参数格式 dir[0] dir[1] ...");
            System.exit(0);
        }
        String[] paths = args;

        System.out.println("加载指定位置的源码版本");
        Set<String> set = new HashSet<>(700);

        MvnVersion mvv = new MvnVersion();
        Stream.of(paths).forEach(path -> {
            try {
                mvv.printInfo(new File(path), set);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        });

        System.out.println("加载完毕");

        //由于各个Maven项目中各个子Module没写的version,都处理为parent的version了,这里就检测不出来了
        //以后若有需要可以在上述处理过程中来采集相关信息
//        System.out.println("======================");
//        System.out.println("pom中没配version的项目有");
//        set.stream().filter(info -> info.endsWith(".")).forEach(System.out::println);//以.结尾说明没有version
//        System.out.println("======================");

        System.out.println("加载指定位置源码的依赖信息 (目前只过滤特定的关键字)");
        BlockingQueue<Runnable> queue = new ArrayBlockingQueue(100);
//        ConcurrentHashMap.KeySetView dependencySet = ConcurrentHashMap.newKeySet(700);
        ConcurrentHashMap<String, Set<String>> dependencyMap = new ConcurrentHashMap<>(700);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(5,
                                                             15,
                                                             30,
                                                             TimeUnit.SECONDS,
                                                             queue,
                                                             new ThreadPoolExecutor.AbortPolicy());

        Stream.of(paths).forEach(path -> mvv.initAllDependencyInfo(new File(path), dependencyMap, executor));

        System.out.println("目前运行任务数\t已完成任务数\t任务总数\t剩余任务数");
        while (true) {
            Thread.sleep(10000);
            System.out.println(executor.getActiveCount() + "\t\t" + executor.getCompletedTaskCount() + "\t\t" + executor.getTaskCount() + "\t\t" + executor.getQueue().size());
            if (executor.getCompletedTaskCount() == executor.getTaskCount()) {
                if (!executor.isShutdown()) {
                    executor.shutdown();
                    System.out.println("加载完毕");
                    break;
                }
            }
        }

        System.out.println("缺少的依赖有");
        dependencyMap.keySet().stream().filter(info -> !set.contains(info)).forEach(info->{
            System.out.println(info);
            System.out.println("\t依赖它的项目:");
            dependencyMap.get(info).forEach(pj->{
                System.out.println("\t\t" + pj);
            });
        });
    }

    /**
     * 找到指定目录的所有Maven项目并输出相关信息
     * @param dirs
     * @param set
     * @throws FileNotFoundException
     */
    private void printInfo(File dirs, Set set) throws FileNotFoundException {
        if (dirs.isFile()) {
            return;
        }
        if (isMavenProject(dirs)) {
            printMavenPjInfo(dirs, 0, set);
        } else {
            for (File file : dirs.listFiles()) {
                printInfo(file, set);
            }
        }
    }

    /**
     * 判断pom文件是否包含子module
     * @param pom
     * @return
     * @throws FileNotFoundException
     */
    private boolean hasModule(File pom) throws FileNotFoundException {
        BufferedReader reader = new BufferedReader(new FileReader(pom));
        return reader.lines().filter(line -> line.contains("<module>") && line.contains("</module>")).findAny().isPresent();
    }

    /**
     * 判断指定目录是否是Maven项目
     * @param dir
     * @return
     */
    private boolean isMavenProject(File dir) {
        if (dir.isDirectory()) {
            return Stream.of(dir.listFiles()).filter(f -> f.isFile() && f.getName().equals("pom.xml")).findAny().isPresent();
        }
        return false;
    }

    /**
     * 打印一个完整的Maven项目的相关信息 -- "groupId + MM + artifactId + MM + packaging + MM + version"
     * @param mavenDir Maven项目根路径
     * @param level 可能Maven中有很多层级,level则表示这个层级
     * @param set 相关信息会填充到set中
     * @throws FileNotFoundException
     */
    private void printMavenPjInfo(File mavenDir, int level, Set set) throws FileNotFoundException {
        File pom = new File(mavenDir.getAbsoluteFile() + File.separator + "pom.xml");
        printModuleInfo(pom, level, set);
        if (hasModule(pom)) {
            File[] modules = getModules(mavenDir, pom);
            for (File module : modules) {
                printMavenPjInfo(module, level + 1, set);
            }
        }
    }

    /**
     * 打印一个子Module的相关信息,并填充到set中
     * @param pom 子Module的pom文件
     * @param level 该Module在本项目中的层级
     * @param set 相关信息会填充到set中
     */
    private void printModuleInfo(File pom, int level, Set set) {
        try {
            String groupId = getValueFromPom("/project/groupId", pom);
            String artifactId = getValueFromPom("/project/artifactId", pom);
            String version = getValueFromPom("/project/version", pom);
            String packaging = getValueFromPom("/project/packaging", pom);
            if (level == 0) {
                //说明是项目根目录
                //加载pom中的properties 目的是为子modules中版本号为${xxx}时查找
                Properties properties = getPropertiesFromPom(pom);
                vMap.put(groupId, properties);
            }

            //处理子modules中的一些特殊情况
            if (groupId == null || "".equals(groupId)) {
                groupId = getValueFromPom("/project/parent/groupId", pom);
            }
            if (version == null || "".equals(version)) {
                version = getValueFromPom("/project/parent/version", pom);
            }
            if (packaging == null || "".equals(packaging)) {
                packaging = "jar";//若pom里没写的话,默认为jar
            }

            //从项目中的properties获取$调用的version号
            if (version.startsWith("$")) {
                String key = version.substring(2, version.length() - 1);
                String parentGroupId = getValueFromPom("/project/parent/groupId", pom);
                try {
                    if (parentGroupId == null || "".equals(parentGroupId)) {
                        //若没有parent-pom 说明这个版本号在自己的properties里
                        version = vMap.get(groupId).getProperty(key);
                    } else {
                        while (vMap.get(parentGroupId) == null) {
                            //再往上一层
                            parentGroupId = parentGroupId.substring(0, parentGroupId.lastIndexOf("."));
                        }
                        version = vMap.get(parentGroupId).getProperty(key);
                    }
                } catch (NullPointerException e) {
                    System.out.println(key);
                    e.printStackTrace();
                }
            }

            //打印结果
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < level; i++) {
                sb.append("\t");
            }
            String singleInfo = groupId + MM + artifactId + MM + packaging + MM + version;
            sb.append(singleInfo);
            //平时用不着打印出来
//            System.out.println(sb.toString());

            //加载结果备用
            set.add(singleInfo);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * 获取maven项目中的各modules
     * @param mavenDir 指定Maven项目的路径
     * @param pom 指定Maven项目的pom文件
     * @return
     */
    private File[] getModules(File mavenDir, File pom) {
        try {
            //注意这个获取子节点列表的表达式用法!
            List modules = getValuesFromPom("/project/modules//module/text()", pom);
            return Stream.of(mavenDir.listFiles()).filter(file -> modules.contains(file.getName())).toArray(File[]::new);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    //从pom中根据表达式获取对应的值
    private String getValueFromPom(String expression, File pom) {
        try {
            Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pom);
            XPath xPath = XPathFactory.newInstance().newXPath();
            return xPath.evaluate(expression, document);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    //从pom中根据表达式获取对应的列表值
    private List<String> getValuesFromPom(String expression, File pom) {
        try {
            Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pom);
            XPath xPath = XPathFactory.newInstance().newXPath();
            NodeList list = (NodeList) xPath.evaluate(expression, document, XPathConstants.NODESET);
            List<String> values = new ArrayList<>(list.getLength());
            for (int i = 0; i < list.getLength(); i++) {
                values.add(list.item(i).getNodeValue());
            }
            return values;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    //加载pom中的properties
    private Properties getPropertiesFromPom(File pom) {
        try {
            Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pom);
            XPath xPath = XPathFactory.newInstance().newXPath();
            NodeList list = (NodeList) xPath.evaluate("/project/properties/*", document, XPathConstants.NODESET);
            Properties properties = new Properties();
            for (int i = 0; i < list.getLength(); i++) {
                properties.put(list.item(i).getNodeName(), list.item(i).getFirstChild().getNodeValue());
            }
            return properties;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 初始化给定目录中所有项目所需的依赖信息
     * @param dir 指定目录
     * @param map 信息会填充到map中
     * @param executor 异步获取每个Maven项目的依赖信息
     */
    private void initAllDependencyInfo(File dir, ConcurrentHashMap<String, Set<String>> map, ThreadPoolExecutor executor) {
        if (dir.isDirectory()) {
            if (isMavenProject(dir)) {
                executor.execute(() -> {
                    Set subSet = getPjDependencyInfo(dir);
                    subSet.stream().forEach(info->{
                        if (!map.containsKey(info)) {
                            map.put(info.toString(), new HashSet<>(30));
                        }
                        Set pj = map.get(info);
                        pj.add(dir.getAbsolutePath());
                    });
                });
            } else {
                for (File subDir : dir.listFiles()) {
                    initAllDependencyInfo(subDir, map, executor);
                }
            }
        }
    }

    //获取一个项目的所有依赖信息 可能会按需过滤
    private Set getPjDependencyInfo(File mavenDir) {
        try {
            Set set = new HashSet<>(30);
            //注意当shell命令有管道时的用法 将整个命令作为sh的参数
            ProcessBuilder pb = new ProcessBuilder("sh",
                                                   "-c",
                                                   "mvn dependency:list | grep \"\\[INFO\\]\" | grep com.netfinworks");

            pb.directory(mavenDir);
            Process p = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));

            String line = null;
            while ((line = reader.readLine()) != null) {
                line = line.replace("[INFO]", "").trim();
                if (line.startsWith("com.netfinworks")) {
                    set.add(line.substring(0, line.lastIndexOf(":")));
                }
            }
            reader.close();
            p.destroy();
            return set;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

}
