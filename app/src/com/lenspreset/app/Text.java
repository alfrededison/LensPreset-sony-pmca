package com.lenspreset.app;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/** UTF-8 text from files and assets. */
final class Text {
    private Text() {
    }

    static String read(File f) throws IOException {
        return read(new FileInputStream(f));
    }

    /** Reads and closes {@code in}. */
    static String read(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            in.close();
        }
    }
}
