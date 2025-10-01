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
        private List<String> failedTestMethods = new ArrayList<>();
        private List<String> libRemovedTestMethods = new ArrayList<>();
        
        public int getTotalTests() { return totalTests; }
        public void setTotalTests(int totalTests) { this.totalTests = totalTests; }
        
        public int getPassedTests() { return passedTests; }
        public void setPassedTests(int passedTests) { this.passedTests = passedTests; }
        
        public int getFailedTests() { return failedTests; }
        public void setFailedTests(int failedTests) { this.failedTests = failedTests; }
        
        public int getSkippedTests() { return skippedTests; }
        public void setSkippedTests(int skippedTests) { this.skippedTests = skippedTests; }
        
        public List<String> getFailedTestMethods() { return failedTestMethods; }
        public void addFailedTestMethod(String methodName) { this.failedTestMethods.add(methodName); }
        
        public List<String> getLibRemovedTestMethods() { return libRemovedTestMethods; }
        public void addLibRemovedTestMethod(String methodName) { this.libRemovedTestMethods.add(methodName); }
        
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
        
        // パターン定義
        Pattern testSummaryPattern = Pattern.compile("Tests run: (\\d+), Failures: (\\d+), Errors: (\\d+), Skipped: (\\d+)");
        // 失敗したテストメソッド名のパターン
        // 例: [ERROR] ncdsearch.comparison.algorithm.NgramTest.testNgramSimilarity -- Time elapsed: 0 s <<< FAILURE!
        Pattern failedTestPattern = Pattern.compile("\\[ERROR\\]\\s+([\\w.]+)\\.([\\w]+)\\s+--\\s+Time elapsed:.*<<<\\s+FAILURE!");
        // [LIB-REMOVED]メッセージのパターン
        Pattern libRemovedPattern = Pattern.compile("\\[LIB-REMOVED\\]");
        
        String line;
        String lastFailedTest = null;
        boolean nextLineIsLibRemoved = false;
        
        while ((line = reader.readLine()) != null) {
            System.out.println(line);
            
            // テストサマリーの解析
            Matcher summaryMatcher = testSummaryPattern.matcher(line);
            if (summaryMatcher.find()) {
                int testsRun = Integer.parseInt(summaryMatcher.group(1));
                int failures = Integer.parseInt(summaryMatcher.group(2));
                int errors = Integer.parseInt(summaryMatcher.group(3));
                int skipped = Integer.parseInt(summaryMatcher.group(4));
                
                result.setTotalTests(testsRun);
                result.setFailedTests(failures + errors);
                result.setPassedTests(testsRun - failures - errors - skipped);
                result.setSkippedTests(skipped);
            }
            
            // 失敗したテストメソッド名の解析
            Matcher failedMatcher = failedTestPattern.matcher(line);
            if (failedMatcher.find()) {
                String className = failedMatcher.group(1);
                String methodName = failedMatcher.group(2);
                String fullTestName = className + "." + methodName;
                lastFailedTest = fullTestName;
                nextLineIsLibRemoved = true;
            }
            
            // [LIB-REMOVED]メッセージの検出
            if (nextLineIsLibRemoved && lastFailedTest != null) {
                Matcher libRemovedMatcher = libRemovedPattern.matcher(line);
                if (libRemovedMatcher.find()) {
                    result.addLibRemovedTestMethod(lastFailedTest);
                    lastFailedTest = null;
                    nextLineIsLibRemoved = false;
                } else if (line.contains("at org.junit.Assert.fail") || 
                          line.contains("at org.junit.Assert.assertTrue") ||
                          line.contains("at org.junit.Assert.assertEquals")) {
                    // スタックトレースの最初の行まで来たら判定を完了
                    if (!result.getLibRemovedTestMethods().contains(lastFailedTest)) {
                        result.addFailedTestMethod(lastFailedTest);
                    }
                    lastFailedTest = null;
                    nextLineIsLibRemoved = false;
                }
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