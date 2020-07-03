import entity.TreeItemVO;
import service.JGitService;
import service.impl.JGitServiceImpl;
import sun.plugin2.util.SystemUtil;
import sun.reflect.generics.tree.Tree;
import utils.PropertiesUtils;

import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class RunnerTest {
    public static void main(String[] args){
        try {
//            ArrayList<File> listFiles = getListFiles("D:/MrJ's Documents/jGit");
//            for (File file : listFiles) {
//                System.out.println(file.getAbsolutePath());
//            }
            String localPath = PropertiesUtils.getProperty("gitlab.localPath");
            String absolutePath = "D:\\MrJ's Documents\\jGit\\微信.docx";
            String relativePath = absolutePath.substring(absolutePath.indexOf(localPath)+1);
            System.out.println(relativePath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
