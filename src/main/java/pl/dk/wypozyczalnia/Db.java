package pl.dk.wypozyczalnia;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class Db {

    private static final String URL = "jdbc:postgresql://localhost:5432/wypozyczalnia";
    private static final String USER = "app_user";
    private static final String PASS = "app_pass";

    public static List<CarRow> listCars() throws SQLException {
        ArrayList<CarRow> out = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT car_id, brand, model, year, daily_price, status " +
                     "FROM car ORDER BY brand, model"
             );
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                out.add(new CarRow(
                        rs.getInt("car_id"),
                        rs.getString("brand"),
                        rs.getString("model"),
                        rs.getInt("year"),
                        rs.getBigDecimal("daily_price").toPlainString(),
                        rs.getString("status")
                ));
            }
        }

        return out;
    }

    // Transakcja + blokada (FOR UPDATE)
    public static void rentCar(int clientId, int carId, Date start, Date end) throws SQLException {
        Connection conn = DriverManager.getConnection(URL, USER, PASS);
        try {
            conn.setAutoCommit(false);

            // Blokujemy rekord auta, żeby nie dało się wypożyczyć go równolegle
            try (PreparedStatement lock = conn.prepareStatement(
                    "SELECT status FROM car WHERE car_id=? FOR UPDATE")) {
                lock.setInt(1, carId);
                try (ResultSet rs = lock.executeQuery()) {
                    if (!rs.next()) throw new SQLException("Nie ma takiego auta (car_id=" + carId + ").");
                    String status = rs.getString("status");
                    if (!"AVAILABLE".equals(status)) throw new SQLException("Auto nie jest dostępne (status=" + status + ").");
                }
            }

            try (PreparedStatement upd = conn.prepareStatement(
                    "UPDATE car SET status='RENTED' WHERE car_id=?")) {
                upd.setInt(1, carId);
                upd.executeUpdate();
            }

            try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO rental(client_id, car_id, start_date, end_date, status) " +
                    "VALUES (?,?,?,?, 'ACTIVE')")) {
                ins.setInt(1, clientId);
                ins.setInt(2, carId);
                ins.setDate(3, start);
                ins.setDate(4, end);
                ins.executeUpdate();
            }

            conn.commit();
        } catch (SQLException ex) {
            try { conn.rollback(); } catch (SQLException ignored) {}
            throw ex;
        } finally {
            try { conn.close(); } catch (SQLException ignored) {}
        }
    }

    // (BONUS) Zwrot auta w transakcji
    public static void returnCar(int carId) throws SQLException {
        Connection conn = DriverManager.getConnection(URL, USER, PASS);
        try {
            conn.setAutoCommit(false);

            // blokujemy auto
            try (PreparedStatement lock = conn.prepareStatement(
                    "SELECT status FROM car WHERE car_id=? FOR UPDATE")) {
                lock.setInt(1, carId);
                try (ResultSet rs = lock.executeQuery()) {
                    if (!rs.next()) throw new SQLException("Nie ma takiego auta (car_id=" + carId + ").");
                }
            }

            // zamknij aktywne wypożyczenie dla auta (jeśli jest)
            int updated;
            try (PreparedStatement updRental = conn.prepareStatement(
                    "UPDATE rental SET status='RETURNED', returned_at=now() " +
                    "WHERE car_id=? AND status='ACTIVE'")) {
                updRental.setInt(1, carId);
                updated = updRental.executeUpdate();
            }

            if (updated == 0) {
                throw new SQLException("To auto nie ma aktywnego wypożyczenia.");
            }

            // ustaw auto jako dostępne
            try (PreparedStatement updCar = conn.prepareStatement(
                    "UPDATE car SET status='AVAILABLE' WHERE car_id=?")) {
                updCar.setInt(1, carId);
                updCar.executeUpdate();
            }

            conn.commit();
        } catch (SQLException ex) {
            try { conn.rollback(); } catch (SQLException ignored) {}
            throw ex;
        } finally {
            try { conn.close(); } catch (SQLException ignored) {}
        }
    }

    public static List<ClientRow> listClients() throws SQLException {
    List<ClientRow> out = new ArrayList<>();

    try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
         PreparedStatement ps = conn.prepareStatement(
                 "SELECT client_id, first_name, last_name FROM client ORDER BY last_name, first_name"
         );
         ResultSet rs = ps.executeQuery()) {

        while (rs.next()) {
            out.add(new ClientRow(
                    rs.getInt("client_id"),
                    rs.getString("first_name"),
                    rs.getString("last_name")
            ));
        }
    }

    return out;
}

}
