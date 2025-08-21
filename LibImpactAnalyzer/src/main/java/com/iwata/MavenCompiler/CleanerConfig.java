package com.iwata.MavenCompiler;

/**
 * アプリケーション設定を管理するクラス
 */
public class CleanerConfig {
    public static final int MAX_ITERATIONS = 20;
    public static final String PROJECT_DIR = "C:\\Users\\cyber\\git\\NCDSearch";
    public static final String SRC_DIR = PROJECT_DIR + "\\src";
    
    /**
     * OSに応じたMavenコマンドを取得
     */
    public static String getMavenCmd() {
        return System.getProperty("os.name").toLowerCase().contains("win") ? "mvn.cmd" : "mvn";
    }
}