package com.iwata.MavenCompiler;

import com.iwata.MavenCompiler.MavenCompiler;
import com.iwata.MavenCompiler.CleanerConfig;
import com.iwata.MavenCompiler.CompilationMetrics;
import com.iwata.MavenCompiler.ErrorInfo;
import com.iwata.MavenCompiler.ErrorFileProcessor;
import com.iwata.MavenCompiler.FileUtility;
import java.io.File;
import java.util.Map;
import java.util.HashMap;

/**
  Mavenプロジェクトのコンパイルエラーを自動修正するメインクラス
  修正順序: メインコード完了後 → テストコード
 **/
public class MavenCompilationCleaner {
    
    private final MavenCompiler compiler;
    private final ErrorFileProcessor processor;
    private final CompilationMetrics metrics;
    
    // 実行時間計測用
    private long mainCodeDeletionTime = 0;
    private long testCodeDeletionTime = 0;
    private long totalDeletionTime = 0;
    private long testExecutionTime = 0;
    private long totalExecutionTime = 0;
    
    public MavenCompilationCleaner() {
        this.compiler = new MavenCompiler();
        this.processor = new ErrorFileProcessor();
        this.metrics = new CompilationMetrics();
    }
    
    public static void main(String[] args) throws Exception {
        MavenCompilationCleaner cleaner = new MavenCompilationCleaner();
        cleaner.run();
    }
    
    public void run() throws Exception {
        long totalStartTime = System.nanoTime();
        
        // 初期メトリクスの設定（メインコードとテストコードを分離）
        int mainFileCount = FileUtility.countJavaFilesInDirectory(CleanerConfig.SRC_DIR);
        int testFileCount = FileUtility.countJavaFilesInDirectory(CleanerConfig.TEST_DIR);
        int mainLineCount = FileUtility.countJavaLinesInDirectory(CleanerConfig.SRC_DIR);
        int testLineCount = FileUtility.countJavaLinesInDirectory(CleanerConfig.TEST_DIR);
        
        metrics.setTotalMainFiles(mainFileCount);
        metrics.setTotalTestFiles(testFileCount);
        metrics.setTotalMainLines(mainLineCount);
        metrics.setTotalTestLines(testLineCount);
        
        System.out.println("プロジェクト内の全Javaファイル数: " + (mainFileCount + testFileCount));
        System.out.println("  - srcディレクトリ: " + mainFileCount);
        System.out.println("  - testsディレクトリ: " + testFileCount);
        System.out.println("プロジェクト内の全行数: " + (mainLineCount + testLineCount));
        System.out.println("  - srcディレクトリ: " + mainLineCount);
        System.out.println("  - testsディレクトリ: " + testLineCount);
        
        long deletionStartTime = System.nanoTime();
        
        // フェーズ1: メインコードの修正
        System.out.println("\n========================================");
        System.out.println("フェーズ1: メインコードの修正を開始");
        System.out.println("========================================");
        long mainCodeStartTime = System.nanoTime();
        boolean mainCodeSuccess = processMainCode();
        long mainCodeEndTime = System.nanoTime();
        mainCodeDeletionTime = mainCodeEndTime - mainCodeStartTime;
        
        // フェーズ2: テストコードの修正
        System.out.println("\n========================================");
        System.out.println("フェーズ2: テストコードの修正を開始");
        System.out.println("========================================");
        long testCodeStartTime = System.nanoTime();
        boolean testCodeSuccess = processTestCode();
        long testCodeEndTime = System.nanoTime();
        testCodeDeletionTime = testCodeEndTime - testCodeStartTime;
        
        long deletionEndTime = System.nanoTime();
        totalDeletionTime = deletionEndTime - deletionStartTime;
        
        boolean compilationSuccess = mainCodeSuccess && testCodeSuccess;
        
        if (compilationSuccess) {
            System.out.println("\n最終的にコンパイル成功");
            
            // テスト実行
            System.out.println("\n===== テスト実行 =====");
            long testExecStartTime = System.nanoTime();
            try {
                MavenCompiler.TestResult testResult = compiler.runMavenTest();
                metrics.setTestResult(testResult);
                
                System.out.println("テスト実行完了");
                System.out.println("総テスト数: " + testResult.getTotalTests());
                System.out.println("成功: " + testResult.getPassedTests());
                System.out.println("失敗: " + testResult.getFailedTests());
                System.out.println("スキップ: " + testResult.getSkippedTests());
                System.out.println("通過率: " + String.format("%.1f", testResult.getPassRate()) + "%");
                
                // 失敗したテストメソッド名の表示
                if (!testResult.getLibRemovedTestMethods().isEmpty()) {
                    System.out.println("\n[LIB-REMOVED] ライブラリ削除により失敗したテストメソッド:");
                    for (String failedTest : testResult.getLibRemovedTestMethods()) {
                        System.out.println("  - " + failedTest);
                    }
                }
                
                if (!testResult.getFailedTestMethods().isEmpty()) {
                    System.out.println("\n通常の失敗したテストメソッド:");
                    for (String failedTest : testResult.getFailedTestMethods()) {
                        System.out.println("  - " + failedTest);
                    }
                }
            } catch (Exception e) {
                System.out.println("テスト実行中にエラーが発生しました: " + e.getMessage());
                // テストが実行できない場合でも処理を続行
            }
            long testExecEndTime = System.nanoTime();
            testExecutionTime = testExecEndTime - testExecStartTime;
        } else {
            System.out.println("最大回数に到達。未解決のエラーがあります。");
        }
        
        long totalEndTime = System.nanoTime();
        totalExecutionTime = totalEndTime - totalStartTime;
        
        // 定量化指標を出力（メインコードとテストコード分離）
        metrics.printMetrics();
        
        // 実行時間を出力
        printExecutionTimes();
        
        // Excelレポートを生成
        System.out.println("\n===== Excelレポート生成 =====");
        ExcelReportGenerator.generateReport(metrics);
    }
    
