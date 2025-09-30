package com.iwata.MavenCompiler;

import com.iwata.MavenCompiler.MavenCompiler;
import com.iwata.MavenCompiler.CleanerConfig;
import com.iwata.MavenCompiler.CompilationMetrics;
import com.iwata.MavenCompiler.ErrorInfo;
import com.iwata.MavenCompiler.ErrorFileProcessor;
import com.iwata.MavenCompiler.FileUtility;
import java.io.File;
import java.util.Map;

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
        // 初期メトリクスの設定
        metrics.setTotalFiles(FileUtility.countAllJavaFiles());
        metrics.setTotalLines(FileUtility.countAllJavaLines());
        System.out.println("プロジェクト内の全Javaファイル数: " + metrics.getTotalFiles());
        System.out.println("プロジェクト内の全行数: " + metrics.getTotalLines());
        
        int iteration = 1;
        boolean compilationSuccess = false;

        while (iteration <= CleanerConfig.MAX_ITERATIONS) {
            System.out.println("===== ループ " + iteration + " 回目 =====");
            metrics.setIterationCount(iteration);

            // Maven コンパイル実行
            Map<String, ErrorInfo> errorFiles = compiler.runMavenCompileAndExtractErrors();
            
            if (errorFiles.isEmpty()) {
                System.out.println("コンパイル成功");
                compilationSuccess = true;
                break;
            }

            System.out.println("エラーが見つかったファイル数: " + errorFiles.size());
            for (ErrorInfo errorInfo : errorFiles.values()) {
                System.out.println("  - " + errorInfo.getFileName() + 
                                 " (エラー行: " + errorInfo.getErrorLines() + ")");
            }

            boolean anyModified = false;

            // 各エラーファイルに対して修正処理を実行
            for (ErrorInfo errorInfo : errorFiles.values()) {
                File file = new File(errorInfo.getFilePath());
                if (!file.exists()) {
                    System.out.println("ファイルが見つかりません: " + file.getAbsolutePath());
                    continue;
                }

                boolean modified = processor.processErrorFile(file, errorInfo, metrics);
                if (modified) {
                    anyModified = true;
                    metrics.addModifiedFile(errorInfo.getFileName());
                    System.out.println("修正完了: " + errorInfo.getFileName());
                } else {
                    System.out.println("修正すべきノードが見つかりません: " + errorInfo.getFileName());
                }
            }

            if (!anyModified) {
                System.out.println("どのファイルも修正されませんでした。終了します。");
                break;
            }

            iteration++;
        }

        if (compilationSuccess) {
            System.out.println("最終的にコンパイル成功");
        } else {
            System.out.println("最大回数に到達。未解決のエラーがあります。");
        }
        
        // 定量化指標を出力
        metrics.printMetrics();
    }
}