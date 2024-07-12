#!/bin/bash

# 设置类路径，包含当前目录和jar目录中的所有jar文件
CP=".:code/jar/*"

# 创建必要的文件夹
mkdir -p output log

# 重新编译 Java 文件
echo "编译 Java 文件..."
javac -cp "$CP" -d . code/CSVDataFiller.java

# 定义输入输出文件夹
SOURCE_DIR="input/source"
TARGET_DIR="input/target"
OUTPUT_DIR="output"
SIMILAR_PATH="lexicon/Similar.txt"
COMPOUND_PATH="lexicon/Compound.txt"

# 检查是否启用日志记录
LOG_ENABLED=false
if [ "$1" == "-log" ]; then
    LOG_ENABLED=true
    LOG_FILE="log/log-$(date '+%Y%m%d%H%M%S').log"
    exec > >(tee -a "$LOG_FILE") 2>&1
fi

# 获取源文件总数
TOTAL_FILES=$(ls $SOURCE_DIR/*.csv | wc -l)
CURRENT_FILE=0

# 循环处理每个源文件
for source_file in $SOURCE_DIR/*.csv; do
    CURRENT_FILE=$((CURRENT_FILE + 1))
    echo "正在处理文件：$CURRENT_FILE/$TOTAL_FILES - $(basename "$source_file")"

    # 获取源文件名，不带路径和后缀
    base_name=$(basename "$source_file" .csv)

    # 定义日志参数
    LOG_PARAM=""
    if $LOG_ENABLED; then
        LOG_PARAM="-log"
    fi

    # 第一次匹配
    java -cp "$CP" code.CSVDataFiller $LOG_PARAM <<EOF
$source_file
1 5
$TARGET_DIR/project.csv
0 1
$SIMILAR_PATH
$COMPOUND_PATH
EOF
    modified_file="${source_file%.csv}_modified.csv"

    # 第二次匹配
    java -cp "$CP" code.CSVDataFiller $LOG_PARAM <<EOF
$modified_file
1 6
$TARGET_DIR/project.csv
0 2
$SIMILAR_PATH

EOF
    modified_file="${modified_file%.csv}_modified.csv"

    # 第三次匹配
    java -cp "$CP" code.CSVDataFiller $LOG_PARAM <<EOF
$modified_file
1 7
$TARGET_DIR/medicine.csv
2 0
$SIMILAR_PATH

EOF
    modified_file="${modified_file%.csv}_modified.csv"

    # 第四次匹配
    java -cp "$CP" code.CSVDataFiller $LOG_PARAM <<EOF
$modified_file
1 8
$TARGET_DIR/medicine.csv
2 1
$SIMILAR_PATH

EOF
    final_file="${modified_file%.csv}_modified.csv"

    # 移动最终文件到输出文件夹，并重命名为原始文件名
    mv $final_file "$OUTPUT_DIR/$base_name.csv"

    # 删除所有中间文件
    rm ${source_file%.csv}_modified*.csv
done

echo "所有文件处理完毕。"