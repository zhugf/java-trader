package trader.common.util;

import java.io.File;
import java.io.IOException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Properties;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 加密解密工具类
 */
public class EncryptionUtil {
    private final static Logger logger = LoggerFactory.getLogger(EncryptionUtil.class);

    private static final char[] KEY_PASSWORD = new char[] {'A','d','m','i','n','@','1','2','3'};

    private static String KEY_AES;

    private static String KEY_RSA;

    private static Cipher CIPHER_AES_ENCRYPT;

    private static Cipher CIPHER_AES_DECRYPT;

    private static Cipher CIPHER_PUB;

    private static Cipher CIPHER_PRIV;

    /**
     * 为密钥文件目录自动创建密钥, 只能从管理节点调用
     * @param keyFileIni INI格式Key文件
     */
    public static void createKeyFile(String keyFileIni) throws Exception
    {
        File file = new File(keyFileIni);
        if ( file.length()>0 ) {
            logger.debug("Encryption key file "+file+" exists, length "+file.length());
            return;
        }
        Base64.Encoder encoder = Base64.getEncoder();

        KeyGenerator aesGen = KeyGenerator.getInstance("AES");
        aesGen.init(256); // 192 and 256 bits may not be available
        SecretKey aesKey = aesGen.generateKey();

        KeyPairGenerator rsaGenerator = KeyPairGenerator.getInstance("RSA");
        rsaGenerator.initialize(1024);

        KeyPair keyPair = rsaGenerator.generateKeyPair();
        PublicKey pub = keyPair.getPublic();
        PrivateKey priv = keyPair.getPrivate();
        String pubBase64 = encoder.encodeToString(pub.getEncoded());
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[20];
        random.nextBytes(salt);
        StringBuilder saltStr = new StringBuilder(128);
        for(int i=0;i<salt.length;i++) {
            if ( i>0 )
                saltStr.append(",");
            saltStr.append( Integer.toHexString( ((salt[i])&0XFF)) );
        }
        Cipher pbeCipher = getPBECipher(salt, Cipher.ENCRYPT_MODE);
        String aesBase64 = encoder.encodeToString(pbeCipher.doFinal(aesKey.getEncoded()));
        String privBase64 = encoder.encodeToString(pbeCipher.doFinal(priv.getEncoded()));
        String aesId = "key_"+Base58.compressedUUID(UUID.randomUUID());
        String rsaId = "key_"+Base58.compressedUUID(UUID.randomUUID());
        IniWriter iniWrite = new IniWriter(FileUtil.bufferedWrite(file));
        iniWrite.writeSection("info");
        iniWrite.writeProperty("aesId", aesId);
        iniWrite.writeProperty("rsaId", rsaId);
        iniWrite.writeProperty("createdTime", LocalDateTime.now());
        iniWrite.writeProperty("publicFormat", pub.getFormat());
        iniWrite.writeProperty("privateFormat", priv.getFormat());
        iniWrite.writeProperty("salt", saltStr.toString());
        iniWrite.writeSection("aes");
        iniWrite.write(aesBase64);
        iniWrite.writeSection("public");
        iniWrite.write(pubBase64);
        iniWrite.writeSection("private");
        iniWrite.write(privBase64);
        iniWrite.close();

        logger.info("Encryption key "+aesId+", "+rsaId+" was created");
    }

    private static Cipher getPBECipher(byte[] salt, int cipherMode) throws Exception
    {
        String MYPBEALG = "PBEWithSHA1AndDESede";
        int count = 32;// hash iteration count

        // Create PBE parameter set
        PBEParameterSpec pbeParamSpec = new PBEParameterSpec(salt, count);
        PBEKeySpec pbeKeySpec = new PBEKeySpec(KEY_PASSWORD);
        SecretKeyFactory keyFac = SecretKeyFactory.getInstance(MYPBEALG);
        SecretKey pbeKey = keyFac.generateSecret(pbeKeySpec);

        Cipher pbeCipher = Cipher.getInstance(MYPBEALG);

        // Initialize PBE Cipher with key and parameters
        pbeCipher.init(cipherMode, pbeKey, pbeParamSpec);
        return pbeCipher;
    }

