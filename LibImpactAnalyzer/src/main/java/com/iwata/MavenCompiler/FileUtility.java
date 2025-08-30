package com.iwata.MavenCompiler;

import com.iwata.MavenCompiler.CleanerConfig;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
  ファイル操作のユーティリティクラス
 **/
public class FileUtility {
    
    /**
      プロジェクト内の全Javaファイル数を取得（src + tests）
     **/
    public static int countAllJavaFiles() {
        return countJavaFilesInDirectory(CleanerConfig.SRC_DIR) + 
               countJavaFilesInDirectory(CleanerConfig.TEST_DIR);
    }
    
    /**
      指定ディレクトリ内のJavaファイル数を取得
     **/
    public static int countJavaFilesInDirectory(String directory) {
        try {
            Path dirPath = Paths.get(directory);
            if (!Files.exists(dirPath)) {
                System.out.println("ディレクトリが見つかりません: " + directory);
                return 0;
            }

            List<Path> javaFiles = Files.walk(dirPath)
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());

            return javaFiles.size();
        } catch (IOException e) {
            System.out.println("Javaファイル数の取得中にエラーが発生しました: " + e.getMessage());
            return 0;
        }
    }
    
    /**
      プロジェクト内の全Javaファイルの総行数を取得（src + tests）
     **/
    public static int countAllJavaLines() {
        return countJavaLinesInDirectory(CleanerConfig.SRC_DIR) + 
               countJavaLinesInDirectory(CleanerConfig.TEST_DIR);
    }
    
    /**
      指定ディレクトリ内のJavaファイルの総行数を取得
     **/
    public static int countJavaLinesInDirectory(String directory) {
        try {
            Path dirPath = Paths.get(directory);
            if (!Files.exists(dirPath)) {
                System.out.println("ディレクトリが見つかりません: " + directory);
                return 0;
            }

            List<Path> javaFiles = Files.walk(dirPath)
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());

            int totalLines = 0;
            for (Path javaFile : javaFiles) {
                try {
                    List<String> lines = Files.readAllLines(javaFile, StandardCharsets.UTF_8);
                    totalLines += lines.size();
                } catch (IOException e) {
                    System.out.println("ファイル読み取りエラー: " + javaFile + " - " + e.getMessage());
                }
            }

            return totalLines;
        } catch (IOException e) {
            System.out.println("Java行数の取得中にエラーが発生しました: " + e.getMessage());
            return 0;
        }
    }
    
    /**
      プロジェクト内でJavaファイルを検索（src + tests）
     **/
    public static String findJavaFile(String fileName) {
        // まずsrcディレクトリで検索
        String foundFile = findJavaFileInDirectory(fileName, CleanerConfig.SRC_DIR);
        if (foundFile != null) {
            return foundFile;
        }
        
        // 次にtestsディレクトリで検索
        return findJavaFileInDirectory(fileName, CleanerConfig.TEST_DIR);
    }
    
    /**
      指定ディレクトリ内でJavaファイルを検索
     **/
    public static String findJavaFileInDirectory(String fileName, String directory) {
        try {
            Path dirPath = Paths.get(directory);
            if (!Files.exists(dirPath)) {
                return null;
            }

            List<Path> foundFiles = Files.walk(dirPath)
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().equals(fileName))
                .collect(Collectors.toList());

            if (foundFiles.isEmpty()) {
                return null;
            }

            if (foundFiles.size() > 1) {
                System.out.println("同名ファイルが複数見つかりました: " + fileName);
                foundFiles.forEach(path -> System.out.println("  - " + path));
            }

            return foundFiles.get(0).toString();
        } catch (IOException e) {
            System.out.println("ファイル検索中にエラーが発生しました: " + e.getMessage());
            return null;
        }
    }
    
    /**
      修正前後のファイルの行数を比較して削除された行数を計算
     **/
    public static int calculateDeletedLines(String originalContent, String modifiedContent) {
        int originalLines = originalContent.split("\\r?\\n").length;
        int modifiedLines = modifiedContent.split("\\r?\\n").length;
        return Math.max(0, originalLines - modifiedLines);
    }
}