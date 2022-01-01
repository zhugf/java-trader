package trader.common.util;

import java.io.File;

import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.VFS;
import org.apache.commons.vfs2.provider.sftp.SftpFileSystemConfigBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VFSUtil {
    private static final Logger logger = LoggerFactory.getLogger(VFSUtil.class);

    private static String clusterPrefix="";
    public static final FileSystemManager fsManager;
    private static FileSystemOptions sftpOpts;
    static {
        try {
            fsManager = VFS.getManager();
        } catch (Throwable e) {
            logger.error("VFS manager init fail", e);
            throw new RuntimeException(e);
        }
    }

    public static void init(String prefix) {
        clusterPrefix = prefix;
        if (!clusterPrefix.endsWith("/")) {
            clusterPrefix = clusterPrefix + "/";
        }

        logger.info("The clusterPrefix of VFSutil is (init) :" + clusterPrefix);
        sftpOpts = new FileSystemOptions();
        SftpFileSystemConfigBuilder.getInstance().setUserDirIsRoot(sftpOpts, false);
    }

    public static FileObject path2object(String path) throws FileSystemException
    {
        if (StringUtil.isEmpty(path)) {
            return null;
        }
        if ( StringUtil.isEmpty(clusterPrefix) ) {
            return fsManager.toFileObject(new File(path));
        } else {
            return fsManager.resolveFile(clusterPrefix+path, sftpOpts);
        }
    }

    public static FileObject file2object(File path) throws FileSystemException
    {
        return fsManager.toFileObject((path));
    }

}
