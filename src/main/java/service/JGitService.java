package service;

import entity.FileInfo;
import entity.TreeItemVO;
import org.eclipse.jgit.lib.ObjectId;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;

/**
 * 项目管理功能接口：提交、更新、项目分支管理、对比功能
 * 1、多选下载目录及文件，能下载历史版本
 * 2、选中文件，可查看当前文件的历史版本信息
 * 3、对比不同版本文件差异
 * 4、数据的恢复
 */
public interface JGitService {
    /**
     * git clone，建立与远程仓库的联系，仅需要执行一次
     */
    void gitClone();

    /**
     * pull拉取远程仓库文件
     *
     * @return true / false
     */
    boolean pullBranchToLocal();

    /**
     * 获取一个文件所有的版本(也就是提交记录)
     * 如果某一次的提交，包含了多个文件，其中包含了这个文件，该次提交也会被包含到结果其中
     *
     * @param fileName 带后缀的完整文件名的相对路径，如dir/test.doc
     * @param maxCount 返回的版本个数
     * @return  key             value                 类型
     *         commitName       提交人                String
     *         commitDate       提交日期              Date
     *         commitId         版本号                String
     *         treeId    用于对比的ID(无实际意义)     ObjectId
     *         commitMsg        提交备注              String
     */
    List<Map<String, Object>> getFileVersion(String fileName,int maxCount);

    /**
     * 获取git版本的最近全部maxCount条差异信息
     *
     * @param maxCount 返回的版本个数
     * @return  key             value                 类型
     *         commitName       提交人                String
     *         commitDate       提交日期              Date
     *         commitId         版本号                String
     *         treeId    用于对比的ID(无实际意义)     ObjectId
     *         commitMsg        提交备注              String
     */
    List<Map<String, Object>> getAllVersion(int maxCount);

    /**
     * 对比两个版本的差异，输出两个版本之间全部的操作
     *
     * @param treeId1 新版本号
     * @param treeId2 老版本号
     */
    void difVersionInfo(ObjectId treeId1, ObjectId treeId2);

    /**
     * 状态
     * @param relativePath 相对git库的文件路径
     * @return NONE：未修改   ADD：新增
     *          DELETE：删除   MODIFY：修改
     */
    String status(String relativePath);

    /**
     * 将文件列表提交到git仓库中
     *
     * @param relativePath 相对git库的文件路径
     * @param msg 提交的备注信息
     * @return 返回本次提交的版本号
     */
    String commitToGitRepository(String relativePath,String msg);

    /**
     *
     * @param commitIds  通过getFileVersion()方法返回值中key为commitId的值
     * @param relativePaths 相对git库的文件路径
     * @return
     */
    byte[] readHisFile(String []commitIds, String []relativePaths);

    /**
     * 创建文件夹
     *
     * @param filePath 相对git库的文件路径，支持多级目录创建
     */
    void createFolder(String filePath);

    /**
     * 文件上传
     * @param file
     * @param relativePath  文件上传相对路径
     */
    void upload(MultipartFile file, String relativePath);

    /**
     * 初始渲染目录结构
     * @return 目录下每个文件的状态
     */
    TreeItemVO initDirTreeStatus();
}
