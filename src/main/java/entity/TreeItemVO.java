package entity;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class TreeItemVO extends BaseVO {
    // 文件名
    private String fileName;

    // 文件绝对路径
    private String filePath;

    // 类型:文件file，目录dir
    private String type;

    // 状态
    private String status;

    private List<TreeItemVO> children = new ArrayList<>();

}
