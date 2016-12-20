package eu.fthevenet.binjr.controllers;

import eu.fthevenet.binjr.commons.charts.XYChartCrosshair;
import eu.fthevenet.binjr.commons.charts.XYChartSelection;
import eu.fthevenet.binjr.commons.controls.ZonedDateTimePicker;
import eu.fthevenet.binjr.commons.logging.Profiler;
import eu.fthevenet.binjr.data.providers.JRDSDataProvider;
import eu.fthevenet.binjr.data.timeseries.TimeSeriesBuilder;
import eu.fthevenet.binjr.data.timeseries.transform.LargestTriangleThreeBucketsTransform;
import javafx.beans.property.*;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.ValueAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import javafx.util.converter.NumberStringConverter;
import jfxtras.scene.control.CalendarTextField;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.controlsfx.control.ToggleSwitch;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.text.ParseException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by FTT2 on 02/12/2016.
 */
public class TimeSeriesController implements Initializable {
    private static final Logger logger = LogManager.getLogger(TimeSeriesController.class);


    @FXML
    public AnchorPane root;

    @FXML
    public AnchorPane chartParent;
    @FXML
    public CalendarTextField beginDateTime;
    //    @FXML
//    public CalendarTextField endDateTime;
    @FXML
    private AreaChart<ZonedDateTime, Double> chart;
   // @FXML
  //  private CheckBox yAutoRange;
    @FXML
    private TextField yMinRange;
    @FXML
    private TextField yMaxRange;
    @FXML
    ListView<SelectableListItem> seriesList;
    @FXML
    private Button backButton;
    @FXML
    private Button forwardButton;
    @FXML
    private Button resetYButton;
    @FXML
    private ZonedDateTimePicker startDate;
    @FXML
    private ZonedDateTimePicker endDate;

    private MainViewController mainViewController;


    private Property<String> currentHost = new SimpleStringProperty("ngwps006:31001/perf-ui");
    private Property<String> currentTarget = new SimpleStringProperty("ngwps006.mshome.net");
    private Property<String> currentProbe = new SimpleStringProperty("memprocPdh");
    private Map<String, Boolean> selectedSeriesCache = new HashMap<>();
    private XYChartCrosshair<ZonedDateTime, Double> crossHair;

    private State currentState;
    private XYChartSelection<ZonedDateTime, Double> previousState;

    private History backwardHistory = new History();
    private History forwardHistory = new History();

    private ZoneId currentZoneId = ZoneId.systemDefault();

    private Stage getStage(){
        if (chartParent != null && chartParent.getScene() != null){
            return (Stage)chartParent.getScene().getWindow();
        }
        return null;
    }

    public History getBackwardHistory() {
        return backwardHistory;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        assert root != null : "fx:id\"root\" was not injected!";
        assert chart != null : "fx:id\"chart\" was not injected!";
        assert chartParent != null : "fx:id\"chartParent\" was not injected!";
        assert yMinRange != null : "fx:id\"yMinRange\" was not injected!";
        assert yMaxRange != null : "fx:id\"yMaxRange\" was not injected!";
        assert seriesList != null : "fx:id\"seriesList\" was not injected!";
        assert backButton != null : "fx:id\"backButton\" was not injected!";
        assert forwardButton != null : "fx:id\"forwardButton\" was not injected!";
        assert resetYButton != null : "fx:id\"resetYButton\" was not injected!";
        assert startDate != null : "fx:id\"beginDateTime\" was not injected!";
        assert endDate != null : "fx:id\"endDateTime\" was not injected!";


        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT, FormatStyle.MEDIUM);
        NumberStringConverter numberFormatter = new NumberStringConverter(Locale.getDefault(Locale.Category.FORMAT));

        ZonedDateTime now = ZonedDateTime.now();
        this.currentState = new State(now.minus(12, ChronoUnit.HOURS), now, 0, 100);
        plotChart(currentState.asSelection());

        backButton.disableProperty().bind(backwardHistory.emptyStackProperty);
        forwardButton.disableProperty().bind(forwardHistory.emptyStackProperty);
        startDate.dateTimeValueProperty().bindBidirectional(currentState.startX);
        endDate.dateTimeValueProperty().bindBidirectional(currentState.endX);
        seriesList.setCellFactory(CheckBoxListCell.forListView(SelectableListItem::selectedProperty));
        crossHair = new XYChartCrosshair<>(chart, chartParent, dateTimeFormatter::format, (n) -> String.format("%,.2f", n.doubleValue()));

