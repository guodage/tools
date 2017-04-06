import java.io.*;
import java.math.BigInteger;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 文件夹合并工具
 * 将path2中的文件/文件夹合并到path1对应目录层级下
 * <p>
 * 1. path2中某文件夹在path1中不存在 - 直接将文件夹拷贝过去
 * 2. path1与path2都有文件夹,但path2下有额外文件 - 将额外文件拷贝过去
 * 3. path1与path2都有该文件(目录层级相同,hash值不同) - 将无法合并的path2中的冲突文件输出,并将所有文件/文件夹拷贝到一个新的文件夹下
 * Created by guodage on 2017/3/7.
 */
public class MergeDir {

    public static void main(String[] args) {
        System.out.println("文件夹合并工具");
        System.out.println("将path2中的文件/文件夹合并到path1对应目录层级下");
        if (args.length != 2) {
            System.out.println("参数格式 dir1_path dir2_path");
            System.exit(0);
        }
        String dir1 = args[0];
        String dir2 = args[1];

        System.out.println("== 合并文件夹 ==");
        //获取dir1下的所有目录列表 子路径
        List<String> dir1NameList = new ArrayList<>(4096);
        addDirsNameList(dir1NameList, dir1, dir1);

        //获取dir2下的所有目录列表 子路径
        List<String> dir2NameList = new ArrayList<>(4096);
        addDirsNameList(dir2NameList, dir2, dir2);

        //遍历dir2,将dir2中独有的文件/文件夹拷贝到dir1对应目录
        System.out.println("将 " + dir2 + " 中以下文件夹合并到 " + dir1 + " 中");
        dir2NameList.stream()
                .filter(subPath -> !dir1NameList.contains(subPath))
                .forEach(subPath -> {
                    copyFolder(new File(dir2, subPath), new File(dir1, subPath));
                    System.out.println(subPath);
                });

        System.out.println("== 填充 " + dir1 + " 中的缺失文件 ==");
        //获取dir1下所有的文件-hash值
        Map<String, String> dir1Map = new HashMap<>(4096);
        addDirHashMap(dir1Map, dir1, dir1);

        //获取dir2下所有的文件-hash值
        Map<String, String> dir2Map = new HashMap<>(4096);
        addDirHashMap(dir2Map, dir2, dir2);

        Map<String, String> diff1Map = dir1Map.entrySet().stream()
                .filter(map -> !map.getValue().equals(dir2Map.get(map.getKey())))
                .collect(Collectors.toMap(p -> p.getKey(), p -> p.getValue()));

        Map<String, String> diff2Map = dir2Map.entrySet().stream()
                .filter(map -> !map.getValue().equals(dir1Map.get(map.getKey())))
                .collect(Collectors.toMap(p -> p.getKey(), p -> p.getValue()));

        //获取dir2中独有文件
        Set<String> dir2DiffFiles = Stream.of(diff1Map, diff2Map)
                .flatMap(map -> map.keySet().stream())
                .filter(key -> !diff1Map.containsKey(key) && diff2Map.containsKey(key) && !key.endsWith(".DS_Store"))
                .collect(Collectors.toSet());

        System.out.println("将 " + dir2 + " 中以下文件拷贝到 " + dir1 + " 中");
        //将dir2独有文件拷贝到dir1对应位置
        dir2DiffFiles.forEach(subPath -> {
            copyFolder(new File(dir2, subPath), new File(dir1, subPath));
            System.out.println(subPath);
        });

        //获取冲突文件
        Set<String> commonDiffFiles = Stream.of(diff1Map, diff2Map)
                .flatMap(map -> map.keySet().stream())
                .filter(key -> diff1Map.containsKey(key) && diff2Map.containsKey(key) && !key.endsWith(".DS_Store"))
                .collect(Collectors.toSet());

        System.out.println("== 以下为冲突文件 请手动处理 ==");
        File conflictDir = new File("conflict");
        conflictDir.mkdir();
        System.out.println("冲突文件均会拷贝到 " + conflictDir.getAbsolutePath() + " 目录中");
        System.out.println("下面只打印无法合并的jar");
        commonDiffFiles.forEach(subPath -> {
            File source = new File(dir2, subPath);
            copyFolder(source, new File(conflictDir, source.getAbsolutePath().replace(dir2, "").replace("/", ".").substring(1)));
            if (subPath.contains(".jar")){
                //其他文件就不打印了 太多
                System.out.println(subPath);
            }
        });


    }

    /**
     * 将文件夹及其内容拷贝到目标文件
     * @param src
     * @param dest 注意目标文件/文件夹不能存在
     */
    private static void copyFolder(File src, File dest) {
        try {
            if (src.isDirectory()) {
                if (!dest.exists()) {
                    dest.mkdir();
                }
                String files[] = src.list();
                for (String file : files) {
                    File srcFile = new File(src, file);
                    File destFile = new File(dest, file);
                    // 递归复制
                    copyFolder(srcFile, destFile);
                }
            } else {
                InputStream in = new FileInputStream(src);
                OutputStream out = new FileOutputStream(dest);

                byte[] buffer = new byte[1024];

                int length;

                while ((length = in.read(buffer)) > 0) {
                    out.write(buffer, 0, length);
                }
                in.close();
                out.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * 加载目录下所有文件夹到list中
     *
     * @param list     值为 文件夹相对路径
     * @param dir      目录路径
     * @param rootPath 目录路径 用于将绝对路径处理为相对路径
     */
    public static void addDirsNameList(List<String> list, String dir, String rootPath) {
        File dirs = new File(dir);
        if (dirs.isDirectory()) {
            list.add(dirs.getAbsolutePath().replace(rootPath, ""));

            for (File file : dirs.listFiles()) {
                addDirsNameList(list, file.getAbsolutePath(), rootPath);
            }
        }
    }


    /**
     * 加载目录下所有文件到map中
     *
     * @param map      key 文件路径(剔除rootPath部分) value hash
     * @param dirPath  目录路径
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
     *
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
