package pl.dk.wypozyczalnia;

public class CarRow {
    public int carId;
    public String brand;
    public String model;
    public int year;
    public String dailyPrice;
    public String status;

    public CarRow(int carId, String brand, String model, int year, String dailyPrice, String status) {
        this.carId = carId;
        this.brand = brand;
        this.model = model;
        this.year = year;
        this.dailyPrice = dailyPrice;
        this.status = status;
    }

    // potrzebne dla PropertyValueFactory
    public int getCarId() { return carId; }
    public String getBrand() { return brand; }
    public String getModel() { return model; }
    public int getYear() { return year; }
    public String getDailyPrice() { return dailyPrice; }
    public String getStatus() { return status; }
}
