package io.jenkins.plugins;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.model.Bucket;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.region.Region;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.exception.CosServiceException;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.apache.commons.lang.time.DurationFormatUtils;
import org.springframework.jdbc.core.ColumnMapRowMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.FileNameMap;
import java.net.URLConnection;
import java.util.List;
import java.util.StringTokenizer;



public class TencentCloudCOSClient {

    // 上传多个文件时，路径分隔符
    private static final String fpSeparator = ";";

    public static boolean validateTencentCloudAccount(
            final String tencentSecretId, final String tencentSecretKey) throws CosClientException {
        try {
            COSCredentials cred = new BasicCOSCredentials(tencentSecretId, tencentSecretKey);
            ClientConfig clientConfig = new ClientConfig();
            COSClient cosClient = new COSClient(cred, clientConfig);
            cosClient.listBuckets();
        } catch (Exception e){
            throw new CosClientException("腾讯云账号验证失败：" + e.getMessage());
        }
        return true;
    }

    public static String validateTencentCOSBucket(
            String tencentSecretId, String tencentSecretKey, String bucketName) throws CosClientException {
        String location = "";
        try {
            COSCredentials cred = new BasicCOSCredentials(tencentSecretId, tencentSecretKey);
            ClientConfig clientConfig = new ClientConfig();
            COSClient cosClient = new COSClient(cred, clientConfig);
            List<Bucket> buckets =  cosClient.listBuckets();
            for (Bucket bucket:buckets) {
                if (bucketName.equals(bucket.getName())) {
                    location = bucket.getLocation();
                    break;
                } else continue;
            }
            clientConfig.setRegion(new Region(location));
            location = cosClient.getBucketLocation(bucketName);
        } catch (Exception e) {
            throw new CosClientException("验证Bucket名称失败：" + e.getMessage());
        }
        return location;
    }

    public static int upload(Run<?, ?> run, TaskListener listener,final String tencentSecretId, final String tencentSecretKey, final String tencentEndPointSuffix,
                             String bucketName, String localFiles, String objectPrefix, FilePath workspacePath, boolean useFullPath)
            throws CosClientException {
        String location = validateTencentCOSBucket(tencentSecretId, tencentSecretKey, bucketName);
        String endpoint = "https://" + bucketName + ".cos." + location + tencentEndPointSuffix;
        COSCredentials cred = new BasicCOSCredentials(tencentSecretId, tencentSecretKey);
        ClientConfig clientConfig = new ClientConfig(new Region(location));
        COSClient client = new COSClient(cred, clientConfig);
        int filesUploaded = 0;
        try {
            if (workspacePath == null) {
                listener.getLogger().println("工作空间中没有任何文件.");
                return filesUploaded;
            }
            StringTokenizer strTokens = new StringTokenizer(localFiles, fpSeparator);
            FilePath[] paths = null;

            listener.getLogger().println("开始上传到腾讯云COS...");
            listener.getLogger().println("上传endpoint是：" + endpoint);

            while (strTokens.hasMoreElements()) {
                String fileName = strTokens.nextToken();

                if (Utils.isNullOrEmpty(fileName)) {
                    return filesUploaded;
                }
                FilePath fp = new FilePath(workspacePath, fileName);

                if (fp.exists() && !fp.isDirectory()) {
                    paths = new FilePath[1];
                    paths[0] = fp;
                } else {
                    paths = workspacePath.list(fileName);
                }

                if (paths.length != 0) {
                    for (FilePath src : paths) {
                        String key = src.getName();
                        if(useFullPath) {
                            key = src.getRemote().replace(workspacePath.toString()+"/","");
                        }
                        if (!Utils.isNullOrEmpty(objectPrefix)){
                            key =  objectPrefix + key;
                        }
                        long startTime = System.currentTimeMillis();
                        InputStream inputStream = src.read();
                        try {
                            ObjectMetadata meta = new ObjectMetadata();
                            meta.setContentLength(src.length());
                            meta.setContentType(getContentType(src));
                            client.putObject(bucketName, key, inputStream, meta);
                        } finally {
                            try {
                                inputStream.close();
                            } catch (IOException e) {
                            }
                        }
                        long endTime = System.currentTimeMillis();
                        listener.getLogger().println("Uploaded object ["+ key + "] in " + getTime(endTime - startTime));
                        filesUploaded++;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new CosClientException(e.getMessage(), e.getCause());
        }
        return filesUploaded;
    }

    public static String getTime(long timeInMills) {
        return DurationFormatUtils.formatDuration(timeInMills, "HH:mm:ss.S") + " (HH:mm:ss.S)";
    }

    private static final String[] COMMON_CONTENT_TYPES = {
            ".js", "application/js",
            ".json", "application/json",
            ".svg", "image/svg+xml",
            ".woff", "application/x-font-woff",
            ".woff2", "application/x-font-woff",
            ".ttf", "application/x-font-ttf"
    };

    private static String getContentType(FilePath filePath) {
        FileNameMap fileNameMap = URLConnection.getFileNameMap();
        String fileName = filePath.getName();
        String type = fileNameMap.getContentTypeFor(fileName);

        if (type == null) {
            for (int i = 0; i < COMMON_CONTENT_TYPES.length; i += 2) {
                String extension = COMMON_CONTENT_TYPES[i];
                int beginIndex = Math.max(0, fileName.length() - extension.length());
                if (fileName.substring(beginIndex).equals(extension)) {
                    return COMMON_CONTENT_TYPES[i + 1];
                }
            }
            type = "application/octet-stream";
        }
        return type;
    }
}
