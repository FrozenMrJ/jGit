package entity;

import lombok.Getter;
import lombok.Setter;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

@Getter
@Setter
public class FileInfo extends File {
    // id
    private long id;

    // 父节点id
    private long parentId;
	public FileInfo(String pathname) {
		super(pathname);
	}
}
