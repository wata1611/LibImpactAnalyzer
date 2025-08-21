package com.iwata.MavenCompiler;

import java.util.*;

/**
 * エラー情報を保持するクラス
 */
public class ErrorInfo {
    private String fileName;
    private String filePath;
    private Set<Integer> errorLines;
    
    public ErrorInfo(String fileName, String filePath) {
        this.fileName = fileName;
        this.filePath = filePath;
        this.errorLines = new HashSet<>();
    }
    
    // Getters and Setters
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    
    public Set<Integer> getErrorLines() { return errorLines; }
    public void addErrorLine(int lineNumber) { this.errorLines.add(lineNumber); }
}