import lombok.Getter;
import lombok.Setter;

import java.io.File;
import java.util.LinkedList;

@Getter
@Setter
public class Fileinfo extends File {
	private int id;
	private int fid;
	
	public Fileinfo(String pathname) {
		super(pathname);
	}

	// 非递归遍历文件夹
	public static void readfiles(String path){
        long a = System.currentTimeMillis();
        
        LinkedList<Fileinfo> list = new LinkedList<>();
        Fileinfo dir = new Fileinfo(path);
        dir.setFid(0);
        dir.setId(1);
        int index = 1;
        File file[] = dir.listFiles();
        for (int i = 0; i < file.length; i++) {
        	Fileinfo tem  = new Fileinfo(file[i].getAbsolutePath());
        	tem.setId(++index);
        	tem.setFid(dir.getId());
        	System.out.println("id: "+tem.getId()+" fid:"+tem.getFid()+" "+tem.getAbsolutePath());
            if (file[i].isDirectory()){
                list.add(tem);
                }
            else{
            	
        }
        }
        Fileinfo tmp;
        while (!list.isEmpty()) {
            tmp = list.removeFirst();
            if (tmp.isDirectory()) {
                file = tmp.listFiles();
                if (file == null)
                    continue;
                for (int i = 0; i < file.length; i++) {
                	Fileinfo tem =new Fileinfo(file[i].getAbsolutePath());
                	tem.setFid(tmp.getId());
                	tem.setId(++index);
                    if (file[i].isDirectory())
                        list.add(tem);
                    else{

                    }
                    System.out.println("id: "+tem.getId()+" fid:"+tem.getFid()+" "+tem.getAbsolutePath());}
            } else {
                System.out.println(tmp.getAbsolutePath());
            }
        }
        
        System.out.println(System.currentTimeMillis() - a);

	}
	
	public static void main(String[] args) {
		readfiles("C:\\Users\\Administrator\\Desktop\\working\\");
	}
	
}
