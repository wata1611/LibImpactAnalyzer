package com.iwata.MavenCompiler;

import java.util.*;

/**
  定量化指標管理クラス（メインコードとテストコード分離対応、実行時間追加）
 **/
public class CompilationMetrics {
    private int iterationCount = 0;
    
    // メインコード用メトリクス
    private Set<String> modifiedMainFiles = new HashSet<>();
    private int totalMainFiles = 0;
    private int totalMainLines = 0;
    private int deletedMainLines = 0;
    private int deletedMainElements = 0;
    private Map<String, Integer> deletedMainElementsByType = new HashMap<>();
    
    // テストコード用メトリクス
    private Set<String> modifiedTestFiles = new HashSet<>();
    private int totalTestFiles = 0;
    private int totalTestLines = 0;
    private int deletedTestLines = 0;
    private int deletedTestElements = 0;
    private Map<String, Integer> deletedTestElementsByType = new HashMap<>();
    
    // テスト実行結果メトリクス
    private int totalTests = 0;
    private int passedTests = 0;
    private int failedTests = 0;
    private int errorTests = 0;  // エラーが発生したテスト数
    private int skippedTests = 0;
    private List<String> failedTestMethods = new ArrayList<>();
    private List<String> errorTestMethods = new ArrayList<>();  // エラーが発生したテストメソッド
    private List<String> libRemovedTestMethods = new ArrayList<>();
    
    // 実行時間メトリクス（ナノ秒）
    private long mainCodeDeletionTime = 0;
    private long testCodeDeletionTime = 0;
    private long totalDeletionTime = 0;
    private long testExecutionTime = 0;
    private long totalExecutionTime = 0;
    
    /**
     * ファイルパスからメインかテストかを判定
     */
    private boolean isTestFile(String filePath) {
        return filePath != null && filePath.contains("\\tests\\");
    }
    
    /**
     * 要素削除の記録（ファイル種別を自動判定）
     */
    public void incrementDeletedElements(String elementType, String filePath) {
        if (isTestFile(filePath)) {
            deletedTestElements++;
            deletedTestElementsByType.merge(elementType, 1, Integer::sum);
        } else {
            deletedMainElements++;
            deletedMainElementsByType.merge(elementType, 1, Integer::sum);
        }
    }
    
    /**
     * 修正ファイルの追加（ファイル種別を自動判定）
     */
    public void addModifiedFile(String fileName, String filePath) {
        if (isTestFile(filePath)) {
            modifiedTestFiles.add(fileName);
        } else {
            modifiedMainFiles.add(fileName);
        }
    }
    
    /**
     * 削除行数の追加（ファイル種別を自動判定）
     */
    public void addDeletedLines(int lines, String filePath) {
        if (isTestFile(filePath)) {
            deletedTestLines += lines;
        } else {
            deletedMainLines += lines;
        }
    }
    
    /**
     * テスト結果を設定
     */
    public void setTestResult(MavenCompiler.TestResult testResult) {
        this.totalTests = testResult.getTotalTests();
        this.passedTests = testResult.getPassedTests();
        this.failedTests = testResult.getFailedTests();
        this.errorTests = testResult.getErrorTests();
        this.skippedTests = testResult.getSkippedTests();
        this.failedTestMethods = new ArrayList<>(testResult.getFailedTestMethods());
        this.errorTestMethods = new ArrayList<>(testResult.getErrorTestMethods());
        this.libRemovedTestMethods = new ArrayList<>(testResult.getLibRemovedTestMethods());
    }
    
    /**
     * テスト通過率を計算
     */
    public double getTestPassRate() {
        return totalTests > 0 ? (double) passedTests / totalTests * 100 : 0.0;
    }
    
    /**
     * ナノ秒を読みやすい形式に変換
     */
    private String formatTime(long nanoSeconds) {
        double seconds = nanoSeconds / 1_000_000_000.0;
        
        if (seconds < 1.0) {
            return String.format("%.3f 秒 (%.0f ミリ秒)", seconds, nanoSeconds / 1_000_000.0);
        } else if (seconds < 60.0) {
            return String.format("%.3f 秒", seconds);
        } else {
            long minutes = (long) (seconds / 60);
            double remainingSeconds = seconds % 60;
            return String.format("%d 分 %.3f 秒", minutes, remainingSeconds);
        }
    }
    
