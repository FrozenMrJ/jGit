package service.impl;

import entity.FileInfo;
import entity.TreeItemVO;
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
import org.springframework.web.multipart.MultipartFile;
import service.JGitService;
import sun.reflect.generics.tree.Tree;
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

    private void init() {
        try {
            git = Git.open(new File(localPath));
            repository = git.getRepository();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean pullBranchToLocal(){
        init();
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

    @Override
    public String commitToGitRepository(String relativePath,String msg){
        init();
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

            RevCommit revCommit = commitCmd.setMessage(msg).call();
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

    @Override
    public List<Map<String, Object>> getFileVersion(String fileName,int maxCount){
        init();
        try {
            Iterable<RevCommit> commits = git.log().addPath(fileName).call();
            return getDifInfo(commits);
        } catch (GitAPIException e) {
            log.error("getFileVersion failed：" + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public List<Map<String, Object>> getAllVersion(int maxCount) {
        init();
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

    @Override
    public void difVersionInfo(ObjectId treeId1,ObjectId treeId2){
        init();
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

    @Override
    public String status(String relativePath) {
        init();
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

    @Override
    public byte[] readHisFile(String []commitIds,String []relativePaths) {
        init();
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

    public void upload(MultipartFile file, String relativePath) {
        try {
            if (file.isEmpty()) {
                throw new Exception("upload上传文件为空");
            }
            // 获取文件名
            String fileName = file.getOriginalFilename();
            System.out.println("上传的文件名为：" + fileName);
            // 获取文件的后缀名
            String suffixName = fileName.substring(fileName.lastIndexOf("."));
            System.out.println("上传的后缀名为：" + suffixName);
            // 文件上传后的路径
            File dest = new File(localPath + "/" + relativePath + "/" + fileName);
            // 检测是否存在目录
            if (!dest.getParentFile().exists()) {
                dest.getParentFile().mkdirs();
            }
            file.transferTo(dest);
            log.info("上传成功");
        } catch (Exception e) {
            log.error(e.getMessage());
        }
    }

    @Override
    public TreeItemVO initDirTreeStatus() {
        TreeItemVO root = new TreeItemVO();
        getListFiles(localPath,root);
        root = root.getChildren().get(0);
        return root;
    }

    @Override
    public void createFolder(String filePath) {
        File dir = new File(localPath + "/" + filePath);
        if(!dir.exists()){
            dir.mkdirs();
            log.info("目录创建完毕。");
        }else{
            log.info("目录已存在！");
        }
    }

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

    /**
     * 递归建立文件目录树
     * @param obj   文件/目录
     * @param root  当前子树根节点
     */
    private void getListFiles(Object obj,TreeItemVO root) {
        File directory;
        if (obj instanceof File) {
            directory = (File) obj;
        } else {
            directory = new File(obj.toString());
        }
        List<TreeItemVO> files;
        if (root.getChildren() == null) {
            files = new ArrayList<>();
        } else {
            files = root.getChildren();
        }
        if (!directory.getName().equals(".git")) {  // 过滤.git目录
            String absolutePath = directory.getAbsolutePath();
            String relativePath = absolutePath.replace(localPath + "\\", "");
            TreeItemVO treeItemVO = new TreeItemVO();
            treeItemVO.setFileName(directory.getName());
            treeItemVO.setFilePath(relativePath);
            if (directory.isFile()) {
                treeItemVO.setType("file");
                treeItemVO.setStatus(status(relativePath));
                files.add(treeItemVO);
            } else if (directory.isDirectory()) {
                treeItemVO.setType("dir");
                File[] fileArr = directory.listFiles();
                for (File fileOne : fileArr) {
                    getListFiles(fileOne, treeItemVO);
                    if (!files.contains(treeItemVO)) {
                        files.add(treeItemVO);
                    }
                }
            }
        }
        root.setChildren(files);
    }
}
