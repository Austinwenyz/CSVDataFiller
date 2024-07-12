package code;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import org.apache.commons.text.similarity.JaccardSimilarity;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvException;

public class CSVDataFiller {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        boolean logEnabled = Arrays.asList(args).contains("-log");

        try {
            // 输入源CSV文件路径
            System.out.println("请输入源CSV文件的完整路径：");
            String sourcePath = scanner.nextLine();
            if (!new File(sourcePath).exists()) {
                System.out.println("源文件不存在，请重新输入！");
                return;
            }

            CSVReader reader = new CSVReader(new FileReader(sourcePath));
            List<String[]> records = preprocessRecords(reader.readAll());
            String[] headers = records.get(0);

            // 输入匹配项列和填写目标列索引
            System.out.println("请选择一个列作为匹配项，然后选择一个列作为填写目标。输入两个数字索引，以空格分隔：");
            printHeadersWithIndex(headers);
            String[] indices = scanner.nextLine().split(" ");
            if (indices.length != 2 || !isValidIndex(indices, headers.length)) {
                System.out.println("输入的索引无效！");
                return;
            }
            int matchIndex = Integer.parseInt(indices[0]);
            int targetIndex = Integer.parseInt(indices[1]);

            // 输入目标匹配CSV文件路径
            System.out.println("请输入目标匹配CSV文件的完整路径：");
            String targetPath = scanner.nextLine();
            if (!new File(targetPath).exists()) {
                System.out.println("目标文件不存在，请重新输入！");
                return;
            }

            CSVReader targetReader = new CSVReader(new FileReader(targetPath));
            List<String[]> targetRecords = preprocessRecords(targetReader.readAll());
            String[] targetHeaders = targetRecords.get(0);

            // 显示目标文件表头并选择匹配列和答案列
            System.out.println("目标文件表头如下，请查看：");
            printHeadersWithIndex(targetHeaders);
            System.out.println("请选择目标文件中的匹配列和答案列。输入两个数字索引，以空格分隔：");
            indices = scanner.nextLine().split(" ");
            if (indices.length != 2 || !isValidIndex(indices, targetHeaders.length)) {
                System.out.println("输入的索引无效！");
                return;
            }
            int targetMatchIndex = Integer.parseInt(indices[0]);
            int answerIndex = Integer.parseInt(indices[1]);

            // 输入近似词词库路径
            System.out.println("请输入近似词词库的完整路径（如果没有，请留空）：");
            String synonymPath = scanner.nextLine();
            Map<String, Set<String>> synonyms = synonymPath.isEmpty() ? new HashMap<>() : loadSynonyms(synonymPath);

            // 输入组合词库路径
            System.out.println("请输入组合词库的完整路径（如果没有，请留空）：");
            String combinationPath = scanner.nextLine();
            Map<String, String[]> combinationDecompositionLibrary = combinationPath.isEmpty() ? new HashMap<>() : loadCombinationDecompositionLibrary(combinationPath);

            // 应用组合分解匹配
            records = applyCombinationDecomposition(records, matchIndex, combinationDecompositionLibrary, logEnabled);

            if (logEnabled) {
                System.out.println("日志记录已启用，显示详细匹配过程。");
            }

            // 处理每一行数据
            for (int i = 1; i < records.size(); i++) {
                String rawMatchValue = records.get(i)[matchIndex].trim();
                String matchValue = cleanMatchItem(rawMatchValue);

                if (logEnabled) {
                    System.out.printf("行 %d：原始主匹配数据 '%s', 清洗后主匹配数据 '%s'\n", i + 1, rawMatchValue, matchValue);
                }

                if (matchValue.isEmpty()) {
                    continue;
                }
                String answer = findAnswer(targetRecords, targetMatchIndex, answerIndex, matchValue, synonyms, logEnabled, i);
                records.get(i)[targetIndex] = answer;
            }

            // 输出修改后的CSV文件
            String outputPath = sourcePath.replace(".csv", "_modified.csv");
            CSVWriter writer = new CSVWriter(new FileWriter(outputPath));
            writer.writeAll(records);
            writer.close();
            System.out.println("处理完成，修改后的文件已输出到：" + outputPath);
        } catch (IOException e) {
            System.err.println("文件读写错误：" + e.getMessage());
        } catch (CsvException e) {
            System.err.println("处理CSV时发生错误：" + e.getMessage());
        } catch (Exception e) {
            System.err.println("发生了一个错误：" + e.getMessage());
        } finally {
            scanner.close();
        }
    }

    // 查找答案方法
    private static String findAnswer(List<String[]> targetRecords, int targetMatchIndex, int answerIndex, String matchValue, Map<String, Set<String>> synonyms, boolean logEnabled, int lineNumber) {
        JaccardSimilarity jaccard = new JaccardSimilarity();
        String bestMatch = "";
        double highestScore = 0.0;
        String bestMatchAnswer = "";

        Set<String> matchCandidates = synonyms.getOrDefault(matchValue, new HashSet<>());
        matchCandidates.add(matchValue); // 确保原始匹配值也被考虑

        for (String[] record : targetRecords) {
            String candidate = cleanMatchItem(record[targetMatchIndex]);
            double score = 0.0;

            // 使用所有的近似词候选进行比较
            for (String matchCandidate : matchCandidates) {
                double currentScore = jaccard.apply(tokenize(matchCandidate), tokenize(candidate)); // 计算相似度得分
                score = Math.max(score, currentScore); // 从所有近似词中取最高分
            }

            score = Math.min(score, 1.0); // 确保得分不超过100%

            // 如果启用日志，记录每个候选项的得分
            //if (logEnabled) {
            //    System.out.printf("行 %d：候选 '%s'，得分 %.4f\n", lineNumber + 1, candidate, score);
            //}

            // 如果此候选项的得分更高，更新最佳匹配
            if (score > highestScore) {
                highestScore = score;
                bestMatch = candidate;
                bestMatchAnswer = record[answerIndex];
            }
        }

        // 检查最高分是否达到置信度阈值
        if (highestScore < 0.60) { // 60% 阈值
            if (logEnabled) {
                System.out.printf("行 %d：'%s' 匹配到 '%s' 的置信度 %.2f%% 低于阈值，未进行匹配。\n", lineNumber + 1, matchValue, bestMatch, highestScore * 100);
            }
            return ""; // 如果低于阈值，返回空字符串
        }

        // 如果启用日志，记录最终选择
        if (logEnabled && !bestMatch.isEmpty()) {
            System.out.printf("行 %d：'%s' 最终匹配到 '%s'，置信度 %.2f%%，输入 '%s' 为答案\n", lineNumber + 1, matchValue, bestMatch, highestScore * 100, bestMatchAnswer);
        }

        return bestMatch.isEmpty() ? "" : bestMatchAnswer; // 返回最佳匹配的答案
    }

    // 简单的中文分词方法
    private static String tokenize(String text) {
        return String.join(" ", text.split(""));
    }

    // 载入近似词库方法
    private static Map<String, Set<String>> loadSynonyms(String path) throws IOException {
        Map<String, Set<String>> synonymsMap = new HashMap<>();
        File file = new File(path);
        if (!file.exists()) {
            System.out.println("指定的近似词词库文件不存在，请检查路径：" + path);
            return synonymsMap;
        }

        try (Scanner scanner = new Scanner(new FileReader(file))) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                String[] parts = line.split(" -", 2); // 确保只拆分成两部分
                if (parts.length < 2) {
                    System.out.println("跳过格式错误的行：" + line);
                    continue; // 跳过格式不正确的行
                }
                String key = parts[0].trim();
                String[] synonyms = parts[1].split("/");
                synonymsMap.put(key, new HashSet<>(Arrays.asList(synonyms)));
            }
        }
        return synonymsMap;
    }

    private static String cleanMatchItem(String data) {
        // 使用正则表达式删除所有类型的括号及其内的所有内容，包括嵌套的括号
        data = data.replaceAll("\\([^\\(\\)]*\\)", "")
                   .replaceAll("\\{[^\\{\\}]*\\}", "")
                   .replaceAll("\\[[^\\[\\]]*\\]", "");
    
        // 再次删除所有括号、空格和数字
        return data.replaceAll("[\\(\\)\\[\\]\\{\\}\\s\\d]", "").trim();
    }

    // 验证索引的有效性
    private static boolean isValidIndex(String[] indices, int length) {
        try {
            for (String index : indices) {
                int idx = Integer.parseInt(index);
                if (idx < 0 || idx >= length) {
                    return false;
                }
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // 预处理记录的方法
    private static List<String[]> preprocessRecords(List<String[]> records) {
        List<String[]> cleanedRecords = new ArrayList<>();
        for (String[] record : records) {
            String[] cleanedRecord = Arrays.stream(record)
                    .map(s -> s.replaceAll("\\s+", ""))
                    .toArray(String[]::new);
            if (Arrays.stream(cleanedRecord).anyMatch(s -> !s.isEmpty())) {
                cleanedRecords.add(cleanedRecord);
            }
        }
        return cleanedRecords;
    }

    // 打印表头并显示索引
    private static void printHeadersWithIndex(String[] headers) {
        for (int i = 0; i < headers.length; i++) {
            System.out.println(i + " - " + headers[i]);
        }
    }

    // 载入组合词库的方法
    private static Map<String, String[]> loadCombinationDecompositionLibrary(String path) throws IOException {
        Map<String, String[]> library = new HashMap<>();
        try (Scanner scanner = new Scanner(new FileReader(path))) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                String[] parts = line.split(" -", 2);
                if (parts.length < 2) {
                    continue; // 跳过格式错误的行
                }
                String key = parts[0].trim();
                String[] components = parts[1].split("/");
                library.put(key, components);
            }
        }
        return library;
    }

    // 应用组合分解匹配的方法
    private static List<String[]> applyCombinationDecomposition(List<String[]> records, int matchIndex, Map<String, String[]> library, boolean logEnabled) {
        List<String[]> newRecords = new ArrayList<>();
        JaccardSimilarity jaccard = new JaccardSimilarity();

        for (String[] record : records) {
            String matchValue = cleanMatchItem(record[matchIndex]);
            String bestMatch = null;
            double highestScore = 0.0;

            for (String key : library.keySet()) {
                double score = jaccard.apply(tokenize(matchValue), tokenize(key));
                if (score > highestScore) {
                    highestScore = score;
                    bestMatch = key;
                }
            }

            if (highestScore >= 0.60 && bestMatch != null) {
                newRecords.add(record); // 保留原始记录
                String[] components = library.get(bestMatch);
                for (String component : components) {
                    String[] newRecord = new String[record.length]; // 创建一个空记录
                    Arrays.fill(newRecord, ""); // 用空字符串填充所有字段
                    newRecord[matchIndex] = component; // 仅设置分解后的组件
                    newRecords.add(newRecord);
                }
                if (logEnabled) {
                    System.out.printf("分组词匹配项 '%s' 被识别为 '%s'，置信度 %.2f%%\n", matchValue, bestMatch, highestScore * 100);
                }
            } else {
                newRecords.add(record);
            }
        }
        return newRecords;
    }
}