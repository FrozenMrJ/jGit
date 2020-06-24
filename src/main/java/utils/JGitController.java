package utils;

import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.*;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.patch.HunkHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

public class JGitController {

    // 代码分支
    private final String branch = PropertiesUtils.getProperty("gitlab.branch");

    // git url
    private final String url = PropertiesUtils.getProperty("gitlab.url");

    // 本地git路径
    private final String localPath = PropertiesUtils.getProperty("gitlab.localPath");

    // 代码分支
    private final String userName = PropertiesUtils.getProperty("gitlab.userName");

    // 代码分支
    private final String password = PropertiesUtils.getProperty("gitlab.password");

    // 指定返回最近maxCount个版本
    private final int maxCount = Integer.parseInt(PropertiesUtils.getProperty("gitlab.maxCount"));

    // 账户
    private UsernamePasswordCredentialsProvider usernamePasswordCredentialsProvider = new
            UsernamePasswordCredentialsProvider(userName, password);

    private Logger log = LoggerFactory.getLogger(JGitController.class);

    private Git git = null;
    private Repository repository = null;

    private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    /**
     * 克隆，建立与远程仓库的联系，仅需要执行一次
     */
    public void gitClone() {
        try {
            Git.cloneRepository()
                    .setURI(url)
                    .setDirectory(new File(localPath))
                    .setCredentialsProvider(usernamePasswordCredentialsProvider)
                    .setBranch("master")
                    .call();
            log.info("本地路径：" + localPath + "，git初始化成功");
        } catch (GitAPIException e) {
            log.error("git初始化失败：" + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 初始化git 和 repository
     */
    public void init() {
        try {
            git = Git.open(new File(localPath));
            repository = git.getRepository();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * pull拉取远程仓库文件
     * @return true / false
     */
    public boolean pullBranchToLocal(){
        boolean resultFlag = false;
        try {
//            git = new Git(new FileRepository(localPath + "/.git"));
            git.pull().setRemoteBranchName(branch).setCredentialsProvider(usernamePasswordCredentialsProvider).call();
            resultFlag = true;
            log.info("git pull success");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return resultFlag;
    }

    /**
     * 提交git
     * @return true / false
     */
    public boolean commitFiles() {
        try {
            git = Git.open(new File(localPath + "/.git"));
            AddCommand addCommand = git.add();
            //add操作 add -A操作在jgit不知道怎么用 没有尝试出来 有兴趣的可以看下jgitAPI研究一下 欢迎留言
            addCommand.addFilepattern(".").call();
            log.info("git add success");

            RmCommand rm = git.rm();
            Status status = git.status().call();
            //循环add missing 的文件 没研究出missing和remove的区别 就是删除的文件也要提交到git
            Set<String> missing = status.getMissing();
            for(String m : missing){
                log.info("missing files: " + m);
                rm.addFilepattern(m).call();
                //每次需重新获取rm status 不然会报错
                rm = git.rm();
                status = git.status().call();
            }
            //循环add remove 的文件
            Set<String> removed = status.getRemoved();
            for(String r : removed){
                log.info("removed files: " + r);
                rm.addFilepattern(r).call();
                rm = git.rm();
                status = git.status().call();
            }
            String username = System.getenv().get("USERNAME");
            //提交
            git.commit().setMessage(username).call();
            log.info("git commit success");
            //推送
            git.push().setCredentialsProvider(usernamePasswordCredentialsProvider).call();
            log.info("git push success");
            return true;
        } catch (Exception e) {
            log.error("git commit fail ：" + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 获取一个文件所有的版本(也就是提交记录)
     * @param fileName 带后缀的完整文件名
     * @return  key             value
     *         commitName       提交人      String
     *         commitDate       提交日期    Date
     *         commitId         版本号      String
     *         treeId           用于对比的ID无实际意义    ObjectId
     */
    public List<Map<String, Object>> getFileVersion(String fileName){
        try {
            Iterable<RevCommit> commits = git.log().addPath(fileName).setMaxCount(maxCount).call();
            return getDifInfo(commits);
        } catch (GitAPIException e) {
            log.error("getFileVersion failed：" + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 获取git版本的最近全部maxCount条差异信息
     * @return  key             value
     *         commitName       提交人      String
     *         commitDate       提交日期    Date
     *         commitId         版本号      String
     *         treeId           用于对比的ID无实际意义    ObjectId
     */
    public List<Map<String, Object>> getAllVersion() {
        try {
            repository = git.getRepository();
            //获取最近提交的MAX_COUNT次记录
            Iterable<RevCommit> commits = git.log().setMaxCount(maxCount).call();
            return getDifInfo(commits);
        } catch (Exception e) {
            log.error(e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 获取差异信息
     * @param commits
     * @return List里的一个元素就是一个版本，Map为版本的各种参数
     */
    private List<Map<String,Object>> getDifInfo(Iterable<RevCommit> commits) {
        List<Map<String,Object>> infoList = new ArrayList<>();
        try {
            for (RevCommit commit : commits) {
                Map<String, Object> map = new HashMap<>();
                String email = commit.getAuthorIdent().getEmailAddress();
                String name = commit.getAuthorIdent().getName();  //作者
                Date commitDate = commit.getAuthorIdent().getWhen();    // 提交时间
                String commitEmail = commit.getCommitterIdent().getEmailAddress();
                String commitName = commit.getCommitterIdent().getName();
                String fullMessage = commit.getFullMessage();
                String shortMessage = commit.getShortMessage();  //返回message的firstLine
                int type = commit.getType();
                String commitId = commit.getName();  //这个应该就是提交的版本
                ObjectId treeId = commit.getTree().getId();     // 对比差异所用的ID
                log.info("提交人：" + name + "\t提交时间：" + sdf.format(commitDate));
//                System.out.println("authorEmail:"+email);
//                System.out.println("authorName:"+name);
//                System.out.println("commitEmail:"+commitEmail);
//                System.out.println("commitName:"+commitName);
//                System.out.println("time:"+time);
//                System.out.println("fullMessage:"+fullMessage);
//                System.out.println("shortMessage:"+shortMessage);
//                System.out.println("commitID:"+commitID);
                map.put("commitName", commitName);
                map.put("commitDate", commitDate);
                map.put("commitId", commitId);
                map.put("treeId", treeId);
                infoList.add(map);
            }
        } catch (Exception e) {
            log.error("打印版本差异信息出错：" + e.getMessage());
            e.printStackTrace();
        }
        return infoList;
    }

    /**
     * 对比两个版本的差异
     * @param treeId1 新版本号
     * @param treeId2 老版本号
     */
    public void difVersionInfo(ObjectId treeId1,ObjectId treeId2){
        try {
            AbstractTreeIterator newTree = prepareTreeParser(treeId1);
            AbstractTreeIterator oldTree = prepareTreeParser(treeId2);
            List<DiffEntry> diff = git.diff().setOldTree(oldTree).setNewTree(newTree).setShowNameAndStatusOnly(true).call();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            DiffFormatter df = new DiffFormatter(out);
            //设置比较器为忽略空白字符对比（Ignores all whitespace）
            df.setDiffComparator(RawTextComparator.WS_IGNORE_ALL);
            df.setRepository(git.getRepository());
            log.info("------------------------------start-----------------------------");
            //每一个diffEntry都是第个文件版本之间的变动差异
            for (DiffEntry diffEntry : diff) {
                //打印文件差异具体内容
                df.format(diffEntry);
                String diffText = out.toString("UTF-8");
                log.info("版本差异信息：\n" + diffText);
                log.info("操作：" + diffEntry.getChangeType());
                //获取文件差异位置，从而统计差异的行数，如增加行数，减少行数
                FileHeader fileHeader = df.toFileHeader(diffEntry);
                List<HunkHeader> hunks = (List<HunkHeader>) fileHeader.getHunks();
                int addSize = 0;
                int subSize = 0;
                for (HunkHeader hunkHeader : hunks) {
                    EditList editList = hunkHeader.toEditList();
                    for (Edit edit : editList) {
                        subSize += edit.getEndA() - edit.getBeginA();
                        addSize += edit.getEndB() - edit.getBeginB();
                    }
                    log.info("从第" + String.valueOf(hunkHeader.getNewStartLine() + 1) + "行开始了修改");
                    log.info("减少行数：" + subSize);
                    log.info("增加行数：" + addSize);
                }
                log.info("------------------------------end-----------------------------");
                out.reset();
            }
        } catch (Exception e) {
            log.error("对比版本差异difVersionInfo()出错：" + e.getMessage());
            e.printStackTrace();
        }
    }

    private AbstractTreeIterator prepareTreeParser(RevCommit commit){
        try (RevWalk walk = new RevWalk(repository)) {
            RevTree tree = walk.parseTree(commit.getTree().getId());

            CanonicalTreeParser oldTreeParser = new CanonicalTreeParser();
            try (ObjectReader oldReader = repository.newObjectReader()) {
                oldTreeParser.reset(oldReader, tree.getId());
            }
            walk.dispose();
            return oldTreeParser;
        }catch (Exception e) {
            log.error(e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    private AbstractTreeIterator prepareTreeParser(ObjectId treeId){
        try (RevWalk walk = new RevWalk(repository)) {
            RevTree tree = walk.parseTree(treeId);
            CanonicalTreeParser oldTreeParser = new CanonicalTreeParser();
            try (ObjectReader oldReader = repository.newObjectReader()) {
                oldTreeParser.reset(oldReader, tree.getId());
            }
            walk.dispose();
            return oldTreeParser;
        }catch (Exception e) {
            log.error(e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 重置
     *
     * @return
     */
    public String reset() {
        String result;

        Repository repo = null;
        try {
            repo = new FileRepository(new File(localPath));
            Git git = new Git(repo);
            git.reset().setMode(ResetCommand.ResetType.HARD).setRef(branch).call();
            result = "重置成功!";

        } catch (Exception e) {
            result = e.getMessage();
        } finally {
            if (repo != null) {
                repo.close();
            }
        }
        return result;
    }

    /**
     * 恢复
     */
    public String revert() {
        String result;

        try {
            git.revert().call();
            result = "恢复成功!";

        } catch (Exception e) {
            result = e.getMessage();
        }
        return result;
    }

    /**
     * 状态
     */
    public void status() {
        try {
            Status status = git.status().call();
            log.info("Git Change: " + status.getChanged());
            log.info("Git Modified: " + status.getModified());
            log.info("Git UncommittedChanges: " + status.getUncommittedChanges());
            log.info("Git Untracked: " + status.getUntracked());
        } catch (Exception e) {
            log.info(e.getMessage());
        }
    }
}