    /**
     * 定量化指標を出力（メインコードとテストコード分離）
     */
    public void printMetrics() {
        System.out.println("\n========== 定量化指標 ==========");
        System.out.println("修正完了までの反復回数: " + iterationCount);
        
        // 全体のサマリー
        System.out.println("\n---------- 全体サマリー ----------");
        System.out.println("全ファイル数: " + (totalMainFiles + totalTestFiles));
        System.out.println("  - メインコード: " + totalMainFiles);
        System.out.println("  - テストコード: " + totalTestFiles);
        System.out.println("全体の行数: " + (totalMainLines + totalTestLines));
        System.out.println("  - メインコード: " + totalMainLines);
        System.out.println("  - テストコード: " + totalTestLines);
        System.out.println("修正されたファイル数: " + (modifiedMainFiles.size() + modifiedTestFiles.size()));
        System.out.println("  - メインコード: " + modifiedMainFiles.size());
        System.out.println("  - テストコード: " + modifiedTestFiles.size());
        
        // メインコードの詳細メトリクス
        System.out.println("\n---------- メインコード詳細 ----------");
        printDetailedMetrics("メインコード", totalMainFiles, totalMainLines, 
                           modifiedMainFiles.size(), deletedMainLines, 
                           deletedMainElements, deletedMainElementsByType);
        
        // テストコードの詳細メトリクス
        System.out.println("\n---------- テストコード詳細 ----------");
        printDetailedMetrics("テストコード", totalTestFiles, totalTestLines, 
                           modifiedTestFiles.size(), deletedTestLines, 
                           deletedTestElements, deletedTestElementsByType);
        
        // テスト実行結果の出力
        System.out.println("\n---------- テスト実行結果 ----------");
        System.out.println("総テスト数: " + totalTests);
        System.out.println("成功テスト数: " + passedTests);
        System.out.println("失敗テスト数: " + failedTests);
        System.out.println("  - [LIB-REMOVED]による失敗: " + libRemovedTestMethods.size());
        System.out.println("  - 通常の失敗: " + failedTestMethods.size());
        System.out.println("エラーテスト数: " + errorTests);
        System.out.println("スキップテスト数: " + skippedTests);
        System.out.println("テスト通過率: " + String.format("%.1f", getTestPassRate()) + "%");
        
        // 失敗したテストメソッド名の表示
        if (!libRemovedTestMethods.isEmpty()) {
            System.out.println("\n[LIB-REMOVED] ライブラリ削除により失敗したテストメソッド一覧 (" + libRemovedTestMethods.size() + "件):");
            for (String failedTest : libRemovedTestMethods) {
                System.out.println("  - " + failedTest);
            }
        }
        
        if (!failedTestMethods.isEmpty()) {
            System.out.println("\n通常の失敗したテストメソッド一覧 (" + failedTestMethods.size() + "件):");
            for (String failedTest : failedTestMethods) {
                System.out.println("  - " + failedTest);
            }
        }
        
        if (!errorTestMethods.isEmpty()) {
            System.out.println("\nエラーが発生したテストメソッド一覧 (" + errorTestMethods.size() + "件):");
            for (String errorTest : errorTestMethods) {
                System.out.println("  - " + errorTest);
            }
        }
        
        if (libRemovedTestMethods.isEmpty() && failedTestMethods.isEmpty() && 
            errorTestMethods.isEmpty() && totalTests > 0) {
            System.out.println("\n全てのテストが成功しました!");
        }
        
        System.out.println("\n===============================");
    }
    
    /**
     * 詳細メトリクスの出力（共通処理）
     */
    private void printDetailedMetrics(String codeType, int totalFiles, int totalLines, 
                                    int modifiedFilesCount, int deletedLines, 
                                    int deletedElements, Map<String, Integer> deletedElementsByType) {
        
        if (totalFiles > 0) {
            double fileModificationRate = (double) modifiedFilesCount / totalFiles * 100;
            System.out.println(codeType + "ファイル修正率: " + String.format("%.1f", fileModificationRate) + "%");
        } else {
            System.out.println(codeType + "ファイル修正率: N/A (対象ファイルなし)");
        }
        
        System.out.println(codeType + "削除された行数: " + deletedLines);
        if (totalLines > 0) {
            double lineModificationRate = (double) deletedLines / totalLines * 100;
            System.out.println(codeType + "行削除率: " + String.format("%.1f", lineModificationRate) + "%");
        } else {
            System.out.println(codeType + "行削除率: N/A (対象行なし)");
        }
        
        System.out.println(codeType + "削除された要素数: " + deletedElements);
        if (!deletedElementsByType.isEmpty()) {
            System.out.println(codeType + "削除された要素の種類別集計:");
            deletedElementsByType.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(entry -> System.out.println("  " + entry.getKey() + ": " + entry.getValue()));
        } else {
            System.out.println(codeType + "削除された要素: なし");
        }
    }
    
