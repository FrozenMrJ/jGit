package service.impl;

import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.diff.*;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
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
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.eclipse.jgit.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.JGitService;
import utils.PropertiesUtils;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class JGitServiceImpl implements JGitService {

    // 代码分支
    private final String branch = PropertiesUtils.getProperty("gitlab.branch");

    // git url
    private final String url = PropertiesUtils.getProperty("gitlab.url");

    // 本地git路径
    private final String localPath = PropertiesUtils.getProperty("gitlab.localPath");

    // 账户名
    private final String userName = PropertiesUtils.getProperty("gitlab.userName");

    // 账户密码
    private final String password = PropertiesUtils.getProperty("gitlab.password");

    // 账户
    private UsernamePasswordCredentialsProvider usernamePasswordCredentialsProvider = new
            UsernamePasswordCredentialsProvider(userName, password);

    private Logger log = LoggerFactory.getLogger(JGitServiceImpl.class);

    private Git git = null;
    private Repository repository = null;
    private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    /**
     * 克隆，建立与远程仓库的联系，仅需要执行一次
     */
    @Override
    public void gitClone() {
        try {
            Git.cloneRepository()
                    .setURI(url)
                    .setDirectory(new File(localPath))
                    .setCredentialsProvider(usernamePasswordCredentialsProvider)
                    .setBranch(branch)
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
    @Override
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
    @Override
    public boolean pullBranchToLocal(){
        boolean resultFlag = false;
        try {
//            git = new Git(new FileRepository(localPath + "/.git"));
//            git.checkout().setCreateBranch(false).setName(branch).setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK).call();
            PullResult call = git.pull().setRemoteBranchName(branch).setCredentialsProvider(usernamePasswordCredentialsProvider).call();
            boolean successful = call.isSuccessful();
            resultFlag = true;
            log.info("git pull success");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return resultFlag;
    }

    /**
     * 将文件列表提交到git仓库中
     * @param relativePath 相对git库的文件路径
     * @param msg 用户提交信息
     * @return 返回本次提交的版本号
     * @throws IOException
     */
    @Override
    public String commitToGitRepository(String relativePath,String msg){
        try {
            List<String> paths = new ArrayList<>();
            paths.add(relativePath);
            //判断是否有被修改过的文件
            List<DiffEntry> diffEntries = git.diff()
                    .setPathFilter(PathFilterGroup.createFromStrings(paths))
                    .setShowNameAndStatusOnly(true).call();
            if (diffEntries == null || diffEntries.size() == 0) {
                log.error("提交的文件内容都没有被修改，不能提交");
                return null;
            }
            //被修改过的文件
            List<String> updateFiles = new ArrayList<>();
            DiffEntry.ChangeType changeType = null;
            for (DiffEntry entry : diffEntries) {
                changeType = entry.getChangeType();
                switch (changeType) {
                    case ADD:
                        updateFiles.add(entry.getNewPath());
                        break;
                    case COPY:
                        updateFiles.add(entry.getNewPath());
                        break;
                    case DELETE:
                        updateFiles.add(entry.getOldPath());
                        break;
                    case MODIFY:
                        updateFiles.add(entry.getOldPath());
                        break;
                    case RENAME:
                        updateFiles.add(entry.getNewPath());
                        break;
                }
            }
            //将文件提交到git仓库中，并返回本次提交的版本号
            AddCommand addCmd = git.add();
            for (String file : updateFiles) {
                addCmd.addFilepattern(file);
            }
            addCmd.call();
            log.info("git add success");

            CommitCommand commitCmd = git.commit();
            for (String file : updateFiles) {
                commitCmd.setOnly(file);
            }

            RevCommit revCommit = commitCmd.setMessage(paths + "_" + changeType.toString() + "_" + msg).call();
            log.info("git commit success");
            //推送
            git.push().setCredentialsProvider(usernamePasswordCredentialsProvider).call();
            log.info("git push success");

            // TODO 将信息保存到数据库

            return revCommit.getName();
        } catch (Exception e) {
            log.error("git 提交出错：" + e.getMessage());
        }
        return null;
    }

    /**
     * 获取一个文件所有的版本(也就是提交记录)
     * 如果某一次的提交，包含了多个文件，其中包含了这个文件，该次提交也会被包含到结果其中
     * @param fileName 带后缀的完整文件名
     * @param maxCount 返回的版本个数
     * @return  key             value                 类型
     *         commitName       提交人                String
     *         commitDate       提交日期              Date
     *         commitId         版本号                String
     *         commitMsg        提交备注              String
     *         treeId    用于对比的ID(无实际意义)     ObjectId
     *         commitMsg        提交备注              String
     *         path             修改的相对路径        String
     */
    @Override
    public List<Map<String, Object>> getFileVersion(String fileName,int maxCount){
        try {
            Iterable<RevCommit> commits = git.log().addPath(fileName).call();
            return getDifInfo(commits);
        } catch (GitAPIException e) {
            log.error("getFileVersion failed：" + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 获取git版本的最近全部maxCount条差异信息
     * @param maxCount 返回的版本个数
     * @return  key             value                 类型
     *         commitName       提交人                String
     *         commitDate       提交日期              Date
     *         commitId         版本号                String
     *         treeId    用于对比的ID(无实际意义)     ObjectId
     *         commitMsg        提交备注              String
     *         path             修改的相对路径        String
     */
    @Override
    public List<Map<String, Object>> getAllVersion(int maxCount) {
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
     * 对比两个版本的差异，输出两个版本之间全部的操作
     * @param treeId1 新版本号
     * @param treeId2 老版本号
     */
    @Override
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
                String oldPath = diffEntry.getOldPath();
                String newPath = diffEntry.getNewPath();
                log.info("oldPath：" + oldPath);
                log.info("newPath：" + newPath);
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
                out.reset();
                log.info("==============版本分割================");
            }
            log.info("------------------------------end-----------------------------");
        } catch (Exception e) {
            log.error("对比版本差异difVersionInfo()出错：" + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 状态
     * untracked 表示是新文件，没有被add过，是为跟踪的意思
     * modified 修改的文件
     * @param relativePath 相对git库的文件路径
     * @return NONE：未修改   ADD：新增
     *          DELETE：删除   MODIFY：修改
     */
    @Override
    public String status(String relativePath) {
        String status = null;
        try {
            ArrayList<String> files = new ArrayList<>();
            files.add(relativePath);
            //判断是否有被修改过的文件
            List<DiffEntry> diffEntries = git.diff()
                    .setPathFilter(PathFilterGroup.createFromStrings(files))
                    .setShowNameAndStatusOnly(true).call();
            if (diffEntries == null || diffEntries.size() == 0) {
                status = "NONE";
            }
            //被修改过的文件
            DiffEntry.ChangeType changeType;
            for (DiffEntry entry : diffEntries) {
                changeType = entry.getChangeType();
                switch (changeType) {
                    case ADD:
                        status = "ADD";
                        break;
                    case DELETE:
                        status = "DELETE";
                        break;
                    case MODIFY:
                        status = "MODIFY";
                        break;
                }
            }
        } catch (Exception e) {
            log.error("status() error:" + e.getMessage());
        }
        return status;
    }

    /**
     *
     * @param commitIds  通过getFileVersion()方法返回值中key为commitId的值
     * @param relativePaths 相对git库的文件路径
     * @return 单个文件直接返回文件的byte[]；多个文件为zip格式的bytes
     */
    @Override
    public byte[] readHisFile(String []commitIds,String []relativePaths) {
        // TODO zip压缩
        if (commitIds.length != relativePaths.length) {
            log.error("readHisFile()参数输入错误");
            return null;
        }
        ByteArrayOutputStream out = null;
        byte[] rtnBytes = null;
        try {
            //定义输出流
            FileOutputStream fout = new FileOutputStream("D:/MrJ's Documents/downFiles/test.zip");
            //将文件的内容放进一个map里
            Map<String, byte[]> datas = new HashMap<>();
            for (int i = 0; i < commitIds.length; i++) {
                String revision = commitIds[i];
                String relativePath = relativePaths[i];
                String fileName = relativePath.substring(relativePath.lastIndexOf("/")+1);
                RevWalk walk = new RevWalk(repository);
                ObjectId objId = repository.resolve(revision);
                RevCommit revCommit = walk.parseCommit(objId);
                RevTree revTree = revCommit.getTree();

                TreeWalk treeWalk = TreeWalk.forPath(repository, relativePath, revTree);
                if (treeWalk == null) {
                    String msg = "版本号：" + revision + "在路径" + relativePath + "下无该文件";
                    log.error(msg);
                    throw new Exception(msg);
                }
                ObjectId blobId = treeWalk.getObjectId(0);
                ObjectLoader loader = repository.open(blobId);
                out = new ByteArrayOutputStream();
                loader.copyTo(out);

                datas.put(fileName, out.toByteArray());
            }
            if (commitIds.length == 1 && relativePaths.length == 1) {
                // 单个文件
                rtnBytes = out.toByteArray();
            } else {
                // 多个文件
                //装饰器模式：用ZipOutputStream包装输出流，使其拥有写入zip文件的能力
                ZipOutputStream zipout = new ZipOutputStream(new BufferedOutputStream(fout));

                //遍历
                Set<String> keys = datas.keySet();
                for (String key : keys) {
                    //下面就是循环把每个文件写进zip文件
                    InputStream bin = new BufferedInputStream(new ByteArrayInputStream(datas.get(key)));
                    byte[] b = new byte[1024];

                    zipout.putNextEntry(new ZipEntry(key));
                    int len;
                    while ((len = bin.read(b)) != -1) {
                        zipout.write(b, 0, len);
                    }
                }
                zipout.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return rtnBytes;
    }

    /**
     * 创建文件夹
     * @param filePath  相对于gitlab.localPath的相对路径，支持多记目录创建
     */
    @Override
    public void createFolder(String filePath) {
        File dir = new File(localPath + "/" + filePath);
        if(!dir.exists()){
            dir.mkdirs();
            System.out.println("目录创建完毕。");
        }else{
            System.out.println("目录已存在！");
        }
    }

    /**
     * 获取差异信息
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
                String commitId = commit.getName();  //这个应该就是提交的版本
                ObjectId treeId = commit.getTree().getId();     // 对比差异所用的ID
                String path = fullMessage.substring(0, fullMessage.indexOf("_"));

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
                map.put("commitMsg", fullMessage);
                map.put("path", path);
                infoList.add(map);
            }
        } catch (Exception e) {
            log.error("打印版本差异信息出错：" + e.getMessage());
            e.printStackTrace();
        }
        return infoList;
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

}
