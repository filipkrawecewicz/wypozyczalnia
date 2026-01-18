package pl.dk.wypozyczalnia;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.net.URL;
import java.sql.Date;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class App extends Application {

    // Table + status
    private final TableView<CarRow> table = new TableView<CarRow>();
    private final Label status = new Label("Gotowe.");

    // Filtering
    private final ObservableList<CarRow> masterCars = FXCollections.observableArrayList();
    private final FilteredList<CarRow> filteredCars = new FilteredList<CarRow>(masterCars, c -> true);
    private final TextField searchField = new TextField();
    private final ComboBox<String> statusFilter = new ComboBox<String>();

    // Details panel (no status here)
    private final Label dTitle = new Label("Wybierz auto");
    private final Label dYear = new Label("-");
    private final Label dPrice = new Label("-");
    private final Button dRent = new Button("Wypożycz…");
    private final Button dReturn = new Button("Zwróć");

    @Override
    public void start(Stage stage) {
        BorderPane root = new BorderPane();

        // Header
        Label title = new Label("Wypożyczalnia samochodów");
        title.getStyleClass().add("header-title");

        HBox header = new HBox(title);
        header.getStyleClass().add("header");
        header.setPadding(new Insets(14, 16, 14, 16));
        root.setTop(header);

        // Center: table + details panel
        configureTable();

        SortedList<CarRow> sortedCars = new SortedList<CarRow>(filteredCars);
        sortedCars.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sortedCars);

        StackPane tableWrap = new StackPane(table);
        tableWrap.setPadding(new Insets(12));
        HBox.setHgrow(tableWrap, Priority.ALWAYS);

        VBox details = buildDetailsPanel();

        HBox main = new HBox(12, tableWrap, details);
        main.setPadding(new Insets(12));
        root.setCenter(main);

        // Toolbar buttons
        Button btnRefresh = new Button("Odśwież");
        btnRefresh.getStyleClass().add("primary");
        btnRefresh.setOnAction(e -> refreshCars());

        Button btnRent = new Button("Wypożycz…");
        btnRent.setOnAction(e -> rentSelected());

        Button btnReturn = new Button("Zwróć");
        btnReturn.getStyleClass().add("danger");
        btnReturn.setOnAction(e -> returnSelected());

        // Search field
        searchField.setPromptText("Szukaj: marka lub model...");
        searchField.setPrefWidth(240);
        searchField.textProperty().addListener((obs, a, b) -> applyFilter());

        // Dropdown status filter
        statusFilter.setItems(FXCollections.observableArrayList(
                "Wszystkie",
                "Dostępne",
                "Wypożyczone",
                "Serwis"
        ));
        statusFilter.getSelectionModel().selectFirst();
        statusFilter.setPrefWidth(150);
        statusFilter.valueProperty().addListener((obs, a, b) -> applyFilter());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label hint = new Label("Tip: podwójny klik na auto = Wypożycz…");

        HBox toolbar = new HBox(
                10,
                btnRefresh, btnRent, btnReturn,
                new Separator(),
                searchField, statusFilter,
                spacer,
                hint
        );
        toolbar.getStyleClass().add("toolbar");

        // Status bar
        BorderPane statusBar = new BorderPane();
        statusBar.setLeft(status);
        statusBar.getStyleClass().add("statusbar");

        VBox bottom = new VBox(toolbar, statusBar);
        root.setBottom(bottom);

        Scene scene = new Scene(root, 1150, 660);

        // CSS (safe load)
        URL css = getClass().getResource("/app.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        } else {
            System.out.println("WARN: app.css not found on classpath (create src/main/resources/app.css).");
        }

        stage.setTitle("Wypożyczalnia - JavaFX");
        stage.setScene(scene);
        stage.show();

        // UX: double click row -> rent
        table.setRowFactory(tv -> {
            TableRow<CarRow> row = new TableRow<CarRow>();
            row.setOnMouseClicked(ev -> {
                if (ev.getClickCount() == 2 && !row.isEmpty()) {
                    rentSelected();
                }
            });
            return row;
        });

        // Update details panel on selection
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> updateDetails(newV));

        refreshCars();
    }

    private void configureTable() {
        // You said you already hid ID, so we keep table minimal:
        TableColumn<CarRow, String> colBrand = new TableColumn<CarRow, String>("Marka");
        colBrand.setCellValueFactory(new PropertyValueFactory<CarRow, String>("brand"));
        colBrand.setPrefWidth(220);

        TableColumn<CarRow, String> colModel = new TableColumn<CarRow, String>("Model");
        colModel.setCellValueFactory(new PropertyValueFactory<CarRow, String>("model"));
        colModel.setPrefWidth(260);

        TableColumn<CarRow, String> colStatus = new TableColumn<CarRow, String>("Status");
        colStatus.setCellValueFactory(new PropertyValueFactory<CarRow, String>("status"));
        colStatus.setPrefWidth(90);

        // Icon (dot) + tooltip instead of text
        colStatus.setCellFactory(col -> new TableCell<CarRow, String>() {
            @Override
            protected void updateItem(String st, boolean empty) {
                super.updateItem(st, empty);

                if (empty || st == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }

                Label dot = new Label("●");
                dot.setStyle("-fx-font-size: 16px;");

                Tooltip tip;
                String up = st.toUpperCase();

                if (up.equals("AVAILABLE")) {
                    dot.setStyle("-fx-font-size: 16px; -fx-text-fill: #2ecc71;");
                    tip = new Tooltip("Dostępne");
                } else if (up.equals("RENTED")) {
                    dot.setStyle("-fx-font-size: 16px; -fx-text-fill: #e74c3c;");
                    tip = new Tooltip("Wypożyczone");
                } else if (up.equals("SERVICE")) {
                    dot.setStyle("-fx-font-size: 16px; -fx-text-fill: #95a5a6;");
                    tip = new Tooltip("Serwis");
                } else {
                    dot.setStyle("-fx-font-size: 16px; -fx-text-fill: black;");
                    tip = new Tooltip(st);
                }

                Tooltip.install(dot, tip);
                setGraphic(dot);
                setText(null);
                setAlignment(Pos.CENTER);
            }
        });

        table.getColumns().clear();
        table.getColumns().addAll(colBrand, colModel, colStatus);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
    }

    private VBox buildDetailsPanel() {
        dTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Label lYear = new Label("Rok:");
        Label lPrice = new Label("Cena/dzień:");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        grid.add(lYear, 0, 0);
        grid.add(dYear, 1, 0);

        grid.add(lPrice, 0, 1);
        grid.add(dPrice, 1, 1);

        dRent.getStyleClass().add("primary");
        dReturn.getStyleClass().add("danger");

        dRent.setMaxWidth(Double.MAX_VALUE);
        dReturn.setMaxWidth(Double.MAX_VALUE);

        dRent.setOnAction(e -> rentSelected());
        dReturn.setOnAction(e -> returnSelected());

        VBox box = new VBox(
                12,
                dTitle,
                new Separator(),
                grid,
                new Separator(),
                dRent,
                dReturn
        );

        box.setPadding(new Insets(14));
        box.setPrefWidth(280);
        box.setStyle(
                "-fx-background-color: white;" +
                "-fx-background-radius: 10px;" +
                "-fx-border-radius: 10px;" +
                "-fx-border-color: rgba(0,0,0,0.10);" +
                "-fx-border-width: 1;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 14, 0, 0, 6);"
        );

        dRent.setDisable(true);
        dReturn.setDisable(true);

        return box;
    }

    private void updateDetails(CarRow car) {
        if (car == null) {
            dTitle.setText("Wybierz auto");
            dYear.setText("-");
            dPrice.setText("-");
            dRent.setDisable(true);
            dReturn.setDisable(true);
            return;
        }

        dTitle.setText(car.getBrand() + " " + car.getModel());
        dYear.setText(String.valueOf(car.getYear()));
        dPrice.setText(car.getDailyPrice() + " PLN");

        boolean available = "AVAILABLE".equalsIgnoreCase(car.getStatus());
        boolean rented = "RENTED".equalsIgnoreCase(car.getStatus());

        dRent.setDisable(!available);
        dReturn.setDisable(!rented);
    }

    private void applyFilter() {
        final String q = (searchField.getText() == null) ? "" : searchField.getText().trim().toLowerCase();
        final String choice = (statusFilter.getValue() == null) ? "Wszystkie" : statusFilter.getValue();

        filteredCars.setPredicate(car -> {
            if (car == null) return false;

            // status filter
            if (!"Wszystkie".equals(choice)) {
                String s = (car.getStatus() == null) ? "" : car.getStatus().toUpperCase();

                if ("Dostępne".equals(choice) && !s.equals("AVAILABLE")) return false;
                if ("Wypożyczone".equals(choice) && !s.equals("RENTED")) return false;
                if ("Serwis".equals(choice) && !s.equals("SERVICE")) return false;
            }

            // text filter
            if (q.isEmpty()) return true;

            String brand = (car.getBrand() == null) ? "" : car.getBrand().toLowerCase();
            String model = (car.getModel() == null) ? "" : car.getModel().toLowerCase();
            return brand.contains(q) || model.contains(q);
        });

        status.setText("Pokazuję: " + filteredCars.size() + " aut (z " + masterCars.size() + ").");
    }

    private void refreshCars() {
        try {
            List<CarRow> cars = Db.listCars();
            masterCars.setAll(cars);
            applyFilter();
            updateDetails(table.getSelectionModel().getSelectedItem());
        } catch (Exception ex) {
            status.setText("Błąd: " + ex.getMessage());
            showError(ex);
        }
    }

    private void rentSelected() {
        CarRow selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            status.setText("Wybierz auto w tabeli.");
            return;
        }

        if (!"AVAILABLE".equalsIgnoreCase(selected.getStatus())) {
            status.setText("To auto nie jest dostępne (status=" + selected.getStatus() + ").");
            return;
        }

        Dialog<RentData> dialog = buildRentDialog(selected);
        dialog.showAndWait().ifPresent(data -> {
            try {
                Db.rentCar(data.clientId, data.carId, data.startDate, data.endDate);
                status.setText("Wypożyczono: " + selected.getBrand() + " " + selected.getModel() + " (" + selected.getYear() + ").");
                refreshCars();
            } catch (Exception ex) {
                status.setText("Błąd wypożyczenia: " + ex.getMessage());
                showError(ex);
            }
        });
    }

    private void returnSelected() {
        CarRow selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            status.setText("Wybierz auto w tabeli.");
            return;
        }

        if (!"RENTED".equalsIgnoreCase(selected.getStatus())) {
            status.setText("To auto nie jest wypożyczone (status=" + selected.getStatus() + ").");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Potwierdzenie");
        confirm.setHeaderText("Zwrot auta");
        confirm.setContentText("Zwrócić auto: " + selected.getBrand() + " " + selected.getModel() + " (" + selected.getYear() + ")?");

        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    Db.returnCar(selected.getCarId());
                    status.setText("Zwrócono: " + selected.getBrand() + " " + selected.getModel() + " (" + selected.getYear() + ").");
                    refreshCars();
                } catch (Exception ex) {
                    status.setText("Błąd zwrotu: " + ex.getMessage());
                    showError(ex);
                }
            }
        });
    }

    private Dialog<RentData> buildRentDialog(CarRow car) {
        Dialog<RentData> dialog = new Dialog<RentData>();
        dialog.setTitle("Wypożycz auto");
        dialog.setHeaderText("Auto: " + car.getBrand() + " " + car.getModel() + " (" + car.getYear() + ")");

        ButtonType rentBtn = new ButtonType("Wypożycz", ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(rentBtn, ButtonType.CANCEL);

        ComboBox<ClientRow> clientBox = new ComboBox<ClientRow>();
        clientBox.setPromptText("Wybierz klienta");
        try {
            clientBox.setItems(FXCollections.observableArrayList(Db.listClients()));
        } catch (Exception ex) {
            showError(ex);
        }

        DatePicker startPicker = new DatePicker();
        DatePicker endPicker = new DatePicker();

        Label totalLabel = new Label("0.00 PLN");
        totalLabel.setStyle("-fx-font-weight: bold;");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(12));

        grid.add(new Label("Klient:"), 0, 0);
        grid.add(clientBox, 1, 0);

        grid.add(new Label("Data od:"), 0, 1);
        grid.add(startPicker, 1, 1);

        grid.add(new Label("Data do:"), 0, 2);
        grid.add(endPicker, 1, 2);

        grid.add(new Label("Cena łącznie:"), 0, 3);
        grid.add(totalLabel, 1, 3);

        dialog.getDialogPane().setContent(grid);

        Node okButton = dialog.getDialogPane().lookupButton(rentBtn);
        okButton.setDisable(true);

        Runnable validate = () -> {
            boolean valid =
                    clientBox.getValue() != null &&
                    startPicker.getValue() != null &&
                    endPicker.getValue() != null &&
                    !endPicker.getValue().isBefore(startPicker.getValue());

            if (startPicker.getValue() != null && endPicker.getValue() != null && !endPicker.getValue().isBefore(startPicker.getValue())) {
                int days = daysInclusive(startPicker.getValue(), endPicker.getValue());
                BigDecimal daily = parseMoney(car.getDailyPrice());
                BigDecimal total = daily.multiply(BigDecimal.valueOf(days));
                totalLabel.setText(total.toPlainString() + " PLN (" + days + " dni)");
            } else {
                totalLabel.setText("0.00 PLN");
            }

            okButton.setDisable(!valid);
        };

        clientBox.valueProperty().addListener((obs, a, b) -> validate.run());
        startPicker.valueProperty().addListener((obs, a, b) -> validate.run());
        endPicker.valueProperty().addListener((obs, a, b) -> validate.run());

        dialog.setResultConverter(button -> {
            if (button == rentBtn) {
                ClientRow client = clientBox.getValue();
                Date start = Date.valueOf(startPicker.getValue());
                Date end = Date.valueOf(endPicker.getValue());
                return new RentData(client.getClientId(), car.getCarId(), start, end);
            }
            return null;
        });

        return dialog;
    }

    private int daysInclusive(LocalDate start, LocalDate end) {
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        return (int) Math.max(days, 0);
    }

    private BigDecimal parseMoney(String s) {
        if (s == null) return BigDecimal.ZERO;
        String normalized = s.trim().replace(",", ".");
        return new BigDecimal(normalized);
    }

    private void showError(Exception ex) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Błąd");
        alert.setHeaderText("Wystąpił błąd");
        alert.setContentText(ex.getMessage());
        alert.showAndWait();
    }

    private static class RentData {
        final int clientId;
        final int carId;
        final Date startDate;
        final Date endDate;

        RentData(int clientId, int carId, Date startDate, Date endDate) {
            this.clientId = clientId;
            this.carId = carId;
            this.startDate = startDate;
            this.endDate = endDate;
        }
    }

    public static void main(String[] args) {
        launch();
    }
}
