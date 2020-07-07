import service.JGitService;
import service.impl.JGitServiceImpl;

import java.util.ArrayList;
import java.util.Map;

public class Runner {
    public static void main(String[] args) {
        ArrayList<String> paths = new ArrayList<>();
        paths.add("微信.docx");
        JGitService jGitService = new JGitServiceImpl();
//        jGitService.createFolder("dir/dir1/dir2");
//        jGitService.gitClone();
//        jGitService.pullBranchToLocal();
//        String status = jGitService.status("微信.docx");
//        TreeItemVO treeItemVOS = jGitService.initDirTreeStatus();
//        System.out.println(treeItemVOS);
//        System.out.println(status);
//        String s = jGitService.commitToGitRepository(paths);
//        System.out.println(s);
//        List<Map<String, Object>> fileVersion = jGitService.getFileVersion("test.txt",2);
//        System.out.println(fileVersion);
        String[] commitIds = {"66c670af446814f41e71f7e1b98603f365d3096e", "a79240edaa2bfc9742f951b5c1297ec7a6f4d06b"};
        String[] relativePaths = {"微信.docx","test.txt"};
        Map<String, byte[]> dataMap = jGitService.readHisFile(commitIds, relativePaths);
        boolean b = jGitService.compressZipFile(dataMap, "test/test.zip");
//        ObjectId treeId1 = (ObjectId)fileVersion.get(0).get("treeId");
//        ObjectId treeId2 = (ObjectId)fileVersion.get(1).get("treeId");
//        jGitService.difVersionInfo(treeId1,treeId2);
    }
}
