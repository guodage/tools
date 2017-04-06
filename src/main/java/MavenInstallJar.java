import java.io.File;

/**
 * 批量install第三方包到本地maven仓库
 */
public class MavenInstallJar {
	private String dir = "/Users/guodage/cm/amlapp-etl-new/src/main/resources/lib";
	private String groupId = "com.jieruan";
	private String defaultVersion = "1.0.0";

	private void outputInstallJarBatchFile() {
		File d = new File(dir);
		File[] fs = d.listFiles();
		for(File f : fs) {
			String fileName = f.getName().toLowerCase();
			String name = fileName;
			if(f.isFile() && name.endsWith(".jar")) {
				String version = "";
				String artifactId = "";
				name = name.replaceAll(".jar", "");
				if(name.contains("-")){
					version = name.substring(name.lastIndexOf("-")+1, name.length());
					if(version.matches("^\\d.*\\d$")){
						artifactId = name.substring(0,name.lastIndexOf("-"));
					}else{
						version = defaultVersion;
						artifactId = name;
					}
				}else{
					version = defaultVersion;
					artifactId = name;
				}

				StringBuffer sb = new StringBuffer();
				sb.setLength(0);
				sb.append("mvn install:install-file -DgroupId=").append(groupId);
				sb.append(" -DartifactId=").append(artifactId);
				sb.append(" -Dversion=").append(version);
				sb.append(" -Dfile=").append(fileName);
				sb.append(" -Dpackaging=jar -DgeneratePom=true");
				System.out.println(sb.toString());
			}
		}
	}

//	private void printDependency() {
//		File d = new File(dir);
//		File[] fs = d.listFiles();
//		for(File f : fs) {
//			String name = f.getName().toLowerCase();
//			String fileName = name;
//			if(f.isFile() && name.endsWith(".jar")) {
//				name = name.replaceAll(".jar", "");
//				String[] ns = name.split("-");
//				String version = "1.0";
//				StringBuffer sb = new StringBuffer(ns[0]);
//				for(String n : ns) {
//					n = Util.o2s(n);
//					if(n.length()>0 && Util.vi(n.substring(0, 1))) {
//						version = n;
//						break;
//					} else {
//						sb.append(n).append("-");
//					}
//				}
//				if(sb.toString().endsWith("-"))
//					sb.deleteCharAt(sb.length()-1);
//				String artifactId = sb.toString();
//				if(Util.o2s(prefix_jar).length()>0)
//					artifactId = prefix_jar + artifactId;
//				sb.setLength(0);
//				sb.append("<dependency>\n");
//				sb.append("  <groupId>").append(groupId).append("</groupId>\n");
//				sb.append("  <artifactId>").append(artifactId).append("</artifactId>\n");
//				sb.append("  <version>").append(version).append("</version>\n");
//				sb.append("</dependency>");
//				System.out.println(sb.toString());
//			}
//		}
//	}

	public static void main(String[] args) {
		MavenInstallJar inst = new MavenInstallJar();
		inst.outputInstallJarBatchFile();
//		inst.printDependency();
	}
}