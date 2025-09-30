package com.iwata.MavenCompiler;

import com.iwata.MavenCompiler.CompilationMetrics;
import com.iwata.MavenCompiler.ErrorInfo;
import com.iwata.MavenCompiler.FileUtility;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.cu.CompilationUnit;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
  エラーファイルの修正処理（メインコードとテストコード分離対応）
 **/
public class ErrorFileProcessor {
    public boolean processErrorFile(File file, ErrorInfo errorInfo, CompilationMetrics metrics) {
        try {
            System.out.println("\n--- " + errorInfo.getFileName() + " の修正処理開始 ---");

            String originalContent = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            boolean isTestFile = errorInfo.getFilePath().contains("\\tests\\");

            Launcher launcher = new Launcher();
            launcher.getEnvironment().setNoClasspath(true);
            launcher.getEnvironment().setAutoImports(true);
            launcher.getEnvironment().setPrettyPrinterCreator(
                () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
            );
            launcher.addInputResource(file.getAbsolutePath());
            launcher.buildModel();
            CtModel model = launcher.getModel();

            boolean modified = false;

            if (isTestFile) {
                // テストコードの場合: エラー行を含むテストメソッドを特定し、本体を削除してAssert.failを挿入
                modified = handleTestFileErrors(launcher, model, errorInfo, metrics);
            } else {
                // メインコードの場合: 従来通りエラー行の要素を削除
                for (int lineNum : errorInfo.getErrorLines()) {
                    List<CtElement> targetNodes = model.getElements(e -> {
                        SourcePosition pos = e.getPosition();
                        return pos != null && pos.isValidPosition() && pos.getLine() == lineNum;
                    });

                    for (CtElement element : targetNodes) {
                        String elementType = element.getClass().getSimpleName();
                        System.out.println("削除対象要素: " + elementType + " - " + element);
                        element.delete();
                        metrics.incrementDeletedElements(elementType, errorInfo.getFilePath());
                        modified = true;
                    }
                    
                    // エラー行で要素が見つからない場合の処理
                    if (targetNodes.isEmpty()) {
                        modified = handleMissingElement(launcher, model, lineNum, metrics, errorInfo.getFilePath()) || modified;
                    }
                }

                // メソッドの本体が空または不完全になった場合の処理
                modified = fixIncompleteMethods(launcher, model, metrics, errorInfo.getFilePath()) || modified;
            }

            // import文の処理とファイル書き込み
            modified = processImportsAndSave(launcher, file, errorInfo, metrics, 
                                            originalContent, modified) || modified;

            return modified;

        } catch (Exception e) {
            System.out.println("ファイル処理中にエラーが発生しました: " + 
                             errorInfo.getFileName() + " - " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * テストファイルのエラー処理: エラー行を含むメソッドの本体を削除してAssert.failを挿入
     * testsディレクトリ内のファイルは全てテストコードとして扱う
     */
    private boolean handleTestFileErrors(Launcher launcher, CtModel model, 
                                        ErrorInfo errorInfo, CompilationMetrics metrics) {
        boolean modified = false;
        Set<CtMethod<?>> processedMethods = new HashSet<>();
        
        for (int lineNum : errorInfo.getErrorLines()) {
            // エラー行を含むメソッドを探す
            for (CtMethod<?> method : model.getElements(e -> e instanceof CtMethod<?>).stream()
                    .map(e -> (CtMethod<?>) e).toList()) {
                
                // すでに処理済みのメソッドはスキップ
                if (processedMethods.contains(method)) {
                    continue;
                }
                
                SourcePosition methodPos = method.getPosition();
                if (methodPos != null && methodPos.isValidPosition()) {
                    int startLine = methodPos.getLine();
                    int endLine = methodPos.getEndLine();
                    
                    // エラー行がメソッド内にあるかチェック
                    if (lineNum >= startLine && lineNum <= endLine) {
                        System.out.println("テストメソッド内エラー検出: " + method.getSimpleName() + 
                                         " (行 " + startLine + "-" + endLine + ")");
                        
                        // メソッド本体を削除
                        CtBlock<?> body = method.getBody();
                        if (body != null) {
                            body.getStatements().clear();
                            System.out.println("メソッド本体を削除しました: " + method.getSimpleName());
                        } else {
                            body = launcher.getFactory().Core().createBlock();
                            method.setBody(body);
                        }
                        
                        // Assert.fail文を文字列から直接パース
                        try {
                            CtStatement failStatement = launcher.getFactory().Code()
                                .createCodeSnippetStatement(
                                    "org.junit.Assert.fail(\"[LIB-REMOVED] このテストは削除対象ライブラリ依存のため失敗扱い\")"
                                );
                            body.addStatement(failStatement);
                            System.out.println("Assert.fail文を挿入しました: " + method.getSimpleName());
                        } catch (Exception e) {
                            System.out.println("Assert.fail文の挿入に失敗しました: " + e.getMessage());
                            // フォールバック: 単純なthrow文を挿入
                            CtStatement throwStatement = launcher.getFactory().Code()
                                .createCodeSnippetStatement(
                                    "throw new RuntimeException(\"[LIB-REMOVED] このテストは削除対象ライブラリ依存のため失敗扱い\")"
                                );
                            body.addStatement(throwStatement);
                            System.out.println("代わりにRuntimeExceptionをスローする文を挿入しました: " + method.getSimpleName());
                        }
                        
                        metrics.incrementDeletedElements("TestMethodBody", errorInfo.getFilePath());
                        processedMethods.add(method);
                        modified = true;
                    }
                }
            }
        }
        
        return modified;
    }
    
    private boolean handleMissingElement(Launcher launcher, CtModel model, 
                                        int lineNum, CompilationMetrics metrics, String filePath) {
        System.out.println("エラー行 " + lineNum + " で要素が見つからないため、メソッドレベルで対応");
        
        for (CtMethod<?> method : model.getElements(e -> e instanceof CtMethod<?>).stream()
                .map(e -> (CtMethod<?>) e).toList()) {
            
            SourcePosition methodPos = method.getPosition();
            if (methodPos != null && methodPos.isValidPosition()) {
                int startLine = methodPos.getLine();
                int endLine = methodPos.getEndLine();
                // エラー行がメソッド内にあるかチェック
                if (lineNum >= startLine && lineNum <= endLine) {
                    // メソッドが戻り値を持つかチェック
                    CtTypeReference<?> returnType = method.getType();
                    if (returnType != null && !returnType.getSimpleName().equals("void")) {
                        CtBlock<?> body = method.getBody();
                        if (body != null) {
                            // デフォルトのreturn文を追加
                            CtReturn<Object> returnStmt = launcher.getFactory().Core().createReturn();
                            CtExpression<Object> defaultValue = createDefaultValue(launcher, returnType);
                            returnStmt.setReturnedExpression(defaultValue);
                            body.addStatement(returnStmt);
                            System.out.println("エラー行を含むメソッドにreturn文を追加: " + 
                                             method.getSignature());
                            metrics.incrementDeletedElements("CtReturn (added)", filePath);
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
    
    private boolean fixIncompleteMethods(Launcher launcher, CtModel model, 
                                        CompilationMetrics metrics, String filePath) {
        boolean modified = false;
        
        for (CtMethod<?> method : model.getElements(e -> e instanceof CtMethod<?>).stream()
                .map(e -> (CtMethod<?>) e).toList()) {
            
            CtBlock<?> body = method.getBody();
            CtTypeReference<?> returnType = method.getType();
            
            if (returnType != null && !returnType.getSimpleName().equals("void")) {
                if (body == null || body.getStatements().isEmpty()) {
                    // 空のメソッドの処理
                    CtReturn<Object> returnStmt = launcher.getFactory().Core().createReturn();
                    CtExpression<Object> defaultValue = createDefaultValue(launcher, returnType);
                    returnStmt.setReturnedExpression(defaultValue);
                    
                    if (body == null) {
                        body = launcher.getFactory().Core().createBlock();
                        method.setBody(body);
                    }
                    body.addStatement(returnStmt);
                    System.out.println("空のメソッドにデフォルトreturn文を追加: " + method.getSignature());
                    metrics.incrementDeletedElements("CtReturn (added)", filePath);
                    modified = true;
                } else {
                    // return文の確認
                    boolean hasReturn = hasReturnStatement(body);
                    boolean hasUnreachableCode = hasUnreachableCodeAfterReturn(body);
                    
                    if (!hasReturn || hasUnreachableCode) {
                        CtReturn<Object> returnStmt = launcher.getFactory().Core().createReturn();
                        CtExpression<Object> defaultValue = createDefaultValue(launcher, returnType);
                        returnStmt.setReturnedExpression(defaultValue);
                        body.addStatement(returnStmt);
                        System.out.println("return文が不足しているメソッドにデフォルトreturn文を追加: " + 
                                         method.getSignature());
                        metrics.incrementDeletedElements("CtReturn (added)", filePath);
                        modified = true;
                    }
                }
            }
        }
        
        return modified;
    }
    
    private boolean processImportsAndSave(Launcher launcher, File file, ErrorInfo errorInfo,
                                         CompilationMetrics metrics, String originalContent,
                                         boolean alreadyModified) throws IOException {
        boolean modified = alreadyModified;
        
        CompilationUnit targetUnit = launcher.getFactory().CompilationUnit().getMap().values().stream()
                .filter(cu -> cu.getFile() != null && 
                            cu.getFile().getName().equals(errorInfo.getFileName()))
                .findFirst()
                .orElse(null);

        if (targetUnit != null) {
            List<CtImport> importsToRemove = new ArrayList<>();
            for (CtImport ctImport : targetUnit.getImports()) {
                SourcePosition pos = ctImport.getPosition();
                if (pos != null && pos.isValidPosition() && 
                    errorInfo.getErrorLines().contains(pos.getLine())) {
                    System.out.println("削除対象import文: " + ctImport);
                    importsToRemove.add(ctImport);
                    metrics.incrementDeletedElements("CtImport", errorInfo.getFilePath());
                    modified = true;
                }
            }
            targetUnit.getImports().removeAll(importsToRemove);

            if (modified) {
                String result = targetUnit.prettyprint();
                try (PrintWriter writer = new PrintWriter(file, StandardCharsets.UTF_8)) {
                    writer.print(result);
                    System.out.println("修正後コードを元ファイルに上書き保存しました: " + 
                                     errorInfo.getFileName());
                }
                
                int deletedLinesCount = FileUtility.calculateDeletedLines(originalContent, result);
                // ファイルパス情報を渡してメイン/テストを判定
                metrics.addDeletedLines(deletedLinesCount, errorInfo.getFilePath());
                System.out.println("削除された行数: " + deletedLinesCount);
            }
        } else {
            System.out.println("指定ファイルの構文ユニットが見つかりませんでした: " + 
                             errorInfo.getFileName());
        }
        
        return modified;
    }
    
    private CtExpression<Object> createDefaultValue(Launcher launcher, CtTypeReference<?> returnType) {
        String typeStr = returnType.unbox().getSimpleName();
        return switch (typeStr) {
            case "boolean" -> launcher.getFactory().Code().createLiteral(false);
            case "char" -> launcher.getFactory().Code().createLiteral('\0');
            case "byte", "short", "int", "long", "float", "double" ->
                    launcher.getFactory().Code().createLiteral(0);
            default -> launcher.getFactory().Code().createLiteral(null);
        };
    }
    
    private boolean hasReturnStatement(CtBlock<?> block) {
        return block.getElements(e -> e instanceof CtReturn<?>).stream()
                .anyMatch(e -> true);
    }
    
    private boolean hasUnreachableCodeAfterReturn(CtBlock<?> block) {
        List<CtStatement> statements = block.getStatements();
        boolean foundReturn = false;
        
        for (CtStatement stmt : statements) {
            if (stmt instanceof CtReturn<?>) {
                foundReturn = true;
            } else if (foundReturn) {
                return true;
            }
        }
        return false;
    }
}