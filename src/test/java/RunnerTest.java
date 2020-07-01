import service.JGitService;
import service.impl.JGitServiceImpl;

import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class RunnerTest {
    public static void main(String[] args){
        try {
            String str = "dir/test1.txt_ADD_信息";
            String substring = str.substring(0,str.indexOf("_"));
            System.out.println(substring);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