    // Getters and Setters
    public int getIterationCount() { return iterationCount; }
    public void setIterationCount(int iterationCount) { this.iterationCount = iterationCount; }
    
    // メインコード関連
    public int getTotalMainFiles() { return totalMainFiles; }
    public void setTotalMainFiles(int totalMainFiles) { this.totalMainFiles = totalMainFiles; }
    
    public int getTotalMainLines() { return totalMainLines; }
    public void setTotalMainLines(int totalMainLines) { this.totalMainLines = totalMainLines; }
    
    public Set<String> getModifiedMainFiles() { return modifiedMainFiles; }
    public int getDeletedMainLines() { return deletedMainLines; }
    
    // テストコード関連
    public int getTotalTestFiles() { return totalTestFiles; }
    public void setTotalTestFiles(int totalTestFiles) { this.totalTestFiles = totalTestFiles; }
    
    public int getTotalTestLines() { return totalTestLines; }
    public void setTotalTestLines(int totalTestLines) { this.totalTestLines = totalTestLines; }
    
    public Set<String> getModifiedTestFiles() { return modifiedTestFiles; }
    public int getDeletedTestLines() { return deletedTestLines; }
    
    // 全体メトリクス（後方互換性のため残す）
    public int getTotalFiles() { return totalMainFiles + totalTestFiles; }
    public void setTotalFiles(int totalFiles) {
        this.totalMainFiles = totalFiles;
    }
    
    public int getTotalLines() { return totalMainLines + totalTestLines; }
    public void setTotalLines(int totalLines) {
        this.totalMainLines = totalLines;
    }
    
    public Set<String> getModifiedFiles() {
        Set<String> allModified = new HashSet<>();
        allModified.addAll(modifiedMainFiles);
        allModified.addAll(modifiedTestFiles);
        return allModified;
    }
    
    public void addModifiedFile(String fileName) {
        modifiedMainFiles.add(fileName);
    }
    
    public int getDeletedLines() { return deletedMainLines + deletedTestLines; }
    public void addDeletedLines(int lines) {
        deletedMainLines += lines;
    }
    
    // テスト実行結果関連のgetters
    public int getTotalTests() { return totalTests; }
    public int getPassedTests() { return passedTests; }
    public int getFailedTests() { return failedTests; }
    public int getErrorTests() { return errorTests; }
    public int getSkippedTests() { return skippedTests; }
    public List<String> getFailedTestMethods() { return new ArrayList<>(failedTestMethods); }
    public List<String> getErrorTestMethods() { return new ArrayList<>(errorTestMethods); }
    public List<String> getLibRemovedTestMethods() { return new ArrayList<>(libRemovedTestMethods); }
    
    // 実行時間関連のgetters/setters
    public long getMainCodeDeletionTime() { return mainCodeDeletionTime; }
    public void setMainCodeDeletionTime(long mainCodeDeletionTime) { 
        this.mainCodeDeletionTime = mainCodeDeletionTime; 
    }
    public String getMainCodeDeletionTimeFormatted() { 
        return formatTime(mainCodeDeletionTime); 
    }
    
    public long getTestCodeDeletionTime() { return testCodeDeletionTime; }
    public void setTestCodeDeletionTime(long testCodeDeletionTime) { 
        this.testCodeDeletionTime = testCodeDeletionTime; 
    }
    public String getTestCodeDeletionTimeFormatted() { 
        return formatTime(testCodeDeletionTime); 
    }
    
    public long getTotalDeletionTime() { return totalDeletionTime; }
    public void setTotalDeletionTime(long totalDeletionTime) { 
        this.totalDeletionTime = totalDeletionTime; 
    }
    public String getTotalDeletionTimeFormatted() { 
        return formatTime(totalDeletionTime); 
    }
    
    public long getTestExecutionTime() { return testExecutionTime; }
    public void setTestExecutionTime(long testExecutionTime) { 
        this.testExecutionTime = testExecutionTime; 
    }
    public String getTestExecutionTimeFormatted() { 
        return formatTime(testExecutionTime); 
    }
    
    public long getTotalExecutionTime() { return totalExecutionTime; }
    public void setTotalExecutionTime(long totalExecutionTime) { 
        this.totalExecutionTime = totalExecutionTime; 
    }
    public String getTotalExecutionTimeFormatted() { 
        return formatTime(totalExecutionTime); 
    }
}