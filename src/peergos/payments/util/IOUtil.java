package peergos.payments.util;

import java.io.*;

public class IOUtil {

    public static byte[] readFully(InputStream in, int maxSize) throws IOException {
        ByteArrayOutputStream bout =  new ByteArrayOutputStream();
        byte[] b =  new  byte[0x1000];
        int nRead;
        while ((nRead = in.read(b, 0, b.length)) != -1 ) {
            bout.write(b, 0, nRead);
            if (bout.size() > maxSize)
                throw new IllegalStateException("Too much data to read!");
        }
        in.close();
        return bout.toByteArray();
    }
}
