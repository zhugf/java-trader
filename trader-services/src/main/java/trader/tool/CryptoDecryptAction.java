package trader.tool;

import java.io.PrintWriter;
import java.util.List;

import trader.common.util.EncryptionUtil;
import trader.common.util.StringUtil;

public class CryptoDecryptAction implements CmdAction {

    @Override
    public String getCommand() {
        return "crypto.encrypt";
    }

    @Override
    public void usage(PrintWriter writer) {
        writer.println("crypto decrypt <PLAIN_TEXT>");
        writer.println("\t解密文本");
    }

    @Override
    public int execute(PrintWriter writer, List<String> options) throws Exception {
        String secretText = options.get(0);
        String result = new String(EncryptionUtil.symmetricDecrypt(secretText), StringUtil.UTF8);
        writer.println(result);
        return 0;
    }

}
