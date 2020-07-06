import entity.FileInfo;
import service.JGitService;
import service.impl.JGitServiceImpl;
import utils.FileUtils;

import java.io.*;
import java.util.*;

public class RunnerTest {
    private static final String localPath = "D:\\MrJ's Documents\\jGit";

    public static void main(String[] args){
        RunnerTest runnerTest = new RunnerTest();
        runnerTest.test();
    }

    private long loopNums = 0;
    private ArrayList<ArrayList<String>> al = null;

    public void test(){
        al = new ArrayList<>();
        File file = new File("D:\\MrJ's Documents\\test");
        genFileTree(file,"root","0");
        System.out.println("");
    }

    private void genFileTree(File filePath ,String parentNode,String node){
        ArrayList<String> list = new ArrayList<String>();
        if(filePath.isDirectory()){
            list.add(parentNode);
            list.add(node);
            list.add(filePath.getName());
            list.add("folder");
            list.add(filePath.getAbsolutePath());
            al.add(list);
            loopNums++;
            File[] file = filePath.listFiles();
            for(int j=0;j<file.length;j++){
                genFileTree(file[j],node,"d"+node+"w"+j);
            }
            loopNums--;
        }else if(filePath.isFile()){
            list.add(parentNode);
            list.add(node);
            list.add(filePath.getName());
            list.add("file");
            list.add(filePath.getAbsolutePath());
            al.add(list);
        }
    }
}