        setAndBindTextFormatter(yMinRange, numberFormatter, currentState.startY,((ValueAxis<Double>) chart.getYAxis()).lowerBoundProperty());
        setAndBindTextFormatter(yMaxRange, numberFormatter, currentState.endY,((ValueAxis<Double>) chart.getYAxis()).upperBoundProperty());
        crossHair.onSelectionDone(s-> {
            logger.debug(() -> "Applying zoom selection: " + s.toString());
            currentState.setSelection(s, true);
        });
    }

    public void invalidate(boolean saveToHistory, boolean plotChart) {
        logger.debug(() -> "Refreshing chart");
        XYChartSelection<ZonedDateTime, Double> currentSelection = currentState.asSelection();
        logger.debug(() -> "currentSelection=" + (currentSelection == null ? "null" : currentSelection.toString()));
        if ( !currentSelection.equals(previousState)) {
            if (saveToHistory) {
                this.backwardHistory.push(previousState);
                this.forwardHistory.clear();
            }
            previousState = currentState.asSelection();
            logger.debug(() -> backwardHistory.dump());
           if (plotChart) {
               plotChart(currentSelection);
           }
        }
        else {
            logger.debug(() -> "State hasn't change, no need to redraw the graph");
        }
    }
    private <T extends Number> void setAndBindTextFormatter(TextField textField, StringConverter<T> converter, Property<T> stateProperty, Property<T> axisBoundProperty) {
        final TextFormatter<T> formatter = new TextFormatter<T>(converter);
        formatter.valueProperty().bindBidirectional(stateProperty);
        axisBoundProperty.bindBidirectional(stateProperty);
        formatter.valueProperty().addListener((observable, o, v) -> {
            chart.getYAxis().setAutoRanging(false);
        });
        textField.setTextFormatter(formatter);
    }

    public XYChartCrosshair<ZonedDateTime, Double> getCrossHair() {
        return crossHair;
    }

    public AreaChart<ZonedDateTime, Double> getChart() {
        return chart;
    }

    private void restoreSelectionFromHistory(History history, History toHistory) {
        if (!history.isEmpty()) {
            toHistory.push(currentState.asSelection());
            currentState.setSelection(history.pop(), false);
        }
          else {
            logger.debug(() -> "History is empty: nothing to go back to.");
        }
    }

    private void plotChart(XYChartSelection<ZonedDateTime, Double> currentSelection) {
        try (Profiler p = Profiler.start("Plotting chart")) {
            chart.getData().clear();
            Map<String, XYChart.Series<ZonedDateTime, Double>> series = getRawData(
                    currentHost.getValue(),
                    currentTarget.getValue(),
                    currentProbe.getValue(),
                    currentSelection.getStartX().toInstant(),
                    currentSelection.getEndX().toInstant());
            chart.getData().addAll(series.values());
            seriesList.getItems().clear();
            for (XYChart.Series s : chart.getData()) {
                SelectableListItem i = new SelectableListItem(s.getName(), true);
                i.selectedProperty().addListener((obs, wasOn, isNowOn) -> {
                    selectedSeriesCache.put(s.getName(), isNowOn);
                    s.getNode().visibleProperty().bindBidirectional(i.selectedProperty());
                });
                i.setSelected(selectedSeriesCache.getOrDefault(s.getName(), true));
                seriesList.getItems().add(i);
            }
        } catch (IOException | ParseException e) {
            logger.error(() -> "Error getting data", e);
            if (getMainViewController() != null) {
                getMainViewController().displayException("Failed to retrieve data from source", e);
            }
           // throw new RuntimeException(e);
        }
    }

    private Map<String, XYChart.Series<ZonedDateTime, Double>> getRawData(String jrdsHost, String target, String probe, Instant begin, Instant end) throws IOException, ParseException {
        JRDSDataProvider dp = new JRDSDataProvider(jrdsHost);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (dp.getData(target, probe, begin, end, out)) {
                final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(currentZoneId);
                TimeSeriesBuilder<Double> timeSeriesBuilder = new TimeSeriesBuilder<>(currentZoneId,
                        s -> {
                            Double val = Double.parseDouble(s);
                            return val.isNaN() ? 0 : val;
                        },
                        s -> ZonedDateTime.parse(s, formatter));
                InputStream in = new ByteArrayInputStream(out.toByteArray());
                return timeSeriesBuilder.fromCSV(in)
                        .transform(new LargestTriangleThreeBucketsTransform<>(1000))
                        .build();
            }
            else {
                throw new IOException(String.format("Failed to retrieve data from JRDS for %s %s %s %s", target, probe, begin.toString(), end.toString()));
            }
        }
    }




    public void handleHistoryBack(ActionEvent actionEvent) {
        restoreSelectionFromHistory(backwardHistory, forwardHistory);

    }
    public void handleHistoryForward(ActionEvent actionEvent) {
        restoreSelectionFromHistory(forwardHistory,backwardHistory);
    }

    public void handleResetYRangeButton(ActionEvent actionEvent) {
        chart.getYAxis().setAutoRanging(true);
    }

    public void handleRefresh(ActionEvent actionEvent) {
        this.invalidate(false, true);
    }

    public MainViewController getMainViewController() {
        return mainViewController;
    }

    public void setMainViewController(MainViewController mainViewController) {
        this.mainViewController = mainViewController;
    }



    public class History {
        private Stack<XYChartSelection<ZonedDateTime, Double>> stack = new Stack<>();
        public SimpleBooleanProperty emptyStackProperty = new SimpleBooleanProperty(true);

        public XYChartSelection<ZonedDateTime, Double> push(XYChartSelection<ZonedDateTime, Double> state) {
            if (state == null){
                logger.warn(()-> "Trying to push null state into backwardHistory");
                return null;
            }
            else {
                emptyStackProperty.set(false);
                return this.stack.push(state);
            }
        }

        public void clear(){
            this.stack.clear();
            emptyStackProperty.set(true);
        }

        public XYChartSelection<ZonedDateTime, Double> pop() {
            XYChartSelection<ZonedDateTime, Double> r = this.stack.pop();
            emptyStackProperty.set(stack.isEmpty());
            return r;
        }

        public boolean isEmpty() {
            return emptyStackProperty.get();
        }


        public String dump() {
            final StringBuilder sb = new StringBuilder("History:");
            AtomicInteger pos = new AtomicInteger(0);
            if (this.isEmpty()){
                sb.append(" { empty }");
            }
            else{
                stack.forEach(h-> {
                    sb.append("\n").append(pos.incrementAndGet()).append(" ->").append(h.toString());
                } );
            }

            return sb.toString();
        }
    }

    /**
     * Created by FTT2 on 07/12/2016.
     */
    public class State {

        private final SimpleObjectProperty<ZonedDateTime> startX;
        private final SimpleObjectProperty<ZonedDateTime> endX;
        private final SimpleDoubleProperty startY;
        private final SimpleDoubleProperty endY;

        private boolean frozen;

     //   private final SimpleObjectProperty<State> state = new SimpleObjectProperty<>();

        public XYChartSelection<ZonedDateTime, Double> asSelection() {
            return new XYChartSelection<>(
                    startX.get(),
                    endX.get(),
                    startY.get(),
                    endY.get()
            );
        }

        public void setSelection(XYChartSelection<ZonedDateTime, Double> selection, boolean toHistory) {
            frozen = true;
            try {
                ZonedDateTime newStartX = roundDateTime(selection.getStartX());
                ZonedDateTime newEndX = roundDateTime(selection.getEndX());
                boolean plotChart = !(newStartX.isEqual(startX.get()) && newEndX.isEqual(endX.get()));
                this.startX.set(newStartX);
                this.endX.set(newEndX);
                this.startY.set(roundYValue(selection.getStartY()));
                this.endY.set(roundYValue(selection.getEndY()));
                invalidate(toHistory, plotChart);
            }
            finally {
                frozen = false;
            }
        }

        public State(ZonedDateTime startX, ZonedDateTime endX, double startY, double endY) {
            this.startX = new SimpleObjectProperty<>(roundDateTime(startX));
            this.endX = new SimpleObjectProperty<>(roundDateTime(endX));
            this.startY = new SimpleDoubleProperty(roundYValue(startY));
            this.endY = new SimpleDoubleProperty(roundYValue(endY));

            this.startX.addListener((observable, oldValue, newValue) -> { if (!frozen) invalidate(true, true);});
            this.endX.addListener((observable, oldValue, newValue) -> { if (!frozen) invalidate(true, true);});
            this.startY.addListener((observable, oldValue, newValue) -> { if (!frozen) invalidate(true, false);});
            this.endY.addListener((observable, oldValue, newValue) -> { if (!frozen) invalidate(true, false);});
        }

        private double roundYValue(double y){
            return Math.round(y);
        }

        private ZonedDateTime roundDateTime(ZonedDateTime zdt){
            return  ZonedDateTime.of(zdt.getYear(),
                    zdt.getMonthValue(),
                    zdt.getDayOfMonth(),
                    zdt.getHour(),
                    zdt.getMinute(),
                    zdt.getSecond(),
                    0,
                    zdt.getZone()
                    );
        }

        @Override
        public String toString() {
            return String.format("State{startX=%s, endX=%s, startY=%s, endY=%s}", startX.get().toString(), endX.get().toString(), startY.get(), endY.get());
        }

    }
}