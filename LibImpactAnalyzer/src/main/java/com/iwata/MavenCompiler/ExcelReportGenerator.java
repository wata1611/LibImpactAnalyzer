package com.iwata.MavenCompiler;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.chart.*;
import org.apache.poi.xssf.usermodel.*;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Apache POIを使用してExcelレポートを生成するクラス
 */
public class ExcelReportGenerator {
    
    private static final String OUTPUT_FILE = "CompilationReport.xlsx";
    
    /**
     * 修正結果をExcelファイルとして出力
     */
    public static void generateReport(CompilationMetrics metrics) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            
            // シート1: ソースコードの影響範囲
            createSourceCodeImpactSheet(workbook, metrics);
            
            // シート2: テストの影響範囲
            createTestImpactSheet(workbook, metrics);
            
            // シート3: サマリー情報
            createSummarySheet(workbook, metrics);
            
            // ファイルに書き込み
            try (FileOutputStream fileOut = new FileOutputStream(OUTPUT_FILE)) {
                workbook.write(fileOut);
                System.out.println("\nExcelレポートを生成しました: " + OUTPUT_FILE);
            }
            
        } catch (IOException e) {
            System.err.println("Excelレポート生成中にエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * ソースコードの影響範囲シートを作成
     */
    private static void createSourceCodeImpactSheet(XSSFWorkbook workbook, CompilationMetrics metrics) {
        XSSFSheet sheet = workbook.createSheet("ソースコードの影響範囲");
        
        // データ作成
        int deletedLines = metrics.getDeletedMainLines();
        int remainingLines = metrics.getTotalMainLines() - deletedLines;
        
        // ヘッダー行
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("分類");
        headerRow.createCell(1).setCellValue("行数");
        headerRow.createCell(2).setCellValue("割合(%)");
        
        // データ行1: 削除された行
        Row row1 = sheet.createRow(1);
        row1.createCell(0).setCellValue("削除された行");
        row1.createCell(1).setCellValue(deletedLines);
        double deletedPercent = metrics.getTotalMainLines() > 0 
            ? (double) deletedLines / metrics.getTotalMainLines() * 100 : 0.0;
        row1.createCell(2).setCellValue(deletedPercent);
        
        // データ行2: 残存している行
        Row row2 = sheet.createRow(2);
        row2.createCell(0).setCellValue("残存している行");
        row2.createCell(1).setCellValue(remainingLines);
        double remainingPercent = metrics.getTotalMainLines() > 0 
            ? (double) remainingLines / metrics.getTotalMainLines() * 100 : 0.0;
        row2.createCell(2).setCellValue(remainingPercent);
        
        // 列幅調整
        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
        sheet.autoSizeColumn(2);
        
        // 円グラフ作成
        XSSFDrawing drawing = sheet.createDrawingPatriarch();
        XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, 0, 5, 10, 20);
        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText("メインコードの影響範囲");
        chart.setTitleOverlay(false);
        
        XDDFChartLegend legend = chart.getOrAddLegend();
        legend.setPosition(LegendPosition.RIGHT);
        
        // データソース設定
        XDDFDataSource<String> categories = XDDFDataSourcesFactory.fromStringCellRange(sheet,
                new CellRangeAddress(1, 2, 0, 0));
        XDDFNumericalDataSource<Double> values = XDDFDataSourcesFactory.fromNumericCellRange(sheet,
                new CellRangeAddress(1, 2, 1, 1));
        
        // 円グラフ作成
        XDDFChartData data = chart.createData(ChartTypes.PIE, null, null);
        XDDFChartData.Series series = data.addSeries(categories, values);
        series.setTitle("行数", null);
        chart.plot(data);
        
        // データラベルを設定（パーセンテージと値を表示）
        setDataLabels(chart, true, true);
    }
    
    /**
     * テストの影響範囲シートを作成
     */
    private static void createTestImpactSheet(XSSFWorkbook workbook, CompilationMetrics metrics) {
        XSSFSheet sheet = workbook.createSheet("テストの影響範囲");
        
        // データ作成
        int totalTests = metrics.getTotalTests();
        int passedTests = metrics.getPassedTests();
        int libRemovedTests = metrics.getLibRemovedTestMethods().size();
        int normalFailedTests = metrics.getFailedTestMethods().size();
        int errorTests = metrics.getErrorTests();
        
        // ヘッダー行
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("分類");
        headerRow.createCell(1).setCellValue("テスト数");
        headerRow.createCell(2).setCellValue("割合(%)");
        
        // データ行1: テストケースの総数
        Row row1 = sheet.createRow(1);
        row1.createCell(0).setCellValue("テストケースの総数");
        row1.createCell(1).setCellValue(totalTests);
        row1.createCell(2).setCellValue(100.0);
        
        // データ行2: 成功
        Row row2 = sheet.createRow(2);
        row2.createCell(0).setCellValue("成功したテスト");
        row2.createCell(1).setCellValue(passedTests);
        double passedPercent = totalTests > 0 
            ? (double) passedTests / totalTests * 100 : 0.0;
        row2.createCell(2).setCellValue(passedPercent);
        
        // データ行3: LIB-REMOVED失敗
        Row row3 = sheet.createRow(3);
        row3.createCell(0).setCellValue("LIB-REMOVED失敗");
        row3.createCell(1).setCellValue(libRemovedTests);
        double libRemovedPercent = totalTests > 0 
            ? (double) libRemovedTests / totalTests * 100 : 0.0;
        row3.createCell(2).setCellValue(libRemovedPercent);
        
        // データ行4: 通常の失敗
        Row row4 = sheet.createRow(4);
        row4.createCell(0).setCellValue("通常の失敗");
        row4.createCell(1).setCellValue(normalFailedTests);
        double normalFailedPercent = totalTests > 0 
            ? (double) normalFailedTests / totalTests * 100 : 0.0;
        row4.createCell(2).setCellValue(normalFailedPercent);
        
        // データ行5: エラー
        Row row5 = sheet.createRow(5);
        row5.createCell(0).setCellValue("エラー");
        row5.createCell(1).setCellValue(errorTests);
        double errorPercent = totalTests > 0 
            ? (double) errorTests / totalTests * 100 : 0.0;
        row5.createCell(2).setCellValue(errorPercent);
        
        // 列幅調整
        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
        sheet.autoSizeColumn(2);
        
        // 円グラフ作成（総数は除外して、成功・失敗・エラーのみ表示）
        XSSFDrawing drawing = sheet.createDrawingPatriarch();
        XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, 0, 8, 10, 23);
        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText("テストの影響範囲");
        chart.setTitleOverlay(false);
        
        XDDFChartLegend legend = chart.getOrAddLegend();
        legend.setPosition(LegendPosition.RIGHT);
        
        // データソース設定（行2-5: 成功、LIB-REMOVED失敗、通常の失敗、エラー）
        XDDFDataSource<String> categories = XDDFDataSourcesFactory.fromStringCellRange(sheet,
                new CellRangeAddress(2, 5, 0, 0));
        XDDFNumericalDataSource<Double> values = XDDFDataSourcesFactory.fromNumericCellRange(sheet,
                new CellRangeAddress(2, 5, 1, 1));
        
        // 円グラフ作成
        XDDFChartData data = chart.createData(ChartTypes.PIE, null, null);
        XDDFChartData.Series series = data.addSeries(categories, values);
        series.setTitle("テスト数", null);
        chart.plot(data);
        
        // データラベルを設定（パーセンテージと値を表示）
        setDataLabels(chart, true, true);
    }
    
    /**
     * サマリーシートを作成
     */
    private static void createSummarySheet(XSSFWorkbook workbook, CompilationMetrics metrics) {
        XSSFSheet sheet = workbook.createSheet("サマリー");
        
        // スタイル作成
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setFontHeightInPoints((short) 12);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        
        int rowNum = 0;
        
        // 基本情報
        Row titleRow = sheet.createRow(rowNum++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("修正サマリー");
        titleCell.setCellStyle(headerStyle);
        rowNum++;
        
        createDataRow(sheet, rowNum++, "反復回数", metrics.getIterationCount() + "回");
        rowNum++;
        
        // 実行時間情報
        Row timeHeader = sheet.createRow(rowNum++);
        Cell timeCell = timeHeader.createCell(0);
        timeCell.setCellValue("実行時間");
        timeCell.setCellStyle(headerStyle);
        
        createDataRow(sheet, rowNum++, "  メインコード削除", metrics.getMainCodeDeletionTimeFormatted());
        createDataRow(sheet, rowNum++, "  テストコード削除", metrics.getTestCodeDeletionTimeFormatted());
        createDataRow(sheet, rowNum++, "  全削除処理", metrics.getTotalDeletionTimeFormatted());
        createDataRow(sheet, rowNum++, "  テスト実行", metrics.getTestExecutionTimeFormatted());
        createDataRow(sheet, rowNum++, "  全体実行時間", metrics.getTotalExecutionTimeFormatted());
        rowNum++;
        
        // メインコード情報
        Row mainCodeHeader = sheet.createRow(rowNum++);
        Cell mainCodeCell = mainCodeHeader.createCell(0);
        mainCodeCell.setCellValue("メインコード");
        mainCodeCell.setCellStyle(headerStyle);
        
        createDataRow(sheet, rowNum++, "  総ファイル数", String.valueOf(metrics.getTotalMainFiles()));
        createDataRow(sheet, rowNum++, "  修正ファイル数", 
            String.format("%d (%.1f%%)", metrics.getModifiedMainFiles().size(),
                metrics.getTotalMainFiles() > 0 ? (double) metrics.getModifiedMainFiles().size() / metrics.getTotalMainFiles() * 100 : 0));
        createDataRow(sheet, rowNum++, "  総行数", String.valueOf(metrics.getTotalMainLines()));
        createDataRow(sheet, rowNum++, "  削除行数", 
            String.format("%d (%.1f%%)", metrics.getDeletedMainLines(),
                metrics.getTotalMainLines() > 0 ? (double) metrics.getDeletedMainLines() / metrics.getTotalMainLines() * 100 : 0));
        rowNum++;
        
        // テストコード情報
        Row testCodeHeader = sheet.createRow(rowNum++);
        Cell testCodeCell = testCodeHeader.createCell(0);
        testCodeCell.setCellValue("テストコード");
        testCodeCell.setCellStyle(headerStyle);
        
        createDataRow(sheet, rowNum++, "  総ファイル数", String.valueOf(metrics.getTotalTestFiles()));
        createDataRow(sheet, rowNum++, "  修正ファイル数", 
            String.format("%d (%.1f%%)", metrics.getModifiedTestFiles().size(),
                metrics.getTotalTestFiles() > 0 ? (double) metrics.getModifiedTestFiles().size() / metrics.getTotalTestFiles() * 100 : 0));
        createDataRow(sheet, rowNum++, "  総行数", String.valueOf(metrics.getTotalTestLines()));
        createDataRow(sheet, rowNum++, "  削除行数", 
            String.format("%d (%.1f%%)", metrics.getDeletedTestLines(),
                metrics.getTotalTestLines() > 0 ? (double) metrics.getDeletedTestLines() / metrics.getTotalTestLines() * 100 : 0));
        rowNum++;
        
        // テスト実行結果
        Row testResultHeader = sheet.createRow(rowNum++);
        Cell testResultCell = testResultHeader.createCell(0);
        testResultCell.setCellValue("テスト実行結果");
        testResultCell.setCellStyle(headerStyle);
        
        createDataRow(sheet, rowNum++, "  総テスト数", String.valueOf(metrics.getTotalTests()));
        createDataRow(sheet, rowNum++, "  成功テスト数", String.valueOf(metrics.getPassedTests()));
        createDataRow(sheet, rowNum++, "  LIB-REMOVED失敗", String.valueOf(metrics.getLibRemovedTestMethods().size()));
        createDataRow(sheet, rowNum++, "  通常の失敗", String.valueOf(metrics.getFailedTestMethods().size()));
        createDataRow(sheet, rowNum++, "  エラー", String.valueOf(metrics.getErrorTests()));
        createDataRow(sheet, rowNum++, "  テスト通過率", String.format("%.1f%%", metrics.getTestPassRate()));
        rowNum++;
        
        // 失敗したテストメソッド一覧
        if (!metrics.getLibRemovedTestMethods().isEmpty()) {
            Row libRemovedHeader = sheet.createRow(rowNum++);
            Cell libRemovedCell = libRemovedHeader.createCell(0);
            libRemovedCell.setCellValue("LIB-REMOVED失敗テスト一覧 (" + metrics.getLibRemovedTestMethods().size() + "件)");
            libRemovedCell.setCellStyle(headerStyle);
            
            for (String testMethod : metrics.getLibRemovedTestMethods()) {
                createDataRow(sheet, rowNum++, "  ", testMethod);
            }
            rowNum++;
        }
        
        if (!metrics.getFailedTestMethods().isEmpty()) {
            Row failedHeader = sheet.createRow(rowNum++);
            Cell failedCell = failedHeader.createCell(0);
            failedCell.setCellValue("通常失敗テスト一覧 (" + metrics.getFailedTestMethods().size() + "件)");
            failedCell.setCellStyle(headerStyle);
            
            for (String testMethod : metrics.getFailedTestMethods()) {
                createDataRow(sheet, rowNum++, "  ", testMethod);
            }
            rowNum++;
        }
        
        if (!metrics.getErrorTestMethods().isEmpty()) {
            Row errorHeader = sheet.createRow(rowNum++);
            Cell errorCell = errorHeader.createCell(0);
            errorCell.setCellValue("エラーが発生したテスト一覧 (" + metrics.getErrorTestMethods().size() + "件)");
            errorCell.setCellStyle(headerStyle);
            
            for (String testMethod : metrics.getErrorTestMethods()) {
                createDataRow(sheet, rowNum++, "  ", testMethod);
            }
        }
        
        // 列幅調整
        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }
    
    /**
     * データ行を作成するヘルパーメソッド
     */
    private static void createDataRow(Sheet sheet, int rowNum, String label, String value) {
        Row row = sheet.createRow(rowNum);
        row.createCell(0).setCellValue(label);
        row.createCell(1).setCellValue(value);
    }
    
    /**
     * 円グラフにデータラベル（パーセンテージと値）を設定
     */
    private static void setDataLabels(XSSFChart chart, boolean showPercent, boolean showValue) {
        try {
            // CTChartを取得してデータラベルを設定
            org.openxmlformats.schemas.drawingml.x2006.chart.CTChart ctChart = chart.getCTChart();
            org.openxmlformats.schemas.drawingml.x2006.chart.CTPlotArea plotArea = ctChart.getPlotArea();
            
            // 円グラフのシリーズを取得
            org.openxmlformats.schemas.drawingml.x2006.chart.CTPieChart pieChart = 
                plotArea.getPieChartArray(0);
            
            if (pieChart.getSerArray().length > 0) {
                org.openxmlformats.schemas.drawingml.x2006.chart.CTPieSer ser = 
                    pieChart.getSerArray(0);
                
                // データラベルの設定
                org.openxmlformats.schemas.drawingml.x2006.chart.CTDLbls dLbls = ser.isSetDLbls() 
                    ? ser.getDLbls() 
                    : ser.addNewDLbls();
                
                // パーセンテージを表示
                if (dLbls.isSetShowPercent()) {
                    dLbls.getShowPercent().setVal(showPercent);
                } else {
                    dLbls.addNewShowPercent().setVal(showPercent);
                }
                
                // 値を表示
                if (dLbls.isSetShowVal()) {
                    dLbls.getShowVal().setVal(showValue);
                } else {
                    dLbls.addNewShowVal().setVal(showValue);
                }
                
                // カテゴリ名は非表示
                if (dLbls.isSetShowCatName()) {
                    dLbls.getShowCatName().setVal(false);
                } else {
                    dLbls.addNewShowCatName().setVal(false);
                }
                
                // 凡例キーは非表示
                if (dLbls.isSetShowLegendKey()) {
                    dLbls.getShowLegendKey().setVal(false);
                } else {
                    dLbls.addNewShowLegendKey().setVal(false);
                }
                
                // データラベルの位置を設定（円グラフの外側に配置）
                if (!dLbls.isSetDLblPos()) {
                    dLbls.addNewDLblPos().setVal(
                        org.openxmlformats.schemas.drawingml.x2006.chart.STDLblPos.BEST_FIT);
                }
            }
        } catch (Exception e) {
            System.err.println("データラベルの設定中にエラーが発生しました: " + e.getMessage());
        }
    }
}