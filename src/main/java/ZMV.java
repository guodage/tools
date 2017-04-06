import java.io.*;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * zm版本对比工具
 * Created by guodage on 2017/2/24.
 */
public class ZMV {

    public static void main(String[] args) {
        if (args.length != 1 && args.length!= 2) {
            System.out.println("用法 zmv <filepath>   对一个文件自然排序\n" +
                    "     zmv <file a path> <file b path> 对比两个文件,并生成这两个文件的自然排序结果");
            return;
        }
        try {
            if(args.length == 1){
                new ZMV().sortVersionsByFjNo(args[0]);
            }else {
                ZMV zmv = new ZMV();
                zmv.compareFile(zmv.sortVersionsByFjNo(args[0]), zmv.sortVersionsByFjNo(args[1]));
            }

        } catch (IOException e) {
            e.printStackTrace();
        }


    }


    public File sortVersionsByFjNo(String filePath) throws IOException {
        File file = new File(filePath);
        File newFile = new File(file.getParent() + "/" + getNoSuffixFileName(file) + "_sort." + getSuffix(file));

        BufferedReader reader = new BufferedReader(new FileReader(file));
        BufferedWriter writer = new BufferedWriter(new FileWriter(newFile));

        if (file.getName().contains("prod")) {
            handleProd(reader, writer);
        } else {
            handleTest(reader, writer);
        }
        System.out.println("排序结果保存在 " + newFile.getAbsolutePath());
        return newFile;
    }

    private void handleProd(BufferedReader reader, BufferedWriter writer) throws IOException {
        Set<String> dmzNoRunVersions = initDMZNoRunVersionsSet();
        reader.lines()
                .filter(line -> line.startsWith("fj"))
                .sorted()
                .forEach(line -> {
                    line = line.split(" ")[0];
                    if (dmzNoRunVersions.contains(line)) {
                        //忽略当前版本号;并为避免将核心区也忽略,所以忽略一次后将line从set中删除
                        //所以version文件需要先记录DMZ区,然后记录核心区
                        dmzNoRunVersions.remove(line);
                    } else {
                        try {
                            writer.write(line);
                            writer.newLine();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                });
        reader.close();
        writer.flush();
        writer.close();
    }

    private void handleTest(BufferedReader reader, BufferedWriter writer) throws IOException {
        reader.lines()
                .filter(line -> line.startsWith("fj"))
                .sorted()
                .forEach(line -> {
                    try {
                        writer.write(line);
                        writer.newLine();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
        reader.close();
        writer.flush();
        writer.close();
    }

    //已文件a为准
    private void compareFile(File a, File b) throws IOException {
        File newFile = new File(a.getParent() + "/" + getNoSuffixFileName(a) + "_" + getNoSuffixFileName(b) + "_diff." + getSuffix(a));

        BufferedReader readerA = new BufferedReader(new FileReader(a));
        BufferedReader readerB = new BufferedReader(new FileReader(b));
        BufferedWriter writer = new BufferedWriter(new FileWriter(newFile));

        Set<String> setB = readerB.lines()
                .filter(line -> line.startsWith("fj"))
                .collect(Collectors.toSet());

        readerA.lines()
                .filter(line -> line.startsWith("fj"))
                .sorted()
                .forEach(line -> {
                    if (!setB.contains(line)){
                        //取出版本号
                        try {
                            writer.write(line);
                            writer.newLine();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
        readerA.close();
        readerB.close();
        writer.close();
        System.out.println("对比结果保存在 " + newFile.getAbsolutePath());
    }


    private String getNoSuffixFileName(File file) {
        String fileName = file.getName();
        return fileName.substring(0, fileName.lastIndexOf("."));
    }

    private String getSuffix(File file) {
        String fileName = file.getName();
        return fileName.substring(fileName.lastIndexOf(".") + 1, fileName.length());
    }

    //DMZ区冗余的项目编号
    private Set<String> initDMZNoRunVersionsSet() {
        return Stream.of("fj006_ufs:fj006_ufs_func100_build_20160725.5",
                "fj075_dpm-accounting_mysql:fj075_dpm_mysql_func100_build_20160726.1",
                "fj077_dpm-manager_mysql:fj075_dpm_mysql_func100_build_20160726.1",
                "fj078_ma-web_mysql:fj078_ma-web_mysql_func100_build_20160726.1",
                "fj081_cashier-api_mysql:fj080_cashier_mysql_func100_build_20160726.1",
                "fj084_fos_mysql:fj084_fos_mysql_func100_build_20160725.1",
                "fj085_rms-rules_mysql:fj085_rms-rules_mysql_func100_build_20160726.2",
                "fj086_rms-cep_mysql:fj085_rms-rules_mysql_func100_build_20160726.2",
                "fj089_voucher_mysql:fj089_voucher_mysql_func2_build_20151028.4",
                "fj090_mns_mysql:fj090_mns_mysql_func100_build_20160725.1",
                "fj091_mns-mq-listener_mysql:fj090_mns_mysql_func100_build_20160725.1",
                "fj092_mns-scheduler-web_mysql:fj090_mns_mysql_func100_build_20160725.1",
                "fj094_ues-ws_mysql:fj094_ues-ws_mysql_func100_build_20160726.1",
                "fj095_cmf_mysql:fj095_cmf_mysql_func100_build_20160726.2",
                "fj097_lflt_mysql:fj097_lflt_mysql_func100_build_20160726.1",
                "fj098_acs_mysql:fj098_acs_mysql_func100_build_20160725.1",
                "fj100_payment_mysql:fj100_payment_mysql_func100_build_20160725.1",
                "fj101_smsgateway-ws_mysql:fj101_smsgateway-ws_mysql_func100_build_20160725.1",
                "fj104_pbs-bos_mysql:fj104_pbs-bos_mysql_func100_build_20160725.1",
                "fj105_deposit_mysql:fj105_deposit_mysql_func100_build_20160726.1",
                "fj106_tradeservice_mysql:fj106_tradeservice_mysql_func100_build_20160726.1",
                "fj107_pbs_mysql:fj107_pbs_mysql_func100_build_20160725.1",
                "fj108_pfs-payment_mysql:fj108_pfs_mysql_func100_build_20160726.1",
                "fj109_pfs-basis_mysql:fj108_pfs_mysql_func100_build_20160726.1",
                "fj110_pfs-manager_mysql:fj108_pfs_mysql_func100_build_20160726.1",
                "fj112_privilege-api_mysql:fj112_guardian_mysql_func100_build_20160726.2",
                "fj114_uni-audit_mysql:fj112_guardian_mysql_func100_build_20160726.2",
                "fj115_uni-auth_mysql:fj112_guardian_mysql_func100_build_20160726.2",
                "fj121_ffs_mysql:fj121_ffs_mysql_func100_build_20160726.1",
                "fj130_counter-api_mysql:fj128_counter_mysql_func100_build_20160725.1",
                "fj147_cas-web:fj147_vfsso_func100_build_20160726.1",
                "fj203_payment-carryover_mysql:fj100_payment_mysql_func100_build_20160725.1",
                "fj213_ma-auth_mysql:fj213_ma-auth_mysql_func100_build_20160726.2",
                "fj214_ma-authcore_mysql:fj213_ma-auth_mysql_func100_build_20160726.2",
                "fj276_mns-dummy:fj276_mns-dummy_mysql_func2_build_20151029.1"
        ).collect(Collectors.toSet());
    }

}
