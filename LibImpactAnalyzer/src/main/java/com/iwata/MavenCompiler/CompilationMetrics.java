package com.iwata.MavenCompiler;

import java.util.*;

/**
  定量化指標管理クラス
 **/
public class CompilationMetrics {
    private int iterationCount = 0;
    private Set<String> modifiedFiles = new HashSet<>();
    private int totalFiles = 0;
    private int totalLines = 0;
    private int deletedLines = 0;
    private int deletedElements = 0;
    private Map<String, Integer> deletedElementsByType = new HashMap<>();
    
    //要素削除の記録
    public void incrementDeletedElements(String elementType) {
        deletedElements++;
        deletedElementsByType.merge(elementType, 1, Integer::sum);
    }
    
    public void printMetrics() {
        System.out.println("\n========== 定量化指標 ==========");
        System.out.println("修正完了までの反復回数: " + iterationCount);
        System.out.println("全ファイル数: " + totalFiles);
        System.out.println("全体の行数: " + totalLines);
        System.out.println("修正されたファイル数: " + modifiedFiles.size());
        
        if (totalFiles > 0) {
            double fileModificationRate = (double) modifiedFiles.size() / totalFiles * 100;
            System.out.println("ファイル修正率: " + String.format("%.1f", fileModificationRate) + "%");
        }
        
        System.out.println("削除された行数: " + deletedLines);
        if (totalLines > 0) {
            double lineModificationRate = (double) deletedLines / totalLines * 100;
            System.out.println("行削除率: " + String.format("%.1f", lineModificationRate) + "%");
        }
        
        System.out.println("削除された要素数: " + deletedElements);
        System.out.println("削除された要素の種類別集計:");
        deletedElementsByType.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .forEach(entry -> System.out.println("  " + entry.getKey() + ": " + entry.getValue()));
        System.out.println("===============================");
    }
    

    public int getIterationCount() { return iterationCount; }
    public void setIterationCount(int iterationCount) { this.iterationCount = iterationCount; }
    
    public Set<String> getModifiedFiles() { return modifiedFiles; }
    public void addModifiedFile(String fileName) { this.modifiedFiles.add(fileName); }
    
    public int getTotalFiles() { return totalFiles; }
    public void setTotalFiles(int totalFiles) { this.totalFiles = totalFiles; }
    
    public int getTotalLines() { return totalLines; }
    public void setTotalLines(int totalLines) { this.totalLines = totalLines; }
    
    public int getDeletedLines() { return deletedLines; }
    public void addDeletedLines(int lines) { this.deletedLines += lines; }
}