    /**
     * 実行時間の計測結果を出力
     */
    private void printExecutionTimes() {
        System.out.println("\n========== 実行時間計測結果 ==========");
        System.out.println("1. メインコード削除の実行時間: " + formatTime(mainCodeDeletionTime));
        System.out.println("2. テストコード削除の実行時間: " + formatTime(testCodeDeletionTime));
        System.out.println("3. メインコード、テストコード削除の実行時間: " + formatTime(totalDeletionTime));
        System.out.println("4. テスト実行時間: " + formatTime(testExecutionTime));
        System.out.println("5. 全体の実行時間: " + formatTime(totalExecutionTime));
        System.out.println("=====================================");
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
     * フェーズ1: メインコード（srcディレクトリ）の修正処理
     */
    private boolean processMainCode() throws Exception {
        int iteration = 1;
        boolean mainCodeSuccess = false;
        
        while (iteration <= CleanerConfig.MAX_ITERATIONS) {
            System.out.println("\n===== メインコード修正 - ループ " + iteration + " 回目 =====");
            metrics.setIterationCount(iteration);

            // メインコードのみコンパイル実行
            Map<String, ErrorInfo> mainErrorFiles = compiler.runMavenCompileAndExtractErrors();
            
            if (mainErrorFiles.isEmpty()) {
                System.out.println("メインコードのコンパイル成功");
                mainCodeSuccess = true;
                break;
            }

            System.out.println("メインコードのエラーファイル数: " + mainErrorFiles.size());
            
            for (ErrorInfo errorInfo : mainErrorFiles.values()) {
                System.out.println("  [MAIN] " + errorInfo.getFileName() + 
                                 " (エラー行: " + errorInfo.getErrorLines() + ")");
            }

            boolean anyModified = false;

            // 各エラーファイルに対して修正処理を実行
            for (ErrorInfo errorInfo : mainErrorFiles.values()) {
                File file = new File(errorInfo.getFilePath());
                if (!file.exists()) {
                    System.out.println("ファイルが見つかりません: " + file.getAbsolutePath());
                    continue;
                }

                boolean modified = processor.processErrorFile(file, errorInfo, metrics);
                if (modified) {
                    anyModified = true;
                    metrics.addModifiedFile(errorInfo.getFileName(), errorInfo.getFilePath());
                    System.out.println("修正完了: [MAIN] " + errorInfo.getFileName());
                } else {
                    System.out.println("修正すべきノードが見つかりません: [MAIN] " + errorInfo.getFileName());
                }
            }

            if (!anyModified) {
                System.out.println("メインコードで修正されたファイルがありませんでした。");
                break;
            }

            iteration++;
        }
        
        if (!mainCodeSuccess) {
            System.out.println("警告: メインコードの修正が完了していません（最大反復回数に到達）");
        }
        
        return mainCodeSuccess;
    }
    
    /**
     * フェーズ2: テストコード（testsディレクトリ）の修正処理
     */
    private boolean processTestCode() throws Exception {
        int iteration = 1;
        boolean testCodeSuccess = false;
        
        while (iteration <= CleanerConfig.MAX_ITERATIONS) {
            System.out.println("\n===== テストコード修正 - ループ " + iteration + " 回目 =====");

            // テストコードのみコンパイル実行
            Map<String, ErrorInfo> testErrorFiles = compiler.runMavenTestCompileAndExtractErrors();
            
            if (testErrorFiles.isEmpty()) {
                System.out.println("テストコードのコンパイル成功");
                testCodeSuccess = true;
                break;
            }

            System.out.println("テストコードのエラーファイル数: " + testErrorFiles.size());
            
            for (ErrorInfo errorInfo : testErrorFiles.values()) {
                System.out.println("  [TEST] " + errorInfo.getFileName() + 
                                 " (エラー行: " + errorInfo.getErrorLines() + ")");
            }

            boolean anyModified = false;

            // 各エラーファイルに対して修正処理を実行
            for (ErrorInfo errorInfo : testErrorFiles.values()) {
                File file = new File(errorInfo.getFilePath());
                if (!file.exists()) {
                    System.out.println("ファイルが見つかりません: " + file.getAbsolutePath());
                    continue;
                }

                boolean modified = processor.processErrorFile(file, errorInfo, metrics);
                if (modified) {
                    anyModified = true;
                    metrics.addModifiedFile(errorInfo.getFileName(), errorInfo.getFilePath());
                    System.out.println("修正完了: [TEST] " + errorInfo.getFileName());
                } else {
                    System.out.println("修正すべきノードが見つかりません: [TEST] " + errorInfo.getFileName());
                }
            }

            if (!anyModified) {
                System.out.println("テストコードで修正されたファイルがありませんでした。");
                break;
            }

            iteration++;
        }
        
        if (!testCodeSuccess) {
            System.out.println("警告: テストコードの修正が完了していません（最大反復回数に到達）");
        }
        
        return testCodeSuccess;
    }
}