    /**
     * 初始化密钥文件目录, 加载密钥
     */
    public static void loadKeyFile(String keyFileIni) throws Exception
    {
        File file = new File(keyFileIni);
        if ( file.length()<= 0) {
            throw new IOException("Key file "+keyFileIni+" doesn't exists");
        }
        Base64.Decoder decoder = Base64.getDecoder();
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        Cipher pbeCipher = null;
        IniFile ini = new IniFile(file);
        for(IniFile.Section section:ini.getAllSections()) {
            switch(section.getName()) {
            case "info":
                Properties props = section.getProperties();
                KEY_AES = props.getProperty("aesId");
                KEY_RSA = props.getProperty("rsaId");
                String[] saltStr = StringUtil.split(props.getProperty("salt"),",");
                byte[] salt = new byte[saltStr.length];
                for(int i=0;i<saltStr.length;i++) {
                    salt[i] = (byte)( Integer.parseInt(saltStr[i], 16) );
                }
                pbeCipher = getPBECipher(salt, Cipher.DECRYPT_MODE);
                break;
            case "aes":
                byte[] aesBytes = decoder.decode(section.getText());
                byte[] aesBytes2 = pbeCipher.doFinal(aesBytes);
                SecretKeySpec aesKeySpec = new SecretKeySpec(aesBytes2, "AES");
                CIPHER_AES_ENCRYPT = Cipher.getInstance("AES/ECB/PKCS5Padding");
                CIPHER_AES_ENCRYPT.init(Cipher.ENCRYPT_MODE, aesKeySpec);

                CIPHER_AES_DECRYPT = Cipher.getInstance("AES/ECB/PKCS5Padding");
                CIPHER_AES_DECRYPT.init(Cipher.DECRYPT_MODE, aesKeySpec);
                break;
            case "public":
                byte[] pubBytes = decoder.decode(section.getText());
                CIPHER_PUB = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                CIPHER_PUB.init(Cipher.ENCRYPT_MODE, keyFactory.generatePublic(new X509EncodedKeySpec(pubBytes)));
                break;
            case "private":
                byte[] privBytes = decoder.decode(section.getText());
                byte[] privateBytes2 = pbeCipher.doFinal(privBytes);
                CIPHER_PRIV = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                CIPHER_PRIV.init(Cipher.DECRYPT_MODE, keyFactory.generatePrivate(new PKCS8EncodedKeySpec(privateBytes2)));
                break;
            }
        }
        logger.info("Encryption key "+KEY_AES+","+KEY_RSA+" was loaded");
    }

    public static boolean isEncryptedData(String data) {
        if ( StringUtil.isEmpty(data) ) {
            return false;
        }
        if ( data.startsWith("{") && data.indexOf("}")>0 ) {
            return true;
        }
        return false;
    }

    /**
     * 使用AES256加密
     */
    public static String symmetricEncrypt(byte[] data)
    {
        return cipherEncrypt(data, CIPHER_AES_ENCRYPT, KEY_AES);
    }

    /**
     * 使用AES256解密
     */
    public static byte[] symmetricDecrypt(String encryptedData)
    {
        return cipherDecrypt(encryptedData, CIPHER_AES_DECRYPT, KEY_AES);
    }

    /**
     * 使用公钥加密
     */
    public static String asymmetricEncrypt(byte[] data)
    {
        return cipherEncrypt(data, CIPHER_PUB, KEY_RSA);
    }

    /**
     * 使用私钥解密
     */
    public static byte[] asymmetricDecrypt(String encryptedData)
    {
        return cipherDecrypt(encryptedData, CIPHER_PRIV, KEY_RSA);
    }

    private static String cipherEncrypt(byte[] data, Cipher cipher, String keyId)
    {
        if ( cipher==null ) {
            throw new RuntimeException("Encryption key is not loaded");
        }
        byte[] encryptedData = null;
        try {
            synchronized(cipher){
                encryptedData = cipher.doFinal(data);
            }
        }catch(Throwable t) {
            throw new RuntimeException("encrypt failed: "+t.toString(), t);
        }
        StringBuilder result = new StringBuilder(128);
        result.append("{").append(keyId).append("}").append( Base58.encode(encryptedData) );
        return result.toString();
    }

    /**
     * 解密
     */
    private static byte[] cipherDecrypt(String encryptedData, Cipher cipher, String keyId)
    {
        if ( cipher==null ) {
            throw new RuntimeException("decryption key not loaded");
        }
        int rightIndex = encryptedData.indexOf('}');
        String encryptedKeyId = encryptedData.substring(1, rightIndex);
        if ( !StringUtil.equals(keyId, encryptedKeyId)) {
            throw new RuntimeException("Encrypt key is not matched: "+encryptedKeyId);
        }
        String data0 = encryptedData.substring(rightIndex+1);
        byte[] data = Base58.decode(data0);
        try {
            synchronized(cipher){
                byte[] result = cipher.doFinal(data);
                return result;
            }
        }catch(Throwable t) {
            throw new RuntimeException("Decrypt failed: "+t, t);
        }
    }

}
