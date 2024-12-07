package se.kau.isgb33;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
import org.bson.conversions.Bson;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Properties;

public class Stub {
    public static void main(String[] args) {
        // Skapa GUI
        JFrame frame = new JFrame("Film Sökare");
        frame.setSize(400, 500);
        frame.setLayout(null);

        JTextArea resultArea = new JTextArea();
        resultArea.setBounds(10, 10, 365, 400);
        resultArea.setLineWrap(true);
        resultArea.setEditable(false);

        JTextField categoryInput = new JTextField();
        categoryInput.setBounds(10, 415, 260, 40);

        JButton searchButton = new JButton("sök");
        searchButton.setBounds(275, 415, 100, 40);

        frame.add(resultArea);
        frame.add(categoryInput);
        frame.add(searchButton);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);

        // Lägg till action listener
        searchButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String genre = categoryInput.getText().trim();
                if (genre.isEmpty()) {
                    resultArea.setText("Vänligen ange en kategori.");
                    return;
                }

                MongoClient mongoClient = null;
                try {
                    // Hämta databasanslutning
                    Properties prop = new Properties();
                    try (InputStream input = new FileInputStream("connection.properties")) {
                        prop.load(input);
                    }

                    ConnectionString connString = new ConnectionString(prop.getProperty("db.connection_string"));
                    MongoClientSettings settings = MongoClientSettings.builder().applyConnectionString(connString).build();
                    mongoClient = MongoClients.create(settings);

                    MongoDatabase database = mongoClient.getDatabase(prop.getProperty("db.name"));
                    MongoCollection<Document> collection = database.getCollection("movies");

                    // Filtrera filmer baserat på kategori
                    Bson filter = Filters.in("genres", genre);
                    AggregateIterable<Document> movies = collection.aggregate(Arrays.asList(
                            Aggregates.match(filter),
                            Aggregates.project(Projections.fields(Projections.include("title", "year"), Projections.exclude("_id"))),
                            Aggregates.sort(Sorts.descending("title")),
                            Aggregates.limit(10)
                    ));

                    // Bygg resultatet
                    StringBuilder result = new StringBuilder();
                    boolean found = false;
                    for (Document movie : movies) {
                        found = true;
                        result.append(movie.getString("title")).append(" (");

                        // Dynamisk hantering av 'year'-fältet
                        Object year = movie.get("year");
                        if (year instanceof Integer) {
                            result.append(year);
                        } else if (year instanceof String) {
                            try {
                                // Remove non-digit characters and parse
                                int parsedYear = Integer.parseInt(((String) year).replaceAll("[^\\d]", ""));
                                result.append(parsedYear);
                            } catch (NumberFormatException ex) {
                                System.err.println("Invalid year format for movie: " + movie.getString("title"));
                                result.append("N/A");
                            }
                        } else {
                            result.append("N/A");
                        }

                        result.append(")\n");
                    }

                    if (!found) {
                        resultArea.setText("Ingen film matchade kategorin.");
                    } else {
                        resultArea.setText(result.toString());
                    }
                } catch (IOException ex) {
                    resultArea.setText("Kunde inte läsa anslutningsfilen.");
                    ex.printStackTrace();
                } catch (Exception ex) {
                    resultArea.setText("Ett fel inträffade: " + ex.getMessage());
                    ex.printStackTrace();
                } finally {
                    if (mongoClient != null) {
                        mongoClient.close();
                    }
                }
            }
        });
    }
}