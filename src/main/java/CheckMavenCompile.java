import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.*;
import java.util.stream.Stream;

/**
 * 批量检查项目maven操作是否通过
 * Created by guodage on 2017/4/5.
 */
public class CheckMavenCompile {

    private static final String COMMANDS = "compile/package/install/dependency:list/deploy";


    public static void main(String[] args) throws IOException, InterruptedException {
        System.out.println("批量检查项目maven编译/打包/安装等操作是否通过");
        if (args.length != 2) {
            System.out.println("参数格式 project_path "+ COMMANDS);
            System.exit(0);
        }
        String dir = args[0];
        String action = args[1];

        if (!Stream.of(COMMANDS.split("/")).anyMatch(str -> str.equals(action))) {
            System.out.println("目前只支持 "+ COMMANDS);
            System.exit(0);
        }

        CheckMavenCompile c = new CheckMavenCompile();
        BlockingQueue<Runnable> queue = new ArrayBlockingQueue(100);
        ConcurrentHashMap<String, CheckStatus> result = new ConcurrentHashMap<>(100);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(5, 15, 30, TimeUnit.SECONDS, queue, new ThreadPoolExecutor.AbortPolicy());
        c.process(executor, new File(dir), result, action);

        System.out.println("目前运行任务数\t已完成任务数\t任务总数\t剩余任务数");
        while (true) {
            Thread.sleep(5000);
            System.out.println(executor.getActiveCount() + "\t\t" + executor.getCompletedTaskCount() + "\t\t" + executor.getTaskCount()+"\t\t" + executor.getQueue().size());

            if (executor.getCompletedTaskCount() == executor.getTaskCount()) {
                if (!executor.isShutdown()) {
                    executor.shutdown();
                    System.out.println("执行完毕 输出结果");
                    result.keySet().stream().sorted().forEach(key -> System.out.println(key + "\t" + result.get(key)));
                    break;
                }
            }
        }

    }

    private void process(ThreadPoolExecutor executor, File file, ConcurrentHashMap<String, CheckStatus> result, String action){
        if (isMavenProject(file)) {
            executor.execute(()->{
                try {
                    result.put(file.getAbsolutePath(), doCheck(file, action));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }else{
            if (file.getName().startsWith(".") || file.isFile()) {
                return;
            }
            Stream.of(file.listFiles()).forEach(f -> process(executor, f, result, action));
//            for (File f : file.listFiles()) {
//                process(executor, f, result);
//            }
        }
    }

    private boolean isMavenProject(File file) {
        if (file.isDirectory()) {
            return Stream.of(file.listFiles()).filter(f -> f.isFile() && f.getName().equals("pom.xml")).findAny().isPresent();
        }
        return false;
    }

    private CheckStatus doCheck(File file, String action) throws IOException {
        CheckStatus checkStatus = new CheckStatus();
        ProcessBuilder pb = new ProcessBuilder("mvn", "clean", action, "-DskipTests", "-Denv=mysql");
        pb.directory(file);
        Process p = pb.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));

        String line = null;
        while ((line = reader.readLine()) != null) {
            if (line.contains(". SUCCESS")) {
                if (checkStatus.getProject() == null) {
                    String subProject = getSubProject(line);
                    checkStatus.setProject(subProject.split(" ")[0]);
                }
            }
            if (line.contains(". FAILURE")) {
                checkStatus.setSuccess(false);
                String errorProject = getSubProject(line);
                checkStatus.setErrorSubProject(errorProject);
                if (checkStatus.getProject() == null) {
                    checkStatus.setProject(errorProject.split(" ")[0]);
                }
            }
            if (line.startsWith("[ERROR]")) {
                checkStatus.setErrorInfo(line.replace("[ERROR] ", ""));
                break;
            }
            if (line.contains("[INFO] BUILD SUCCESS")) {
                checkStatus.setSuccess(true);
                break;
            }
        }
        reader.close();
        p.destroy();
        return checkStatus;
    }

    private String getSubProject(String line){
        String subStr = line.replace("[INFO] ", "");
        return subStr.substring(0, subStr.indexOf(" "));
    }
    class CheckStatus {
        private String project;
        private boolean success;
        private String errorSubProject;
        private String errorInfo;

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getErrorSubProject() {
            return errorSubProject;
        }

        public void setErrorSubProject(String errorSubProject) {
            this.errorSubProject = errorSubProject;
        }

        public String getErrorInfo() {
            return errorInfo;
        }

        public void setErrorInfo(String errorInfo) {
            this.errorInfo = errorInfo;
        }

        public String getProject() {
            return project;
        }

        public void setProject(String project) {
            this.project = project;
        }

        @Override
        public String toString() {
            if (success) {
                return project + "\tSUCCESS";
            } else {
                return project + "\t" + errorSubProject + "\t" + errorInfo;
            }
        }
    }
}
