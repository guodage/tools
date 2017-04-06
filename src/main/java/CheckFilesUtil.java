import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 递归比较两个目录 将hash值不同的文件名输出
 * Created by guodage on 2016/12/29.
 */
public class CheckFilesUtil {


    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.out.println("参数格式 dir1_path dir2_path");
            System.exit(0);
        }
        String dir1 = args[0];
        String dir2 = args[1];


        Map<String, String> dir1Map = new HashMap<>(4096);
        addDirHashMap(dir1Map, dir1, dir1);

        Map<String, String> dir2Map = new HashMap<>(4096);
        addDirHashMap(dir2Map, dir2, dir2);

        Map<String, String> diff1Map = dir1Map.entrySet().stream()
                .filter(map -> !map.getValue().equals(dir2Map.get(map.getKey())))
                .collect(Collectors.toMap(p -> p.getKey(), p -> p.getValue()));

        Map<String, String> diff2Map = dir2Map.entrySet().stream()
                .filter(map -> !map.getValue().equals(dir1Map.get(map.getKey())))
                .collect(Collectors.toMap(p -> p.getKey(), p -> p.getValue()));

        Set<String> commonDiffFiles = Stream.of(diff1Map, diff2Map)
                .flatMap(map -> map.keySet().stream())
                .filter(key -> diff1Map.containsKey(key) && diff2Map.containsKey(key) && !key.endsWith(".DS_Store"))
                .collect(Collectors.toSet());

        Set<String> dir1DiffFiles = Stream.of(diff1Map, diff2Map)
                .flatMap(map -> map.keySet().stream())
                .filter(key -> diff1Map.containsKey(key) && !diff2Map.containsKey(key) && !key.endsWith(".DS_Store"))
                .collect(Collectors.toSet());

        Set<String> dir2DiffFiles = Stream.of(diff1Map, diff2Map)
                .flatMap(map -> map.keySet().stream())
                .filter(key -> !diff1Map.containsKey(key) && diff2Map.containsKey(key) && !key.endsWith(".DS_Store"))
                .collect(Collectors.toSet());


        System.out.println("共有但不同的.jsp文件:");
        commonDiffFiles.stream()
                .filter(path -> path.endsWith(".jsp"))
                .forEach(path ->{
                    System.out.println(dir1 + path);
                    System.out.println(dir2 + path);
                });

        System.out.println("共有但不同的.class文件:");
        commonDiffFiles.stream()
                .filter(path -> path.endsWith(".class"))
                .forEach(path ->{
                    System.out.println(dir1 + path);
                    System.out.println(dir2 + path);
                });

        System.out.println("共有但不同的其他文件:");
        commonDiffFiles.stream()
                .filter(path -> !path.endsWith(".class")&&!path.endsWith(".jsp"))
                .forEach(path ->{
                    System.out.println(dir1 + path);
                    System.out.println(dir2 + path);
                });


        System.out.println("\n只在"+dir1+"下存在的文件:");
        dir1DiffFiles.forEach(path -> System.out.println(dir1 + path));

        System.out.println("\n只在"+dir2+"下存在的文件:");
        dir2DiffFiles.forEach(path -> System.out.println(dir2 + path));

    }


    /**
     * 加载目录下所有文件到map中
     * @param map key 文件路径(剔除rootPath部分) value hash
     * @param dirPath 目录路径
     * @param rootPath key中剔除的部分(用于map中比对)
     */
    public static void addDirHashMap(Map<String, String> map, String dirPath, String rootPath) {
        File dirs = new File(dirPath);
        if (dirs.isDirectory()) {
            File[] files = dirs.listFiles();
            for (File file : files) {
                if (file.isFile()) {
                    map.put(file.getAbsolutePath().replace(rootPath, ""), fileHash(file.getAbsolutePath()));
                } else if (file.isDirectory()) {
                    addDirHashMap(map, file.getAbsolutePath(), rootPath);
                }
            }
        } else if (dirs.isFile()) {
            addDirHashMap(map, dirs.getAbsolutePath(), rootPath);
        }
    }

    /**
     * 计算文件hash值
     * @param filePath
     * @return
     */
    public static String fileHash(String filePath) {
        File file = new File(filePath);
        if (file == null || !file.exists()) {
            return null;
        }

        String result = null;
        FileInputStream fis = null;

        try {
            fis = new FileInputStream(file);
            MappedByteBuffer mbf = fis.getChannel().map(
                    FileChannel.MapMode.READ_ONLY, 0, file.length());
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(mbf);
            BigInteger bi = new BigInteger(1, md.digest());
            result = bi.toString(16);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                /* ignore */
                }
            }
        }

        return result;
    }
}
