## CSVDataFiller 使用说明

### 目录

- [简介](#简介)
- [依赖](#依赖)
- [编译与运行](#编译与运行)
- [功能说明](#功能说明)
- [代码结构](#代码结构)
- [详细功能说明](#详细功能说明)
  - [近似词库](#近似词库)
  - [组合词库](#组合词库)
- [使用示例](#使用示例)
- [常见问题](#常见问题)

### 简介

`CSVDataFiller` 是一个 Java 程序，用于处理和填充 CSV 文件中的数据。该程序可以通过模糊匹配、组合分解等方式在源文件和目标文件之间进行数据匹配，并自动填充相应的数据。

### 依赖

- Java 8 或更高版本
- Apache Commons Text 库（用于计算 Jaccard 相似度）
- OpenCSV 库（用于读取和写入 CSV 文件）

### 编译与运行

#### 编译

在命令行中执行以下命令编译 Java 文件：

```bash
javac -cp "path/to/commons-text.jar:path/to/opencsv.jar" -d . code/CSVDataFiller.java
```

#### 运行

运行程序：

```bash
java -cp ".:path/to/commons-text.jar:path/to/opencsv.jar" code.CSVDataFiller
```

### 功能说明

1. **读取 CSV 文件**：程序首先读取源 CSV 文件和目标 CSV 文件的内容。
2. **用户输入**：用户选择匹配项列和填写目标列的索引。
3. **模糊匹配**：程序使用 Jaccard 相似度进行模糊匹配，找出最相似的记录。
4. **组合分解**：程序可以根据组合词库对数据进行分解匹配。
5. **填充数据**：程序将匹配结果填充到源文件中的目标列。
6. **日志记录**：如果启用日志记录，程序会详细记录匹配过程。

### 代码结构

- `main` 方法：程序入口，负责读取输入、调用处理函数并输出结果。
- `findAnswer` 方法：在目标文件中查找最匹配的答案。
- `tokenize` 方法：对中文字符串进行分词。
- `loadSynonyms` 方法：加载近似词词库。
- `cleanMatchItem` 方法：清理匹配项数据，删除括号内的内容、括号、空格和数字。
- `isValidIndex` 方法：验证用户输入的索引是否有效。
- `preprocessRecords` 方法：预处理 CSV 文件记录，去除空格。
- `printHeadersWithIndex` 方法：打印 CSV 文件的表头和索引。
- `loadCombinationDecompositionLibrary` 方法：加载组合分解词库。
- `applyCombinationDecomposition` 方法：应用组合分解匹配。

### 详细功能说明

#### 近似词库

近似词库用于存储近似词和其对应的近义词。在数据匹配过程中，程序会使用近似词库中的近义词进行模糊匹配，提高匹配的准确性。

**词库文件格式**：
每行一个词条，格式为：
```
词条 - 近义词1/近义词2/近义词3
```

例如：
```
Apple - 苹果/苹
Banana - 香蕉/蕉
Orange - 橙子/橙
```

**加载词库的方法**：

```java
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
```

#### 组合词库

组合词库用于存储组合词及其分解后的组成部分。在数据处理过程中，如果源文件中的匹配项是一个组合词，程序会将其分解为多个组成部分进行匹配。

**词库文件格式**：
每行一个词条，格式为：
```
组合词 - 组成部分1/组成部分2/组成部分3
```

例如：
```
肝功能8项 - 血清总胆红素测定/血清直接胆红素测定/血清总蛋白测定/血清天门冬氨酸氨基转移酶测定/血清γ-谷氨酰基转移酶测定/血清碱性磷酸酶测定/血清白蛋白测定/血清丙氨酸氨基转移酶测定
```

**加载词库的方法**：

```java
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
```

**应用组合分解匹配的方法**：

```java
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
```

### 使用示例

#### 示例 CSV 文件

源文件 `source.csv`：

```
A,B,C,D
1,Apple,,Fruit
2,Banana,,Fruit
3,Orange,,Fruit
```

目标文件 `target.csv`：

```
X,Y,Z
Fruit,Apple,Red
Fruit,Banana,Yellow
Fruit,Orange,Orange
```

#### 运行程序

```bash
java -cp ".:path/to/commons-text.jar:path/to/opencsv.jar" code.CSVDataFiller
```

按照提示输入文件路径和列索引：

```
请输入源CSV文件的完整路径：
input/source.csv
请选择一个列作为匹配项，然后选择一个列作为填写目标。输入两个数字索引，以空格分隔：
1 2
请输入目标匹配CSV文件的完整路径：
input/target.csv
目标文件表头如下，请查看：
0 - X
1 - Y
2 - Z
请选择目标文件中的匹配列和答案列。输入两个数字索引，以空格分隔：
1 2
请输入近似词词库的完整路径（如果没有，请留空）：
lexicon/Similar.txt
请输入组合词库的完整路径（如果没有，请留空）：
lexicon/Compound.txt
```

程序将处理文件并输出结果：

```
处理完成，修改后的文件已输出到：input/source_modified.csv
```

### 常见问题

#### 如何启用日志记录？

运行程序时添加 `-log` 参数即可启用日志记录。

```bash
java -cp ".:path/to/commons-text
.jar:path/to/opencsv.jar" code.CSVDataFiller -log
```

#### 处理大文件时程序卡住怎么办？

如果处理大文件时程序卡住，可以尝试以下方法：
- 确保有足够的内存和处理能力。
- 尝试分批处理文件。

#### 如何配置词库？

词库文件使用简单的键值对格式，每行一个键值对，使用 `-` 分隔键和值，值可以有多个，用 `/` 分隔。例如：

**近似词词库示例**：
```
Apple - 苹果/苹
Banana - 香蕉/蕉
Orange - 橙子/橙
```

**组合词词库示例**：
```
肝功能8项 - 血清总胆红素测定/血清直接胆红素测定/血清总蛋白测定/血清天门冬氨酸氨基转移酶测定/血清γ-谷氨酰基转移酶测定/血清碱性磷酸酶测定/血清白蛋白测定/血清丙氨酸氨基转移酶测定
```

将词库文件保存到 `lexicon` 目录下，然后在程序运行时输入词库文件的路径即可。

# CSV 数据处理脚本

该 bash 脚本旨在通过 Java 程序 (`CSVDataFiller`) 自动化处理 CSV 文件。它负责编译 Java 文件、管理输入/输出目录、记录日志和显示进度。

## 前提条件

1. 确保已安装 Java 开发工具包 (JDK)。
2. `CSVDataFiller.java` 文件应位于 `code/` 目录中。
3. 所有必要的 jar 文件应位于 `code/jar/` 目录中。
4. 输入的 CSV 文件应放置在 `input/source/` 目录中。
5. 目标 CSV 文件应放置在 `input/target/` 目录中。
6. 相似词词典应放在 `lexicon/Similar.txt` 中。
7. 复合词词典应放在 `lexicon/Compound.txt` 中。

## 脚本概述

### 目录结构

```
project_root/
├── code/
│   ├── jar/
│   └── CSVDataFiller.java
├── input/
│   ├── source/
│   └── target/
├── lexicon/
│   ├── Similar.txt
│   └── Compound.txt
├── output/
└── log/
```

### 脚本执行的步骤

1. **设置类路径**：包含当前目录和 `code/jar/` 中的所有 jar 文件。
2. **创建必要的文件夹**：确保 `output` 和 `log` 目录存在。
3. **编译 Java 文件**：编译 `CSVDataFiller.java` 文件。
4. **定义输入和输出目录**：设置源文件、目标文件、输出文件和词典文件的路径。
5. **启用日志记录（可选）**：如果使用 `-log` 标志运行脚本，则启用日志记录并创建带时间戳的日志目录。
6. **处理每个源文件**：
   - 循环处理 `input/source/` 目录中的所有 CSV 文件。
   - 对每个文件执行四个任务，每个任务有特定的匹配索引和额外参数。
   - 处理完后，将最终文件移动到 `output/` 目录，并重命名为原始文件名。
   - 删除所有中间文件。

### 使用方法

1. 将源 CSV 文件放入 `input/source/` 目录。
2. 将目标 CSV 文件放入 `input/target/` 目录。
3. 在终端中运行脚本：

   ```bash
   ./script.sh
   ```

   如果需要记录日志，可以使用 `-log` 标志：

   ```bash
   ./script.sh -log
   ```
