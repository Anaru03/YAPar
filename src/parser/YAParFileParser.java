package parser;

import java.io.*;

public class YAParFileParser {

    public static Grammar parse(String filePath) throws IOException {
        String content = readFile(filePath);
        return parseContent(content);
    }

    public static Grammar parseContent(String content) {
        return null;
    }

    private static String readFile(String path) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
        }
        return sb.toString();
    }
}