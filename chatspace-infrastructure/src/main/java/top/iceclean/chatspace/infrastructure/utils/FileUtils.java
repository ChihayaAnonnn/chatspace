package top.iceclean.chatspace.infrastructure.utils;

import com.obs.services.ObsClient;
import com.obs.services.model.DeleteObjectResult;
import com.obs.services.model.HttpMethodEnum;
import com.obs.services.model.PutObjectRequest;
import org.springframework.web.multipart.MultipartFile;
import top.iceclean.chatspace.infrastructure.constant.FileConst;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Objects;

/**
 * 文件处理工具
 * @author : Ice'Clean
 * @date : 2022-06-26
 */
public class FileUtils {

    /**
     * 通过文件生成标准的文件名
     * @param file 文件数据
     * @return 标准化文件名
     */
    public static String generateFileName(MultipartFile file, String extend) {
        String extentName = file.getOriginalFilename();
        extentName = Objects.requireNonNull(extentName).substring(extentName.lastIndexOf("."));
        return String.format("%s_%s_%d%s", extend, DateUtils.getTimeCompact(), file.getSize(), extentName);
    }

    /**
     * 上传文件
     * @param file 文件数据
     * @param filePath 文件路径
     * @param fileName 上传后的文件名称
     * @return 上传状态码
     */
    public static FileConst.Code upload(MultipartFile file, String filePath, String fileName) {
        // 文件为空，上传失败
        if (file.isEmpty()) {
            return FileConst.Code.EMPTY_FILE;
        }

        try {
            // 获取文件目录
            File dir = new File(FileConst.ROOT_PATH + filePath);
            System.out.println(dir.isAbsolute());
            if (dir.exists() || dir.mkdirs()) {
                // 尝试上传文件
                File file1 = new File(FileConst.ROOT_PATH + filePath + fileName);
                System.out.println(file1.isAbsolute());
                file.transferTo(new File(FileConst.ROOT_PATH + filePath + fileName));
                return FileConst.Code.UPLOAD_SUCCESS;
            } else {
                // 创建文件夹失败
                return FileConst.Code.CREATE_DIR_FAILED;
            }
        } catch (Exception e) {
             e.printStackTrace();
        }

        return FileConst.Code.UNKNOWN_ERROR;
    }

    // Endpoint以北京四为例，其他地区请按实际情况填写。
    private static final String END_POINT = "https://obs.cn-north-4.myhuaweicloud.com";
    private static final String ACCESS_KEY = "QUUSZFGWXNQSZWZ0TZT0";
    private static final String SECRET_KEY = "rqmzM1JJuMVM2c4bggvFKSXSAxLEiKXKZGMsBFCz";
    /**
     * 将文件上传到华为云对象存储
     * @param file 文件实体
     * @param filePath 文件路径
     * @param fileName 文件名
     * @return 上传状态
     */
    public static FileConst.Code uploadToHuaWeiObs(MultipartFile file, String filePath, String fileName) {

        try {
            // 创建ObsClient实例
            ObsClient obsClient = new ObsClient(ACCESS_KEY, SECRET_KEY, END_POINT);

            // 获取文件目录
            String objectKey = filePath + fileName;
            // 待上传的本地文件路径，需要指定到具体的文件名
            obsClient.putObject(FileConst.BUCKET_NAME, objectKey, file.getInputStream());

            // 待上传的本地文件路径，需要指定到具体的文件名
            PutObjectRequest request = new PutObjectRequest();
            request.setBucketName(FileConst.BUCKET_NAME);
            request.setObjectKey(objectKey);
            request.setInput(file.getInputStream());
            obsClient.putObject(request);
            return FileConst.Code.UPLOAD_SUCCESS;
        } catch (IOException e) {
            System.err.println("上传 OSS 对象失败！");
            e.printStackTrace();
        }
        return FileConst.Code.UNKNOWN_ERROR;
    }

    public static void deleteFromHuaWeiObs(String filePath, String fileName) {
        String objectKey = filePath + fileName;
        ObsClient obsClient = new ObsClient(ACCESS_KEY, SECRET_KEY, END_POINT);
        obsClient.deleteObject(FileConst.BUCKET_NAME, objectKey);
    }
}
