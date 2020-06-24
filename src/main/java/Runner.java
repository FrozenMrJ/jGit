import org.eclipse.jgit.lib.ObjectId;
import utils.JGitController;

import java.util.List;
import java.util.Map;

public class Runner {
    public static void main(String[] args) {
        JGitController jGitController = new JGitController();
        jGitController.init();
//        jGitController.pullBranchToLocal();
//        jGitController.commitFiles();
        List<Map<String, Object>> fileVersion = jGitController.getFileVersion("微信.docx");
        System.out.println(fileVersion);
        ObjectId treeId1 = (ObjectId)fileVersion.get(0).get("treeId");
        ObjectId treeId2 = (ObjectId)fileVersion.get(1).get("treeId");
        jGitController.difVersionInfo(treeId1,treeId2);
    }
}
