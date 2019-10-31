package io.jenkins.plugins;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import jenkins.tasks.SimpleBuildStep;

import java.io.IOException;
import java.io.PrintStream;
import javax.annotation.Nonnull;
import javax.servlet.ServletException;

import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;


public class TencentCloudCOSPublisher extends Publisher implements SimpleBuildStep {

    private PrintStream logger;
    String bucketName;
    String filesPath;
    String objectPrefix;
    boolean useFullPath;

    public String getBucketName() {
        return bucketName;
    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    public String getFilesPath() {
        return filesPath;
    }

    public void setFilesPath(String filesPath) {
        this.filesPath = filesPath;
    }

    public String getObjectPrefix() {
        return objectPrefix;
    }

    public void setObjectPrefix(String objectPrefix) {
        this.objectPrefix = objectPrefix;
    }

    public boolean isUseFullPath() { return useFullPath; }

    @DataBoundSetter
    public void setUseFullPath(boolean useFullPath) { this.useFullPath = useFullPath; }

    @DataBoundConstructor
    public TencentCloudCOSPublisher(final String bucketName, final String filesPath, final String objectPrefix) {
        this.bucketName = bucketName;
        this.filesPath = filesPath;
        this.objectPrefix = objectPrefix;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.STEP;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Symbol("cos")
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        private String tencentSecretId;
        private String tencentSecretKey;
        private String tencentEndPointSuffix;

        public DescriptorImpl() {
            super(TencentCloudCOSPublisher.class);
            load();
        }

        @Override
        public Publisher newInstance(StaplerRequest req, JSONObject formData)
                throws FormException {
            return super.newInstance(req, formData);
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "上传Aartifacts到腾讯云COS";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData)
                throws FormException {
            req.bindParameters(this);
            this.tencentSecretId        = formData.getString("tencentSecretId");
            this.tencentSecretKey       = formData.getString("tencentSecretKey");
            this.tencentEndPointSuffix  = formData.getString("tencentEndPointSuffix");
            save();
            return super.configure(req, formData);
        }

        public FormValidation doCheckTencentCloudAccount(
                @QueryParameter String tencentSecretId,
                @QueryParameter String tencentSecretKey,
                @QueryParameter String tencentEndPointSuffix) {
            if (Utils.isNullOrEmpty(tencentSecretId)) {
                return FormValidation.error("腾讯云SecretId不能为空！");
            }
            if (Utils.isNullOrEmpty(tencentSecretKey)) {
                return FormValidation.error("腾讯云SecretKey不能为空！");
            }
            if (Utils.isNullOrEmpty(tencentEndPointSuffix)) {
                return FormValidation.error("腾讯云EndPointSuffix不能为空！");
            }
            try {
                TencentCloudCOSClient.validateTencentCloudAccount(tencentSecretId,
                        tencentSecretKey);
            } catch (Exception e) {
                return FormValidation.error(e.getMessage());
            }
            return FormValidation.ok("验证腾讯云帐号成功！");
        }

        public FormValidation doCheckCOSBucket(@QueryParameter String val)
                throws IOException, ServletException {
            if (Utils.isNullOrEmpty(val)) {
                return FormValidation.error("Bucket不能为空！");
            }
            try {
                TencentCloudCOSClient.validateTencentCOSBucket(tencentSecretId, tencentSecretKey, val);
            } catch (Exception e) {
                return FormValidation.error(e.getMessage());
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckFilePath(@QueryParameter String val) {
            if (Utils.isNullOrEmpty(val)) {
                return FormValidation.error("Artifact路径不能为空！");
            }
            return FormValidation.ok();
        }

        public String getTencentSecretId() {
            return tencentSecretId;
        }

        public void setTencentSecretId(String tencentSecretId) {
            this.tencentSecretId = tencentSecretId;
        }

        public String getTencentSecretKey() {
            return tencentSecretKey;
        }

        public void setTencentSecretKey(String tencentSecretKey) {
            this.tencentSecretKey = tencentSecretKey;
        }

        public String getTencentCloudEndPointSuffix() {
            return tencentEndPointSuffix;
        }

        public void setTencentEndPointSuffix(String tencentEndPointSuffix) {
            this.tencentEndPointSuffix = tencentEndPointSuffix;
        }
    }

    @Override
    public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath filePath, @Nonnull Launcher launcher, @Nonnull TaskListener listener) throws InterruptedException, IOException {
        this.logger = listener.getLogger();
        final boolean buildFailed = run.getResult() == Result.FAILURE;
        if (buildFailed) {
            logger.println("Job构建失败, 结束上传Aritfacts到腾讯云COS操作.");
        }

        // Resolve file path
        String localFile = Utils.replaceTokens(run, listener, filesPath);

        if (localFile != null) {
            localFile = localFile.trim();
        }

        // Resolve virtual path
        String expVP = Utils.replaceTokens(run, listener, objectPrefix);
        if (Utils.isNullOrEmpty(expVP)) {
            expVP = null;
        }
        if (!Utils.isNullOrEmpty(expVP) && !expVP.endsWith(Utils.FWD_SLASH)) {
            expVP = expVP.trim() + Utils.FWD_SLASH;
        }

        try {
            int filesUploaded = TencentCloudCOSClient.upload(run, listener,
                    this.getDescriptor().tencentSecretId,
                    this.getDescriptor().tencentSecretKey,
                    this.getDescriptor().tencentEndPointSuffix,
                    bucketName, localFile, expVP, filePath, useFullPath);
            if (filesUploaded > 0) {
                listener.getLogger().println("上传Artifacts到腾讯云C0S成功，文件个数:" + filesUploaded);
            }
            System.out.println("hello");
        } catch (Exception e) {
            this.logger.println("上传Artifact到腾讯云COS失败，错误消息: ");
            this.logger.println(e.getMessage());
            e.printStackTrace(this.logger);
        }
    }
}
