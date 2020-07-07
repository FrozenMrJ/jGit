import utils.FileUtils;

/**
 * @ClassName FileUtilsTest
 * @Description
 * @Author jinym
 * @Date 2020/7/7
 **/
public class FileUtilsTest {
    public static void main(String[] args){
        boolean b = FileUtils.deleteFolder("D:\\MrJ's Documents\\downFiles\\test\\2018");
        System.out.println(b);
    }
}
