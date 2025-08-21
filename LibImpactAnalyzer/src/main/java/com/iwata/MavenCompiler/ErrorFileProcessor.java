// ========== ErrorFileProcessor.java ==========
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
  エラーファイルの修正処理
 **/
public class ErrorFileProcessor {
    public boolean processErrorFile(File file, ErrorInfo errorInfo, CompilationMetrics metrics) {
        try {
            System.out.println("\n--- " + errorInfo.getFileName() + " の修正処理開始 ---");

            String originalContent = Files.readString(file.toPath(), StandardCharsets.UTF_8);

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

            // エラー行の要素を削除
            for (int lineNum : errorInfo.getErrorLines()) {
                List<CtElement> targetNodes = model.getElements(e -> {
                    SourcePosition pos = e.getPosition();
                    return pos != null && pos.isValidPosition() && pos.getLine() == lineNum;
                });

                for (CtElement element : targetNodes) {
                    String elementType = element.getClass().getSimpleName();
                    System.out.println("削除対象要素: " + elementType + " - " + element);
                    element.delete();
                    metrics.incrementDeletedElements(elementType);
                    modified = true;
                }
                
                // エラー行で要素が見つからない場合の処理
                if (targetNodes.isEmpty()) {
                    modified = handleMissingElement(launcher, model, lineNum, metrics) || modified;
                }
            }

            // メソッドの本体が空または不完全になった場合の処理
            modified = fixIncompleteMethods(launcher, model, metrics) || modified;

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
    
    private boolean handleMissingElement(Launcher launcher, CtModel model, 
                                        int lineNum, CompilationMetrics metrics) {
        System.out.println("エラー行 " + lineNum + " で要素が見つからないため、メソッドレベルで対応");
        
        for (CtMethod<?> method : model.getElements(e -> e instanceof CtMethod<?>).stream()
                .map(e -> (CtMethod<?>) e).toList()) {
            
            SourcePosition methodPos = method.getPosition();
            if (methodPos != null && methodPos.isValidPosition()) {
                int startLine = methodPos.getLine();
                int endLine = methodPos.getEndLine();
                //エラー行がメソッド内にあるかチェック
                if (lineNum >= startLine && lineNum <= endLine) {
                	//メソッドが戻り値を持つかチェック
                    CtTypeReference<?> returnType = method.getType();
                    if (returnType != null && !returnType.getSimpleName().equals("void")) {
                        CtBlock<?> body = method.getBody();
                        if (body != null) {
                        	//デフォルトのreturn文を追加
                            CtReturn<Object> returnStmt = launcher.getFactory().Core().createReturn();
                            CtExpression<Object> defaultValue = createDefaultValue(launcher, returnType);
                            returnStmt.setReturnedExpression(defaultValue);
                            body.addStatement(returnStmt);
                            System.out.println("エラー行を含むメソッドにreturn文を追加: " + 
                                             method.getSignature());
                            metrics.incrementDeletedElements("CtReturn (added)");
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
    
    private boolean fixIncompleteMethods(Launcher launcher, CtModel model, 
                                        CompilationMetrics metrics) {
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
                    metrics.incrementDeletedElements("CtReturn (added)");
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
                        metrics.incrementDeletedElements("CtReturn (added)");
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
                    metrics.incrementDeletedElements("CtImport");
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
                metrics.addDeletedLines(deletedLinesCount);
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