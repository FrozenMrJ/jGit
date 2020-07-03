package utils;

import java.io.File;
import java.util.ArrayList;

public class CommonUtils {
    /**
     * 返回目录下全部的文件
     *
     * @param obj 调用处传入目录的绝对路径
     * @return 文件的绝对路径
     */
    public static ArrayList<File> getListFiles(Object obj) {
        File directory;
        if (obj instanceof File) {
            directory = (File) obj;
        } else {
            directory = new File(obj.toString());
        }
        ArrayList<File> files = new ArrayList<>();
        if (!directory.getName().equals(".git")) {  // 过滤.git目录
            if (directory.isFile()) {
                files.add(directory);
                return files;
            } else if (directory.isDirectory()) {
                File[] fileArr = directory.listFiles();
                for (int i = 0; i < fileArr.length; i++) {
                    File fileOne = fileArr[i];
                    files.addAll(getListFiles(fileOne));
                }
            }
        }
        return files;
    }
}
