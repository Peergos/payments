package peergos.payments.http;

import java.io.*;
import java.nio.file.*;

public class JarHandler extends StaticHandler {
    private final Path root;

    public JarHandler(boolean isGzip, Path root) {
        super(isGzip);
        this.root = root;
    }

    @Override
    public Asset getAsset(String resourcePath) throws IOException {
        String pathWithinJar = "/" + root.resolve(resourcePath).toString()
                .replaceAll("\\\\", "/"); // needed for Windows!
        byte[] data = StaticHandler.readResource(JarHandler.class.getResourceAsStream(pathWithinJar), isGzip());
        return new Asset(data);
    }
}
