package pl.dk.wypozyczalnia;

public class ClientRow {
    private final int clientId;
    private final String firstName;
    private final String lastName;

    public ClientRow(int clientId, String firstName, String lastName) {
        this.clientId = clientId;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public int getClientId() {
        return clientId;
    }

    public String getDisplayName() {
        return firstName + " " + lastName;
    }

    @Override
    public String toString() {
        // TO jest kluczowe — ComboBox użyje tego do wyświetlania
        return getDisplayName();
    }
}
