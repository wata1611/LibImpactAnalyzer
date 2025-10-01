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
 **/
public class MavenCompilationCleaner {
    
    private final MavenCompiler compiler;
    private final ErrorFileProcessor processor;
    private final CompilationMetrics metrics;
    
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
        
        int iteration = 1;
        boolean compilationSuccess = false;

        while (iteration <= CleanerConfig.MAX_ITERATIONS) {
            System.out.println("\n===== ループ " + iteration + " 回目 =====");
            metrics.setIterationCount(iteration);

            // Maven コンパイル実行（メインコードとテストコード）
            Map<String, ErrorInfo> mainErrorFiles = compiler.runMavenCompileAndExtractErrors();
            Map<String, ErrorInfo> testErrorFiles = compiler.runMavenTestCompileAndExtractErrors();
            
            // エラーファイルをマージ
            Map<String, ErrorInfo> allErrorFiles = new HashMap<>();
            allErrorFiles.putAll(mainErrorFiles);
            allErrorFiles.putAll(testErrorFiles);
            
            if (allErrorFiles.isEmpty()) {
                System.out.println("コンパイル成功");
                compilationSuccess = true;
                break;
            }

            System.out.println("エラーが見つかったファイル数: " + allErrorFiles.size());
            System.out.println("  - メインコードのエラー: " + mainErrorFiles.size() + "ファイル");
            System.out.println("  - テストコードのエラー: " + testErrorFiles.size() + "ファイル");
            
            for (ErrorInfo errorInfo : allErrorFiles.values()) {
                String fileType = errorInfo.getFilePath().contains("\\tests\\") ? "[TEST]" : "[MAIN]";
                System.out.println("  " + fileType + " " + errorInfo.getFileName() + 
                                 " (エラー行: " + errorInfo.getErrorLines() + ")");
            }

            boolean anyModified = false;

            // 各エラーファイルに対して修正処理を実行
            for (ErrorInfo errorInfo : allErrorFiles.values()) {
                File file = new File(errorInfo.getFilePath());
                if (!file.exists()) {
                    System.out.println("ファイルが見つかりません: " + file.getAbsolutePath());
                    continue;
                }

                boolean modified = processor.processErrorFile(file, errorInfo, metrics);
                if (modified) {
                    anyModified = true;
                    // ファイルパスも渡してメイン/テスト判定を可能にする
                    metrics.addModifiedFile(errorInfo.getFileName(), errorInfo.getFilePath());
                    String fileType = errorInfo.getFilePath().contains("\\tests\\") ? "[TEST]" : "[MAIN]";
                    System.out.println("修正完了: " + fileType + " " + errorInfo.getFileName());
                } else {
                    String fileType = errorInfo.getFilePath().contains("\\tests\\") ? "[TEST]" : "[MAIN]";
                    System.out.println("修正すべきノードが見つかりません: " + fileType + " " + errorInfo.getFileName());
                }
            }

            if (!anyModified) {
                System.out.println("どのファイルも修正されませんでした。終了します。");
                break;
            }

            iteration++;
        }

        if (compilationSuccess) {
            System.out.println("\n最終的にコンパイル成功");
            
            // テスト実行
            System.out.println("\n===== テスト実行 =====");
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
        } else {
            System.out.println("最大回数に到達。未解決のエラーがあります。");
        }
        
        // 定量化指標を出力（メインコードとテストコード分離）
        metrics.printMetrics();
        
        // Excelレポートを生成
        System.out.println("\n===== Excelレポート生成 =====");
        ExcelReportGenerator.generateReport(metrics);
    }
}