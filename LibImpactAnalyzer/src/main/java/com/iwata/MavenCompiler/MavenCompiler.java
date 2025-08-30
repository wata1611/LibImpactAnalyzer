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
    
    /**
     * テスト結果を格納するクラス
     */
    public static class TestResult {
        private int totalTests = 0;
        private int passedTests = 0;
        private int failedTests = 0;
        private int skippedTests = 0;
        
        public int getTotalTests() { return totalTests; }
        public void setTotalTests(int totalTests) { this.totalTests = totalTests; }
        
        public int getPassedTests() { return passedTests; }
        public void setPassedTests(int passedTests) { this.passedTests = passedTests; }
        
        public int getFailedTests() { return failedTests; }
        public void setFailedTests(int failedTests) { this.failedTests = failedTests; }
        
        public int getSkippedTests() { return skippedTests; }
        public void setSkippedTests(int skippedTests) { this.skippedTests = skippedTests; }
        
        public double getPassRate() {
            return totalTests > 0 ? (double) passedTests / totalTests * 100 : 0.0;
        }
    }
    
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
    
    /**
     * Mavenテストを実行してテスト結果を取得
     */
    public TestResult runMavenTest() throws Exception {
        ProcessBuilder pb = new ProcessBuilder(CleanerConfig.getMavenCmd(), "test");
        pb.directory(new File(CleanerConfig.PROJECT_DIR));
        pb.redirectErrorStream(true);

        Process process = pb.start();
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)
        );

        TestResult result = new TestResult();
        
        // Surefireプラグインの出力パターン
        Pattern testSummaryPattern = Pattern.compile("Tests run: (\\d+), Failures: (\\d+), Errors: (\\d+), Skipped: (\\d+)");
        
        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println(line);
            
            Matcher m = testSummaryPattern.matcher(line);
            if (m.find()) {
                int testsRun = Integer.parseInt(m.group(1));
                int failures = Integer.parseInt(m.group(2));
                int errors = Integer.parseInt(m.group(3));
                int skipped = Integer.parseInt(m.group(4));
                
                result.setTotalTests(testsRun);
                result.setFailedTests(failures + errors);
                result.setPassedTests(testsRun - failures - errors - skipped);
                result.setSkippedTests(skipped);
            }
        }
        
        process.waitFor();
        return result;
    }
    
    /**
     * テスト実行と関連のコンパイルエラー抽出
     */
    public Map<String, ErrorInfo> runMavenTestCompileAndExtractErrors() throws Exception {
        ProcessBuilder pb = new ProcessBuilder(CleanerConfig.getMavenCmd(), "test-compile");
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