import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import org.apache.commons.lang3.StringUtils;
import org.controlsfx.control.CheckComboBox;
import org.controlsfx.control.IndexedCheckModel;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class WrapperController {

    public static final int DEFAULT_COLUMN_PAD = 10;
    public static final int CUSTOM_COLUMN_PAD = 20;
    public static final int DATE_INDEX = 2;
    public static final int TIME_INDEX = 3;

    private static final String NULL_VALUE = "9999999999.999";
    public static final String COMPONENT_FILE_NAME = "componentCounter.txt";
    private CheckComboBox<String> fileTypesChoiceBox;

    private CheckComboBox<String> satSysChoiceBox;

    @FXML
    private VBox fileTypeVBox;
    @FXML
    private VBox satSysVBox;

    @FXML
    private TextField inputTxtField;

    @FXML
    private TextField outputTxtField;

    @FXML
    private TextField outputName;

    @FXML
    private AnchorPane mainPane;
    @FXML
    private VBox loadPane;

    private static final List<String> FILE_TYPES;

    private static final List<String> SAT_SYSTEMS;

    private static final HashMap<String, String> MAPPED_SAT_SYSTEMS;

    private static final Pattern INPUT_NAME = Pattern.compile(".*\\.[0-9]{2,4}([op])$", Pattern.CASE_INSENSITIVE);

    private static final Pattern WHITESPACE_SPLIT_PATTERN = Pattern.compile("\\s+");

    private static final List<String> SELECTED_COLUMNS = List.of("DATE", "TIME");

    static {
        FILE_TYPES = new ArrayList<>();
        FILE_TYPES.add("Все");
        FILE_TYPES.add("C1");
        FILE_TYPES.add("C2");
        FILE_TYPES.add("C3");
        FILE_TYPES.add("C4");
        FILE_TYPES.add("C5");
        FILE_TYPES.add("C6");
        FILE_TYPES.add("C7");
        FILE_TYPES.add("C8");

        FILE_TYPES.add("L1");
        FILE_TYPES.add("L2");
        FILE_TYPES.add("L3");
        FILE_TYPES.add("L4");
        FILE_TYPES.add("L5");
        FILE_TYPES.add("L6");
        FILE_TYPES.add("L7");
        FILE_TYPES.add("L8");


        SAT_SYSTEMS = new ArrayList<>();
        SAT_SYSTEMS.add("Все");
        SAT_SYSTEMS.add("GPS");
        SAT_SYSTEMS.add("GLONASS");
        SAT_SYSTEMS.add("BeiDou");
        SAT_SYSTEMS.add("Galileo");
        SAT_SYSTEMS.add("IRNSS");
        SAT_SYSTEMS.add("QZSS");
        SAT_SYSTEMS.add("SBAS");


        MAPPED_SAT_SYSTEMS = new HashMap<>();
        MAPPED_SAT_SYSTEMS.put("GPS", "G");
        MAPPED_SAT_SYSTEMS.put("GLONASS", "R");
        MAPPED_SAT_SYSTEMS.put("BeiDou", "C");
        MAPPED_SAT_SYSTEMS.put("Galileo", "E");
        MAPPED_SAT_SYSTEMS.put("IRNSS", "I");
        MAPPED_SAT_SYSTEMS.put("QZSS", "J");
        MAPPED_SAT_SYSTEMS.put("SBAS", "S");
    }

    private static final String SELECT_ALL = "Все";

    private static final String TYPES_PREFIX = "-obs_types";
    private static final String SAT_SYS_PREFIX = "-satsys";


    private static class ColumnIndices {
        Map<String, Integer> nameToIndexMap;
        int dataStartIndex;
    }

    private static class RnxDto {
        public final SystemSatNum systemSatNum;
        public final String line;

        private RnxDto(String system, String satelliteNumber, String line) {
            this.systemSatNum = new SystemSatNum(system, satelliteNumber);
            this.line = line;
        }

        public String getSystem() {
            return systemSatNum.system;
        }

        public String getLine() {
            return line;
        }

        public SystemSatNum getSystemSatNum(){
            return systemSatNum;
        }

    }

    private static class SystemSatNum{
        public final String system;
        public final String satelliteNumber;

        private SystemSatNum(String system, String satelliteNumber) {
            this.system = system;
            this.satelliteNumber = satelliteNumber;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SystemSatNum that = (SystemSatNum) o;
            return Objects.equals(system, that.system) && Objects.equals(satelliteNumber, that.satelliteNumber);
        }

        @Override
        public int hashCode() {
            return Objects.hash(system, satelliteNumber);
        }
    }
    @FXML
    public void initialize() {
        ObservableList<String> fileTypes = FXCollections.observableList(FILE_TYPES);
        fileTypesChoiceBox = new CheckComboBox<>(fileTypes);
        IndexedCheckModel<String> typesCheckModel = fileTypesChoiceBox.getCheckModel();
        ObservableList<String> checkedTypes = typesCheckModel.getCheckedItems();
        checkedTypes.addListener((ListChangeListener<? super String>) c -> {
            if (c.next()) {
                if (c.wasRemoved()) {
                    return;
                }
                if (c.getList().isEmpty()) {
                    return;
                }
                List<? extends String> addedDataTypes = c.getAddedSubList();
                if (addedDataTypes.contains(SELECT_ALL)) {
                    for (int i = 1; i < FILE_TYPES.size(); i++) {
                        typesCheckModel.clearCheck(i);
                    }
                }
            }
        });
        ObservableList<String> satSystems = FXCollections.observableList(SAT_SYSTEMS);
        satSysChoiceBox = new CheckComboBox<>(satSystems);
        IndexedCheckModel<String> satCheckModel = satSysChoiceBox.getCheckModel();
        ObservableList<String> checkedSatSys = satCheckModel.getCheckedItems();
        checkedSatSys.addListener((ListChangeListener<? super String>) c -> {
            if (c.next()) {
                if (c.wasRemoved()) {
                    return;
                }
                if (c.getList().isEmpty()) {
                    return;
                }
                List<? extends String> addedDataTypes = c.getAddedSubList();
                if (addedDataTypes.contains(SELECT_ALL)) {
                    for (int i = 1; i < SAT_SYSTEMS.size(); i++) {
                        satCheckModel.clearCheck(i);
                    }
                }
            }
        });
        fileTypeVBox.getChildren().add(fileTypesChoiceBox);
        satSysVBox.getChildren().add(satSysChoiceBox);
    }

    @FXML
    protected void onOutputBtnClick() {
        boolean isValidated = validateInput();
        if (!isValidated) {
            return;
        }
        runApp();
    }

    public void runApp() {
        String command = getCommand();
        if (command == null) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Ошибка при запуске");
            alert.setHeaderText(null);
            alert.setContentText("Ошибка при определении параметров запуска GFZRNX");
            alert.showAndWait();
            return;
        }
        mainPane.setDisable(true);
        loadPane.setDisable(false);
        loadPane.getChildren().add(new ProgressIndicator());
        loadPane.setAlignment(Pos.CENTER);
        Task<Integer> rinexTask = new Task<>() {
            @Override
            protected Integer call() {
                try {
                    ProcessBuilder builder = new ProcessBuilder();
                    builder.command(tokenizeCommandStr(command));
                    builder.redirectErrorStream(true);
                    Process process = builder.start();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null)
                        System.out.println("tasklist: " + line);
                    return process.waitFor();
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
                return -1;
            }
        };
        AtomicInteger result = new AtomicInteger(-1);
        rinexTask.setOnSucceeded(t -> {
            mainPane.setDisable(false);
            loadPane.setDisable(true);
            loadPane.getChildren().clear();
            result.set(rinexTask.getValue());
            if (result.get() == 0) {
                createRegistryFile();
                createGroupedFiles();
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Статус программы");
                alert.setHeaderText(null);
                alert.setContentText("Программа успешно завершила работу");
                alert.showAndWait();
            } else {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Статус программы");
                alert.setHeaderText(null);
                alert.setContentText("Во время выполнения GFZRNX произошла ошибка");
                alert.showAndWait();
            }
        });
        Thread thread = new Thread(rinexTask);
        thread.start();
    }


    private String getCommand() {
        URL resource = WrapperController.class.getClassLoader().getResource("gfzrnx_2.0.1_win32.exe");
        String pathToExecutable = null;
        if (resource != null) {
            pathToExecutable = resource.getPath();
        } else {
            File exeDirectory = new File("./gfzrnx");
            if (exeDirectory.exists() && exeDirectory.isDirectory()) {
                Path path = null;
                try {
                    path = Files.list(exeDirectory.toPath()).filter(x -> {
                        File file = x.toFile();
                        return file.isFile() && file.getName().endsWith(".exe");
                    }).findFirst().orElse(null);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if (path != null) {
                    pathToExecutable = path.toAbsolutePath().toString();
                }
            }
        }
        if (pathToExecutable == null) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Ошибка при запуске");
            alert.setHeaderText(null);
            alert.setContentText("EXE-файл программы GFZRNX не найден");
            alert.showAndWait();
            return null;
        }
        StringBuilder cmdPromptBuilder = new StringBuilder(pathToExecutable);
        cmdPromptBuilder.append(" -finp ");
        cmdPromptBuilder.append("\"" + inputTxtField.getText() + "\"");
        cmdPromptBuilder.append(" -tab ");
        cmdPromptBuilder.append(" -fout ");
        cmdPromptBuilder.append("\""+outputTxtField.getText());
        cmdPromptBuilder.append(File.separator);
        cmdPromptBuilder.append(outputName.getText()+ "\"");
        cmdPromptBuilder.append(" ");
        cmdPromptBuilder.append(" -f ");
        cmdPromptBuilder.append(getChoiceBoxString(fileTypesChoiceBox, TYPES_PREFIX, ','));
        cmdPromptBuilder.append(getChoiceBoxString(satSysChoiceBox, SAT_SYS_PREFIX, MAPPED_SAT_SYSTEMS, null));
        return cmdPromptBuilder.toString();
    }

    public void pickFile(TextField textField){
        pickFile(textField, false);
    }
    public void pickFile(TextField textField, boolean directoryOnly) {
        if (directoryOnly){
            DirectoryChooser directoryChooser = new DirectoryChooser();
            File file = directoryChooser.showDialog(null);
            if (file != null){
                textField.setText(file.getAbsolutePath());
            }
        } else {
            FileChooser fileChooser = new FileChooser();
            File f = fileChooser.showOpenDialog(null);
            if (f != null) {
                textField.setText(f.getAbsolutePath());
            }
        }
    }

    @FXML
    public void pickInputFile() {
        pickFile(inputTxtField);
    }

    @FXML
    public void pickOutputFile() {
        pickFile(outputTxtField, true);
    }

    private boolean checkFiles() {
        String inputPath = inputTxtField.getText();
        if (inputPath == null || inputPath.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Ошибка при запуске");
            alert.setHeaderText(null);
            alert.setContentText("Входной файл не определен");
            alert.showAndWait();
            return false;
        }
/*        Matcher matcher = INPUT_NAME.matcher(inputPath);
        if (!matcher.find()){
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Предупреждение");
            alert.setHeaderText(null);
            alert.setContentText("Входной файл имеет неподдерживаемый формат, возможны ошибки");
            alert.showAndWait();
        }*/
        String outputPath = outputTxtField.getText();
        if (outputPath == null || outputPath.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Ошибка при запуске");
            alert.setHeaderText(null);
            alert.setContentText("Директория вывода не определена");
            alert.showAndWait();
            return false;
        }
        if (outputName.getText() == null || outputName.getText().isEmpty()){
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Ошибка при запуске");
            alert.setHeaderText(null);
            alert.setContentText("Название файла вывода не определено");
            alert.showAndWait();
            return false;
        }
        return true;
    }

    private boolean checkChoiceBoxes() {
        if (fileTypesChoiceBox.getCheckModel().getCheckedItems().isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Ошибка при запуске");
            alert.setHeaderText(null);
            alert.setContentText("Не выбраны типы данных");
            alert.showAndWait();
            return false;
        }
        if (satSysChoiceBox.getCheckModel().getCheckedItems().isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Ошибка при запуске");
            alert.setHeaderText(null);
            alert.setContentText("Не выбраны типы систем навигации");
            alert.showAndWait();
            return false;
        }
        return true;
    }

    private boolean validateInput() {
        boolean filesCorrect = checkFiles();
        if (!filesCorrect) {
            return false;
        }
        return checkChoiceBoxes();
    }

    private String getChoiceBoxString(CheckComboBox<String> checkComboBox, String
            prefix, @Nullable Character separator) {
        return getChoiceBoxString(checkComboBox, prefix, null, separator);
    }

    private String getChoiceBoxString(CheckComboBox<String> checkComboBox, String
            prefix, @Nullable Map<String, String> nameMap, @Nullable Character separator) {
        ObservableList<String> checkedItems = checkComboBox.getCheckModel().getCheckedItems();
        StringBuilder result = new StringBuilder();
        result.append(prefix);
        result.append(" ");
        List<String> finalList;
        if (checkedItems.contains(SELECT_ALL)) {
            ObservableList<String> items = checkComboBox.getItems();
            if (nameMap == null) {
                finalList = new ArrayList<>(items);
                finalList.remove(SELECT_ALL);
            } else {
                List<String> keyList = new ArrayList<>(items);
                keyList.remove(SELECT_ALL);
                finalList = new ArrayList<>();
                for (String x : keyList) {
                    finalList.add(nameMap.get(x));
                }
            }
        } else {
            if (nameMap == null) {
                finalList = new ArrayList<>(checkedItems);
            } else {
                List<String> keyList = new ArrayList<>(checkedItems);
                finalList = new ArrayList<>();
                for (String x : keyList) {
                    finalList.add(nameMap.get(x));
                }
            }
        }

        for (int i = 0; i < finalList.size(); i++) {
            result.append(finalList.get(i));
            if (separator != null) {
                if (i != finalList.size() - 1) {
                    result.append(separator);
                }
            }
        }
        result.append(" ");
        return result.toString();
    }

    private void createGroupedFiles(){
        String outputFilePath = outputTxtField.getText() + File.separator + outputName.getText();
        File outputFile = new File(outputFilePath);
        List<RnxDto> result = parseOutputFile(outputFile, inputTxtField.getText());
        Map<String, String> systemToHeaderMap = getHeaderMap(outputFile);
        groupDto(result, systemToHeaderMap);
    }

    private void createRegistryFile(){
        String outputFilePath = outputTxtField.getText() + File.separator + outputName.getText();
        File outputFile = new File(outputFilePath);
        try (BufferedReader br = new BufferedReader(new FileReader(outputFile))) {
            String line;
            String currentTime = null;
            List<String> infoBlock = new ArrayList<>();
            String fullFilePath = outputTxtField.getText() + File.separator + COMPONENT_FILE_NAME;
            File currentFile = Paths.get(fullFilePath).toFile();
            try(FileWriter outputWriter = new FileWriter(currentFile)) {
                while ((line = br.readLine()) != null) {
                    if (line.startsWith("#")){
                        continue;
                    }
                    List<String> tokens = Arrays.asList(WHITESPACE_SPLIT_PATTERN.split(line));
                    if (tokens.size() < 5){
                        throw new RuntimeException("Строка неправильного формата: "+ line);
                    }
                    String date = tokens.get(DATE_INDEX);
                    String time = tokens.get(TIME_INDEX);
                    String dateTime = date + " " + time;
                    if (currentTime == null){
                        currentTime= dateTime;
                    } else {
                        if (currentTime.equals(dateTime)){
                            infoBlock.add(line);
                        } else {
                            processInfoBlock(infoBlock, outputWriter);
                            infoBlock.clear();
                            infoBlock.add(line);
                            currentTime = dateTime;
                        }
                    }
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void processInfoBlock(List<String> infoBlock, FileWriter writer) throws IOException {
        Map<String, List<String>> groupedData = infoBlock.stream().collect(Collectors.groupingBy(this::getSystem));
        Set<Map.Entry<String, List<String>>> entries = groupedData.entrySet();
        for (Map.Entry<String, List<String>> entry : entries) {
            List<String> systemBlock = entry.getValue();
            String firstLine = systemBlock.get(0);
            List<String> firstTokens = Arrays.asList(WHITESPACE_SPLIT_PATTERN.split(firstLine));
            String date = firstTokens.get(DATE_INDEX);
            String time = firstTokens.get(TIME_INDEX);
            String dateTime = date + " " + time;
            String systemType = firstTokens.get(1);
            int numberOfColumns = firstTokens.size() - 5;
            int[] countArray = new int[numberOfColumns];
            for (String line : systemBlock) {
                List<String> tokens = Arrays.asList(WHITESPACE_SPLIT_PATTERN.split(line));
                for (int i = 5; i < tokens.size(); i++) {
                    String currentToken = tokens.get(i);
                    if (!NULL_VALUE.equals(currentToken)) {
                        countArray[i-5]++;
                    }
                }
            }
            String finalString = getFinalString(dateTime, systemType, countArray);
            writer.write(finalString);
            writer.write(System.lineSeparator());
        }
    }

    private String getFinalString(String dateTime, String system, int[] countArray){
        StringBuilder s = new StringBuilder(system + " " + dateTime + " ");
        for (int i = 0; i < countArray.length; i++) {
           s.append(countArray[i]).append(" ");
        }
        return s.toString();
    }
    private String getSystem(String line){
        List<String> tokens = Arrays.asList(WHITESPACE_SPLIT_PATTERN.split(line));
        if (tokens.size() < 2){
            throw new RuntimeException("Строка неправильного формата: "+ line);
        }
        return tokens.get(1);
    }
    private Map<String, String> getHeaderMap(File outputFile){
        try (BufferedReader br = new BufferedReader(new FileReader(outputFile))) {
            String line;
            Map<String, String> resultMap = new HashMap<>();
            while ((line = br.readLine()) != null) {
                if (!line.startsWith("#")){
                    continue;
                }
                List<String> tokens = Arrays.asList(WHITESPACE_SPLIT_PATTERN.split(line));
                if (tokens.size() < 2){
                    throw new RuntimeException("Заголовок неправильного формата: "+ line);
                }
                String system = tokens.get(1);
                resultMap.put(system, line);
            }
            return resultMap;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
    //TODO: убьет работу с .P файлами, надо будет пофиксить
    private static String getHeader(File outputFile){
        try (BufferedReader br = new BufferedReader(new FileReader(outputFile))) {
            String line = br.readLine();
            if (line.startsWith("#")){
                return line;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
    public void groupDto(List<RnxDto> dtoList, Map<String, String> headerMap){
        List<String> checked = satSysChoiceBox.getCheckModel().getCheckedItems();
        List<String> checkedMapped = new ArrayList<>();
        checked.forEach(x-> checkedMapped.add(MAPPED_SAT_SYSTEMS.get(x)));
        List<RnxDto> finalList = dtoList;
        if (!checked.contains(SELECT_ALL)){
            finalList = finalList.stream().filter(x -> equalsAny(x.getSystem(), checkedMapped)).collect(Collectors.toList());
        }
        Map<SystemSatNum, List<RnxDto>> listMap = finalList.stream().collect(Collectors.groupingBy(RnxDto::getSystemSatNum));
        String string = outputTxtField.getText() + File.separator + "grouped-files";
        File childFile = new File(string);
        childFile.mkdirs();
        Set<Map.Entry<SystemSatNum, List<RnxDto>>> entries = listMap.entrySet();
        for (Map.Entry<SystemSatNum, List<RnxDto>> entry : entries) {
            File subfolder = Paths.get(string, entry.getKey().system).toFile();
            subfolder.mkdirs();
        }

        for (Map.Entry<SystemSatNum, List<RnxDto>> entry : entries) {
            SystemSatNum key = entry.getKey();
            String header = headerMap.get(key.system);
            ColumnIndices headerIndices = getHeaderIndices(header);
            String formattedHeader = getFormattedHeader(header, headerIndices);
            File currentFile = Paths.get(string, key.system, key.satelliteNumber + ".txt").toFile();
            List<RnxDto> value = entry.getValue();
            try(FileWriter output = new FileWriter(currentFile)) {
                output.write(formattedHeader);
                output.write("\n");
                value.forEach(x-> {
                    try {
                        output.write(getProcessedLine(x.getLine(), headerIndices));
                        output.write(System.lineSeparator());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static String getProcessedLine(String line, @Nullable ColumnIndices headerIndices){
        if (headerIndices == null){
            return line;
        }
        String[] strArr = WHITESPACE_SPLIT_PATTERN.split(line);
        List<String> strings = Arrays.asList(strArr);
//        String date = strings.get(headerIndices.nameToIndexMap.get("DATE"));
        String time = strings.get(headerIndices.nameToIndexMap.get("TIME"));
        LocalTime localTime = LocalTime.parse(time);
        double decimalPart = localTime.getMinute()/60. + localTime.getSecond()/3600.;
        double result = localTime.getHour() + decimalPart;
        StringBuilder builder = new StringBuilder();
        String timeResult = String.format(Locale.US, "%08.6f", result);
        String paddedTime = StringUtils.rightPad(timeResult, DEFAULT_COLUMN_PAD);
        builder.append(paddedTime);
        for (int i = headerIndices.dataStartIndex; i < strings.size(); i++) {
            builder.append(StringUtils.rightPad(strings.get(i), CUSTOM_COLUMN_PAD));
        }
        return builder.toString();
    }

    private static ColumnIndices getHeaderIndices(String header){
        String[] strArr = WHITESPACE_SPLIT_PATTERN.split(header);
        List<String> strings = Arrays.asList(strArr);
        Map<String, Integer> indexMap = new HashMap<>();
        SELECTED_COLUMNS.forEach(x-> {
            int i = strings.indexOf(x);
            if (i != -1){
                indexMap.put(x, i);
            } else {
                throw new RuntimeException("Incorrect Header format");
            }
        });
        ColumnIndices columnIndices = new ColumnIndices();
        columnIndices.dataStartIndex = getDataIndex(strings);
        columnIndices.nameToIndexMap = indexMap;
        return columnIndices;
    }

    private static String getFormattedHeader(String header, ColumnIndices columnIndices){
        String[] strArr = WHITESPACE_SPLIT_PATTERN.split(header);
        List<String> strings = Arrays.asList(strArr);
        StringBuilder result = new StringBuilder("#");
        Set<String> elements = new HashSet<>(columnIndices.nameToIndexMap.keySet());
        elements.remove("DATE");
        elements.forEach(x->result.append(StringUtils.rightPad(x, DEFAULT_COLUMN_PAD)));
        for (int i = columnIndices.dataStartIndex; i < strings.size(); i++) {
           result.append(StringUtils.rightPad(strings.get(i), CUSTOM_COLUMN_PAD));
        }
        return result.toString();
    }

    private static int getDataIndex(List<String> headerList){
        int dataIndex = headerList.indexOf("DATA");
        if (dataIndex != -1){
            return dataIndex;
        }
        int prnIndex = headerList.indexOf("PRN");
        if (prnIndex == -1){
            throw new RuntimeException("Incorrect Header: no PRN");
        }
        if (prnIndex + 1 == headerList.size()){
            throw new RuntimeException("Incorrect Header: PRN is last element");
        }
        return prnIndex + 1;
    }

    public static boolean equalsAny(String source, List<String> list){
        return list.stream().anyMatch(x->x.equals(source));
    }
    public static List<RnxDto> parseOutputFile(File outputFile, String inputFileName){
        List<RnxDto> result = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(outputFile))) {
            String line;
            FormatType type;
            type = getFormat(inputFileName);
            if (type == FormatType.UNKNOWN){
                throw new RuntimeException("Не удалось определить тип входного файла");
            }
            while ((line = br.readLine()) != null) {
                if (line.startsWith("#")){
                    continue;
                }
                result.add(parseOutputLine(line, type));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    private static FormatType getFormat(String fileName){
        String lowerCase = fileName.toLowerCase();
        if (lowerCase.endsWith(".rx3")){
            return FormatType.RX3;
        }
        if (INPUT_NAME.matcher(lowerCase).find()){
            if (lowerCase.endsWith("o")){
                return FormatType.O;
            } else {
                return FormatType.P;
            }
        }
        return FormatType.UNKNOWN;
    }
    private enum FormatType{
        RX3,
        P,
        O,
        UNKNOWN
    }
    public static RnxDto parseOutputLine(String line, FormatType type){
        String[] strArr = WHITESPACE_SPLIT_PATTERN.split(line);
        int sysIndex = 1;
        int satNumIndex = getSatNumIndex(type);
        String system = strArr[sysIndex];
        String satelliteNum = strArr[satNumIndex];
        return new RnxDto(system, satelliteNum, line);
    }

    public static int getSatNumIndex(FormatType type){
        if (type == FormatType.RX3 || type == FormatType.O){
            return  4;
        }
        if (type == FormatType.P){
            return  5;
        }
        throw new RuntimeException("Неизвестный тип записи");
    }

    private static String[] tokenizeCommandStr(String command){
        StringTokenizer st = new StringTokenizer(command);
        String[] cmdarray = new String[st.countTokens()];
        for (int i = 0; st.hasMoreTokens(); i++)
            cmdarray[i] = st.nextToken();
        return cmdarray;
    }
}