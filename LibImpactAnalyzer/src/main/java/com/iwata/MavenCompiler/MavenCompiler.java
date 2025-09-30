package com.iwata.MavenCompiler;

import com.iwata.MavenCompiler.CleanerConfig;
import com.iwata.MavenCompiler.ErrorInfo;
import com.iwata.MavenCompiler.FileUtility;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

/**
  Mavenコンパイルの実行とエラー抽出
 **/
public class MavenCompiler {
    public Map<String, ErrorInfo> runMavenCompileAndExtractErrors() throws Exception {
        ProcessBuilder pb = new ProcessBuilder(CleanerConfig.getMavenCmd(), "clean", "compile");
        pb.directory(new File(CleanerConfig.PROJECT_DIR));
        pb.redirectErrorStream(true);

        Process process = pb.start();
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)
        );

        Map<String, ErrorInfo> errorFiles = new HashMap<>();
        Pattern errorPattern = Pattern.compile("([^\\\\/:*?\"<>|]+\\.java).*?\\[(\\d+),");

        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println(line);
            
            Matcher m = errorPattern.matcher(line);
            if (m.find()) {
                String fileName = m.group(1);
                int lineNumber = Integer.parseInt(m.group(2));
                
                String filePath = FileUtility.findJavaFile(fileName);
                if (filePath != null) {
                    ErrorInfo errorInfo = errorFiles.computeIfAbsent(fileName, 
                        k -> new ErrorInfo(fileName, filePath));
                    errorInfo.addErrorLine(lineNumber);
                }
            }
        }
        process.waitFor();

        return errorFiles;
    }
}